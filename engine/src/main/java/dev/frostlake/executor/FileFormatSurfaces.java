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

package dev.frostlake.executor;

import dev.frostlake.executor.copy.StagedFileIo;
import dev.frostlake.metastore.model.FileFormat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The per-TYPE file-format property surface a real account exposes, measured tree by tree: which
 * options each type accepts (an option outside its type's tree refuses {@code Option X is not
 * valid for file format type Y.}), each property's declared type, its current-value default and
 * the constant default column DESC prints (JSON-family NULL_IF starts {@code []} while the
 * default column still reads {@code [\N]}). One table drives DESC FILE FORMAT, DESC STAGE's
 * format group, SHOW FILE FORMATS' format_options blob and the option validation. The ORC tree
 * follows the documented option set; every other tree is account-measured.
 *
 * <p>A String property's default is kept decoded - a record delimiter is a line feed, an unenclosed escape one
 * backslash - as a written value is kept, and each surface renders both the same way: DESCRIBE and the listings
 * spell a backslash, a quote and a control character behind a backslash ({@link #shown}), the format_options blob
 * spells them as JSON does ({@link #json}). A List property's default is kept as DESCRIBE shows it.
 */
public final class FileFormatSurfaces {

private static final Map<String, List<FormatProperty>> TREES = new LinkedHashMap<>();

    private static FormatProperty prop(final String name, final String type, final String both) {
        return new FormatProperty(name, type, both, "String".equals(type) ? shown(both) : both);
    }

    static {
        TREES.put("CSV", Arrays.asList(
            prop("TYPE", "String", "CSV"),
            prop("RECORD_DELIMITER", "String", "\n"),
            prop("FIELD_DELIMITER", "String", ","),
            prop("FILE_EXTENSION", "String", ""),
            prop("SKIP_HEADER", "Integer", "0"),
            prop("PARSE_HEADER", "Boolean", "false"),
            prop("DATE_FORMAT", "String", "AUTO"),
            prop("TIME_FORMAT", "String", "AUTO"),
            prop("TIMESTAMP_FORMAT", "String", "AUTO"),
            prop("BINARY_FORMAT", "String", "HEX"),
            prop("ESCAPE", "String", "NONE"),
            prop("ESCAPE_UNENCLOSED_FIELD", "String", "\\"),
            prop("TRIM_SPACE", "Boolean", "false"),
            prop("FIELD_OPTIONALLY_ENCLOSED_BY", "String", "NONE"),
            prop("NULL_IF", "List", "[\\\\N]"),
            prop("COMPRESSION", "String", "AUTO"),
            prop("ERROR_ON_COLUMN_COUNT_MISMATCH", "Boolean", "true"),
            prop("VALIDATE_UTF8", "Boolean", "true"),
            prop("SKIP_BLANK_LINES", "Boolean", "false"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false"),
            prop("EMPTY_FIELD_AS_NULL", "Boolean", "true"),
            prop("SKIP_BYTE_ORDER_MARK", "Boolean", "true"),
            prop("ENCODING", "String", "UTF8"),
            prop("MULTI_LINE", "Boolean", "true")));
        TREES.put("JSON", Arrays.asList(
            prop("TYPE", "String", "JSON"),
            prop("FILE_EXTENSION", "String", ""),
            prop("DATE_FORMAT", "String", "AUTO"),
            prop("TIME_FORMAT", "String", "AUTO"),
            prop("TIMESTAMP_FORMAT", "String", "AUTO"),
            prop("BINARY_FORMAT", "String", "HEX"),
            prop("TRIM_SPACE", "Boolean", "false"),
            new FormatProperty("NULL_IF", "List", "[]", "[\\\\N]"),
            prop("COMPRESSION", "String", "AUTO"),
            prop("ENABLE_OCTAL", "Boolean", "false"),
            prop("ALLOW_DUPLICATE", "Boolean", "false"),
            prop("STRIP_OUTER_ARRAY", "Boolean", "false"),
            prop("STRIP_NULL_VALUES", "Boolean", "false"),
            prop("IGNORE_UTF8_ERRORS", "Boolean", "false"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false"),
            prop("SKIP_BYTE_ORDER_MARK", "Boolean", "true"),
            prop("MULTI_LINE", "Boolean", "true")));
        TREES.put("PARQUET", Arrays.asList(
            prop("TYPE", "String", "PARQUET"),
            prop("TRIM_SPACE", "Boolean", "false"),
            new FormatProperty("NULL_IF", "List", "[]", "[\\\\N]"),
            prop("COMPRESSION", "String", "AUTO"),
            prop("BINARY_AS_TEXT", "Boolean", "true"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false"),
            prop("USE_LOGICAL_TYPE", "Boolean", "false"),
            prop("USE_VECTORIZED_SCANNER", "Boolean", "false")));
        TREES.put("XML", Arrays.asList(
            prop("TYPE", "String", "XML"),
            prop("COMPRESSION", "String", "AUTO"),
            prop("IGNORE_UTF8_ERRORS", "Boolean", "false"),
            prop("PRESERVE_SPACE", "Boolean", "false"),
            prop("STRIP_OUTER_ELEMENT", "Boolean", "false"),
            prop("DISABLE_SNOWFLAKE_DATA", "Boolean", "false"),
            prop("DISABLE_AUTO_CONVERT", "Boolean", "false"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false"),
            prop("SKIP_BYTE_ORDER_MARK", "Boolean", "true")));
        TREES.put("AVRO", Arrays.asList(
            prop("TYPE", "String", "AVRO"),
            prop("TRIM_SPACE", "Boolean", "false"),
            new FormatProperty("NULL_IF", "List", "[]", "[\\\\N]"),
            prop("COMPRESSION", "String", "AUTO"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false")));
        TREES.put("ORC", Arrays.asList(
            prop("TYPE", "String", "ORC"),
            prop("TRIM_SPACE", "Boolean", "false"),
            new FormatProperty("NULL_IF", "List", "[]", "[\\\\N]"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false")));
    }

    /**
     * The COMPRESSION values each TYPE takes, as docs.snowflake.com CREATE FILE FORMAT lists them: CSV, JSON and XML
     * the eight codecs, AVRO the same but BZ2, PARQUET its own AUTO, LZO, SNAPPY and NONE (SNAPPY is the account's
     * Parquet default). ORC has no COMPRESSION option at all; that refusal is the option's, not the value's.
     */
    private static final Map<String, Set<String>> COMPRESSION_VALUES = new LinkedHashMap<>();

    /** Every codec there is: a word outside them is no codec, whatever the type. */
    private static final Set<String> CODECS = Set.of("AUTO", "GZIP", "BZ2", "BROTLI", "ZSTD", "DEFLATE", "RAW_DEFLATE",
        "NONE", "LZO", "SNAPPY");

    static {
        final Set<String> codecs = Set.of("AUTO", "GZIP", "BZ2", "BROTLI", "ZSTD", "DEFLATE", "RAW_DEFLATE", "NONE");
        COMPRESSION_VALUES.put("CSV", codecs);
        COMPRESSION_VALUES.put("JSON", codecs);
        COMPRESSION_VALUES.put("XML", codecs);
        COMPRESSION_VALUES.put("AVRO", codecs);
        COMPRESSION_VALUES.put("PARQUET", Set.of("AUTO", "LZO", "SNAPPY", "NONE"));
    }

    private FileFormatSurfaces() {
    }

    public static boolean isKnownType(final String type) {
        return type != null && TREES.containsKey(type.toUpperCase(Locale.ROOT));
    }

    public static List<FormatProperty> tree(final String type) {
        final List<FormatProperty> tree = TREES.get(type == null ? "CSV" : type.toUpperCase(Locale.ROOT));
        return tree != null ? tree : TREES.get("CSV");
    }

    /** Whether ANY type's tree carries this option — outside that, the name itself is invalid. */
    public static boolean isKnownOption(final String name) {
        for (final List<FormatProperty> tree : TREES.values()) {
            for (final FormatProperty property : tree) {
                if (property.name.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The kind of value an option takes as the first type's tree that carries it declares it: String, Boolean,
     * Integer or List.
     *
     * @param name the option
     * @return its kind, or null for an option no type's tree carries
     */
    public static String optionType(final String name) {
        for (final List<FormatProperty> tree : TREES.values()) {
            for (final FormatProperty property : tree) {
                if (property.name.equalsIgnoreCase(name)) {
                    return property.type;
                }
            }
        }
        return null;
    }

    /** Whether any type's tree declares the option Boolean. */
    private static boolean isBooleanOption(final String name) {
        for (final List<FormatProperty> tree : TREES.values()) {
            for (final FormatProperty property : tree) {
                if (property.name.equalsIgnoreCase(name)) {
                    return "Boolean".equals(property.type);
                }
            }
        }
        return false;
    }

    /** Whether the given type's tree carries the option. */
    public static boolean isOptionValidFor(final String type, final String name) {
        for (final FormatProperty property : tree(type)) {
            if (property.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The measured value refusals: an unknown TYPE, a negative SKIP_HEADER (echoed UNQUOTED), and
     * a compression outside the codec set (echoed quoted). Unmeasured values pass.
     */
    /** How long a FIELD_DELIMITER or RECORD_DELIMITER may be: twenty characters is accepted, 21 refused. */
    private static final int DELIMITER_LIMIT = 20;

    /** The parameters that take a delimiter of up to {@link #DELIMITER_LIMIT} characters. */
    private static final Set<String> DELIMITERS =
        new HashSet<>(Arrays.asList("FIELD_DELIMITER", "RECORD_DELIMITER"));

    /** The parameters that take exactly one character. */
    private static final Set<String> ESCAPES =
        new HashSet<>(Arrays.asList("ESCAPE", "ESCAPE_UNENCLOSED_FIELD"));

    /** What may enclose a field: the account takes these three and refuses every other character. */
    private static final Set<String> ENCLOSERS = new HashSet<>(Arrays.asList("\"", "'", "NONE"));

    public static void requireLegalValue(final String name, final String value) {
        requireLegalValue(name, value, value);
    }

    /**
     * Judge a COMPRESSION value in the account's words (live-verified): a word that is no codec is an invalid value
     * as written, and a codec the format TYPE does not take is an invalid compression format, named in upper case. A
     * type this cannot name — a list that only names a format, or ORC, which has no COMPRESSION — and the lists a
     * stage or a table keeps, which the account judges no further than that, take every codec.
     *
     * @param type    the effective format type, or null when it is not known
     * @param value   the value, decoded
     * @param written the value as the statement spells it
     */
    public static void requireLegalCompression(final String type, final String value, final String written) {
        if (value == null) {
            return;
        }
        final String codec = value.toUpperCase(Locale.ROOT);
        if (!CODECS.contains(codec)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'COMPRESSION'"));
        }
        final Set<String> legal = type == null ? null : COMPRESSION_VALUES.get(type.toUpperCase(Locale.ROOT));
        if (legal != null && !legal.contains(codec)) {
            throw new RuntimeException(SqlCompilationError.of("Invalid compression format " + codec));
        }
    }

    /**
     * Judge a parameter's value and refuse it in the account's own words. The value is judged DECODED,
     * as the reader will see it - a doubled quote is ONE quote and an escaped backslash ONE backslash -
     * while the refusal echoes the text AS WRITTEN: a bare word bare, a string literal with its quotes.
     *
     * @param name    the parameter
     * @param value   its value, decoded
     * @param written its value as the statement spells it
     */
    public static void requireLegalValue(final String name, final String value, final String written) {
        if (value == null) {
            return;
        }
        final String upper = name.toUpperCase(Locale.ROOT);
        if ("TYPE".equals(upper) && !isKnownType(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'TYPE'"));
        }
        if ("SKIP_HEADER".equals(upper)) {
            try {
                if (Integer.parseInt(value) < 0) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid value [" + value + "] for parameter 'SKIP_HEADER'"));
                }
            } catch (final NumberFormatException ignored) {
                // A non-numeric SKIP_HEADER never parses this far.
            }
        }
        if ("COMPRESSION".equals(upper)) {
            requireLegalCompression(null, value, written);
        }
        if (DELIMITERS.contains(upper) && !isDelimiter(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter '" + upper + "'"));
        }
        if (ESCAPES.contains(upper) && !isOneCharacter(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter '" + upper + "'"));
        }
        if ("FIELD_OPTIONALLY_ENCLOSED_BY".equals(upper) && !ENCLOSERS.contains(value.toUpperCase(Locale.ROOT))) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'FIELD_OPTIONALLY_ENCLOSED_BY'"));
        }
        // A Boolean option takes the bare words TRUE and FALSE in any case: a quoted 'TRUE', a number or any other word
        // is refused (live-verified).
        if (isBooleanOption(upper) && !"TRUE".equalsIgnoreCase(written) && !"FALSE".equalsIgnoreCase(written)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter '" + upper + "'"));
        }
        // BINARY_FORMAT names how a BINARY column's text reads, in any case, and the refusal echoes the name without
        // its quotes (live-verified).
        if ("BINARY_FORMAT".equals(upper) && !SessionInputFormats.isBinaryFormat(value)) {
            throw new RuntimeException(SqlCompilationError.of("Invalid binary format string '" + value
                + "': Must be 'HEX', 'BASE64', or 'UTF-8'"));
        }
        // ENCODING names one of the account's character sets, by its own name (ISO88591) or the set's (ISO-8859-1).
        if ("ENCODING".equals(upper) && !StagedFileIo.isEncodingName(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'ENCODING'"));
        }
    }


    /** A field or record delimiter: one to twenty characters, or the word NONE (live-verified). */
    private static boolean isDelimiter(final String value) {
        return "NONE".equalsIgnoreCase(value) || value.length() >= 1 && value.length() <= DELIMITER_LIMIT;
    }

    /** An escape: exactly one character, or the word NONE (live-verified). */
    private static boolean isOneCharacter(final String value) {
        return "NONE".equalsIgnoreCase(value) || value.length() == 1;
    }

    /**
     * A value as DESCRIBE and the listings spell it (live-verified): a backslash, a double quote and a backspace,
     * form-feed, tab, line-feed or carriage-return character behind a backslash, every other character as it is.
     *
     * @param decoded the value as kept
     * @return the value as shown
     */
    public static String shown(final String decoded) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < decoded.length(); i++) {
            final char c = decoded.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A property's value as DESCRIBE FILE FORMAT, DESCRIBE STAGE and DESCRIBE TABLE … TYPE = STAGE list it: the
     * stored value, or the property's default when none is stored, a boolean in lower case, a string
     * {@link #shown}, a NULL_IF list as {@code [a, b]} with each value shown.
     *
     * @param property the property
     * @param stored   the value as the format or the stage keeps it, or null for none
     * @return the value as listed
     */
    public static String described(final FormatProperty property, final String stored) {
        if ("List".equals(property.type)) {
            return stored == null ? property.valueDefault : listed(FileFormat.nullIfValues(stored));
        }
        if (stored == null) {
            return "String".equals(property.type) ? shown(property.valueDefault) : property.valueDefault;
        }
        if ("Boolean".equals(property.type)) {
            return stored.toLowerCase(Locale.ROOT);
        }
        return "String".equals(property.type) ? shown(stored) : stored;
    }

    /** NULL_IF's values as a listing spells them: {@code [\\N, NULL]}. */
    private static String listed(final List<String> values) {
        final StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i > 0 ? ", " : "").append(shown(values.get(i)));
        }
        return out.append(']').toString();
    }

    /**
     * A property's value as SHOW FILE FORMATS' format_options blob spells it: a number and a boolean bare, a
     * string as a JSON string, NULL_IF as a JSON array, and FILE_EXTENSION null until one is written
     * (live-verified).
     *
     * @param property the property
     * @param stored   the value as the format keeps it, or null for none
     * @return the value as JSON
     */
    public static String json(final FormatProperty property, final String stored) {
        if ("List".equals(property.type)) {
            final List<String> values = FileFormat.nullIfValues(stored);
            if (values == null) {
                return "[]".equals(property.valueDefault) ? "[]" : "[\"\\\\N\"]";
            }
            final StringBuilder array = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                array.append(i > 0 ? "," : "").append(jsonText(values.get(i)));
            }
            return array.append(']').toString();
        }
        if (stored == null && "FILE_EXTENSION".equals(property.name)) {
            return "null";
        }
        final String value = stored == null ? property.valueDefault : stored;
        if ("Boolean".equals(property.type)) {
            return value.isEmpty() ? "null" : value.toLowerCase(Locale.ROOT);
        }
        if ("Integer".equals(property.type) || "Long".equals(property.type)) {
            return value.isEmpty() ? "null" : value;
        }
        return jsonText(value);
    }

    /** A text as a JSON string, quoted and escaped. */
    private static String jsonText(final String text) {
        final StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        out.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(c)));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }
}
