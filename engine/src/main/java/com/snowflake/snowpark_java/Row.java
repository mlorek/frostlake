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
import com.snowflake.snowpark_java.types.Geography;
import com.snowflake.snowpark_java.types.Geometry;
import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.SnowparkStatementFailure;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.GeoValue;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /** The smallest whole number a cell of no scale may hold and still be handed over. */
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    /** The largest whole number a cell of no scale may hold and still be handed over. */
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    /** The SQLSTATE of the driver's refusal of a whole number no Long holds. */
    private static final String NOT_CONVERTED_STATE = "0A000";

    /** The driver's error code for that refusal. */
    private static final int NOT_CONVERTED_CODE = 200038;

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

    /** The cell, a geospatial one as the {@code com.snowflake.snowpark_java.types} class of its kind. */
    public Object get(final int index) {
        return javaValue(values[index]);
    }

    public boolean isNullAt(final int index) {
        return values[index] == null;
    }

    /**
     * The row's values, in their order, as Snowpark's own row holds them: a geospatial one as the
     * {@code com.snowflake.snowpark.types} class of its kind, where {@link #get} hands the Java surface's
     * (live-verified).
     */
    public List<Object> toList() {
        return Collections.unmodifiableList(Arrays.asList(Arrays.copyOf(values, values.length)));
    }

    /** A value as the Java surface hands it: a geospatial one as that surface's class, any other as it is. */
    private static Object javaValue(final Object value) {
        if (value instanceof com.snowflake.snowpark.types.Geography) {
            return Geography.fromGeoJSON(((com.snowflake.snowpark.types.Geography) value).asGeoJSON());
        }
        if (value instanceof com.snowflake.snowpark.types.Geometry) {
            return Geometry.fromGeoJSON(value.toString());
        }
        return value;
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
            return "[" + byteNumbers((byte[]) value) + "]";
        }
        if (value instanceof com.snowflake.snowpark.types.Geography
                || value instanceof com.snowflake.snowpark.types.Geometry) {
            return value.toString();
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

    /**
     * A GEOGRAPHY's cell. Any other cell fails the cast to the class Snowpark holds it as, {@code class
     * java.lang.Long cannot be cast to class com.snowflake.snowpark.types.Geography}, and a NULL is no value
     * (live-verified).
     */
    public Geography getGeography(final int index) {
        final Object value = values[index];
        if (value instanceof Geography) {
            return (Geography) value;
        }
        final com.snowflake.snowpark.types.Geography held = (com.snowflake.snowpark.types.Geography) value;
        if (held == null) {
            throw new NullPointerException();
        }
        return Geography.fromGeoJSON(held.asGeoJSON());
    }

    /**
     * A GEOMETRY's cell. Any other cell fails the cast to the class Snowpark holds it as, a GEOGRAPHY's among them
     * — {@code class com.snowflake.snowpark.types.Geography cannot be cast to class
     * com.snowflake.snowpark.types.Geometry} — and a NULL is no value (live-verified).
     */
    public Geometry getGeometry(final int index) {
        final Object value = values[index];
        if (value instanceof Geometry) {
            return (Geometry) value;
        }
        final com.snowflake.snowpark.types.Geometry held = (com.snowflake.snowpark.types.Geometry) value;
        if (held == null) {
            throw new NullPointerException();
        }
        return Geometry.fromGeoJSON(held.toString());
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
     * BINARY its bytes, a VARIANT, an ARRAY or an OBJECT its JSON text, and a GEOGRAPHY or a GEOMETRY its
     * GeoJSON (live-verified). A whole number no Long holds is not handed over at all: the driver under Snowpark
     * reads a NUMBER of no scale as a Long, and the collect fails, {@code Cannot convert value in the driver from
     * type:FIXED(null,null) to type:Long, value=12345678901234567890.}, as a {@link SQLException} of SQLSTATE
     * 0A000 and code 200038 — over a table's NUMBER(38,0) column as over a literal, whichever row holds it.
     *
     * <p>A TIMESTAMP_LTZ and a TIMESTAMP_TZ cell print as the account prints them, in the zone the session's
     * TIMEZONE names and in their own offset (see {@link SessionTimestamp}).
     *
     * @param cells the engine's values
     * @param whole for each cell, whether its column is a NUMBER of no scale; null where no column is known
     * @param local for each cell, whether its column is a TIMESTAMP_LTZ, printed in the session's zone
     * @param own   for each cell, whether its column is a TIMESTAMP_TZ, printed in its own wall clock
     * @return the row a handler reads
     */
    static Row ofEngineCells(final List<Object> cells, final boolean[] whole, final boolean[] local,
                             final boolean[] own) {
        final ZoneId zone = SessionZone.current();
        final Object[] read = new Object[cells == null ? 0 : cells.size()];
        for (int i = 0; i < read.length; i++) {
            final Object cell = cells.get(i);
            read[i] = whole != null && i < whole.length && whole[i] && cell instanceof Number
                ? wholeNumber((Number) cell) : handed(cell, zone, marked(local, i), marked(own, i));
        }
        return new Row(read);
    }

    private static boolean marked(final boolean[] columns, final int index) {
        return columns != null && index < columns.length && columns[index];
    }

    /** A whole number's cell as a Long, or the driver's refusal when no Long holds it. */
    private static Long wholeNumber(final Number cell) {
        final BigInteger whole = cell instanceof BigDecimal ? ((BigDecimal) cell).toBigInteger()
            : cell instanceof BigInteger ? (BigInteger) cell : BigInteger.valueOf(cell.longValue());
        if (whole.compareTo(LONG_MIN) < 0 || whole.compareTo(LONG_MAX) > 0) {
            throw SnowparkStatementFailure.raise("Cannot convert value in the driver from type:FIXED(null,null) to "
                + "type:Long, value=" + whole + ".", NOT_CONVERTED_STATE, NOT_CONVERTED_CODE);
        }
        return Long.valueOf(whole.longValue());
    }

    /**
     * @param local whether the cell's column is a TIMESTAMP_LTZ
     * @param own   whether the cell's column is a TIMESTAMP_TZ; where neither is known an offset cell is the
     *              engine's TIMESTAMP_LTZ and a zoned one its TIMESTAMP_TZ
     */
    private static Object handed(final Object cell, final ZoneId zone, final boolean local, final boolean own) {
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
            final OffsetDateTime offset = (OffsetDateTime) cell;
            return own ? SessionTimestamp.ofOwnClock(offset.toInstant(), offset.toLocalDateTime(), zone)
                : SessionTimestamp.ofInstant(offset.toInstant(), zone);
        }
        if (cell instanceof ZonedDateTime) {
            final ZonedDateTime zoned = (ZonedDateTime) cell;
            return local ? SessionTimestamp.ofInstant(zoned.toInstant(), zone)
                : SessionTimestamp.ofOwnClock(zoned.toInstant(), zoned.toLocalDateTime(), zone);
        }
        if (cell instanceof Instant) {
            return SessionTimestamp.ofInstant((Instant) cell, zone);
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
            return wholeNumber((BigInteger) cell);
        }
        if (cell instanceof BigDecimal) {
            final BigDecimal number = (BigDecimal) cell;
            return number.scale() <= 0 ? (Object) wholeNumber(number) : number;
        }
        if (cell instanceof Float) {
            return Double.valueOf(((Float) cell).doubleValue());
        }
        if (cell instanceof GeoValue) {
            final GeoValue geo = (GeoValue) cell;
            return geo.isGeography() ? (Object) com.snowflake.snowpark.types.Geography.fromGeoJSON(geo.geoJson())
                : com.snowflake.snowpark.types.Geometry.fromGeoJSON(geometryText(geo.node()));
        }
        return cell;
    }

    /**
     * A GEOMETRY's GeoJSON as the driver hands it over: its keys in order and every number written with fifteen
     * decimals and an exponent, {@code {"coordinates":[1.000000000000000e+00,2.000000000000000e+00],"type":"Point"}}
     * (live-verified).
     */
    private static String geometryText(final JsonNode node) {
        final StringBuilder text = new StringBuilder();
        writeGeometry(node, text);
        return text.toString();
    }

    private static void writeGeometry(final JsonNode node, final StringBuilder text) {
        if (node.isObject()) {
            text.append('{');
            boolean first = true;
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                text.append(first ? "" : ",").append('"').append(member.getKey()).append("\":");
                writeGeometry(member.getValue(), text);
                first = false;
            }
            text.append('}');
        } else if (node.isArray()) {
            text.append('[');
            for (int i = 0; i < node.size(); i++) {
                text.append(i > 0 ? "," : "");
                writeGeometry(node.get(i), text);
            }
            text.append(']');
        } else if (node.isNumber()) {
            text.append(String.format(Locale.ROOT, "%.15e", node.doubleValue()));
        } else {
            text.append(node.toString());
        }
    }

    /**
     * The row as Snowpark prints it: its values between brackets, a text between double quotes and a binary as its
     * bytes, {@code Row[Binary(65,66,67)]} (live-verified).
     */
    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("Row[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(",");
            }
            if (values[i] instanceof String) {
                text.append('"').append(values[i]).append('"');
            } else if (values[i] instanceof byte[]) {
                text.append("Binary(").append(byteNumbers((byte[]) values[i])).append(')');
            } else {
                text.append(values[i]);
            }
        }
        return text.append("]").toString();
    }

    /** Bytes as the numbers they are, between commas. */
    private static String byteNumbers(final byte[] bytes) {
        final StringBuilder told = new StringBuilder();
        for (final byte one : bytes) {
            told.append(told.length() > 0 ? "," : "").append(one);
        }
        return told.toString();
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
