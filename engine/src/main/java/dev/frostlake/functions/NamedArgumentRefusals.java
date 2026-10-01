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
package dev.frostlake.functions;

import dev.frostlake.functions.window.NamedArgumentWindowFunctions;
import dev.frostlake.functions.window.WindowFunctionNames;
import java.util.Set;

/**
 * The built-ins the account refuses when a call names its arguments, once their count is judged:
 * {@code function UPPER does not support named arguments}, positioned at the call. Every scalar built-in was
 * measured with named arguments at its minimum count, and a large part of them answers such a call as the
 * positional one — the conversions (TO_DATE, TO_VARCHAR, …), the date parts (DAY, YEAR, …), DATEADD, DECODE,
 * LEFT, ARRAY_CONSTRUCT and more — while ROUND, PARSE_XML, SEARCH and the FL_ family take names of their own. The
 * refusing scalars follow no rule of family, so they are kept as measured; an aggregate refuses unless the account
 * answers its named plain call (see {@link NamedArgumentWindowFunctions}), and so does every window function but
 * FIRST_VALUE and LAST_VALUE, which are refused for their missing window instead.
 */
public final class NamedArgumentRefusals {

    private static final Set<String> SCALARS = Set.of(
        "ABS", "ACOS", "ACOSH", "APPROX_PERCENTILE_ESTIMATE", "APPROX_TOP_K_ESTIMATE", "ARRAYS_OVERLAP",
        "ARRAYS_TO_OBJECT", "ARRAYS_ZIP", "ARRAY_APPEND", "ARRAY_CAT", "ARRAY_COMPACT", "ARRAY_CONTAINS",
        "ARRAY_DISTINCT", "ARRAY_EXCEPT", "ARRAY_FLATTEN", "ARRAY_GENERATE_RANGE", "ARRAY_INSERT",
        "ARRAY_INTERSECTION", "ARRAY_MAX", "ARRAY_MIN", "ARRAY_POSITION", "ARRAY_PREPEND", "ARRAY_REMOVE",
        "ARRAY_REMOVE_AT", "ARRAY_REPEAT", "ARRAY_REVERSE", "ARRAY_SIZE", "ARRAY_SORT", "ARRAY_TO_STRING",
        "ASCII", "ASIN", "ASINH", "AS_ARRAY", "AS_BINARY", "AS_BOOLEAN", "AS_CHAR", "AS_DATE", "AS_DOUBLE",
        "AS_OBJECT", "AS_REAL", "AS_TIME", "AS_VARCHAR", "ATAN", "ATAN2", "ATANH", "BASE64_DECODE_BINARY",
        "BASE64_DECODE_STRING", "BASE64_ENCODE", "BINARY_AS_STRING", "BITAND", "BITCOUNT", "BITNOT", "BITOR",
        "BITSHIFTLEFT", "BITSHIFTRIGHT", "BITXOR", "BIT_AND", "BIT_NOT", "BIT_OR", "BIT_SHIFTLEFT",
        "BIT_SHIFTRIGHT", "BIT_XOR", "BOOLAND", "BOOLNOT", "BOOLOR", "BOOLXOR", "CBRT", "CEIL", "CHAR",
        "CHARINDEX", "CHECK_JSON", "CHR", "COALESCE", "COLLATION", "COMPRESS", "CONCAT", "CONCAT_WS", "CONTAINS",
        "CONVERT_TIMEZONE", "COS", "COSH", "COT", "CURRENT_TIME", "CURRENT_TIMESTAMP", "DATEFROMPARTS",
        "DATE_FROM_PARTS", "DAYNAME", "DECOMPRESS_BINARY", "DECOMPRESS_STRING", "DECRYPT", "DECRYPT_RAW",
        "DEGREES", "DIV0", "EDITDISTANCE", "ENCRYPT", "ENCRYPT_RAW", "ENDSWITH", "EQUAL_NULL", "EXP", "FACTORIAL",
        "FLOOR", "GET", "GETBIT", "GETVARIABLE", "GET_IGNORE_CASE", "GET_PATH", "GREATEST",
        "GREATEST_IGNORE_NULLS", "HASH", "HAVERSINE", "HEX_DECODE_BINARY", "HEX_DECODE_STRING", "HEX_ENCODE",
        "HLL_ESTIMATE", "HLL_EXPORT", "HLL_IMPORT", "IFF", "IFNULL", "INITCAP", "IS_ARRAY", "IS_BINARY",
        "IS_BOOLEAN", "IS_CHAR", "IS_DATE", "IS_DATE_VALUE", "IS_DECIMAL", "IS_DOUBLE", "IS_INTEGER",
        "IS_NULL_VALUE", "IS_OBJECT", "IS_REAL", "IS_ROLE_IN_SESSION", "IS_TIME", "IS_TIMESTAMP_LTZ",
        "IS_TIMESTAMP_NTZ", "IS_TIMESTAMP_TZ", "IS_VARCHAR", "IS_VECTOR", "JAROWINKLER_SIMILARITY",
        "LAST_QUERY_ID", "LEAST", "LEAST_IGNORE_NULLS", "LENGTH", "LN", "LOCALTIME", "LOCALTIMESTAMP", "LOG",
        "LOWER", "LPAD", "LTRIM", "MAP_CAT", "MAP_CONTAINS_KEY", "MAP_DELETE", "MAP_ENTRIES", "MAP_INSERT",
        "MAP_KEYS", "MAP_PICK", "MAP_SIZE", "MD5", "MD5_BINARY", "MD5_HEX", "MOD", "MONTHNAME", "NEGATE",
        "NEXT_DAY", "NORMAL", "NORMALIZE", "NULLIF", "NVL", "NVL2", "OBJECT_DELETE", "OBJECT_INSERT",
        "OBJECT_KEYS", "OBJECT_PICK", "OCTET_LENGTH", "PARSE_IP", "PARSE_JSON", "PARSE_URL", "POW", "POWER",
        "PREVIOUS_DAY", "RADIANS", "RANDOM", "RANDSTR", "REGEXP_COUNT", "REGEXP_EXTRACT_ALL", "REGEXP_INSTR",
        "REGEXP_LIKE", "REGEXP_REPLACE", "REGEXP_SUBSTR", "REGEXP_SUBSTR_ALL", "REPLACE", "REVERSE", "RPAD",
        "RTRIM", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "SHA1", "SHA1_BINARY", "SHA1_HEX", "SHA2", "SHA2_BINARY",
        "SHA2_HEX", "SIGN", "SIN", "SINH", "SOUNDEX", "SOUNDEX_P123", "SPLIT", "SPLIT_PART", "SQRT", "SQUARE",
        "STARTSWITH", "STRING_AS_BINARY", "STRIP_NULL_VALUE", "STRTOK", "STRTOK_TO_ARRAY", "SUBSTR", "SUBSTRING",
        "SYSTEM$AUTO_REFRESH_STATUS", "SYSTEM$CLUSTERING_DEPTH", "SYSTEM$CLUSTERING_RATIO",
        "SYSTEM$GET_PREDECESSOR_RETURN_VALUE", "SYSTEM$LAST_CHANGE_COMMIT_TIME", "SYSTEM$QUERY_REFERENCE",
        "SYSTEM$STREAM_GET_TABLE_TIMESTAMP", "SYSTEM$STREAM_HAS_DATA", "SYSTEM$TASK_RUNTIME_INFO", "TAN", "TANH",
        "TIMEFROMPARTS", "TIMESTAMPFROMPARTS", "TIMESTAMPLTZFROMPARTS", "TIMESTAMPNTZFROMPARTS",
        "TIMESTAMPTZFROMPARTS", "TIMESTAMP_FROM_PARTS", "TIMESTAMP_LTZ_FROM_PARTS", "TIMESTAMP_NTZ_FROM_PARTS",
        "TIMESTAMP_TZ_FROM_PARTS", "TIME_FROM_PARTS", "TO_JSON", "TO_XML", "TRANSLATE", "TRIM",
        "TRY_BASE64_DECODE_BINARY", "TRY_BASE64_DECODE_STRING", "TRY_DECRYPT", "TRY_DECRYPT_RAW",
        "TRY_HEX_DECODE_BINARY", "TRY_HEX_DECODE_STRING", "TRY_PARSE_IP", "TRY_PARSE_JSON", "TRY_VALIDATE_UTF8",
        "TYPEOF", "UNICODE", "UNIFORM", "UPPER", "UUID_STRING", "VALIDATE_UTF8", "VECTOR_COSINE_SIMILARITY",
        "VECTOR_INNER_PRODUCT", "VECTOR_L1_DISTANCE", "VECTOR_L2_DISTANCE", "VECTOR_NORMALIZE", "VECTOR_TRUNC",
        "VECTOR_TRUNCATE", "WIDTH_BUCKET", "XMLGET", "ZEROIFNULL");

    private NamedArgumentRefusals() {
    }

    /**
     * Whether a call of this built-in written with named arguments is refused.
     *
     * @param name     the canonical (upper-cased) function name
     * @param registry the function registry, which tells the aggregates
     * @return whether the named call is refused
     */
    public static boolean refuses(final String name, final FunctionRegistry registry) {
        if (SCALARS.contains(name)) {
            return true;
        }
        if (registry.hasAggregateFunction(name)) {
            return !NamedArgumentWindowFunctions.acceptsPlain(name);
        }
        return WindowFunctionNames.handles(name) && !"FIRST_VALUE".equals(name) && !"LAST_VALUE".equals(name);
    }
}
