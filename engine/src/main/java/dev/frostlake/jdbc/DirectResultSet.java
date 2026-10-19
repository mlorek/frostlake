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
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.ClientValueText;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The in-process transport's result set: the engine's own result, its typed values read through
 * {@link AbstractFrostlakeResultSet}. Built with a table and its key columns, it also writes rows back — an
 * embedded caller's convenience the HTTP transport does not offer.
 */
public class DirectResultSet extends AbstractFrostlakeResultSet {

    private final ResultSet engineResultSet;
    private final Statement statement;

    // Updatable ResultSet support
    private final DatabaseEngine engine;
    private final String tableName;
    private final List<String> keyColumns;
    private final Map<String, Object> currentRowValues;
    private final Map<String, Object> updatedValues;
    private final Map<String, Object> insertRowValues;
    private boolean onInsertRow = false;

    public DirectResultSet(final Statement statement, final ResultSet engineResultSet) {
        this(statement, engineResultSet, null, null, null);
    }

    public DirectResultSet(final Statement statement, final ResultSet engineResultSet,
                          final DatabaseEngine engine, final String tableName, final List<String> keyColumns) {
        super(statement);
        this.statement = statement;
        this.engineResultSet = engineResultSet;
        this.engine = engine;
        this.tableName = tableName;
        this.keyColumns = keyColumns != null ? keyColumns : new ArrayList<>();
        this.currentRowValues = new HashMap<>();
        this.updatedValues = new HashMap<>();
        this.insertRowValues = new HashMap<>();
    }

    /** The engine-native result this JDBC view wraps — for embedders needing the typed columns. */
    public ResultSet getEngineResultSet() {
        return engineResultSet;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DirectResultSetMetaData(engineResultSet.getColumns());
    }

    @Override
    protected int columnCount() {
        return engineResultSet.getColumns().size();
    }

    @Override
    protected String columnLabel(final int column) {
        return engineResultSet.getColumns().get(column - 1).getName();
    }

    @Override
    protected int rowCount() {
        return engineResultSet.getRows().size();
    }

    @Override
    protected Object cell(final int row, final int column) {
        return engineResultSet.getRows().get(row - 1).getValue(column - 1);
    }

    /** The declared type of a column, from 1. */
    private DataType declaredType(final int column) {
        return engineResultSet.getColumns().get(column - 1).getDataType();
    }

    /** The declared interval type decides, and a column the engine could not type is judged by its cell. */
    @Override
    protected IntervalKind intervalKind(final int column, final Object cell) {
        if (column < 1 || column > columnCount()) {
            return null;
        }
        return IntervalColumnReads.kindOf(declaredType(column), cell);
    }

    @Override
    protected BigInteger intervalNumber(final Object cell) {
        return IntervalCells.number(cell);
    }

    @Override
    protected String text(final int column, final Object cell) {
        return ClientValueText.render(cell, declaredType(column));
    }

    @Override
    protected BigDecimal decimal(final int column, final Object cell) throws SQLException {
        if (cell instanceof Number && NumericType.isDecfloat(declaredType(column))) {
            return ClientValueText.decfloatObject((Number) cell);
        }
        return super.decimal(column, cell);
    }

    /**
     * Map engine-internal value objects to their JDBC-visible form (BINARY cells become byte[]). An
     * APPROXIMATE column's value is a Double whatever carrier the engine holds it in — the JDBC contract
     * for FLOAT and DOUBLE, and what the account's driver hands back — so an expression the engine
     * computed exactly still reaches the client as the double its declared type promises.
     *
     * <p>A temporal cell comes back as the {@code java.sql} class the column's metadata NAMES, not as the
     * {@code java.time} value the engine carries: the account's driver answers {@code java.sql.Date} for a
     * DATE, {@code java.sql.Time} for a TIME and a {@code java.sql.Timestamp} for each timestamp flavour,
     * and a client reading {@code getColumnClassName} is entitled to cast to it.
     */
    @Override
    protected Object object(final int column, final Object value) {
        final DataType declared = declaredType(column);
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        if (declared instanceof DateTimeType) {
            final String family = declared.getName().toUpperCase(Locale.ROOT);
            if (family.equals("DATE")) {
                return JdbcMarshaling.toDate(value);
            }
            if (family.equals("TIME")) {
                return JdbcMarshaling.toTime(value);
            }
            if (family.startsWith("TIMESTAMP") || family.equals("DATETIME")) {
                return JdbcMarshaling.toTimestamp(value);
            }
        }
        if (value instanceof VariantValue) {
            // Snowflake's JDBC driver surfaces VARIANT/OBJECT/ARRAY as their JSON text, a DOUBLE in the
            // fifteen-decimal form: the text the HTTP transport carries for the same cell.
            return VariantJsonText.clientTextOf((VariantValue) value);
        }
        if (value instanceof VectorValue) {
            // The driver hands a VECTOR back as its text, the same getString reads (live-verified).
            return ClientValueText.render(value, declared);
        }
        if (value instanceof String && VariantJsonText.isSemiStructured(declared)) {
            // A string read out of a VARIANT is its JSON text too, quotes included, as getString spells it.
            return VariantJsonText.unwrappedStringText((String) value);
        }
        if (value instanceof Number && !(value instanceof Double) && NumericType.isApproximate(declared)) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof Number && !(value instanceof BigDecimal) && JdbcMarshaling.isClientDecimal(declared)) {
            // A file transfer's size is the client's own DECIMAL, read as the BigDecimal its metadata names.
            return new BigDecimal(value.toString());
        }
        if (value instanceof Number && NumericType.isDecfloat(declared)) {
            return ClientValueText.decfloatObject((Number) value);
        }
        if (value instanceof BigDecimal && declared instanceof NumericType && !NumericType.isApproximate(declared)
                && ((NumericType) declared).getScale() == 0) {
            // A scale-0 NUMBER is the Long its metadata names while its value fits one, as the HTTP transport and
            // the account's driver read it, and a BigDecimal past it (live-verified).
            final BigDecimal whole = (BigDecimal) value;
            if (whole.signum() == 0
                    || whole.stripTrailingZeros().scale() <= 0 && whole.toBigInteger().bitLength() < Long.SIZE) {
                return Long.valueOf(whole.longValue());
            }
        }
        return value;
    }

    // ---------------------------------------------------------------- writing rows back

    @Override
    protected void enteredRow(final int row) {
        updatedValues.clear();
        onInsertRow = false;
        currentRowValues.clear();
        final List<ResultSetColumn> columns = engineResultSet.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            currentRowValues.put(columns.get(i).getName().toUpperCase(Locale.ROOT), cell(row, i + 1));
        }
    }

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
        return columns.get(columnIndex - 1).getName().toUpperCase(Locale.ROOT);
    }

    /** Keep a value for the insert row or the current row's update, by its upper-case column name. */
    private void put(final String columnName, final Object value) throws SQLException {
        checkUpdatable();
        if (onInsertRow) {
            insertRowValues.put(columnName, value);
        } else {
            updatedValues.put(columnName, value);
        }
    }

    @Override
    public void updateNull(final int columnIndex) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), null);
    }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scaleOrLength) throws SQLException {
        updateObject(columnIndex, x);
    }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException {
        checkUpdatable();
        put(getColumnName(columnIndex), x);
    }

    @Override
    public void updateNull(final String columnLabel) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), null);
    }

    @Override
    public void updateBoolean(final String columnLabel, final boolean x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateByte(final String columnLabel, final byte x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateShort(final String columnLabel, final short x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateInt(final String columnLabel, final int x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateLong(final String columnLabel, final long x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateFloat(final String columnLabel, final float x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateDouble(final String columnLabel, final double x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateBigDecimal(final String columnLabel, final BigDecimal x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateString(final String columnLabel, final String x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateBytes(final String columnLabel, final byte[] x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateDate(final String columnLabel, final Date x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateTime(final String columnLabel, final Time x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateTimestamp(final String columnLabel, final Timestamp x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
    }

    @Override
    public void updateObject(final String columnLabel, final Object x, final int scaleOrLength) throws SQLException {
        updateObject(columnLabel, x);
    }

    @Override
    public void updateObject(final String columnLabel, final Object x) throws SQLException {
        put(columnLabel.toUpperCase(Locale.ROOT), x);
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
        executeWriteback(sql.toString());
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
        sql.append(" WHERE ").append(keyCondition());
        executeWriteback(sql.toString());
        currentRowValues.putAll(updatedValues);
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
        executeWriteback("DELETE FROM " + tableName + " WHERE " + keyCondition());
    }

    /** The current row's key columns, each equal to its value. */
    private String keyCondition() {
        final StringBuilder condition = new StringBuilder();
        boolean first = true;
        for (final String keyCol : keyColumns) {
            if (!first) {
                condition.append(" AND ");
            }
            final Object keyValue = currentRowValues.get(keyCol.toUpperCase(Locale.ROOT));
            condition.append(keyCol).append(" = ").append(formatValue(keyValue));
            first = false;
        }
        return condition.toString();
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
}
