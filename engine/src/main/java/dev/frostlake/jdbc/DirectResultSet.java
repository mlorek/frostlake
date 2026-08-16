/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.ClientValueText;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple ResultSet adapter for engine ResultSet with updatable support
 */
public class DirectResultSet implements java.sql.ResultSet {

    private final ResultSet engineResultSet;
    private final Statement statement;
    private boolean closed = false;

    // Cursor position for the JDBC location predicates: 0 = before the first row; incremented by
    // next(); exhausted once next() has returned false after consuming rows.
    private int positionRow = 0;
    private boolean exhausted = false;
    private boolean lastReadWasNull = false;

    // Updatable ResultSet support
    private final DatabaseEngine engine;
    private final String tableName;
    private final List<String> keyColumns;
    private final Map<String, Object> currentRowValues;
    private final Map<String, Object> updatedValues;
    private final Map<String, Object> insertRowValues;
    private boolean onInsertRow = false;

    /**
     * Run one of this result set's write-back statements (updateRow/insertRow/deleteRow) under the
     * owning connection's session scope, so its unqualified table name resolves exactly as the query
     * that produced these rows did; falls back to the raw engine when the owner is not a direct
     * connection (embedded callers).
     */
    private void executeWriteback(final String sql) throws SQLException {
        final java.sql.Connection owner = statement != null ? statement.getConnection() : null;
        if (owner instanceof DirectConnection) {
            ((DirectConnection) owner).executeScoped(sql);
            return;
        }
        engine.execute(sql);
    }

    public DirectResultSet(final Statement statement, final ResultSet engineResultSet) {
        this(statement, engineResultSet, null, null, null);
    }

    public DirectResultSet(final Statement statement, final ResultSet engineResultSet,
                          final DatabaseEngine engine, final String tableName, final List<String> keyColumns) {
        this.statement = statement;
        this.engineResultSet = engineResultSet;
        this.engine = engine;
        this.tableName = tableName;
        this.keyColumns = keyColumns != null ? keyColumns : new ArrayList<>();
        this.currentRowValues = new HashMap<>();
        this.updatedValues = new HashMap<>();
        this.insertRowValues = new HashMap<>();
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        final boolean hasNext = engineResultSet.next();
        if (hasNext) {
            positionRow++;
            // Clear updated values when moving to new row
            updatedValues.clear();
            onInsertRow = false;
            // Capture current row values for potential updates
            captureCurrentRowValues();
        } else {
            exhausted = true;
        }
        return hasNext;
    }

    private void captureCurrentRowValues() throws SQLException {
        currentRowValues.clear();
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            final String columnName = columns.get(i).getName();
            final Object value = engineResultSet.getValue(i);
            currentRowValues.put(columnName.toUpperCase(), value);
        }
    }

    @Override
    public String getString(final int columnIndex) throws SQLException {
        checkClosed();
        return ClientValueText.render(readValue(columnIndex), declaredType(columnIndex - 1));
    }

    @Override
    public String getString(final String columnLabel) throws SQLException {
        checkClosed();
        return ClientValueText.render(readValue(columnLabel), declaredType(indexOf(columnLabel)));
    }

    /** The declared type of a 0-based column, or null when the index is out of range. */
    private DataType declaredType(final int index) {
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        return index >= 0 && index < columns.size() ? columns.get(index).getDataType() : null;
    }

    /** The 0-based position of a named column, or -1. */
    private int indexOf(final String columnLabel) {
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnLabel)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getInt(final int columnIndex) throws SQLException {
        checkClosed();
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public int getInt(final String columnLabel) throws SQLException {
        checkClosed();
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    @Override
    public Object getObject(final int columnIndex) throws SQLException {
        checkClosed();
        return unwrapEngineValue(engineResultSet.getValue(columnIndex - 1), declaredType(columnIndex - 1));
    }

    @Override
    public Object getObject(final String columnLabel) throws SQLException {
        checkClosed();
        return unwrapEngineValue(engineResultSet.getValue(columnLabel), declaredType(indexOf(columnLabel)));
    }

    /**
     * Map engine-internal value objects to their JDBC-visible form (BINARY cells become byte[]). An
     * APPROXIMATE column's value is a Double whatever carrier the engine holds it in — the JDBC contract
     * for FLOAT and DOUBLE, and what the account's driver hands back — so an expression the engine
     * computed exactly still reaches the client as the double its declared type promises.
     */
    private Object unwrapEngineValue(final Object value, final DataType declared) {
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        if (value instanceof VariantValue) {
            // Snowflake's JDBC driver surfaces VARIANT/OBJECT/ARRAY as their JSON text, a DOUBLE in the
            // fifteen-decimal form: the text the HTTP transport carries for the same cell.
            return VariantJsonText.clientTextOf((VariantValue) value);
        }
        if (value instanceof String && VariantJsonText.isSemiStructured(declared)) {
            // A string read out of a VARIANT is its JSON text too, quotes included, as getString spells it.
            return VariantJsonText.unwrappedStringText((String) value);
        }
        if (value instanceof Number && !(value instanceof Double) && NumericType.isApproximate(declared)) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        return value;
    }

    /** The engine-native result this JDBC view wraps — for embedders needing the typed columns. */
    public ResultSet getEngineResultSet() {
        return engineResultSet;
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("ResultSet is closed");
        }
    }

    private void checkUpdatable() throws SQLException {
        checkClosed();
        if (engine == null || tableName == null) {
            throw new SQLException("ResultSet is not updatable");
        }
    }

    private String getColumnName(final int columnIndex) throws SQLException {
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        if (columnIndex < 1 || columnIndex > columns.size()) {
            throw new SQLException("Invalid column index: " + columnIndex);
        }
        return columns.get(columnIndex - 1).getName().toUpperCase();
    }

    @Override
    public int getRow() throws SQLException {
        // The current row number (1-based), 0 when before the first row or after the last.
        return exhausted ? 0 : positionRow;
    }

    @Override
    public Statement getStatement() throws SQLException {
        return statement;
    }

    // Minimal implementations for other required methods

    @Override
    public boolean wasNull() throws SQLException {
        return lastReadWasNull;
    }

    /** Read a column by 1-based index, recording its nullness for {@link #wasNull()}. */
    private Object readValue(final int columnIndex) {
        final Object value = engineResultSet.getValue(columnIndex - 1);
        lastReadWasNull = value == null;
        return value;
    }

    /** Read a column by label, recording its nullness for {@link #wasNull()}. */
    private Object readValue(final String columnLabel) {
        final Object value = engineResultSet.getValue(columnLabel);
        lastReadWasNull = value == null;
        return value;
    }

    @Override
    public boolean getBoolean(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public byte getByte(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).byteValue();
        return Byte.parseByte(value.toString());
    }

    @Override
    public short getShort(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).shortValue();
        return Short.parseShort(value.toString());
    }

    @Override
    public long getLong(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    @Override
    public float getFloat(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }

    @Override
    public double getDouble(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex, final int scale) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    @Override
    public byte[] getBytes(final int columnIndex) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toBytes(engineResultSet.getValue(columnIndex - 1));
    }

    @Override
    public Date getDate(final int columnIndex) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toDate(engineResultSet.getValue(columnIndex - 1));
    }

    @Override
    public Time getTime(final int columnIndex) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toTime(engineResultSet.getValue(columnIndex - 1));
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toTimestamp(engineResultSet.getValue(columnIndex - 1));
    }

    @Override
    public InputStream getAsciiStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAsciiStream not supported");
    }

    @Override
    public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getUnicodeStream not supported");
    }

    @Override
    public InputStream getBinaryStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBinaryStream not supported");
    }

    @Override
    public boolean getBoolean(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public byte getByte(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).byteValue();
        return Byte.parseByte(value.toString());
    }

    @Override
    public short getShort(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).shortValue();
        return Short.parseShort(value.toString());
    }

    @Override
    public long getLong(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    @Override
    public float getFloat(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }

    @Override
    public double getDouble(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    @Override
    public BigDecimal getBigDecimal(final String columnLabel, final int scale) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    // Remaining unimplemented methods - throw UnsupportedOperationException or return defaults

    @Override
    public byte[] getBytes(final String columnLabel) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toBytes(engineResultSet.getValue(columnLabel));
    }

    @Override
    public Date getDate(final String columnLabel) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toDate(engineResultSet.getValue(columnLabel));
    }

    @Override
    public Time getTime(final String columnLabel) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toTime(engineResultSet.getValue(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(final String columnLabel) throws SQLException {
        checkClosed();
        return JdbcMarshaling.toTimestamp(engineResultSet.getValue(columnLabel));
    }

    @Override
    public InputStream getAsciiStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getUnicodeStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getBinaryStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        // No-op
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return new DirectResultSetMetaData(engineResultSet.getColumns());
    }

    @Override
    public int findColumn(final String columnLabel) throws SQLException {
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnLabel)) {
                return i + 1;
            }
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    @Override
    public Reader getCharacterStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Reader getCharacterStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
        final Object value = readValue(columnIndex);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    @Override
    public BigDecimal getBigDecimal(final String columnLabel) throws SQLException {
        final Object value = readValue(columnLabel);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return positionRow == 0 && !engineResultSet.getRows().isEmpty();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return exhausted && !engineResultSet.getRows().isEmpty();
    }

    @Override
    public boolean isFirst() throws SQLException {
        return positionRow == 1 && !exhausted;
    }

    @Override
    public boolean isLast() throws SQLException {
        return !exhausted && positionRow > 0 && positionRow == engineResultSet.getRows().size();
    }

    @Override
    public void beforeFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void afterLast() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean first() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean last() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean absolute(final int row) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean relative(final int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean previous() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getType() throws SQLException {
        return TYPE_FORWARD_ONLY;
    }

    @Override
    public int getConcurrency() throws SQLException {
        return CONCUR_READ_ONLY;
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        return false;
    }

    @Override
    public boolean rowInserted() throws SQLException {
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        return false;
    }

    @Override
    public void updateNull(final int columnIndex) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, null);
        } else {
            updatedValues.put(columnName, null);
        }
    }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateDate(final int columnIndex, final Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTime(final int columnIndex, final Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scaleOrLength) throws SQLException {
        updateObject(columnIndex, x);
    }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException {
        checkUpdatable();
        final String columnName = getColumnName(columnIndex);
        if (onInsertRow) {
            insertRowValues.put(columnName, x);
        } else {
            updatedValues.put(columnName, x);
        }
    }

    @Override
    public void updateNull(final String columnLabel) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), null);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), null);
        }
    }

    @Override
    public void updateBoolean(final String columnLabel, final boolean x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateByte(final String columnLabel, final byte x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateShort(final String columnLabel, final short x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateInt(final String columnLabel, final int x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateLong(final String columnLabel, final long x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateFloat(final String columnLabel, final float x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateDouble(final String columnLabel, final double x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateBigDecimal(final String columnLabel, final BigDecimal x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateString(final String columnLabel, final String x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateBytes(final String columnLabel, final byte[] x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateDate(final String columnLabel, final Date x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateTime(final String columnLabel, final Time x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateTimestamp(final String columnLabel, final Timestamp x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not supported");
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not supported");
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not supported");
    }

    @Override
    public void updateObject(final String columnLabel, final Object x, final int scaleOrLength) throws SQLException {
        updateObject(columnLabel, x);
    }

    @Override
    public void updateObject(final String columnLabel, final Object x) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnLabel.toUpperCase(), x);
        } else {
            updatedValues.put(columnLabel.toUpperCase(), x);
        }
    }

    @Override
    public void insertRow() throws SQLException {
        checkUpdatable();
        if (!onInsertRow) {
            throw new SQLException("Not on insert row");
        }
        if (insertRowValues.isEmpty()) {
            throw new SQLException("No values set for insert");
        }

        // Build INSERT statement
        final StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        final StringBuilder values = new StringBuilder(" VALUES (");

        boolean first = true;
        for (final Map.Entry<String, Object> entry : insertRowValues.entrySet()) {
            if (!first) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(entry.getKey());
            values.append(formatValue(entry.getValue()));
            first = false;
        }

        sql.append(")");
        values.append(")");
        sql.append(values);

        // Execute INSERT
        executeWriteback(sql.toString());

        // Clear insert row values
        insertRowValues.clear();
    }

    @Override
    public void updateRow() throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            throw new SQLException("Cannot call updateRow() when on insert row");
        }
        if (updatedValues.isEmpty()) {
            throw new SQLException("No values updated");
        }
        if (keyColumns.isEmpty()) {
            throw new SQLException("No key columns defined for update");
        }

        // Build UPDATE statement
        final StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(tableName).append(" SET ");

        boolean first = true;
        for (final Map.Entry<String, Object> entry : updatedValues.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(entry.getKey()).append(" = ").append(formatValue(entry.getValue()));
            first = false;
        }

        sql.append(" WHERE ");
        first = true;
        for (final String keyCol : keyColumns) {
            if (!first) {
                sql.append(" AND ");
            }
            final Object keyValue = currentRowValues.get(keyCol.toUpperCase());
            sql.append(keyCol).append(" = ").append(formatValue(keyValue));
            first = false;
        }

        // Execute UPDATE
        executeWriteback(sql.toString());

        // Update current row values with updated values
        currentRowValues.putAll(updatedValues);

        // Clear updated values
        updatedValues.clear();
    }

    @Override
    public void deleteRow() throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            throw new SQLException("Cannot call deleteRow() when on insert row");
        }
        if (keyColumns.isEmpty()) {
            throw new SQLException("No key columns defined for delete");
        }

        // Build DELETE statement
        final StringBuilder sql = new StringBuilder("DELETE FROM ");
        sql.append(tableName).append(" WHERE ");

        boolean first = true;
        for (final String keyCol : keyColumns) {
            if (!first) {
                sql.append(" AND ");
            }
            final Object keyValue = currentRowValues.get(keyCol.toUpperCase());
            sql.append(keyCol).append(" = ").append(formatValue(keyValue));
            first = false;
        }

        // Execute DELETE
        executeWriteback(sql.toString());
    }

    @Override
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("refreshRow not supported");
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.clear();
        } else {
            updatedValues.clear();
        }
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        checkUpdatable();
        onInsertRow = true;
        insertRowValues.clear();
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        checkUpdatable();
        onInsertRow = false;
        insertRowValues.clear();
    }

    private String formatValue(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Date) {
            return "'" + value.toString() + "'";
        }
        if (value instanceof Timestamp) {
            return "'" + value.toString() + "'";
        }
        if (value instanceof BinaryValue) {
            return "X'" + ((BinaryValue) value).toHex() + "'";
        }
        if (value instanceof byte[]) {
            return JdbcMarshaling.formatLiteral(value);
        }
        // Default: treat as string
        return "'" + value.toString().replace("'", "''") + "'";
    }

    @Override
    public Object getObject(final int columnIndex, final Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex);
    }

    @Override
    public Ref getRef(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Array getArray(final int columnIndex) throws SQLException {
        checkClosed();
        return EngineArray.from(engineResultSet.getValue(columnIndex - 1));
    }

    @Override
    public Object getObject(final String columnLabel, final Map<String, Class<?>> map) throws SQLException {
        return getObject(columnLabel);
    }

    @Override
    public Ref getRef(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Array getArray(final String columnLabel) throws SQLException {
        checkClosed();
        return EngineArray.from(engineResultSet.getValue(columnLabel));
    }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
        return getDate(columnIndex);   // engine values are not zoned; the Calendar is ignored
    }

    @Override
    public Date getDate(final String columnLabel, final Calendar cal) throws SQLException {
        return getDate(columnLabel);
    }

    @Override
    public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
        return getTime(columnIndex);
    }

    @Override
    public Time getTime(final String columnLabel, final Calendar cal) throws SQLException {
        return getTime(columnLabel);
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
        return getTimestamp(columnIndex);
    }

    @Override
    public Timestamp getTimestamp(final String columnLabel, final Calendar cal) throws SQLException {
        return getTimestamp(columnLabel);
    }

    @Override
    public URL getURL(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public URL getURL(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRef(final int columnIndex, final Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRef(final String columnLabel, final Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final int columnIndex, final Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final String columnLabel, final Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final int columnIndex, final Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final String columnLabel, final Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateArray(final int columnIndex, final Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateArray(final String columnLabel, final Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public RowId getRowId(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public RowId getRowId(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRowId(final int columnIndex, final RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRowId(final String columnLabel, final RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getHoldability() throws SQLException {
        return HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public void updateNString(final int columnIndex, final String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNString(final String columnLabel, final String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final int columnIndex, final NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final String columnLabel, final NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob getNClob(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob getNClob(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML getSQLXML(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML getSQLXML(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateSQLXML(final int columnIndex, final SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateSQLXML(final String columnLabel, final SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String getNString(final int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    @Override
    public String getNString(final String columnLabel) throws SQLException {
        return getString(columnLabel);
    }

    @Override
    public Reader getNCharacterStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Reader getNCharacterStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

}
