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

import com.snowflake.snowpark.SnowparkClientException;
import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row handed back by {@link DataFrame#collect()}.
 *
 * <p>This is the type a handler written against Snowpark expects — {@code rows[0].getString(0)} — and it
 * is deliberately NOT Frostlake's own {@code storage.Row}, whose only accessor is {@code getValue(int)}.
 * A cell arrives as the class Snowpark hands a handler for its type (see {@link #ofEngineCells}), and an
 * accessor reads it as Snowpark's does: by a cast, so that a cell of another kind fails the handler here as it
 * fails it on an account.
 */
public class Row {

    /** The digits a whole number may have and still be handed over as a Long. */
    private static final int LONG_DIGITS = 18;

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

    /** The row's values, in their order. */
    public List<Object> toList() {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(values, values.length)));
    }

    @Override
    public Row clone() {
        return new Row(Arrays.copyOf(values, values.length));
    }

    // --- typed accessors -----------------------------------------------------------------------
    // Each reads the cell as Snowpark does: a cast to the accessor's own class, so a cell of another kind fails
    // the handler with the cast's exception; the whole and the real numbers alone are read from one another.

    public String getString(final int index) {
        final Object value = values[index];
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            // Bytes are told as the array of numbers they are.
            final StringBuilder told = new StringBuilder("[");
            for (final byte one : (byte[]) value) {
                told.append(told.length() > 1 ? "," : "").append(one);
            }
            return told.append(']').toString();
        }
        return (String) value;
    }

    public boolean getBoolean(final int index) {
        final Object value = values[index];
        if (value == null) {
            throw new NullPointerException("Value at index " + index + " is null");
        }
        return ((Boolean) value).booleanValue();
    }

    public byte getByte(final int index) {
        return whole(index, "Byte").byteValue();
    }

    public short getShort(final int index) {
        return whole(index, "Short").shortValue();
    }

    public int getInt(final int index) {
        return whole(index, "Int").intValue();
    }

    public long getLong(final int index) {
        return whole(index, "Long").longValue();
    }

    public float getFloat(final int index) {
        return real(index, "Float").floatValue();
    }

    public double getDouble(final int index) {
        return real(index, "Double").doubleValue();
    }

    public BigDecimal getDecimal(final int index) {
        return (BigDecimal) values[index];
    }

    public Date getDate(final int index) {
        return (Date) values[index];
    }

    public Time getTime(final int index) {
        return (Time) values[index];
    }

    public Timestamp getTimestamp(final int index) {
        return (Timestamp) values[index];
    }

    public byte[] getBinary(final int index) {
        return (byte[]) values[index];
    }

    public Variant getVariant(final int index) {
        final String text = getString(index);
        if (text == null) {
            throw new NullPointerException();
        }
        return new Variant(text);
    }

    /** A structured OBJECT's cell, which is a row: any other cell is none. */
    public Row getObject(final int index) {
        return (Row) values[index];
    }

    public List<?> getList(final int index) {
        return (List<?>) values[index];
    }

    public Map<?, ?> getMap(final int index) {
        return (Map<?, ?>) values[index];
    }

    /** The elements of an ARRAY's cell, each a Variant. */
    public List<Variant> getListOfVariant(final int index) {
        return new ArrayList<Variant>(getVariant(index).asList());
    }

    /** The members of an OBJECT's cell, each value a Variant. */
    public Map<String, Variant> getMapOfVariant(final int index) {
        return new HashMap<String, Variant>(getVariant(index).asMap());
    }

    /**
     * A whole number's cell, which a NUMBER of no scale is: any other cell is no Int, Long, Short or Byte, and
     * neither is a number the accessor's type cannot hold — 300 is no Byte.
     */
    private Number whole(final int index, final String asType) {
        final Object value = values[index];
        if (value == null) {
            throw new NullPointerException();
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            final long number = ((Number) value).longValue();
            final long widest = "Byte".equals(asType) ? Byte.MAX_VALUE : "Short".equals(asType) ? Short.MAX_VALUE
                : "Int".equals(asType) ? Integer.MAX_VALUE : Long.MAX_VALUE;
            if (number <= widest && number >= -widest - 1) {
                return (Number) value;
            }
        }
        throw notCast(value, asType);
    }

    /** A cell read as a Double or a Float: a whole number's and a FLOAT's, and no other. */
    private Number real(final int index, final String asType) {
        final Object value = values[index];
        if (value == null) {
            throw new NullPointerException();
        }
        if (value instanceof Double || value instanceof Float || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return (Number) value;
        }
        throw notCast(value, asType);
    }

    private static SnowparkClientException notCast(final Object value, final String asType) {
        return new SnowparkClientException("Error Code: 0400, Error message: Cannot cast "
            + value.getClass().getName() + "(" + value + ") to " + asType + ".");
    }

    /**
     * A row of the engine's cells, each as the class Snowpark hands a handler: a NUMBER of no scale a Long and
     * one with a scale a BigDecimal, a FLOAT a Double, a DATE, a TIME and a TIMESTAMP the java.sql classes, a
     * BINARY its bytes, and a VARIANT, an ARRAY or an OBJECT its JSON text (live-verified).
     *
     * @param cells the engine's values
     * @return the row a handler reads
     */
    static Row ofEngineCells(final List<Object> cells) {
        final Object[] read = new Object[cells == null ? 0 : cells.size()];
        for (int i = 0; i < read.length; i++) {
            read[i] = handed(cells.get(i));
        }
        return new Row(read);
    }

    private static Object handed(final Object cell) {
        if (cell instanceof LocalDate) {
            return Date.valueOf((LocalDate) cell);
        }
        if (cell instanceof LocalTime) {
            return Time.valueOf((LocalTime) cell);
        }
        if (cell instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) cell);
        }
        if (cell instanceof OffsetDateTime) {
            return Timestamp.from(((OffsetDateTime) cell).toInstant());
        }
        if (cell instanceof ZonedDateTime) {
            return Timestamp.from(((ZonedDateTime) cell).toInstant());
        }
        if (cell instanceof Instant) {
            return Timestamp.from((Instant) cell);
        }
        if (cell instanceof BinaryValue) {
            return ((BinaryValue) cell).bytes();
        }
        if (cell instanceof VariantValue) {
            return ((VariantValue) cell).text();
        }
        if (cell instanceof Integer || cell instanceof Short || cell instanceof Byte) {
            return Long.valueOf(((Number) cell).longValue());
        }
        if (cell instanceof BigInteger) {
            final BigInteger whole = (BigInteger) cell;
            return whole.bitLength() < Long.SIZE ? (Object) Long.valueOf(whole.longValue()) : new BigDecimal(whole);
        }
        if (cell instanceof BigDecimal) {
            final BigDecimal number = (BigDecimal) cell;
            return number.scale() <= 0 && number.precision() - number.scale() <= LONG_DIGITS
                ? (Object) Long.valueOf(number.longValue()) : number;
        }
        if (cell instanceof Float) {
            return Double.valueOf(((Float) cell).doubleValue());
        }
        return cell;
    }

    /** The row as Snowpark prints it: its values between brackets, a text between double quotes. */
    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("Row[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(",");
            }
            if (values[i] instanceof String) {
                text.append('"').append(values[i]).append('"');
            } else {
                text.append(values[i]);
            }
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
