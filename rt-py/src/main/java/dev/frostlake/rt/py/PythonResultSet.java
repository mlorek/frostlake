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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
     * bool, None), and handed the cpython worker the same. Temporals cross as ISO strings and BigDecimal as
     * long/double — the Python side re-types them from {@link #columnTypes()}; host objects would otherwise
     * surface as opaque foreign values that break {@code json.dumps} and pandas.
     *
     * <p>Snowpark's rows hold what the Python connector maps each type to (docs.snowflake.com/en/developer-guide/
     * python-connector/python-connector-api, "Data type mappings"), and the shim builds those from what crosses here
     * (#2392): a NUMBER with a scale crosses as its exact text at that scale and becomes a {@code decimal.Decimal}, a
     * whole NUMBER as a long or, past one, as its exact text and becomes an {@code int} - as a double either lost
     * its digits -, a TIME as its text to the microsecond and becomes a {@code datetime.time}, a TIMESTAMP_TZ as its
     * text with its own offset and a TIMESTAMP_LTZ with the session zone's, each becoming an aware
     * {@code datetime}, and a VECTOR as the list of its elements.
     */
    public List<List<Object>> data() {
        final List<DataType> types = new ArrayList<>();
        for (int i = 0; i < resultSet.getColumns().size(); i++) {
            types.add(resultSet.getColumns().get(i).getDataType());
        }
        final List<List<Object>> out = new ArrayList<>();
        for (final Row row : rows) {
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                values.add(toPythonFriendly(row.getValue(i), i < types.size() ? types.get(i) : null));
            }
            out.add(values);
        }
        return out;
    }

    private static final DateTimeFormatter PYTHON_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS", Locale.ROOT);
    /** {@code xxx} writes {@code +00:00} for UTC rather than {@code Z}, which Python before 3.11 cannot read. */
    private static final DateTimeFormatter PYTHON_ZONED =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx", Locale.ROOT);

    private static Object toPythonFriendly(final Object value, final DataType type) {
        if (value instanceof Number && type instanceof NumericType && !NumericType.isApproximate(type)
                && !NumericType.isDecfloat(type) && !(value instanceof Double) && !(value instanceof Float)) {
            return exact((Number) value, ((NumericType) type).getScale());
        }
        if (value instanceof LocalTime) {
            return ((LocalTime) value).truncatedTo(ChronoUnit.MICROS).format(PYTHON_TIME);
        }
        if (value instanceof ZonedDateTime || value instanceof OffsetDateTime || value instanceof Instant) {
            return zoned(value, type);
        }
        if (value instanceof VectorValue) {
            final VectorValue vector = (VectorValue) value;
            final List<Object> elements = new ArrayList<>();
            for (int i = 0; i < vector.dimension(); i++) {
                elements.add(vector.getElementType() == VectorElementType.INT
                    ? (Object) Long.valueOf((long) vector.element(i)) : (Object) Double.valueOf(vector.element(i)));
            }
            return elements;
        }
        return toPythonFriendly(value);
    }

    /** An exact NUMBER: a whole one as a long while it fits and as its digits past that, one with a scale as text. */
    private static Object exact(final Number value, final int scale) {
        if (scale <= 0 && (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte)) {
            return Long.valueOf(value.longValue());
        }
        final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof BigInteger ? new BigDecimal((BigInteger) value) : new BigDecimal(value.toString());
        if (scale <= 0) {
            final BigInteger whole = number.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
            return whole.bitLength() < Long.SIZE ? (Object) Long.valueOf(whole.longValue()) : whole.toString();
        }
        return number.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    /** A zoned timestamp's text with its offset: a TIMESTAMP_TZ's own, a TIMESTAMP_LTZ's the session zone's. */
    private static String zoned(final Object value, final DataType type) {
        final OffsetDateTime instant = value instanceof ZonedDateTime ? ((ZonedDateTime) value).toOffsetDateTime()
            : value instanceof OffsetDateTime ? (OffsetDateTime) value : ((Instant) value).atOffset(ZoneOffset.UTC);
        final String name = type == null || type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        final OffsetDateTime shown = name.endsWith("_TZ") ? instant
            : instant.atZoneSameInstant(SessionZone.current()).toOffsetDateTime();
        return shown.truncatedTo(ChronoUnit.MICROS).format(PYTHON_ZONED);
    }

    private static Object toPythonFriendly(final Object value) {
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
