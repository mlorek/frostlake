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

package dev.frostlake.rt.js;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * What a JavaScript procedure's result set and statement tell of a column and of the statement that answered it
 * (live-verified). A column is told by the account's name for its type ({@code getColumnSqlType}: FIXED, REAL,
 * TEXT, BOOLEAN, DATE, TIME, TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ, VARIANT, ARRAY, OBJECT, BINARY), the
 * family the handler reads it in ({@code getColumnType}: number, string, boolean, date, json, buffer, and
 * {@code invalid} for a TIME), its scale (a number's, or a time's fractional digits), whether it is nullable and
 * which of the {@code isColumn…} kinds it is: an ARRAY and an OBJECT are a VARIANT too.
 */
final class JavaScriptColumns {

    private static final String FIXED = "FIXED";
    private static final String REAL = "REAL";
    private static final String TEXT = "TEXT";
    private static final String DATE = "DATE";
    private static final String TIME = "TIME";
    private static final String VARIANT = "VARIANT";
    private static final String ARRAY = "ARRAY";
    private static final String OBJECT = "OBJECT";
    private static final String BINARY = "BINARY";
    private static final String BOOLEAN = "BOOLEAN";
    private static final String TIMESTAMP = "TIMESTAMP_";

    /** The column a DML statement's count of inserted rows is answered in. */
    static final String INSERTED = "number of rows inserted";
    /** The column a DML statement's count of updated rows is answered in. */
    static final String UPDATED = "number of rows updated";
    /** The column a DML statement's count of deleted rows is answered in. */
    static final String DELETED = "number of rows deleted";
    /** The column an UPDATE's count of rows a join matched more than once is answered in. */
    static final String MULTI_JOINED = "number of multi-joined rows updated";

    private JavaScriptColumns() {
    }

    /**
     * What a column is asked for: its name, a family, a kind, its scale, whether it is nullable, or its description.
     *
     * @param column the column
     * @param what   the member asked: name, type, sqlType, scale, nullable, description, or a kind (array, binary,
     *               boolean, date, number, object, text, time, timestamp, variant)
     * @return the answer: a text, a number or a boolean
     */
    static Object about(final ResultSetColumn column, final String what) {
        final DataType type = column.getDataType();
        final String sql = sqlType(type);
        switch (what) {
            case "name":
                return column.getName();
            case "type":
                return family(sql);
            case "sqlType":
                return sql;
            case "scale":
                return Integer.valueOf(scale(type, sql));
            case "nullable":
                // What the projection worked out from its expression, where it did, as the metadata tells it
                // (live-verified).
                return column.getReportedNullable() != null ? column.getReportedNullable()
                    : Boolean.valueOf(column.isNullabilityKnown() && column.isNullable());
            case "description":
                return "{\"sqlType\":\"\",\"scale\":" + scale(type, sql) + ",\"precision\":"
                    + (FIXED.equals(sql) ? ((NumericType) type).getPrecision() : 0)
                    + ",\"dbType\":" + dbType(sql) + "}";
            case "array":
                return Boolean.valueOf(ARRAY.equals(sql));
            case "binary":
                return Boolean.valueOf(BINARY.equals(sql));
            case "boolean":
                return Boolean.valueOf(BOOLEAN.equals(sql));
            case "date":
                return Boolean.valueOf(DATE.equals(sql));
            case "number":
                return Boolean.valueOf(FIXED.equals(sql) || REAL.equals(sql));
            case "object":
                return Boolean.valueOf(OBJECT.equals(sql));
            case "text":
                return Boolean.valueOf(TEXT.equals(sql));
            case "time":
                return Boolean.valueOf(TIME.equals(sql));
            case "timestamp":
                return Boolean.valueOf(sql.startsWith(TIMESTAMP));
            case "variant":
                return Boolean.valueOf(VARIANT.equals(sql) || ARRAY.equals(sql) || OBJECT.equals(sql));
            default:
                return null;
        }
    }

    /** The account's name for a column's type. */
    static String sqlType(final DataType type) {
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? REAL : FIXED;
        }
        if (type instanceof StringType) {
            return TEXT;
        }
        if (type instanceof BooleanType) {
            return BOOLEAN;
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if (DATE.equals(name) || TIME.equals(name)) {
                return name;
            }
            if (!((DateTimeType) type).hasTimeZone()) {
                return "TIMESTAMP_NTZ";
            }
            return name.endsWith("LTZ") ? "TIMESTAMP_LTZ" : "TIMESTAMP_TZ";
        }
        if (type instanceof VariantType) {
            return VARIANT;
        }
        if (type instanceof ArrayType) {
            return ARRAY;
        }
        if (type instanceof ObjectType || type instanceof MapType) {
            return OBJECT;
        }
        if (type instanceof BinaryType) {
            return BINARY;
        }
        return type == null ? TEXT : type.getName().toUpperCase(Locale.ROOT);
    }

    /** The family the handler reads a column of this type in. */
    private static String family(final String sql) {
        if (FIXED.equals(sql) || REAL.equals(sql)) {
            return "number";
        }
        if (DATE.equals(sql) || sql.startsWith(TIMESTAMP)) {
            return "date";
        }
        if (TIME.equals(sql)) {
            return "invalid";
        }
        if (VARIANT.equals(sql) || ARRAY.equals(sql) || OBJECT.equals(sql)) {
            return "json";
        }
        if (BINARY.equals(sql)) {
            return "buffer";
        }
        return BOOLEAN.equals(sql) ? "boolean" : "string";
    }

    private static int scale(final DataType type, final String sql) {
        if (FIXED.equals(sql)) {
            return ((NumericType) type).getScale();
        }
        return TIME.equals(sql) || sql.startsWith(TIMESTAMP) ? ((DateTimeType) type).getPrecision() : 0;
    }

    /** The account's number for a type in a column's description. */
    private static int dbType(final String sql) {
        switch (sql) {
            case FIXED:
                return 0;
            case REAL:
                return 1;
            case TEXT:
                return 2;
            case DATE:
                return 3;
            case "TIMESTAMP_LTZ":
                return 4;
            case "TIMESTAMP_NTZ":
                return 5;
            case "TIMESTAMP_TZ":
                return 6;
            case VARIANT:
                return 7;
            case OBJECT:
                return 8;
            case ARRAY:
                return 9;
            case BINARY:
                return 10;
            case TIME:
                return 11;
            case BOOLEAN:
                return 12;
            default:
                return 2;
        }
    }

    /**
     * A value as {@code getColumnValueAsString} answers it: NULL as the text {@code null}, a DATE as its midnight, a
     * TIME and a timestamp with all their fractional digits and a TIMESTAMP_TZ with its offset, a semi-structured
     * value as its JSON, and a BINARY as the JSON of its bytes by position.
     *
     * @param value the value
     * @param type  its column's type
     * @return the text
     */
    static String asString(final Object value, final DataType type) {
        if (value == null) {
            return "null";
        }
        final String sql = sqlType(type);
        final int digits = TIME.equals(sql) || sql.startsWith(TIMESTAMP) ? ((DateTimeType) type).getPrecision() : 0;
        if (value instanceof LocalDate) {
            return value + " 00:00:00";
        }
        if (value instanceof LocalTime) {
            return clock((LocalTime) value, digits);
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime at = (LocalDateTime) value;
            return at.toLocalDate() + " " + clock(at.toLocalTime(), digits);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime at = (OffsetDateTime) value;
            return at.toLocalDate() + " " + clock(at.toLocalTime(), digits) + " " + offset(at.getOffset());
        }
        if (value instanceof ZonedDateTime) {
            return asString(((ZonedDateTime) value).toOffsetDateTime(), type);
        }
        if (value instanceof Instant) {
            return asString(((Instant) value).atOffset(ZoneOffset.UTC), type);
        }
        if (value instanceof VariantValue) {
            return ((VariantValue) value).text();
        }
        if (value instanceof BinaryValue) {
            final byte[] bytes = ((BinaryValue) value).bytes();
            final StringBuilder json = new StringBuilder("{");
            for (int i = 0; i < bytes.length; i++) {
                json.append(i == 0 ? "" : ",").append('"').append(i).append("\":").append(bytes[i] & 0xFF);
            }
            return json.append('}').toString();
        }
        if (value instanceof BigDecimal) {
            // A DECFLOAT reads as its own text, 1e16384, where an exact number writes its digits (live-verified).
            return NumericType.isDecfloat(type) ? Decfloats.text((BigDecimal) value)
                : ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            final double number = ((Number) value).doubleValue();
            return number == Math.rint(number) && Math.abs(number) < 1e21 ? Long.toString((long) number)
                : Double.toString(number);
        }
        return String.valueOf(value);
    }

    private static String clock(final LocalTime time, final int digits) {
        final String whole = String.format(Locale.ROOT, "%02d:%02d:%02d", time.getHour(), time.getMinute(),
            time.getSecond());
        if (digits <= 0) {
            return whole;
        }
        final String nanos = String.format(Locale.ROOT, "%09d", time.getNano());
        return whole + "." + nanos.substring(0, Math.min(digits, 9));
    }

    private static String offset(final ZoneOffset offset) {
        final int seconds = offset.getTotalSeconds();
        final int minutes = Math.abs(seconds) / 60;
        return String.format(Locale.ROOT, "%s%02d:%02d", seconds < 0 ? "-" : "+", minutes / 60, minutes % 60);
    }

    /**
     * How many rows a statement inserted, updated, deleted, or updated through a join matching them more than once,
     * from the counts its answer holds: none for a statement that answers no such count.
     *
     * @param answer the statement's answer
     * @param column the count's column
     * @return the count
     */
    static long count(final ResultSet answer, final String column) {
        for (int i = 0; i < answer.getColumns().size(); i++) {
            if (answer.getColumns().get(i).getName().equals(column) && answer.getRowCount() > 0) {
                final Row row = answer.getRows().get(0);
                final Object value = i < row.size() ? row.getValue(i) : null;
                return value instanceof Number ? ((Number) value).longValue() : 0L;
            }
        }
        return 0L;
    }

    /**
     * Whether a statement wrote rows: its answer is the count of what it inserted, updated or deleted.
     *
     * @param answer the statement's answer
     * @return whether it is a DML statement's
     */
    static boolean isDml(final ResultSet answer) {
        for (final ResultSetColumn column : answer.getColumns()) {
            final String name = column.getName();
            if (INSERTED.equals(name) || UPDATED.equals(name) || DELETED.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
