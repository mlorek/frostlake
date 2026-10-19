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

import java.util.List;

/**
 * The in-process transport's result-set metadata: the engine's own {@link ResultSetColumn}s, read through
 * {@link AbstractFrostlakeResultSetMetaData}.
 */
class DirectResultSetMetaData extends AbstractFrostlakeResultSetMetaData {

    private final List<ResultSetColumn> columns;

    DirectResultSetMetaData(final List<ResultSetColumn> columns) {
        this.columns = columns;
    }

    private DataType type(final int column) {
        return columns.get(column - 1).getDataType();
    }

    @Override
    protected int columnCount() {
        return columns.size();
    }

    @Override
    protected String columnName(final int column) {
        return columns.get(column - 1).getName();
    }

    @Override
    protected String typeName(final int column) {
        return type(column).getName();
    }

    @Override
    protected int typeScale(final int column) {
        final DataType type = type(column);
        return type instanceof NumericType ? ((NumericType) type).getScale() : 0;
    }

    /**
     * The APPROXIMATE family carries no precision: Snowflake's own driver answers 0 for a FLOAT column, and SHOW
     * COLUMNS and INFORMATION_SCHEMA print no numbers for it either. The engine keeps a nominal pair internally; it
     * must not reach a client through here.
     */
    @Override
    protected int numericPrecision(final int column) {
        final DataType type = type(column);
        return type instanceof NumericType && !NumericType.isApproximate(type)
            ? ((NumericType) type).getPrecision() : 0;
    }

    @Override
    protected int numericScale(final int column) {
        final DataType type = type(column);
        if (type instanceof IntervalDayTimeType) {
            return ((IntervalDayTimeType) type).getQualifier().driverScale();
        }
        if (type instanceof IntervalYearMonthType) {
            return ((IntervalYearMonthType) type).getQualifier().driverScale();
        }
        return type instanceof NumericType && !NumericType.isApproximate(type) ? ((NumericType) type).getScale() : 0;
    }

    @Override
    protected int fractionalDigits(final int column) {
        final DataType type = type(column);
        return type instanceof DateTimeType ? ((DateTimeType) type).getPrecision() : 0;
    }

    @Override
    protected Integer length(final int column) {
        return ColumnLengths.of(type(column));
    }

    @Override
    protected int nullability(final int column) {
        final ResultSetColumn described = columns.get(column - 1);
        return described.isNullabilityKnown() && described.isNullable() ? columnNullable : columnNoNulls;
    }
}
