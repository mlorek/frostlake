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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 */
public final class FileFormatSurfaces {

private static final Map<String, List<FormatProperty>> TREES = new LinkedHashMap<>();

    private static FormatProperty prop(final String name, final String type, final String both) {
        return new FormatProperty(name, type, both, both);
    }

    static {
        TREES.put("CSV", Arrays.asList(
            prop("TYPE", "String", "CSV"),
            prop("RECORD_DELIMITER", "String", "\\n"),
            prop("FIELD_DELIMITER", "String", ","),
            prop("FILE_EXTENSION", "String", ""),
            prop("SKIP_HEADER", "Integer", "0"),
            prop("PARSE_HEADER", "Boolean", "false"),
            prop("DATE_FORMAT", "String", "AUTO"),
            prop("TIME_FORMAT", "String", "AUTO"),
            prop("TIMESTAMP_FORMAT", "String", "AUTO"),
            prop("BINARY_FORMAT", "String", "HEX"),
            prop("ESCAPE", "String", "NONE"),
            prop("ESCAPE_UNENCLOSED_FIELD", "String", "\\\\"),
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
            prop("COMPRESSION", "String", "AUTO"),
            prop("REPLACE_INVALID_CHARACTERS", "Boolean", "false")));
    }

    private static final Set<String> COMPRESSION_VALUES = Set.of(
        "AUTO", "GZIP", "BZ2", "BROTLI", "ZSTD", "DEFLATE", "RAW_DEFLATE", "NONE");

    private FileFormatSurfaces() {
    }

    public static boolean isKnownType(final String type) {
        return type != null && TREES.containsKey(type.toUpperCase());
    }

    public static List<FormatProperty> tree(final String type) {
        final List<FormatProperty> tree = TREES.get(type == null ? "CSV" : type.toUpperCase());
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
        final String upper = name.toUpperCase();
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
        if ("COMPRESSION".equals(upper) && !COMPRESSION_VALUES.contains(value.toUpperCase())) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'COMPRESSION'"));
        }
        if (DELIMITERS.contains(upper) && !isDelimiter(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter '" + upper + "'"));
        }
        if (ESCAPES.contains(upper) && !isOneCharacter(value)) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter '" + upper + "'"));
        }
        if ("FIELD_OPTIONALLY_ENCLOSED_BY".equals(upper) && !ENCLOSERS.contains(value.toUpperCase())) {
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value [" + written + "] for parameter 'FIELD_OPTIONALLY_ENCLOSED_BY'"));
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
}
