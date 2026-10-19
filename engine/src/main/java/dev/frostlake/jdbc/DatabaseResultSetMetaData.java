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

import java.util.List;

/**
 * The HTTP transport's result-set metadata: the wire's column descriptions, read through
 * {@link AbstractFrostlakeResultSetMetaData}. The wire carries a NUMBER's precision and scale, a text or binary
 * column's length, and as the scale an interval's field code or a time or timestamp's digits of a second.
 */
public class DatabaseResultSetMetaData extends AbstractFrostlakeResultSetMetaData {

    private final List<ColumnData> columns;

    public DatabaseResultSetMetaData(final List<ColumnData> columns) {
        this.columns = columns;
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
        return columns.get(column - 1).getDataType();
    }

    @Override
    protected int typeScale(final int column) {
        return columns.get(column - 1).scaleOrZero();
    }

    @Override
    protected int numericPrecision(final int column) {
        return columns.get(column - 1).precisionOrZero();
    }

    @Override
    protected int numericScale(final int column) {
        return columns.get(column - 1).scaleOrZero();
    }

    /** A server that predates the digits sends 0 for every time and timestamp column. */
    @Override
    protected int fractionalDigits(final int column) {
        return columns.get(column - 1).scaleOrZero();
    }

    @Override
    protected Integer length(final int column) {
        return columns.get(column - 1).getLength();
    }

    /** A server that predates the wire field sends nothing, and that stays honestly unknown. */
    @Override
    protected int nullability(final int column) {
        final Boolean nullable = columns.get(column - 1).getNullable();
        if (nullable == null) {
            return columnNullableUnknown;
        }
        return nullable.booleanValue() ? columnNullable : columnNoNulls;
    }
}
