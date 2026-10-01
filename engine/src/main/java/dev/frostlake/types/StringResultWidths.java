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

package dev.frostlake.types;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How WIDE the string a function returns is declared to be. Snowflake computes a width from the
 * arguments wherever it can, and falls back to the 128MB conversion width where it cannot — it never
 * answers the 16MB a declared column defaults to unless a 16MB column is what went in. Measured
 * function by function on a live account:
 *
 * <pre>
 *   UPPER(v)                v is VARCHAR(5)   VARCHAR(15)          THREE times — upper-casing can lengthen
 *   LOWER(v)                                  VARCHAR(5)           lower-casing cannot, so it does not
 *   TRIM / SUBSTR / LEFT                      VARCHAR(5)           the SOURCE width, not the length asked for
 *   CONCAT(v, w)            w is VARCHAR(9)   VARCHAR(14)          the widths add
 *   CONCAT_WS(',', v, w)                      VARCHAR(15)          plus the separator, once per gap
 *   INSERT(v, 1, 1, 'x')                      VARCHAR(11)          twice the base, plus the insert
 *   MD5 / SHA1 / SHA2                         VARCHAR(32/40/128)   a digest is a fixed width
 *   HEX_ENCODE(v)                             VARCHAR(40)          8 per character, 2 per BINARY byte
 *   BASE64_ENCODE(v)                          VARCHAR(28)          4 per 3 bytes, and a character is 4 bytes
 *   TO_CHAR / CAST(x AS VARCHAR) / CURRENT_*  VARCHAR(134217728)   nothing bounds it, so the maximum
 *   UPPER(d) / SUBSTR(n, 1, 2)                VARCHAR(134217728)   a number, date or BOOLEAN converted to text first
 * </pre>
 *
 * <p>Everything saturates at 134217728: {@code CONCAT} of eight 16MB columns is exactly that, and of
 * nine still is.
 */
public final class StringResultWidths {

    /** The width a string takes when nothing about its arguments can bound it. */
    public static final int UNBOUNDED = 134217728;

    /** What one character can occupy once encoded — the factor the byte-wise encoders charge. */
    private static final int BYTES_PER_CHARACTER = 4;

    /** The functions that hand back a piece of their first argument, so its width carries over. */
    private static final Set<String> KEEPS_SOURCE_WIDTH = new HashSet<>(Arrays.asList(
        "LOWER", "TRIM", "LTRIM", "RTRIM", "SUBSTR", "SUBSTRING", "MID", "LEFT", "RIGHT",
        "SPLIT_PART", "REGEXP_SUBSTR", "REVERSE", "STRTOK", "COLLATE"));

    /** The two that may LENGTHEN what they are given, and are budgeted three characters for one. */
    private static final Set<String> TRIPLES_SOURCE_WIDTH = new HashSet<>(Arrays.asList(
        "UPPER", "INITCAP"));

    /** The functions that declare a bare VARCHAR, whose answer is widthless whatever went in (live-verified). */
    private static final Set<String> WIDTHLESS_RESULT = new HashSet<>(Arrays.asList(
        "REPLACE", "TO_VARCHAR", "TO_CHAR", "TO_JSON", "ARRAY_TO_STRING", "TYPEOF", "COLLATION", "LAST_QUERY_ID",
        "CURRENT_DATABASE", "CURRENT_SCHEMA", "CURRENT_USER", "CURRENT_ROLE", "CURRENT_WAREHOUSE", "CURRENT_VERSION",
        "CURRENT_REGION", "CURRENT_ACCOUNT", "CURRENT_STATEMENT", "CURRENT_SESSION", "GETVARIABLE",
        "CURRENT_TRANSACTION", "LAST_TRANSACTION",
        // A replacement can be any length, so the account bounds it at nothing at all — a widthless
        // VARCHAR, not the 128MB one, at every arity and over a column as well as a literal.
        "REGEXP_REPLACE"));

    /** Of the functions that keep their source's width, those that take a bare cut of it: over a widthless source
     *  SUBSTR and LEFT answer the width nothing bounds, where LOWER or TRIM stay widthless (live-verified). */
    private static final Set<String> CUTS_SOURCE = new HashSet<>(Arrays.asList(
        "SUBSTR", "SUBSTRING", "MID", "LEFT", "RIGHT", "REGEXP_SUBSTR", "STRTOK"));

    /** The functions whose answer is a fixed width whatever went in. */
    private static final Map<String, Integer> FIXED_WIDTH = new HashMap<>();

    static {
        FIXED_WIDTH.put("MD5", Integer.valueOf(32));
        FIXED_WIDTH.put("MD5_HEX", Integer.valueOf(32));
        FIXED_WIDTH.put("SHA1", Integer.valueOf(40));
        FIXED_WIDTH.put("SHA1_HEX", Integer.valueOf(40));
        FIXED_WIDTH.put("SHA2", Integer.valueOf(128));
        FIXED_WIDTH.put("SHA2_HEX", Integer.valueOf(128));
        FIXED_WIDTH.put("SOUNDEX", Integer.valueOf(7));
        FIXED_WIDTH.put("SOUNDEX_P123", Integer.valueOf(7));
        FIXED_WIDTH.put("UUID_STRING", Integer.valueOf(36));
        FIXED_WIDTH.put("CHR", Integer.valueOf(1));
        FIXED_WIDTH.put("CHAR", Integer.valueOf(1));
        FIXED_WIDTH.put("DAYNAME", Integer.valueOf(3));
        FIXED_WIDTH.put("MONTHNAME", Integer.valueOf(3));
    }

    private StringResultWidths() {
    }

    /**
     * The width the named function's string result is declared at, or null when the arguments do not
     * determine one and the caller should keep what the registry declares.
     *
     * @param funcName the function's name, upper-cased
     * @param argTypes each argument's declared type, in written order, null where undetermined
     * @return the declared string type, or null
     */
    public static DataType forFunction(final String funcName, final List<DataType> argTypes) {
        final Integer fixed = FIXED_WIDTH.get(funcName);
        if (fixed != null) {
            return varchar(fixed.intValue());
        }
        if (WIDTHLESS_RESULT.contains(funcName)) {
            return WidthlessStringType.WIDTHLESS;
        }
        final boolean widthlessSource = !argTypes.isEmpty() && (argTypes.get(0) instanceof WidthlessStringType
            || argTypes.get(0) instanceof LengthlessStringType);
        if (KEEPS_SOURCE_WIDTH.contains(funcName) || TRIPLES_SOURCE_WIDTH.contains(funcName)) {
            if (widthlessSource) {
                return CUTS_SOURCE.contains(funcName) ? varchar(UNBOUNDED) : WidthlessStringType.WIDTHLESS;
            }
            // A COLLATE over an untyped NULL has no width to keep: NULL COLLATE 'en-ci' is a bare VARCHAR.
            if (funcName.equals("COLLATE") && !argTypes.isEmpty() && argTypes.get(0) == null) {
                return WidthlessStringType.WIDTHLESS;
            }
            final long source = stringWidth(argTypes, 0);
            if (source < 0) {
                // A number, a date or time or a BOOLEAN is converted to text first, which nothing bounds:
                // UPPER(d) and SUBSTR(n, 1, 2) are VARCHAR(134217728) (live-verified).
                return convertedToText(argTypes.isEmpty() ? null : argTypes.get(0)) ? varchar(UNBOUNDED) : null;
            }
            if (funcName.equals("REGEXP_SUBSTR")) {
                // The match can be no longer than the source, but the account declares the WIDER of the
                // source and the PATTERN: REGEXP_SUBSTR('abcd', '[a-z]') is VARCHAR(5) for a four-character
                // source and a five-character pattern, and a twenty-eight-character pattern over a
                // VARCHAR(20) column is VARCHAR(28). Arity has nothing to do with it.
                final long pattern = stringWidth(argTypes, 1);
                return varchar(Math.max(source, pattern));
            }
            return varchar(TRIPLES_SOURCE_WIDTH.contains(funcName) ? source * 3 : source);
        }
        if (funcName.equals("CONCAT")) {
            return summedWidth(argTypes, 0, 0);
        }
        if (funcName.equals("CONCAT_WS")) {
            final long separator = stringWidth(argTypes, 0);
            if (separator < 0 || argTypes.size() < 2) {
                return separator < 0 && joinsAsText(argTypes, 0) ? varchar(UNBOUNDED) : null;
            }
            return summedWidth(argTypes, 1, separator * (argTypes.size() - 2));
        }
        if (funcName.equals("INSERT")) {
            final long base = stringWidth(argTypes, 0);
            final long inserted = stringWidth(argTypes, 3);
            if (base < 0 || inserted < 0) {
                return (base >= 0 || joinsAsText(argTypes, 0)) && (inserted >= 0 || joinsAsText(argTypes, 3))
                    ? varchar(UNBOUNDED) : null;
            }
            return varchar(base * 2 + inserted);
        }
        if (widthlessSource && funcName.equals("HEX_ENCODE")) {
            return WidthlessStringType.WIDTHLESS;
        }
        final DataType encoded = encodedWidth(funcName, argTypes);
        return encoded != null ? encoded : varchar(UNBOUNDED);
    }

    /**
     * The width of an ENCODING or DECODING function's result, which counts bytes rather than
     * characters: a character contributes four bytes where a BINARY byte contributes one.
     *
     * @param funcName the function's name, upper-cased
     * @param argTypes the argument types
     * @return the declared type, or null when this is not one of them
     */
    private static DataType encodedWidth(final String funcName, final List<DataType> argTypes) {
        final long bytes = sourceBytes(argTypes);
        if (funcName.equals("HEX_ENCODE")) {
            return bytes < 0 ? null : varchar(bytes * 2);
        }
        if (funcName.equals("BASE64_ENCODE")) {
            return bytes < 0 ? null : varchar((bytes + 2) / 3 * 4);
        }
        final long characters = stringWidth(argTypes, 0);
        if (funcName.equals("HEX_DECODE_STRING") || funcName.equals("TRY_HEX_DECODE_STRING")) {
            return characters < 0 ? null : varchar(characters / 2);
        }
        if (funcName.equals("BASE64_DECODE_STRING")
                || funcName.equals("TRY_BASE64_DECODE_STRING")) {
            return characters < 0 ? null : varchar(characters / 4 * 3);
        }
        return null;
    }

    /**
     * How many bytes the first argument can hold — its own length for a BINARY, four times its
     * length for a string.
     *
     * @param argTypes the argument types
     * @return the byte count, or -1 when the argument determines none
     */
    private static long sourceBytes(final List<DataType> argTypes) {
        if (argTypes.isEmpty()) {
            return -1;
        }
        final DataType first = argTypes.get(0);
        if (first instanceof BinaryType) {
            return ((BinaryType) first).getMaxLength();
        }
        final long characters = stringWidth(argTypes, 0);
        return characters < 0 ? -1 : characters * BYTES_PER_CHARACTER;
    }

    /**
     * The declared width of the argument at {@code index}, or -1 when it is absent or not a string.
     *
     * @param argTypes the argument types
     * @param index    the position to read
     * @return the width, or -1
     */
    /** Whether a string function reads a value of this type through its conversion to text. */
    private static boolean convertedToText(final DataType type) {
        return type instanceof NumericType || type instanceof DateTimeType || type instanceof BooleanType;
    }

    private static long stringWidth(final List<DataType> argTypes, final int index) {
        if (index >= argTypes.size() || !(argTypes.get(index) instanceof StringType)) {
            return -1;
        }
        return ((StringType) argTypes.get(index)).getMaxLength();
    }

    /**
     * The widths of every argument from {@code from} onwards, added together with a fixed extra.
     *
     * @param argTypes the argument types
     * @param from     the first argument to count
     * @param extra    a width to add on top — the separators of a CONCAT_WS
     * @return the declared type, or null when any counted argument is not a string
     */
    private static DataType summedWidth(final List<DataType> argTypes, final int from,
                                        final long extra) {
        long total = extra;
        boolean unbounded = false;
        for (int i = from; i < argTypes.size(); i++) {
            final long width = stringWidth(argTypes, i);
            if (width < 0) {
                if (!joinsAsText(argTypes, i)) {
                    return null;
                }
                unbounded = true;
            }
            total += Math.max(width, 0);
        }
        return varchar(unbounded ? UNBOUNDED : total);
    }

    /**
     * Whether a concatenation's argument joins through its conversion to text, which nothing bounds: a
     * number, a date or time, a BOOLEAN or a VARIANT makes CONCAT, CONCAT_WS and INSERT VARCHAR(134217728)
     * — {@code CONCAT(i, 'a')}, {@code CONCAT(ts, v)} and {@code INSERT(d, 1, 1, 'x')} alike
     * (live-verified).
     *
     * @param argTypes the argument types
     * @param index    the position to read
     * @return true when that argument is present and converts to text
     */
    private static boolean joinsAsText(final List<DataType> argTypes, final int index) {
        if (index >= argTypes.size()) {
            return false;
        }
        final DataType type = argTypes.get(index);
        return convertedToText(type) || type instanceof VariantType;
    }

    /**
     * A VARCHAR of that width, saturated at the maximum a Snowflake string can hold.
     *
     * @param width the computed width
     * @return the declared type
     */
    private static StringType varchar(final long width) {
        return new StringType("VARCHAR", (int) Math.max(0, Math.min(width, UNBOUNDED)));
    }
}
