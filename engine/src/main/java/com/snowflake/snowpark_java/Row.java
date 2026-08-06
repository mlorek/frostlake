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

package com.snowflake.snowpark_java;

import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One row handed back by {@link DataFrame#collect()}.
 *
 * <p>This is the type a handler written against Snowpark expects — {@code rows[0].getString(0)} — and it
 * is deliberately NOT Frostlake's own {@code storage.Row}, whose only accessor is {@code getValue(int)}.
 * Values arrive from the engine already typed, so each getter coerces rather than parses: a NUMBER cell
 * is some {@link Number} whatever its declared width, and asking for it as an int, a long or a
 * BigDecimal all have to work.
 */
public class Row {

    private final Object[] values;

    public Row(final Object[] values) {
        this.values = values == null ? new Object[0] : values;
    }

    public Row(final List<Object> values) {
        this(values == null ? new Object[0] : values.toArray());
    }

    /** Snowpark's factory spelling. */
    public static Row create(final Object... values) {
        return new Row(values);
    }

    public int size() {
        return values.length;
    }

    public Object get(final int index) {
        return values[index];
    }

    public boolean isNullAt(final int index) {
        return values[index] == null;
    }

    public Object[] toArray() {
        return Arrays.copyOf(values, values.length);
    }

    // --- typed accessors -----------------------------------------------------------------------
    // Each refuses a NULL the way Snowpark does for a primitive, and coerces otherwise.

    public String getString(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof BinaryValue) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    public boolean getBoolean(final int index) {
        final Object value = required(index, "Boolean");
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public byte getByte(final int index) {
        return number(index, "Byte").byteValue();
    }

    public short getShort(final int index) {
        return number(index, "Short").shortValue();
    }

    public int getInt(final int index) {
        return number(index, "Int").intValue();
    }

    public long getLong(final int index) {
        return number(index, "Long").longValue();
    }

    public float getFloat(final int index) {
        return number(index, "Float").floatValue();
    }

    public double getDouble(final int index) {
        return number(index, "Double").doubleValue();
    }

    public BigDecimal getDecimal(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    public Date getDate(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof LocalDate) {
            return Date.valueOf((LocalDate) value);
        }
        return Date.valueOf(String.valueOf(value));
    }

    public Time getTime(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Time) {
            return (Time) value;
        }
        if (value instanceof LocalTime) {
            return Time.valueOf((LocalTime) value);
        }
        return Time.valueOf(String.valueOf(value));
    }

    public Timestamp getTimestamp(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        if (value instanceof Instant) {
            return Timestamp.from((Instant) value);
        }
        return Timestamp.valueOf(String.valueOf(value));
    }

    public byte[] getBinary(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    public Variant getVariant(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Variant) {
            return (Variant) value;
        }
        if (value instanceof VariantValue) {
            return new Variant(((VariantValue) value).text());
        }
        return new Variant(String.valueOf(value));
    }

    /** A nested row, for a value that is itself structured. */
    public Row getStruct(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Row) {
            return (Row) value;
        }
        return new Row(new Object[]{value});
    }

    public List<Object> getList(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return new ArrayList<Object>((List<?>) value);
        }
        if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        final List<Object> single = new ArrayList<Object>();
        single.add(value);
        return single;
    }

    public Map<?, ?> getMap(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<?, ?>) value;
        }
        throw new IllegalArgumentException("Value at index " + index + " is not a map: " + value);
    }

    private Object required(final int index, final String asType) {
        final Object value = values[index];
        if (value == null) {
            throw new NullPointerException("Value at index " + index + " is NULL and cannot be read as "
                + asType + "; check isNullAt(" + index + ") first");
        }
        return value;
    }

    private Number number(final int index, final String asType) {
        final Object value = required(index, asType);
        if (value instanceof Number) {
            return (Number) value;
        }
        if (value instanceof Boolean) {
            return Integer.valueOf(((Boolean) value).booleanValue() ? 1 : 0);
        }
        return new BigDecimal(String.valueOf(value));
    }

    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("Row[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(",");
            }
            text.append(values[i]);
        }
        return text.append("]").toString();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Row && Arrays.deepEquals(values, ((Row) other).values);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }
}
