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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Map;

/**
 * The result set both transports share: the cursor, every getter, the label rule and the read-only update surface,
 * written once over the cells a transport supplies. The in-process transport hands over the engine's typed values
 * and the HTTP transport the wire's JSON cells; each says how a cell of its own reads as text and as an object, and
 * everything else — the typed getters' conversions and refusals (see {@link JdbcCellReads}), the temporal reads, the
 * null tracking — is decided here, as the account's driver decides it.
 *
 * <p>The cursor moves forward only: the account's driver offers no other movement and refuses every scrolling call.
 * Its position reads as that driver's does: {@code getRow} counts the rows {@code next} has reached and keeps the
 * last one's number once the rows are exhausted, {@code isBeforeFirst} holds until a row is reached — for an empty
 * result, always — and {@code isLast} holds while the next call would pass the end. A label names the column spelled
 * exactly as it is, as the account's driver reads one by default, until the connection's session sets
 * {@code CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE}; from then on it names the first column whose name matches it
 * ignoring case (see {@link ResultColumnCase}). A label no column has is refused with SQLSTATE 42703: {@code Column
 * not found: x} (live-verified).
 * A closed result set refuses every read with {@code Result set has been closed.}, while {@code next} answers false.
 */
public abstract class AbstractFrostlakeResultSet implements ResultSet {

    /** The SQLSTATE of a label that names no column. */
    private static final String UNDEFINED_COLUMN = "42703";

    /** The SQLSTATE and vendor code of a read of no column, or of no row. */
    private static final String NO_SUCH_COLUMN_STATE = "22000";
    private static final int NO_SUCH_COLUMN_CODE = 200032;

    /** The SQLSTATE and vendor code of a read of a closed result set. */
    private static final String CLOSED_STATE = "0A000";
    private static final int CLOSED_CODE = 200037;

    private final Statement statement;
    /** The zone the session's TIMEZONE named when the result was made (see {@link #sessionZone}). */
    private final ZoneId zone;
    private boolean closed;
    private boolean lastReadWasNull;
    // The row the cursor stands on, from 0: -1 before the first, rowCount() or more once next() has passed the last.
    private int cursor = -1;
    // How many rows next() has reached.
    private int reached;
    // The most rows the cursor reaches, the statement's setMaxRows when the result was made; 0 for every row.
    private final int rowLimit;

    /**
     * A result set the statement produced.
     *
     * @param statement the statement whose result this is, or null for a result no statement produced
     */
    protected AbstractFrostlakeResultSet(final Statement statement) {
        this.statement = statement;
        this.rowLimit = maxRowsOf(statement);
        this.zone = zoneOf(statement);
    }

    /** The zone the statement's session named when the result was made, UTC for a statement of no such session. */
    private static ZoneId zoneOf(final Statement statement) {
        try {
            final Connection connection = statement == null ? null : statement.getConnection();
            return connection instanceof SessionZoneSource ? ((SessionZoneSource) connection).sessionZone()
                : ZoneOffset.UTC;
        } catch (final SQLException closed) {
            return ZoneOffset.UTC;
        }
    }

    /** The statement's setMaxRows, which bounds the rows its result hands out (live-verified); 0 when unset. */
    private static int maxRowsOf(final Statement statement) {
        if (statement == null) {
            return 0;
        }
        try {
            return statement.getMaxRows();
        } catch (final SQLException closed) {
            return 0;
        }
    }

    /** The rows the cursor reaches: every row, or the first {@link #rowLimit} of them. */
    private int rows() {
        final int all = rowCount();
        return rowLimit > 0 && rowLimit < all ? rowLimit : all;
    }

    // ---------------------------------------------------------------- what a transport supplies

    /**
     * The number of columns.
     *
     * @return the column count
     */
    protected abstract int columnCount();

    /**
     * A column's label.
     *
     * @param column the column, from 1
     * @return its label
     */
    protected abstract String columnLabel(int column);

    /**
     * The number of rows.
     *
     * @return the row count
     */
    protected abstract int rowCount();

    /**
     * A cell as the transport carries it.
     *
     * @param row    the row, from 1
     * @param column the column, from 1
     * @return the cell, null for SQL NULL
     */
    protected abstract Object cell(int row, int column);

    /**
     * The interval kind of a column (see {@link IntervalColumnReads}).
     *
     * @param column the column, from 1
     * @param cell   the column's cell in the current row, null for SQL NULL
     * @return its kind, or null for a column of any other type
     */
    protected abstract IntervalKind intervalKind(int column, Object cell);

    /**
     * An interval cell as its nanoseconds or months.
     *
     * @param cell the cell, not null
     * @return its count
     */
    protected abstract BigInteger intervalNumber(Object cell);

    /**
     * A cell's text, as {@code getString} answers it.
     *
     * @param column the column, from 1
     * @param cell   the cell, not null and not an interval
     * @return its text
     */
    protected abstract String text(int column, Object cell);

    /**
     * A cell as {@code getObject} answers it: the class the column's metadata names.
     *
     * @param column the column, from 1
     * @param cell   the cell, not null and not an interval
     * @return its object
     */
    protected abstract Object object(int column, Object cell);

    /**
     * A cell as {@code getBigDecimal} answers it.
     *
     * @param column the column, from 1
     * @param cell   the cell, not null and not an interval
     * @return its decimal
     * @throws SQLException when the cell spells no number
     */
    protected BigDecimal decimal(final int column, final Object cell) throws SQLException {
        return JdbcCellReads.decimalOf(jdbcType(column), cell, wire(column, cell));
    }

    /**
     * The zone the session's TIMEZONE named when the statement ran: what a DATE's midnight and a wall clock read as a
     * time are read in (see {@link TemporalCellReads}).
     *
     * @return the zone
     */
    protected ZoneId sessionZone() {
        return zone;
    }

    /**
     * Called once the cursor has moved onto a row, before any getter reads it.
     *
     * @param row the row, from 1
     * @throws SQLException when the transport cannot take the row
     */
    protected void enteredRow(final int row) throws SQLException {
        // A transport keeping state per row takes it here.
    }

    // ---------------------------------------------------------------- reading

    /**
     * The current row, from 1, for a transport that writes a row back.
     *
     * @return the current row, or 0 when the cursor is on none
     */
    protected final int currentRow() {
        return cursor >= 0 && cursor < rows() ? cursor + 1 : 0;
    }

    /** Refuses every call on a closed result set, as the account's driver refuses it. */
    protected final void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Result set has been closed.", CLOSED_STATE, CLOSED_CODE);
        }
    }

    /**
     * The current row's cell of a column, its nullness recorded for {@link #wasNull()}.
     *
     * @param column the column, from 1
     * @return the cell, null for SQL NULL
     * @throws SQLException when the result set is closed, or there is no such column or no current row — the
     *     account's driver refuses a read once the rows are exhausted as it refuses a column it does not have
     */
    protected final Object read(final int column) throws SQLException {
        checkClosed();
        if (column < 1 || column > columnCount() || cursor < 0 || cursor >= rows()) {
            throw new SQLException("Invalid column index: " + column, NO_SUCH_COLUMN_STATE, NO_SUCH_COLUMN_CODE);
        }
        final Object value = cell(cursor + 1, column);
        lastReadWasNull = value == null;
        return value;
    }

    /** A column's JDBC type, as the result's metadata names it. */
    private int jdbcType(final int column) throws SQLException {
        return getMetaData().getColumnType(column);
    }

    /** A column's temporal family, by the type its metadata names; null for a column that is not temporal. */
    private TemporalColumnKind temporalKind(final int column) throws SQLException {
        return TemporalColumnKind.of(getMetaData().getColumnTypeName(column));
    }

    /**
     * A cell's wire text: what the account's driver converts when a typed getter reads it (see
     * {@link JdbcMarshaling#quotedCell}).
     */
    private String wire(final int column, final Object cell) throws SQLException {
        final ResultSetMetaData described = getMetaData();
        return JdbcMarshaling.quotedCell(cell, described.getColumnType(column), described.getScale(column),
            text(column, cell));
    }

    /**
     * The cell a temporal getter reads; a cell of a column that getter does not read is refused as the account's
     * driver refuses it (see {@link JdbcMarshaling#readsTemporal}).
     */
    private Object temporalCell(final int column, final String target) throws SQLException {
        final Object value = read(column);
        final int type = jdbcType(column);
        if (value != null && !JdbcMarshaling.readsTemporal(type, target)) {
            throw JdbcMarshaling.unconvertible(type, target, wire(column, value));
        }
        return value;
    }

    /** An interval cell's count for {@link IntervalColumnReads}, null for SQL NULL. */
    private BigInteger intervalCount(final Object cell) {
        return cell == null ? null : intervalNumber(cell);
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkClosed();
        return lastReadWasNull;
    }

    @Override
    public String getString(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getString(interval, intervalCount(cell));
        }
        return cell == null ? null : text(columnIndex, cell);
    }

    @Override
    public boolean getBoolean(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getBoolean(interval, intervalCount(cell));
        }
        return cell != null && JdbcCellReads.booleanOf(jdbcType(columnIndex), cell, wire(columnIndex, cell));
    }

    @Override
    public byte getByte(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getByte(interval, intervalCount(cell));
        }
        return cell == null ? 0 : JdbcCellReads.byteOf(jdbcType(columnIndex), wire(columnIndex, cell));
    }

    @Override
    public short getShort(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getShort(interval, intervalCount(cell));
        }
        return cell == null ? 0 : JdbcCellReads.shortOf(jdbcType(columnIndex), wire(columnIndex, cell));
    }

    @Override
    public int getInt(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getInt(interval, intervalCount(cell));
        }
        if (cell == null) {
            return 0;
        }
        return JdbcCellReads.intOf(jdbcType(columnIndex), getMetaData().getColumnTypeName(columnIndex), cell,
            wire(columnIndex, cell));
    }

    @Override
    public long getLong(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getLong(interval, intervalCount(cell));
        }
        if (cell == null) {
            return 0;
        }
        return JdbcCellReads.longOf(jdbcType(columnIndex), getMetaData().getColumnTypeName(columnIndex), cell,
            wire(columnIndex, cell));
    }

    @Override
    public float getFloat(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getFloat(interval, intervalCount(cell));
        }
        return cell == null ? 0 : JdbcCellReads.floatOf(jdbcType(columnIndex), cell, wire(columnIndex, cell));
    }

    @Override
    public double getDouble(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getDouble(interval, intervalCount(cell));
        }
        return cell == null ? 0 : JdbcCellReads.doubleOf(jdbcType(columnIndex), cell, wire(columnIndex, cell));
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getBigDecimal(interval, intervalCount(cell));
        }
        return cell == null ? null : decimal(columnIndex, cell);
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex, final int scale) throws SQLException {
        return getBigDecimal(columnIndex);
    }

    @Override
    public byte[] getBytes(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getBytes(interval, intervalCount(cell));
        }
        return cell == null ? null
            : JdbcCellReads.bytesOf(jdbcType(columnIndex), cell, text(columnIndex, cell), wire(columnIndex, cell));
    }

    @Override
    public Date getDate(final int columnIndex) throws SQLException {
        final IntervalKind interval = intervalKind(columnIndex, read(columnIndex));
        if (interval != null) {
            return IntervalColumnReads.getDate(interval);
        }
        final Object cell = temporalCell(columnIndex, "date");
        final TemporalColumnKind kind = temporalKind(columnIndex);
        return cell == null || kind == null ? JdbcMarshaling.toDate(cell)
            : TemporalCellReads.date(cell, kind, sessionZone());
    }

    @Override
    public Time getTime(final int columnIndex) throws SQLException {
        final IntervalKind interval = intervalKind(columnIndex, read(columnIndex));
        if (interval != null) {
            return IntervalColumnReads.getTime(interval);
        }
        final Object cell = temporalCell(columnIndex, "time");
        final TemporalColumnKind kind = temporalKind(columnIndex);
        return cell == null || kind == null ? JdbcMarshaling.toTime(cell, jdbcType(columnIndex))
            : TemporalCellReads.time(cell, kind, sessionZone(), jdbcType(columnIndex));
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex) throws SQLException {
        final IntervalKind interval = intervalKind(columnIndex, read(columnIndex));
        if (interval != null) {
            return IntervalColumnReads.getTimestamp(interval);
        }
        final Object cell = temporalCell(columnIndex, "timestamp");
        final TemporalColumnKind kind = temporalKind(columnIndex);
        return cell == null || kind == null ? JdbcMarshaling.toTimestamp(cell, jdbcType(columnIndex))
            : TemporalCellReads.timestamp(cell, kind, sessionZone());
    }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
        if (cal == null || intervalKind(columnIndex, read(columnIndex)) != null) {
            return getDate(columnIndex);
        }
        final Object cell = temporalCell(columnIndex, "date");
        final TemporalColumnKind kind = temporalKind(columnIndex);
        return cell == null || kind == null ? JdbcMarshaling.toDate(cell, cal)
            : TemporalCellReads.date(cell, kind, sessionZone(), cal);
    }

    @Override
    public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
        // The account's driver reads no Calendar for a TIME (live-verified).
        return getTime(columnIndex);
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
        if (cal == null || intervalKind(columnIndex, read(columnIndex)) != null) {
            return getTimestamp(columnIndex);
        }
        final Object cell = temporalCell(columnIndex, "timestamp");
        final TemporalColumnKind kind = temporalKind(columnIndex);
        if (cell == null || kind == null) {
            // A TIME is read without the Calendar, and any other cell in the Calendar's zone.
            return jdbcType(columnIndex) == Types.TIME ? JdbcMarshaling.toTimestamp(cell, Types.TIME)
                : JdbcMarshaling.toTimestamp(cell, cal);
        }
        return TemporalCellReads.timestamp(cell, kind, sessionZone(), cal);
    }

    @Override
    public Object getObject(final int columnIndex) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getObject(interval, intervalCount(cell));
        }
        if (cell == null) {
            return null;
        }
        final Object temporal = TemporalCellReads.object(cell, temporalKind(columnIndex), sessionZone());
        return temporal != null ? temporal : object(columnIndex, cell);
    }

    /** The account's driver reads no cell through a type map (live-verified), so neither does this one. */
    @Override
    public Object getObject(final int columnIndex, final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
        final Object cell = read(columnIndex);
        final IntervalKind interval = intervalKind(columnIndex, cell);
        if (interval != null) {
            return IntervalColumnReads.getObject(interval, intervalCount(cell), type);
        }
        return TypedObjectReads.getObject(this, columnIndex, type);
    }

    /**
     * The account's driver reads no cell as a {@link java.sql.Array} — a semi-structured ARRAY, a structured
     * {@code ARRAY(NUMBER)} and a NULL alike, over JSON and Arrow results (live-verified) — so neither does this one:
     * an ARRAY reads as its JSON text through getString and getObject.
     */
    @Override
    public Array getArray(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String getNString(final int columnIndex) throws SQLException {
        return getString(columnIndex);
    }

    // ---------------------------------------------------------------- labels

    @Override
    public int findColumn(final String columnLabel) throws SQLException {
        checkClosed();
        final int count = columnCount();
        final boolean ignoreCase = labelsIgnoreCase();
        for (int i = 1; i <= count; i++) {
            final String name = columnLabel(i);
            if (columnLabel != null && (ignoreCase ? columnLabel.equalsIgnoreCase(name) : columnLabel.equals(name))) {
                return i;
            }
        }
        throw new SQLException("Column not found: " + columnLabel, UNDEFINED_COLUMN);
    }

    /** Whether the connection this result set came from matches a label ignoring case (see ResultColumnCase). */
    private boolean labelsIgnoreCase() {
        if (statement == null) {
            return false;
        }
        try {
            final Connection connection = statement.getConnection();
            return connection instanceof ResultColumnCase && ((ResultColumnCase) connection).labelsIgnoreCase();
        } catch (final SQLException noConnection) {
            return false;
        }
    }

    @Override
    public String getString(final String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(final String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte getByte(final String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public short getShort(final String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public int getInt(final String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(final String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public float getFloat(final String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public double getDouble(final String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(final String columnLabel, final int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public BigDecimal getBigDecimal(final String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    @Override
    public byte[] getBytes(final String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public Date getDate(final String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public Time getTime(final String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(final String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public Date getDate(final String columnLabel, final Calendar cal) throws SQLException {
        return getDate(findColumn(columnLabel), cal);
    }

    @Override
    public Time getTime(final String columnLabel, final Calendar cal) throws SQLException {
        return getTime(findColumn(columnLabel), cal);
    }

    @Override
    public Timestamp getTimestamp(final String columnLabel, final Calendar cal) throws SQLException {
        return getTimestamp(findColumn(columnLabel), cal);
    }

    @Override
    public Object getObject(final String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public Object getObject(final String columnLabel, final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    @Override
    public Array getArray(final String columnLabel) throws SQLException {
        return getArray(findColumn(columnLabel));
    }

    @Override
    public String getNString(final String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    // ---------------------------------------------------------------- the cursor

    /** A closed result set has no more rows, and says so rather than refusing. */
    @Override
    public boolean next() throws SQLException {
        if (closed) {
            return false;
        }
        if (cursor < rows()) {
            cursor++;
        }
        if (cursor < rows()) {
            reached++;
            enteredRow(cursor + 1);
            return true;
        }
        return false;
    }

    @Override
    public int getRow() throws SQLException {
        checkClosed();
        return reached;
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkClosed();
        return reached == 0;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        checkClosed();
        return cursor >= rows();
    }

    @Override
    public boolean isFirst() throws SQLException {
        checkClosed();
        return reached == 1;
    }

    @Override
    public boolean isLast() throws SQLException {
        checkClosed();
        return cursor + 1 == rows();
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
        checkClosed();
        if (direction != FETCH_FORWARD) {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        checkClosed();
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return 0;
    }

    @Override
    public int getType() throws SQLException {
        checkClosed();
        return TYPE_FORWARD_ONLY;
    }

    @Override
    public int getConcurrency() throws SQLException {
        checkClosed();
        return CONCUR_READ_ONLY;
    }

    /** A cursor closes at commit, as the account's driver declares for every result set. */
    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public void close() throws SQLException {
        closed = true;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public Statement getStatement() throws SQLException {
        checkClosed();
        return statement;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException();
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
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    // ---------------------------------------------------------------- what neither transport reads

    @Override
    public InputStream getAsciiStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getBinaryStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
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
    public Reader getCharacterStream(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Reader getCharacterStream(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
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
    public Ref getRef(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Ref getRef(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(final String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(final String columnLabel) throws SQLException {
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
    public URL getURL(final int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public URL getURL(final String columnLabel) throws SQLException {
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
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    // ---------------------------------------------------------------- the update surface: read-only unless a
    // transport writes rows back

    /**
     * The refusal of a write through a result set no transport writes back.
     *
     * @return the refusal
     */
    protected SQLException readOnly() {
        return new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNull(final int columnIndex) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateDate(final int columnIndex, final Date x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateTime(final int columnIndex, final Time x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scaleOrLength) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNull(final String columnLabel) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBoolean(final String columnLabel, final boolean x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateByte(final String columnLabel, final byte x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateShort(final String columnLabel, final short x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateInt(final String columnLabel, final int x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateLong(final String columnLabel, final long x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateFloat(final String columnLabel, final float x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateDouble(final String columnLabel, final double x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBigDecimal(final String columnLabel, final BigDecimal x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateString(final String columnLabel, final String x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBytes(final String columnLabel, final byte[] x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateDate(final String columnLabel, final Date x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateTime(final String columnLabel, final Time x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateTimestamp(final String columnLabel, final Timestamp x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x, final int length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x, final int length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final int length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateObject(final String columnLabel, final Object x, final int scaleOrLength) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateObject(final String columnLabel, final Object x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void insertRow() throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateRow() throws SQLException {
        throw readOnly();
    }

    @Override
    public void deleteRow() throws SQLException {
        throw readOnly();
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        throw readOnly();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        throw readOnly();
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateRef(final int columnIndex, final Ref x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateRef(final String columnLabel, final Ref x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final int columnIndex, final Blob x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final String columnLabel, final Blob x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final int columnIndex, final Clob x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final String columnLabel, final Clob x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateArray(final int columnIndex, final Array x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateArray(final String columnLabel, final Array x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateRowId(final int columnIndex, final RowId x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateRowId(final String columnLabel, final RowId x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNString(final int columnIndex, final String nString) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNString(final String columnLabel, final String nString) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final int columnIndex, final NClob nClob) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final String columnLabel, final NClob nClob) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream, final long length)
            throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream x) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateSQLXML(final int columnIndex, final SQLXML xmlObject) throws SQLException {
        throw readOnly();
    }

    @Override
    public void updateSQLXML(final String columnLabel, final SQLXML xmlObject) throws SQLException {
        throw readOnly();
    }
}
