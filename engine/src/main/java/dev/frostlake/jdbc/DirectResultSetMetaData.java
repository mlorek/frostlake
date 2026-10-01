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

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.ColumnLengths;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * {@link ResultSetMetaData} for the in-process ({@code Direct}) JDBC transport, backed directly by the
 * engine's {@link ResultSetColumn}s. Extracted from {@link DirectResultSet} (it was a static nested class).
 * Type mapping and class names go through {@link JdbcMarshaling} so both transports agree.
 */
class DirectResultSetMetaData implements ResultSetMetaData {

    private final List<ResultSetColumn> columns;

    DirectResultSetMetaData(final List<ResultSetColumn> columns) {
        this.columns = columns;
    }

    @Override
    public int getColumnCount() throws SQLException {
        return columns.size();
    }

    @Override
    public String getColumnName(final int column) throws SQLException {
        return columns.get(column - 1).getName();
    }

    @Override
    public String getColumnLabel(final int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public int getColumnType(final int column) throws SQLException {
        final DataType type = columns.get(column - 1).getDataType();
        return JdbcMarshaling.driverSqlType(getColumnTypeName(column),
            type instanceof NumericType ? ((NumericType) type).getScale() : 0);
    }

    @Override
    public String getColumnTypeName(final int column) throws SQLException {
        return JdbcMarshaling.driverTypeName(columns.get(column - 1).getDataType().getName());
    }

    @Override
    public boolean isAutoIncrement(final int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isCaseSensitive(final int column) throws SQLException {
        return DriverColumnMetrics.isCaseSensitive(getColumnTypeName(column));
    }

    @Override
    public boolean isSearchable(final int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isCurrency(final int column) throws SQLException {
        return false;
    }

    @Override
    public int isNullable(final int column) throws SQLException {
        // The driver's rule is NOT the catalog surfaces' rule (live-measured against the account's own
        // driver): columnNullable only for a column KNOWN to accept NULL, and columnNoNulls for
        // everything else — a NOT NULL column, an expression over one, and a literal alike. So
        // SELECT UPPER(v) reads columnNoNulls here while INFORMATION_SCHEMA reports it nullable.
        final ResultSetColumn described = columns.get(column - 1);
        return described.isNullabilityKnown() && described.isNullable() ? columnNullable : columnNoNulls;
    }

    @Override
    public boolean isSigned(final int column) throws SQLException {
        return false;
    }

    @Override
    public int getColumnDisplaySize(final int column) throws SQLException {
        final DataType type = columns.get(column - 1).getDataType();
        return DriverColumnMetrics.displaySize(getColumnTypeName(column), numericPrecision(type), numericScale(type),
            ColumnLengths.of(type));
    }

    @Override
    public String getSchemaName(final int column) throws SQLException {
        return "";
    }

    @Override
    public int getPrecision(final int column) throws SQLException {
        final DataType type = columns.get(column - 1).getDataType();
        return DriverColumnMetrics.precision(getColumnTypeName(column), numericPrecision(type), ColumnLengths.of(type));
    }

    @Override
    public int getScale(final int column) throws SQLException {
        final DataType type = columns.get(column - 1).getDataType();
        return DriverColumnMetrics.scale(getColumnTypeName(column), numericScale(type),
            type instanceof DateTimeType ? ((DateTimeType) type).getPrecision() : 0);
    }

    /**
     * A fixed-point number's precision, 0 for any other type. The APPROXIMATE family carries none: Snowflake's
     * own driver answers 0 for a FLOAT column, and SHOW COLUMNS and INFORMATION_SCHEMA print no numbers for
     * it either. The engine keeps a nominal pair internally; it must not reach a client through here.
     */
    private static int numericPrecision(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) ? ((NumericType) type).getPrecision() : 0;
    }

    /**
     * A fixed-point number's scale (see {@link #numericPrecision}), or the code the driver reports for the fields
     * an interval spans; 0 for any other type.
     */
    private static int numericScale(final DataType type) {
        if (type instanceof IntervalDayTimeType) {
            return ((IntervalDayTimeType) type).getQualifier().driverScale();
        }
        if (type instanceof IntervalYearMonthType) {
            return ((IntervalYearMonthType) type).getQualifier().driverScale();
        }
        return type instanceof NumericType && !NumericType.isApproximate(type) ? ((NumericType) type).getScale() : 0;
    }

    @Override
    public String getTableName(final int column) throws SQLException {
        return "";
    }

    @Override
    public String getCatalogName(final int column) throws SQLException {
        return "";
    }

    @Override
    public boolean isReadOnly(final int column) throws SQLException {
        return true;
    }

    @Override
    public boolean isWritable(final int column) throws SQLException {
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(final int column) throws SQLException {
        return false;
    }

    @Override
    public String getColumnClassName(final int column) throws SQLException {
        return JdbcMarshaling.driverColumnClassName(getColumnType(column));
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
