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

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * The result-set metadata both transports share: every answer the account's driver gives about a result column,
 * decided once over the few facts a transport knows about each column — its name, its type, its numbers and whether
 * it accepts NULL. Type names, JDBC types, classes and sizes all go through {@link JdbcMarshaling} and
 * {@link DriverColumnMetrics}.
 */
public abstract class AbstractFrostlakeResultSetMetaData implements ResultSetMetaData {

    /**
     * The number of columns.
     *
     * @return the column count
     */
    protected abstract int columnCount();

    /**
     * A column's name.
     *
     * @param column the column, from 1
     * @return its name
     */
    protected abstract String columnName(int column);

    /**
     * A column's type name as the transport carries it, before the driver's spelling.
     *
     * @param column the column, from 1
     * @return the type name
     */
    protected abstract String typeName(int column);

    /**
     * The scale that decides a column's JDBC type: a fixed-point number's scale, 0 for most other types.
     *
     * @param column the column, from 1
     * @return the scale
     */
    protected abstract int typeScale(int column);

    /**
     * A fixed-point number's precision, 0 for any other type.
     *
     * @param column the column, from 1
     * @return the precision
     */
    protected abstract int numericPrecision(int column);

    /**
     * A fixed-point number's scale, or the code the driver reports for the fields an interval spans; 0 for any other
     * type.
     *
     * @param column the column, from 1
     * @return the scale
     */
    protected abstract int numericScale(int column);

    /**
     * A time or timestamp's digits of a second's fraction, 0 for any other type.
     *
     * @param column the column, from 1
     * @return the digits
     */
    protected abstract int fractionalDigits(int column);

    /**
     * A text or binary column's length.
     *
     * @param column the column, from 1
     * @return the length, or null for a column of any other type
     */
    protected abstract Integer length(int column);

    /**
     * Whether a column accepts NULL, as {@link #isNullable} answers it.
     *
     * @param column the column, from 1
     * @return {@code columnNullable}, {@code columnNoNulls} or {@code columnNullableUnknown}
     */
    protected abstract int nullability(int column);

    private void checkColumn(final int column) throws SQLException {
        if (column < 1 || column > columnCount()) {
            throw new SQLException("Invalid column index: " + column);
        }
    }

    @Override
    public int getColumnCount() throws SQLException {
        return columnCount();
    }

    @Override
    public String getColumnName(final int column) throws SQLException {
        checkColumn(column);
        return columnName(column);
    }

    @Override
    public String getColumnLabel(final int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public String getColumnTypeName(final int column) throws SQLException {
        checkColumn(column);
        return JdbcMarshaling.driverTypeName(typeName(column));
    }

    @Override
    public int getColumnType(final int column) throws SQLException {
        return JdbcMarshaling.driverSqlType(getColumnTypeName(column), typeScale(column));
    }

    @Override
    public String getColumnClassName(final int column) throws SQLException {
        return JdbcMarshaling.driverColumnClassName(getColumnType(column));
    }

    /**
     * The driver's rule (live-measured against the account's own driver): {@code columnNullable} only for a column
     * KNOWN to accept NULL, {@code columnNoNulls} for a NOT NULL column and for every expression and literal alike —
     * so {@code SELECT UPPER(v)} reads {@code columnNoNulls} here while INFORMATION_SCHEMA reports it nullable.
     */
    @Override
    public int isNullable(final int column) throws SQLException {
        checkColumn(column);
        return nullability(column);
    }

    @Override
    public int getColumnDisplaySize(final int column) throws SQLException {
        return DriverColumnMetrics.displaySize(getColumnTypeName(column), numericPrecision(column),
            numericScale(column), length(column));
    }

    @Override
    public int getPrecision(final int column) throws SQLException {
        return DriverColumnMetrics.precision(getColumnTypeName(column), numericPrecision(column), length(column));
    }

    @Override
    public int getScale(final int column) throws SQLException {
        return DriverColumnMetrics.scale(getColumnTypeName(column), numericScale(column), fractionalDigits(column));
    }

    @Override
    public boolean isCaseSensitive(final int column) throws SQLException {
        return DriverColumnMetrics.isCaseSensitive(getColumnTypeName(column));
    }

    @Override
    public boolean isSigned(final int column) throws SQLException {
        return DriverColumnMetrics.isSigned(getColumnTypeName(column));
    }

    @Override
    public boolean isAutoIncrement(final int column) throws SQLException {
        checkColumn(column);
        return false;
    }

    @Override
    public boolean isSearchable(final int column) throws SQLException {
        checkColumn(column);
        return true;
    }

    @Override
    public boolean isCurrency(final int column) throws SQLException {
        checkColumn(column);
        return false;
    }

    @Override
    public String getSchemaName(final int column) throws SQLException {
        checkColumn(column);
        return "";
    }

    @Override
    public String getTableName(final int column) throws SQLException {
        checkColumn(column);
        return "";
    }

    @Override
    public String getCatalogName(final int column) throws SQLException {
        checkColumn(column);
        return "";
    }

    @Override
    public boolean isReadOnly(final int column) throws SQLException {
        checkColumn(column);
        return true;
    }

    @Override
    public boolean isWritable(final int column) throws SQLException {
        checkColumn(column);
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(final int column) throws SQLException {
        checkColumn(column);
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
}
