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

import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.SqlTokens;
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStreams;
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

    /**
     * Map an engine {@code DataType} to a {@link java.sql.Types} constant, using its PARAMETERS where
     * they decide the answer. Every integer alias is NUMBER(38,0) in the catalog, as on a real account,
     * so the name alone can no longer tell an integer column from a decimal one — the SCALE does, and
     * live agrees: an INT column reports {@code Types.BIGINT} over JDBC and a NUMBER(10,2) reports
     * {@code Types.DECIMAL}. It is the same rule this driver's metadata queries already apply to
     * INFORMATION_SCHEMA rows.
     */
    public static int toSqlType(final DataType type) {
        return type instanceof NumericType
            ? toSqlType(type.getName(), ((NumericType) type).getScale())
            : toSqlType(type.getName());
    }

    /** As above, for a caller that has the type's NAME and SCALE rather than the type itself. */
    public static int toSqlType(final String typeName, final int scale) {
        final String name = typeName == null ? "" : typeName.toUpperCase();
        if (name.equals("NUMBER") || name.equals("NUMERIC") || name.equals("DECIMAL")) {
            return scale == 0 ? Types.BIGINT : Types.DECIMAL;
        }
        return toSqlType(typeName);
    }

    /** Map an engine {@code DataType} name to a {@link java.sql.Types} constant. */
    public static int toSqlType(final String typeName) {
        if (typeName == null) {
            return Types.OTHER;
        }
        final String t = typeName.toUpperCase();
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
        if (t.contains("DECIMAL") || t.contains("NUMBER") || t.contains("NUMERIC")) {
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
        if (v instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) v).getTime());
        }
        return Timestamp.valueOf(parseDateTime(v.toString()));
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

    public static Time toTime(final Object v) {
        if (v == null || v instanceof Time) {
            return (Time) v;
        }
        if (v instanceof LocalTime) {
            return Time.valueOf((LocalTime) v);
        }
        if (v instanceof LocalDateTime) {
            return Time.valueOf(((LocalDateTime) v).toLocalTime());
        }
        if (v instanceof ZonedDateTime) {
            return Time.valueOf(((ZonedDateTime) v).toLocalTime());
        }
        if (v instanceof java.util.Date) {
            return new Time(((java.util.Date) v).getTime());
        }
        return Time.valueOf(LocalTime.parse(v.toString()));
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
        return substitute(sql, params, unbound);
    }

    /**
     * Substitute {@code ?} placeholders in {@code sql} with the formatted parameters (1-based keys), skipping
     * any {@code ?} that sits inside a single-quoted string literal so a literal like {@code 'a?b'} doesn't
     * consume a placeholder slot. (PreparedStatement params are inlined client-side; there is no server-side
     * binding.)
     */
    public static String substitutePlaceholders(final String sql, final Map<Integer, Object> params) {
        return substitute(sql, params, Collections.<Integer>emptySet());
    }

    private static String substitute(final String sql, final Map<Integer, Object> params, final Set<Integer> unbound) {
        final CommonTokenStream tokens = lex(sql);
        final StringBuilder out = new StringBuilder(sql.length() + 16);
        int param = 0;
        int cursor = 0;
        for (final Token t : tokens.getTokens()) {
            if (t.getType() == FrostlakeLexer.QUESTION && !unbound.contains(++param)) {
                out.append(sql, cursor, t.getStartIndex());
                out.append(formatLiteral(params.get(param)));
                cursor = t.getStopIndex() + 1;
            }
        }
        out.append(sql, cursor, sql.length());
        return out.toString();
    }

    /** Tokenize with the SQL lexer so that a {@code ?} inside a string literal is part of the
     *  STRING_LITERAL token (never a QUESTION token) — the grammar decides what is a placeholder. */
    private static CommonTokenStream lex(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
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
                names.add(name.getText().toUpperCase());
            }
        }
        return names;
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
        if (value instanceof Timestamp) {
            return "'" + ((Timestamp) value).toLocalDateTime() + "'::TIMESTAMP_NTZ";
        }
        if (value instanceof Date) {
            return "'" + ((Date) value).toLocalDate() + "'::DATE";
        }
        if (value instanceof Time) {
            return "'" + ((Time) value).toLocalTime() + "'::TIME";
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
        return sb.toString().toUpperCase();
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
