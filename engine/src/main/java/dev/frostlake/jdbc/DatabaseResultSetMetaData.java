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

import dev.frostlake.http.ColumnData;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC ResultSetMetaData implementation for Frostlake SQL Engine
 */
public class DatabaseResultSetMetaData implements ResultSetMetaData {
    private final List<ColumnData> columns;

    public DatabaseResultSetMetaData(final List<ColumnData> columns) {
        this.columns = columns;
    }

    @Override
    public int getColumnCount() throws SQLException {
        return columns.size();
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
        // The same rule the in-process transport applies (live-measured against the account's own
        // driver): columnNullable only for a column KNOWN to accept NULL, columnNoNulls for a NOT NULL
        // column and for every expression and literal alike. A server that predates the wire field
        // sends nothing, and that stays honestly unknown rather than being guessed either way.
        final Boolean nullable = columns.get(column - 1).getNullable();
        if (nullable == null) {
            return columnNullableUnknown;
        }
        return nullable.booleanValue() ? columnNullable : columnNoNulls;
    }

    @Override
    public boolean isSigned(final int column) throws SQLException {
        final String type = getColumnTypeName(column);
        if (DriverColumnMetrics.isInterval(type)) {
            // An interval is reported unsigned, its negative values notwithstanding (live-verified).
            return false;
        }
        return type.contains("INT") || type.contains("DECIMAL") || type.contains("FLOAT") || type.contains("DOUBLE");
    }

    @Override
    public int getColumnDisplaySize(final int column) throws SQLException {
        checkColumnIndex(column);
        final ColumnData described = columns.get(column - 1);
        return DriverColumnMetrics.displaySize(getColumnTypeName(column), described.getPrecision(),
            described.getScale(), described.getLength());
    }

    @Override
    public String getColumnLabel(final int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public String getColumnName(final int column) throws SQLException {
        checkColumnIndex(column);
        return columns.get(column - 1).getName();
    }

    @Override
    public String getSchemaName(final int column) throws SQLException {
        return "";
    }

    @Override
    public int getPrecision(final int column) throws SQLException {
        checkColumnIndex(column);
        // The wire carries a NUMBER's precision and scale, and a text or binary column's length.
        final ColumnData described = columns.get(column - 1);
        return DriverColumnMetrics.precision(getColumnTypeName(column), described.getPrecision(), described.getLength());
    }

    @Override
    public int getScale(final int column) throws SQLException {
        checkColumnIndex(column);
        // The wire's scale is a NUMBER's scale, an interval's field code, and a time or timestamp's
        // fractional-second precision, so a TIMESTAMP_NTZ(3) reads 3 here as it does in process. A server
        // that predates the digits sends 0 for every time and timestamp column.
        final int scale = columns.get(column - 1).getScale();
        return DriverColumnMetrics.scale(getColumnTypeName(column), scale, scale);
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
    public int getColumnType(final int column) throws SQLException {
        // The scale travels on the wire beside the name, and it is what separates an integer column
        // from a decimal one now that every integer alias is NUMBER — so both transports answer alike.
        return JdbcMarshaling.driverSqlType(getColumnTypeName(column), columns.get(column - 1).getScale());
    }

    @Override
    public String getColumnTypeName(final int column) throws SQLException {
        checkColumnIndex(column);
        return JdbcMarshaling.driverTypeName(columns.get(column - 1).getDataType());
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
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }

    private void checkColumnIndex(final int column) throws SQLException {
        if (column < 1 || column > columns.size()) {
            throw new SQLException("Invalid column index: " + column);
        }
    }

    private int mapToSqlType(final String typeName) {
        // Delegate to the shared mapper — the old inline version tested contains("INT") before BIGINT,
        // so BIGINT mis-mapped to INTEGER, and it lacked TIME/BINARY/ARRAY/etc.
        return JdbcMarshaling.toSqlType(typeName);
    }
}
