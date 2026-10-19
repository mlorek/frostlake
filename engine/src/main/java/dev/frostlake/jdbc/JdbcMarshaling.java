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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.SqlTokens;
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Shared marshaling between the engine's stored values / type names and JDBC types, used by both the
 * HTTP-backed ({@code Database*}) and in-process ({@code Direct*}) ResultSet and metadata implementations so
 * the conversion lives in ONE place. The engine stores temporal values as {@code java.time} types
 * (see {@code DateTimeType}) and BINARY as {@code byte[]} (see {@code BinaryType}); over the HTTP/JSON wire a
 * {@code LocalDateTime} arrives as an ISO string and a {@code byte[]} as Base64, so the converters accept
 * both the live objects and their string renderings.
 */
public final class JdbcMarshaling {

    /** How a bound Timestamp, Date and Time are written for their explicit formats (see {@link #formatLiteral}). */
    private static final DateTimeFormatter BOUND_TIMESTAMP =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSSSS");
    private static final DateTimeFormatter BOUND_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final DateTimeFormatter BOUND_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Map an engine {@code DataType} to a {@link java.sql.Types} constant, using its PARAMETERS where
     * they decide the answer. Every integer alias is NUMBER(38,0) in the catalog, as on a real account,
     * so the name alone can no longer tell an integer column from a decimal one — the SCALE does, and
     * live agrees: an INT column reports {@code Types.BIGINT} over JDBC and a NUMBER(10,2) reports
     * {@code Types.DECIMAL}. It is the same rule this driver's metadata queries already apply to
     * INFORMATION_SCHEMA rows. A column NAMED DECIMAL is a file transfer's size, which the client answers itself
     * and reports as {@code Types.DECIMAL} though its scale is 0 (live-verified).
     */
    public static int toSqlType(final DataType type) {
        return type instanceof NumericType
            ? toSqlType(type.getName(), ((NumericType) type).getScale())
            : toSqlType(type.getName());
    }

    /** As above, for a caller that has the type's NAME and SCALE rather than the type itself. */
    public static int toSqlType(final String typeName, final int scale) {
        final String name = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
        if (name.equals("DECIMAL")) {
            return Types.DECIMAL;
        }
        if (name.equals("NUMBER") || name.equals("NUMERIC")) {
            return scale == 0 ? Types.BIGINT : Types.DECIMAL;
        }
        return toSqlType(typeName);
    }

    /**
     * Whether a column's type is the DECIMAL a file transfer's size carries: the client's own type, which no
     * other column is named by.
     *
     * @param type a result column's declared type
     * @return whether the driver reads the column as a {@code BigDecimal} whatever its scale
     */
    public static boolean isClientDecimal(final DataType type) {
        return type instanceof NumericType && "DECIMAL".equalsIgnoreCase(type.getName());
    }

    /** Map an engine {@code DataType} name to a {@link java.sql.Types} constant. */
    public static int toSqlType(final String typeName) {
        if (typeName == null) {
            return Types.OTHER;
        }
        final String t = typeName.toUpperCase(Locale.ROOT);
        // An interval's codes are the driver's own, outside java.sql.Types; its name contains INT and,
        // for INTERVAL_DAY_TIME, TIME, so it is tested before either.
        if (t.startsWith("INTERVAL")) {
            return isYearMonthInterval(t) ? IntervalCells.YEAR_MONTH_TYPE : IntervalCells.DAY_TIME_TYPE;
        }
        // Order matters: a more specific name often contains a shorter one (TIMESTAMP contains TIME,
        // BIGINT contains INT), so the specific cases must be tested first.
        if (t.contains("TIMESTAMP") || t.contains("DATETIME")) {
            return Types.TIMESTAMP;
        }
        if (t.contains("DATE")) {
            return Types.DATE;
        }
        if (t.contains("TIME")) {
            return Types.TIME;
        }
        if (t.contains("BIGINT")) {
            return Types.BIGINT;
        }
        if (t.contains("SMALLINT")) {
            return Types.SMALLINT;
        }
        if (t.contains("TINYINT")) {
            return Types.TINYINT;
        }
        if (t.contains("INT")) {
            return Types.INTEGER;
        }
        if (t.contains("DECIMAL") || t.contains("NUMBER") || t.contains("NUMERIC") || t.contains("DECFLOAT")) {
            return Types.DECIMAL;
        }
        if (t.contains("DOUBLE") || t.contains("REAL")) {
            return Types.DOUBLE;
        }
        if (t.contains("FLOAT")) {
            return Types.FLOAT;
        }
        if (t.contains("BOOLEAN") || t.equals("BOOL")) {
            return Types.BOOLEAN;
        }
        if (t.contains("BINARY") || t.contains("BYTES")) {
            return Types.BINARY;
        }
        if (t.contains("ARRAY")) {
            return Types.ARRAY;
        }
        // A UUID reaches JDBC as its text, which live's driver reports as a VARCHAR.
        if (t.contains("CHAR") || t.contains("VARCHAR") || t.contains("STRING") || t.contains("TEXT")
                || t.equals("UUID")) {
            return Types.VARCHAR;
        }
        // OBJECT / VARIANT / VECTOR have no precise java.sql.Types — surfaced via getObject().
        return Types.OTHER;
    }

    /**
     * The class name {@code ResultSet.getObject} yields for a {@link java.sql.Types} constant. Integral types
     * map to {@code Long} because the engine stores INTEGER/BIGINT/etc. as {@code Long} (not {@code Integer}).
     */
    /**
     * A column's type as Snowflake's JDBC driver names it in {@code ResultSetMetaData}: the timestamp flavours
     * without their underscore — TIMESTAMPNTZ, TIMESTAMPLTZ, TIMESTAMPTZ — and every approximate number DOUBLE,
     * however the column was declared (live-verified over literals, CURRENT_TIMESTAMP() and FLOAT, FLOAT4, FLOAT8,
     * REAL, DOUBLE and DOUBLE PRECISION columns). The SQL surfaces — DESCRIBE, SYSTEM$TYPEOF, the refusal
     * sentences — keep the underscores, as they do on the account.
     *
     * @param engineName the engine's type name
     * @return the driver's name for it
     */
    public static String driverTypeName(final String engineName) {
        if (engineName == null) {
            return null;
        }
        final String upper = engineName.toUpperCase(Locale.ROOT);
        // An interval is reported by its family alone, whatever fields it spans (live-verified).
        if (upper.startsWith("INTERVAL")) {
            return isYearMonthInterval(upper) ? "INTERVAL_YEAR_MONTH" : "INTERVAL_DAY_TIME";
        }
        // A VECTOR's name drops its parameters, and a MAP is reported as the OBJECT it is (live-verified).
        if (upper.startsWith("VECTOR")) {
            return "VECTOR";
        }
        if (upper.equals("MAP") || upper.startsWith("MAP(")) {
            return "OBJECT";
        }
        switch (upper) {
            case "TIMESTAMP_NTZ":
                return "TIMESTAMPNTZ";
            case "TIMESTAMP_LTZ":
                return "TIMESTAMPLTZ";
            case "TIMESTAMP_TZ":
                return "TIMESTAMPTZ";
            case "FLOAT":
                return "DOUBLE";
            default:
                return engineName;
        }
    }

    /**
     * A column's {@link Types} code by the driver's name for its type: as {@link #toSqlType(String, int)} maps
     * it, but for a TIMESTAMPTZ, which the driver reports as TIMESTAMP_WITH_TIMEZONE while a TIMESTAMPLTZ stays
     * TIMESTAMP (live-verified).
     *
     * @param driverName the driver's name for the type, see {@link #driverTypeName}
     * @param scale      the column's scale
     * @return the type code
     */
    public static int driverSqlType(final String driverName, final int scale) {
        if ("TIMESTAMPTZ".equalsIgnoreCase(driverName)) {
            return Types.TIMESTAMP_WITH_TIMEZONE;
        }
        if (reportedAsText(driverName)) {
            return Types.VARCHAR;
        }
        return "VECTOR".equalsIgnoreCase(driverName) ? VECTOR_TYPE : toSqlType(driverName, scale);
    }

    /**
     * The code the driver reports for a VECTOR column, outside {@link Types}: {@code getColumnClassName} has no class
     * for it and refuses (live-verified).
     */
    public static final int VECTOR_TYPE = 50003;

    /**
     * Whether the driver reports a column of this type as text: the semi-structured and geospatial types are type
     * code VARCHAR and {@code java.lang.String}, and {@code getObject} reads their text (live-verified).
     *
     * @param driverName the driver's name for the type, see {@link #driverTypeName}
     * @return true for VARIANT, OBJECT, ARRAY, GEOGRAPHY and GEOMETRY
     */
    public static boolean reportedAsText(final String driverName) {
        if (driverName == null) {
            return false;
        }
        switch (driverName.toUpperCase(Locale.ROOT)) {
            case "VARIANT":
            case "OBJECT":
            case "ARRAY":
            case "GEOGRAPHY":
            case "GEOMETRY":
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether an interval type's name, engine or driver spelling, names the year-month family.
     *
     * @param upper the name, upper-cased
     * @return true for a year-month interval
     */
    private static boolean isYearMonthInterval(final String upper) {
        return upper.contains("YEAR") || upper.contains("MONTH");
    }

    /**
     * A column's class name by its type code, refusing a VECTOR and an interval as the driver does.
     *
     * @param sqlType the column's type code
     * @return the class name
     * @throws SQLFeatureNotSupportedException for a VECTOR column
     */
    public static String driverColumnClassName(final int sqlType) throws SQLFeatureNotSupportedException {
        if (sqlType == VECTOR_TYPE || sqlType == IntervalCells.DAY_TIME_TYPE || sqlType == IntervalCells.YEAR_MONTH_TYPE) {
            throw new SQLFeatureNotSupportedException("No corresponding Java type is found for java.sql.Type: " + sqlType);
        }
        return columnClassName(sqlType);
    }

    public static String columnClassName(final int sqlType) {
        switch (sqlType) {
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.SMALLINT:
            case Types.TINYINT:
                return "java.lang.Long";
            case Types.FLOAT:
                return "java.lang.Float";
            case Types.DOUBLE:
                return "java.lang.Double";
            case Types.DECIMAL:
                return "java.math.BigDecimal";
            case Types.BOOLEAN:
                return "java.lang.Boolean";
            case Types.DATE:
                return "java.sql.Date";
            case Types.TIME:
                return "java.sql.Time";
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "java.sql.Timestamp";
            case Types.BINARY:
                return "[B";
            case Types.VARCHAR:
                return "java.lang.String";
            default:
                return "java.lang.Object";
        }
    }

    /**
     * Refuses a bind of a java.time value as the account's driver refuses it: it binds none of them — a LocalDate, a
     * LocalDateTime, a LocalTime, an OffsetDateTime or an Instant alike, whatever the column or the session's input
     * formats — and says {@code Data type not supported for binding: Object type: class java.time.LocalDate.}
     * (live-verified). A java.sql Date, Time or Timestamp is the bind it takes.
     *
     * @param x the value bound
     * @throws SQLException for a java.time value
     */
    public static void requireBindable(final Object x) throws SQLException {
        if (x != null && x.getClass().getName().startsWith("java.time.")) {
            throw new SQLException("Data type not supported for binding: Object type: " + x.getClass() + ".");
        }
    }

    public static Timestamp toTimestamp(final Object v) {
        if (v == null || v instanceof Timestamp) {
            return (Timestamp) v;
        }
        if (v instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) v);
        }
        if (v instanceof ZonedDateTime) {
            // The LOCAL part, for the reason the OffsetDateTime branch below gives: a TIMESTAMP_TZ's
            // wall clock IS what getString reports, and Timestamp.from would re-read the instant in
            // the host's zone and disagree with it.
            return Timestamp.valueOf(((ZonedDateTime) v).toLocalDateTime());
        }
        if (v instanceof OffsetDateTime) {
            // The LOCAL part, not the instant: a java.sql.Timestamp carries no zone, so
            // Timestamp.from would re-read the instant in the HOST's zone and disagree with the very
            // text getString returns for the same cell. A TIMESTAMP_LTZ is already expressed at the
            // session's offset, so its local part IS the wall clock the session sees.
            return Timestamp.valueOf(((OffsetDateTime) v).toLocalDateTime());
        }
        if (v instanceof LocalDate) {
            return Timestamp.valueOf(((LocalDate) v).atStartOfDay());
        }
        if (v instanceof LocalTime) {
            // A TIME is read as its time of day on 1970-01-01 (live-verified).
            return Timestamp.valueOf(LocalDate.EPOCH.atTime((LocalTime) v));
        }
        if (v instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) v).getTime());
        }
        return Timestamp.valueOf(parseDateTime(v.toString()));
    }

    /**
     * A cell of a column of the given JDBC type read by {@code getTimestamp}: a TIME's text — the wire's spelling of
     * one — is its time of day on 1970-01-01, as a TIME cell is.
     *
     * @param v          the cell
     * @param columnType the column's JDBC type
     * @return the Timestamp
     */
    public static Timestamp toTimestamp(final Object v, final int columnType) {
        if (columnType == Types.TIME && v instanceof CharSequence) {
            return toTimestamp(LocalTime.parse(v.toString().trim()));
        }
        return toTimestamp(v);
    }

    /**
     * Whether {@code getDate}, {@code getTime} or {@code getTimestamp} reads a column of the given JDBC type, as the
     * account's driver reads it: a DATE by getDate and getTimestamp, a TIME by getTime and getTimestamp, a timestamp
     * of every flavour by all three, and nothing else by any of them — a text column holding a date's own text, a
     * number and a BOOLEAN are refused alike (live-verified).
     *
     * @param columnType the column's JDBC type
     * @param target     what the getter reads: {@code date}, {@code time} or {@code timestamp}
     * @return whether the getter reads the column
     */
    public static boolean readsTemporal(final int columnType, final String target) {
        switch (columnType) {
            case Types.DATE:
                return !"time".equals(target);
            case Types.TIME:
                return !"date".equals(target);
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return true;
            default:
                return false;
        }
    }

    /**
     * The account's driver's refusal of a getter its column's type does not convert to, worded as it words it:
     * {@code Cannot convert value in the driver from type:91 to type:time, value=19724.}, SQLSTATE {@code 0A000},
     * vendor code 200038 (live-verified).
     *
     * @param columnType the column's JDBC type
     * @param target     what the getter reads: {@code date}, {@code time} or {@code timestamp}
     * @param value      the cell as the refusal quotes it (see {@link #quotedCell})
     * @return the refusal to throw
     */
    public static SQLException unconvertible(final int columnType, final String target, final String value) {
        return new SQLException("Cannot convert value in the driver from type:" + columnType + " to type:" + target
            + ", value=" + value + ".", "0A000", 200038);
    }

    /**
     * A cell as the driver's refusal quotes it: the account's wire form of it — a DATE as its day number since
     * 1970-01-01, a TIME as its seconds since midnight to the column's scale ({@code 36672.345000000} for a
     * TIME(9), {@code 36672} for a TIME(0)), a BOOLEAN as 1 or 0 — and any other cell as its text (live-verified).
     *
     * @param cell       the cell, not null
     * @param columnType the column's JDBC type
     * @param scale      the column's scale
     * @param text       the cell's text, as getString reads it
     * @return the quoted value
     */
    public static String quotedCell(final Object cell, final int columnType, final int scale, final String text) {
        if (columnType == Types.DATE) {
            final LocalDate day = cell instanceof LocalDate ? (LocalDate) cell : parseDate(cell.toString());
            return Long.toString(day.toEpochDay());
        }
        if (columnType == Types.TIME) {
            final LocalTime time = cell instanceof LocalTime ? (LocalTime) cell
                : LocalTime.parse(cell.toString().trim());
            final StringBuilder seconds = new StringBuilder(Integer.toString(time.toSecondOfDay()));
            if (scale > 0) {
                seconds.append('.').append(String.format(Locale.ROOT, "%09d", time.getNano()), 0, Math.min(scale, 9));
            }
            return seconds.toString();
        }
        if (cell instanceof Boolean) {
            return ((Boolean) cell) ? "1" : "0";
        }
        return text;
    }

    /**
     * A Timestamp bound with a Calendar, as the account's driver binds one (see {@link CalendarBoundInstant}).
     *
     * @param x   the value
     * @param cal the Calendar
     * @return the value to bind
     */
    public static Object boundAtCalendar(final Timestamp x, final Calendar cal) {
        if (x == null || cal == null) {
            return x;
        }
        return new CalendarBoundInstant(
            x.toInstant().atZone(cal.getTimeZone().toZoneId()).toLocalDateTime().toInstant(ZoneOffset.UTC));
    }

    /**
     * A Timestamp bound without a Calendar, as the account's driver binds one: its moment, a TIMESTAMP_LTZ, so a
     * TIMESTAMP_NTZ column holds the moment as the session's zone shows it, whatever the client's zone
     * (live-verified).
     *
     * @param x the value
     * @return the value to bind
     */
    public static Object boundMoment(final Timestamp x) {
        return x == null ? null : new CalendarBoundInstant(x.toInstant());
    }

    /**
     * A value bound by setObject, as the account's driver binds it: a Timestamp as its moment and a Time at the clock
     * its moment shows in UTC, as their own setters bind them; anything else as it is.
     *
     * @param x the value
     * @return the value to bind
     */
    public static Object boundObject(final Object x) {
        if (x instanceof Timestamp) {
            return boundMoment((Timestamp) x);
        }
        if (x instanceof Time) {
            return timeAtUtc((Time) x);
        }
        return x;
    }

    /**
     * A Date bound with a Calendar: the day its moment falls on in the Calendar's zone (live-verified).
     *
     * @param x   the value
     * @param cal the Calendar
     * @return the value to bind
     */
    public static Date dateAtCalendar(final Date x, final Calendar cal) {
        if (x == null || cal == null) {
            return x;
        }
        return Date.valueOf(Instant.ofEpochMilli(x.getTime()).atZone(cal.getTimeZone().toZoneId()).toLocalDate());
    }

    /**
     * A Time bound with a Calendar, as the account's driver binds one: the Calendar is not read, and the time is the
     * one its moment shows in UTC, in any session (live-verified).
     *
     * @param x the value
     * @return the value to bind
     */
    public static Time timeAtUtc(final Time x) {
        if (x == null) {
            return null;
        }
        return Time.valueOf(LocalTime.ofInstant(Instant.ofEpochMilli(x.getTime()), ZoneOffset.UTC));
    }

    /**
     * A cell read with a Calendar: a wall clock with no zone — a TIMESTAMP_NTZ or a DATE — names the moment it shows
     * in the Calendar's zone, and a zoned cell keeps its own moment, as the account's driver reads them
     * (live-verified).
     *
     * @param v   the cell
     * @param cal the Calendar, or null
     * @return the Timestamp
     */
    public static Timestamp toTimestamp(final Object v, final Calendar cal) {
        if (v == null || cal == null) {
            return toTimestamp(v);
        }
        final ZoneId zone = cal.getTimeZone().toZoneId();
        if (v instanceof OffsetDateTime) {
            return Timestamp.from(((OffsetDateTime) v).toInstant());
        }
        if (v instanceof ZonedDateTime) {
            return Timestamp.from(((ZonedDateTime) v).toInstant());
        }
        if (v instanceof LocalDateTime) {
            return Timestamp.from(((LocalDateTime) v).atZone(zone).toInstant());
        }
        if (v instanceof LocalDate) {
            return Timestamp.from(((LocalDate) v).atStartOfDay(zone).toInstant());
        }
        if (v instanceof CharSequence) {
            final Instant zoned = zonedMoment(v.toString());
            return Timestamp.from(zoned != null ? zoned : parseDateTime(v.toString()).atZone(zone).toInstant());
        }
        return toTimestamp(v);
    }

    /**
     * A DATE cell read with a Calendar: its day's midnight in the Calendar's zone.
     *
     * @param v   the cell
     * @param cal the Calendar, or null
     * @return the Date
     */
    public static Date toDate(final Object v, final Calendar cal) {
        if (v == null || cal == null) {
            return toDate(v);
        }
        return new Date(toDate(v).toLocalDate().atStartOfDay(cal.getTimeZone().toZoneId()).toInstant().toEpochMilli());
    }

    /** The moment a wire text with a trailing offset names, or null for a text with no offset. */
    private static Instant zonedMoment(final String text) {
        final String trimmed = text.trim();
        final int space = trimmed.lastIndexOf(' ');
        if (space <= 0) {
            return null;
        }
        final String token = trimmed.substring(space + 1);
        if ("Z".equals(token)) {
            return parseDateTime(trimmed.substring(0, space)).toInstant(ZoneOffset.UTC);
        }
        if (!isNumericOffset(token)) {
            return null;
        }
        final String colonless = token.replace(":", "");
        final int hours = Integer.parseInt(colonless.substring(1, 3));
        final int minutes = colonless.length() >= 5 ? Integer.parseInt(colonless.substring(3, 5)) : 0;
        final int sign = colonless.charAt(0) == '-' ? -1 : 1;
        return parseDateTime(trimmed.substring(0, space))
            .toInstant(ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes));
    }

    public static Date toDate(final Object v) {
        if (v == null || v instanceof Date) {
            return (Date) v;
        }
        if (v instanceof LocalDate) {
            return Date.valueOf((LocalDate) v);
        }
        if (v instanceof LocalDateTime) {
            return Date.valueOf(((LocalDateTime) v).toLocalDate());
        }
        if (v instanceof ZonedDateTime) {
            return Date.valueOf(((ZonedDateTime) v).toLocalDate());
        }
        if (v instanceof java.util.Date) {
            return new Date(((java.util.Date) v).getTime());
        }
        return Date.valueOf(parseDate(v.toString()));
    }

    /**
     * A DATE's text as the wire carries it, including a year before the first: that one crosses with its
     * SIGNED proleptic number — {@code -1-01-15}, {@code 0000-01-15} — because the year of the era would read as
     * a different year. Anything after the date (a timestamp's time) is ignored.
     *
     * @param text the cell's text
     * @return the date it names
     */
    static LocalDate parseDate(final String text) {
        final String iso = isoYear(text.trim());
        final int timeAt = iso.indexOf(iso.indexOf('T') >= 0 ? 'T' : ' ');
        return LocalDate.parse(timeAt > 0 ? iso.substring(0, timeAt) : iso);
    }

    /**
     * A wire text's leading year in the ISO spelling {@code java.time} parses: a negative year padded to four
     * digits ({@code -1} becomes {@code -0001}) and a year past 9999 signed ({@code 20201} becomes
     * {@code +20201}). A four-digit year passes through.
     */
    private static String isoYear(final String text) {
        final boolean negative = text.startsWith("-");
        final int dash = text.indexOf('-', negative ? 1 : 0);
        if (dash < 0) {
            return text;
        }
        final String digits = text.substring(negative ? 1 : 0, dash);
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return text;
            }
        }
        if (negative) {
            final StringBuilder padded = new StringBuilder("-");
            for (int i = digits.length(); i < 4; i++) {
                padded.append('0');
            }
            return padded.append(text.substring(1)).toString();
        }
        return digits.length() > 4 ? "+" + text : text;
    }

    /**
     * A cell read as a Time: its time of day — a timestamp's wall clock, a TIMESTAMP_LTZ's at the session's offset
     * as getString shows it — with its milliseconds, which the account's driver keeps (live-verified).
     *
     * @param v the cell
     * @return the Time
     */
    public static Time toTime(final Object v) {
        if (v == null || v instanceof Time) {
            return (Time) v;
        }
        if (v instanceof LocalTime) {
            return timeOf((LocalTime) v);
        }
        if (v instanceof LocalDateTime) {
            return timeOf(((LocalDateTime) v).toLocalTime());
        }
        if (v instanceof ZonedDateTime) {
            return timeOf(((ZonedDateTime) v).toLocalTime());
        }
        if (v instanceof OffsetDateTime) {
            return timeOf(((OffsetDateTime) v).toLocalTime());
        }
        if (v instanceof java.util.Date) {
            return new Time(((java.util.Date) v).getTime());
        }
        return timeOf(LocalTime.parse(v.toString().trim()));
    }

    /**
     * A cell of a column of the given JDBC type read by {@code getTime}: a timestamp's text — the wire's spelling of
     * one, with or without its offset — is its wall clock's time of day, as a timestamp cell is.
     *
     * @param v          the cell
     * @param columnType the column's JDBC type
     * @return the Time
     */
    public static Time toTime(final Object v, final int columnType) {
        final boolean timestamp = columnType == Types.TIMESTAMP || columnType == Types.TIMESTAMP_WITH_TIMEZONE;
        if (timestamp && v instanceof CharSequence) {
            return timeOf(parseDateTime(v.toString()).toLocalTime());
        }
        return toTime(v);
    }

    /** A time of day as a Time in this JVM's zone, its milliseconds kept. */
    private static Time timeOf(final LocalTime time) {
        return new Time(Time.valueOf(time).getTime() + time.getNano() / 1000000);
    }

    public static byte[] toBytes(final Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BinaryValue) {
            return ((BinaryValue) v).bytes();
        }
        if (v instanceof byte[]) {
            return (byte[]) v;
        }
        final String s = v.toString();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return hexToBytes(s.substring(2));
        }
        if (isHexText(s)) {                                   // BINARY crosses the JSON wire as bare hex
            return hexToBytes(s);
        }
        try {
            return Base64.getDecoder().decode(s);             // legacy byte[] cells crossed as Base64
        } catch (final IllegalArgumentException notBase64) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** True when {@code s} is non-empty, even-length, and entirely hex digits. */
    private static boolean isHexText(final String s) {
        if (s.isEmpty() || s.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bind an ordered list of values to the {@code ?} placeholders in {@code sql} (1-based, positional) —
     * e.g. Snowflake's {@code EXECUTE IMMEDIATE '… ? … ?' USING (v1, v2)}.
     */
    public static String substitutePlaceholders(final String sql, final List<Object> values) {
        return substitutePlaceholders(sql, values, Collections.<Integer>emptySet());
    }

    /**
     * {@link #substitutePlaceholders(String, List)}, leaving as written each placeholder whose 1-based number
     * {@code unbound} holds: the statement then refuses it as a bind variable that is not set.
     */
    public static String substitutePlaceholders(final String sql, final List<Object> values,
                                                final Set<Integer> unbound) {
        final Map<Integer, Object> params = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            params.put(i + 1, values.get(i));
        }
        return substitute(sql, params, unbound, false, new ArrayList<int[]>());
    }

    /**
     * Bind a USING list into a statement's text: each {@code ?} takes the next value, and each {@code :n} the n-th
     * one, in any order and as often as it is written — {@code 'SELECT :2, :1, :1' USING (a, b)}. A number past the
     * list, {@code :0} and a number written with a leading zero bind nothing and stay as written, as does each
     * placeholder whose 1-based number {@code unbound} holds (live-verified).
     *
     * @param sql     the text
     * @param values  the USING values, in order
     * @param unbound the 1-based numbers that bind nothing
     * @return the text with each bound placeholder written out
     */
    public static String bindUsing(final String sql, final List<Object> values, final Set<Integer> unbound) {
        return bindUsing(sql, values, unbound, new ArrayList<int[]>());
    }

    /**
     * {@link #bindUsing(String, List, Set)}, noting each placeholder it writes out, so a refusal of the text that
     * runs can be placed in the text as written.
     *
     * @param sql     the text
     * @param values  the USING values, in order
     * @param unbound the 1-based numbers that bind nothing
     * @param written takes each bound placeholder: its line, its column, its written length and the length of
     *                what stands in its place, in the order written
     * @return the text with each bound placeholder written out
     */
    public static String bindUsing(final String sql, final List<Object> values, final Set<Integer> unbound,
                                   final List<int[]> written) {
        final Map<Integer, Object> params = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            params.put(i + 1, values.get(i));
        }
        return substitute(sql, params, unbound, true, written);
    }

    /**
     * Substitute {@code ?} placeholders in {@code sql} with the formatted parameters (1-based keys), skipping
     * any {@code ?} that sits inside a single-quoted string literal so a literal like {@code 'a?b'} doesn't
     * consume a placeholder slot. (PreparedStatement params are inlined client-side; there is no server-side
     * binding.)
     */
    public static String substitutePlaceholders(final String sql, final Map<Integer, Object> params) {
        return substitute(sql, params, Collections.<Integer>emptySet(), false, new ArrayList<int[]>());
    }

    private static String substitute(final String sql, final Map<Integer, Object> params, final Set<Integer> unbound,
                                     final boolean numbered, final List<int[]> written) {
        final List<Token> tokens = lex(sql).getTokens();
        final StringBuilder out = new StringBuilder(sql.length() + 16);
        int param = 0;
        int cursor = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final Token t = tokens.get(i);
            if (numbered && t.getType() == FrostlakeLexer.QUESTION && unbound.contains(param + 1)) {
                // A placeholder the USING list leaves unset is refused where it stands in the text as written,
                // whatever the values before it were bound as.
                throw new RuntimeException(SqlCompilationError.inline("error line " + t.getLine() + " at position "
                    + t.getCharPositionInLine() + "\nBind variable ? not set."));
            }
            if (t.getType() == FrostlakeLexer.QUESTION && !unbound.contains(++param)) {
                if (numbered) {
                    requireNoRowCount(tokens, i);
                }
                final String literal = !numbered ? formatLiteral(params.get(param))
                    : aloneInValuesRow(tokens, i) ? formatLiteral(params.get(param)) : usingLiteral(params.get(param));
                out.append(sql, cursor, t.getStartIndex());
                out.append(literal);
                cursor = t.getStopIndex() + 1;
                written.add(new int[] {t.getLine(), t.getCharPositionInLine(), 1, literal.length()});
            } else if (numbered && t.getType() == FrostlakeLexer.COLON && i + 1 < tokens.size()
                    && tokens.get(i + 1).getType() == FrostlakeLexer.INTEGER_LITERAL) {
                final Token digits = tokens.get(i + 1);
                final Integer number = bindNumber(digits.getText());
                if (number != null && params.containsKey(number) && !unbound.contains(number)) {
                    final String literal = aloneInValuesRow(tokens, i + 1) ? formatLiteral(params.get(number))
                        : usingLiteral(params.get(number));
                    out.append(sql, cursor, t.getStartIndex());
                    out.append(literal);
                    cursor = digits.getStopIndex() + 1;
                    written.add(new int[] {t.getLine(), t.getCharPositionInLine(),
                        digits.getStopIndex() + 1 - t.getStartIndex(), literal.length()});
                    i++;
                }
            }
        }
        out.append(sql, cursor, sql.length());
        return out.toString();
    }

    /**
     * A USING value as the text written in its placeholder's place: a NULL is bound as a text with no value, a
     * widthless VARCHAR — {@code SYSTEM$TYPEOF(?)} over it is {@code VARCHAR[LOB]} whatever the variable's type
     * (live-verified) — and every other value as {@link #formatLiteral} writes it.
     *
     * @param value the value
     * @return its text
     */
    public static String usingLiteral(final Object value) {
        return value == null ? "NULL::VARCHAR" : formatLiteral(value);
    }

    /**
     * Whether the bind ending at token {@code last} stands alone as an item of a VALUES row, where a NULL carries no
     * type and fits any column (live-verified: {@code VALUES (?)} bound to a NULL is inserted into a VARIANT column).
     */
    private static boolean aloneInValuesRow(final List<Token> tokens, final int last) {
        int first = last;
        if (first > 0 && tokens.get(first).getType() == FrostlakeLexer.INTEGER_LITERAL
                && tokens.get(first - 1).getType() == FrostlakeLexer.COLON) {
            first--;
        }
        final int before = spokenBefore(tokens, first);
        final int after = spokenAfter(tokens, last);
        if (before < 0 || after < 0) {
            return false;
        }
        final int previous = tokens.get(before).getType();
        final int next = tokens.get(after).getType();
        if (previous != FrostlakeLexer.LPAREN && previous != FrostlakeLexer.COMMA
                || next != FrostlakeLexer.RPAREN && next != FrostlakeLexer.COMMA) {
            return false;
        }
        // The row's opening bracket, then the rows before it, each a bracketed list after a ',', back to the VALUES.
        int open = openingBracket(tokens, first);
        int row = open < 0 ? -1 : spokenBefore(tokens, open);
        while (row >= 0 && tokens.get(row).getType() == FrostlakeLexer.COMMA) {
            final int close = spokenBefore(tokens, row);
            if (close < 0 || tokens.get(close).getType() != FrostlakeLexer.RPAREN) {
                return false;
            }
            open = openingBracket(tokens, close);
            row = open < 0 ? -1 : spokenBefore(tokens, open);
        }
        return row >= 0 && tokens.get(row).getType() == FrostlakeLexer.VALUES;
    }

    /** The first '(' before the token at {@code index} that no ')' between them closes, or -1. */
    private static int openingBracket(final List<Token> tokens, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static int spokenBefore(final List<Token> tokens, final int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return -1;
    }

    private static int spokenAfter(final List<Token> tokens, final int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return -1;
    }

    /** The number a {@code :n} bind is written with, or null for digits that name none: a leading zero, or too many. */
    private static Integer bindNumber(final String digits) {
        if (digits.startsWith("0") || digits.length() > 6) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    /** Tokenize with the SQL lexer so that a {@code ?} inside a string literal is part of the
     *  STRING_LITERAL token (never a QUESTION token) — the grammar decides what is a placeholder. */
    private static CommonTokenStream lex(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        return tokens;
    }

    /**
     * Rewrite named parameters ({@code :name}) in a callable-statement SQL to positional {@code ?}
     * placeholders. Driven by the lexer, so a {@code :} that sits inside a string literal (part of a
     * STRING_LITERAL token) — or {@code ::} / {@code :=} (their own tokens) — is never a parameter.
     */
    public static String namedParametersToPositional(final String sql) {
        final List<Token> toks = lex(sql).getTokens();
        final StringBuilder out = new StringBuilder(sql.length());
        int cursor = 0;
        for (int i = 0; i + 1 < toks.size(); i++) {
            final Token t = toks.get(i);
            final Token name = toks.get(i + 1);
            if (t.getType() == FrostlakeLexer.COLON && SqlTokens.isWord(name)
                    && name.getStartIndex() == t.getStopIndex() + 1) {
                out.append(sql, cursor, t.getStartIndex());
                out.append('?');
                cursor = name.getStopIndex() + 1;
                i++;   // consumed the name token
            }
        }
        out.append(sql, cursor, sql.length());
        return out.toString();
    }

    /** The named parameters ({@code :name}) of {@code sql}, uppercased, in order of appearance. */
    public static List<String> namedParameterOrder(final String sql) {
        final List<String> names = new ArrayList<>();
        final List<Token> toks = lex(sql).getTokens();
        for (int i = 0; i + 1 < toks.size(); i++) {
            final Token t = toks.get(i);
            final Token name = toks.get(i + 1);
            if (t.getType() == FrostlakeLexer.COLON && SqlTokens.isWord(name)
                    && name.getStartIndex() == t.getStopIndex() + 1) {
                names.add(name.getText().toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * Refuse a USING list's {@code ?} standing where a row count belongs: the list hands a text over, and a LIMIT
     * and an OFFSET take none — {@code Invalid row count '?' in limit clause} (live-verified).
     */
    private static void requireNoRowCount(final List<Token> tokens, final int placeholder) {
        int before = placeholder - 1;
        while (before >= 0 && tokens.get(before).getChannel() != Token.DEFAULT_CHANNEL) {
            before--;
        }
        final int type = before < 0 ? Token.EOF : tokens.get(before).getType();
        if (type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET) {
            throw new RuntimeException(SqlCompilationError.of("Invalid row count '?' in "
                + (type == FrostlakeLexer.LIMIT ? "limit clause" : "result offset clause")));
        }
    }

    /**
     * Render a parameter value as a SQL literal. Strings escape BOTH backslash and single-quote — the lexer
     * honors {@code \}-escapes (see the STRING_LITERAL grammar rule), so doubling only the quote would let a
     * trailing backslash break out of the literal. Temporal values are emitted in ISO form the engine parses.
     */
    public static String formatLiteral(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number && NonFiniteDoubles.isNonFinite((Number) value)) {
            // NaN and the infinities have no literal: bound bare, the word would be read as a name.
            return NonFiniteDoubles.castOfWord((Number) value);
        }
        if (value instanceof Number) {
            // A negative number is parenthesised: spliced bare after a minus — `3-?` bound to -5 — the text
            // became `3--5`, a line comment, and the statement silently answered 3. The account binds
            // server-side, so 3-? with -5 is 8 there; (-5) is legal wherever a bound value can stand.
            final String text = value.toString();
            return text.startsWith("-") ? "(" + text + ")" : text;
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        // A bound date or time is a typed value, which the session's input formats do not read: each is written with
        // a format of its own, so DATE_INPUT_FORMAT and its kin leave a binding alone, as they leave the account's.
        if (value instanceof CalendarBoundInstant) {
            return "TO_TIMESTAMP_LTZ('" + ((CalendarBoundInstant) value).text()
                + "', 'YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM')";
        }
        if (value instanceof Timestamp) {
            return "TO_TIMESTAMP_NTZ('" + BOUND_TIMESTAMP.format(((Timestamp) value).toLocalDateTime())
                + "', 'YYYY-MM-DD HH24:MI:SS.FF9')";
        }
        if (value instanceof Date) {
            return "TO_DATE('" + BOUND_DATE.format(((Date) value).toLocalDate()) + "', 'YYYY-MM-DD')";
        }
        if (value instanceof Time) {
            return "TO_TIME('" + ((Time) value).toLocalTime().format(BOUND_TIME) + "', 'HH24:MI:SS')";
        }
        if (value instanceof BinaryValue) {
            // A binary parameter binds as a hex literal, which the parser reads back as BINARY.
            return "X'" + ((BinaryValue) value).toHex() + "'";
        }
        if (value instanceof VariantValue) {
            // A semi-structured parameter binds as PARSE_JSON of its text, keeping its variant-ness.
            return "PARSE_JSON(" + SqlStringLiterals.encode(((VariantValue) value).text()) + ")";
        }
        if (value instanceof byte[]) {
            return "X'" + bytesToHex((byte[]) value) + "'";
        }
        if (value instanceof List) {
            final List<?> list = (List<?>) value;
            final StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(formatLiteral(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (value instanceof Object[]) {
            return formatLiteral(java.util.Arrays.asList((Object[]) value));
        }
        return SqlStringLiterals.encode(value.toString());
    }

    /** Count the {@code ?} placeholders in {@code sql}, skipping any inside single-quoted string literals. */
    public static int countPlaceholders(final String sql) {
        int count = 0;
        for (final Token t : lex(sql).getTokens()) {
            if (t.getType() == FrostlakeLexer.QUESTION) {
                count++;
            }
        }
        return count;
    }

    /** Read a character stream fully into a String (for PreparedStatement character/clob parameters). */
    public static String readToString(final Reader reader) throws SQLException {
        if (reader == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        final char[] buf = new char[2048];
        try {
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } catch (final IOException e) {
            throw new SQLException("Failed to read character-stream parameter: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    /** Read a byte stream fully into a UTF-8 String (for PreparedStatement ASCII/unicode-stream parameters). */
    public static String readToString(final InputStream in) throws SQLException {
        final byte[] bytes = readToBytes(in);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /** Read a byte stream fully into a byte[] (for PreparedStatement binary/blob parameters). */
    public static byte[] readToBytes(final InputStream in) throws SQLException {
        if (in == null) {
            return null;
        }
        try {
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new SQLException("Failed to read binary-stream parameter: " + e.getMessage(), e);
        }
    }

    private static String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime parseDateTime(final String s) {
        // Three spellings reach here: ISO 'T' ("2025-06-17T14:30:00"), a SQL space
        // ("2025-06-17 14:30:00"), and the zoned form the HTTP wire now carries for a TIMESTAMP_LTZ
        // ("2025-06-17 14:30:00.000 -0700"). Drop a trailing numeric offset before normalizing —
        // without that the parse fails and the date-only fallback silently loses the time of day.
        String text = s.trim();
        final int offset = text.lastIndexOf(' ');
        if (offset > 0 && isNumericOffset(text.substring(offset + 1))) {
            text = text.substring(0, offset);
        }
        final String spaced = text.contains(" ") && !text.contains("T") ? text.replace(' ', 'T') : text;
        final String iso = isoYear(spaced);
        try {
            return LocalDateTime.parse(iso);
        } catch (final DateTimeParseException dateOnly) {
            return parseDate(s).atStartOfDay();
        }
    }

    /** Whether a trailing token is a {@code ±HHMM} / {@code ±HH:MM} zone offset rather than text. */
    private static boolean isNumericOffset(final String token) {
        if (token.length() < 3 || (token.charAt(0) != '+' && token.charAt(0) != '-')) {
            return false;
        }
        for (int i = 1; i < token.length(); i++) {
            final char c = token.charAt(i);
            if (c != ':' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexToBytes(final String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private JdbcMarshaling() {
    }
}
