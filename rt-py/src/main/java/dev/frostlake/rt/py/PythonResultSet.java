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

package dev.frostlake.rt.py;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PythonResultSet {
    private final ResultSet resultSet;
    private final List<Row> rows;
    private int currentRow = -1;

    public PythonResultSet(final ResultSet resultSet) {
        this.resultSet = resultSet;
        this.rows = resultSet.getRows();
    }

    public boolean next() {
        currentRow++;
        return currentRow < rows.size();
    }

    public Object get(final int columnIndex) {
        if (currentRow < 0 || currentRow >= rows.size()) {
            throw new RuntimeException("No current row. Call next() first.");
        }

        final Row row = rows.get(currentRow);
        final int index = columnIndex;

        if (index < 0 || index >= row.size()) {
            throw new RuntimeException("Column index " + columnIndex + " is out of range");
        }

        return row.getValue(index);
    }

    public Object get(final String columnName) {
        if (currentRow < 0 || currentRow >= rows.size()) {
            throw new RuntimeException("No current row. Call next() first.");
        }

        final Row row = rows.get(currentRow);

        for (int i = 0; i < resultSet.getColumns().size(); i++) {
            if (resultSet.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
                return row.getValue(i);
            }
        }

        throw new RuntimeException("Column not found: " + columnName);
    }

    public List<Row> collect() {
        return new ArrayList<>(rows);
    }

    public int count() {
        return rows.size();
    }

    /** Column names in result order, for the Python snowpark emulation. */
    public List<String> columnNames() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < resultSet.getColumns().size(); i++) {
            names.add(resultSet.getColumns().get(i).getName());
        }
        return names;
    }

    /** SQL type names ({@code VARCHAR}, {@code VARIANT}, ...) per column, aligned with {@link #columnNames()}. */
    public List<String> columnTypes() {
        final List<String> types = new ArrayList<>();
        for (int i = 0; i < resultSet.getColumns().size(); i++) {
            types.add(resultSet.getColumns().get(i).getDataType() == null
                ? "VARCHAR"
                : resultSet.getColumns().get(i).getDataType().getName());
        }
        return types;
    }

    /**
     * All row values, normalized to types GraalPy maps onto native Python values (str, int, float,
     * bool, None). Temporals cross as ISO strings and BigDecimal as long/double — the Python side
     * re-types them from {@link #columnTypes()}; host objects would otherwise surface as opaque
     * foreign values that break {@code json.dumps} and pandas.
     */
    public List<List<Object>> data() {
        final List<List<Object>> out = new ArrayList<>();
        for (final Row row : rows) {
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                values.add(toPythonFriendly(row.getValue(i)));
            }
            out.add(values);
        }
        return out;
    }

    private Object toPythonFriendly(final Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
            || value instanceof Long || value instanceof Integer || value instanceof Short
            || value instanceof Byte || value instanceof Double || value instanceof Float) {
            return value;
        }
        if (value instanceof BigDecimal) {
            final BigDecimal decimal = ((BigDecimal) value).stripTrailingZeros();
            if (decimal.scale() <= 0) {
                try {
                    return decimal.longValueExact();
                } catch (final ArithmeticException tooBig) {
                    return decimal.doubleValue();
                }
            }
            return decimal.doubleValue();
        }
        if (value instanceof BigInteger) {
            try {
                return ((BigInteger) value).longValueExact();
            } catch (final ArithmeticException tooBig) {
                return ((BigInteger) value).doubleValue();
            }
        }
        if (value instanceof LocalDateTime || value instanceof LocalDate || value instanceof LocalTime) {
            return value.toString();
        }
        if (value instanceof BinaryValue) {
            // BINARY crosses as its hex text; the shim re-types it to bytes via columnTypes().
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof VariantValue) {
            // Semi-structured crosses as its JSON text; the shim re-types via columnTypes().
            return ((VariantValue) value).text();
        }
        return value;
    }
}
