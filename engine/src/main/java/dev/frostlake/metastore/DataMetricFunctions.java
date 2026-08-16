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

package dev.frostlake.metastore;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The system DATA METRIC FUNCTIONS, which all live in SNOWFLAKE.CORE — read off a live account, so
 * an attachment naming something else is refused the way live refuses it.
 *
 * <p>Most take ONE column; the table-level ones take none. A metric may appear in both lists, as
 * DUPLICATE_COUNT and FRESHNESS do.
 */
public final class DataMetricFunctions {

    /** Where every system metric lives; a name qualified any other way does not resolve. */
    public static final String SYSTEM_SCHEMA = "SNOWFLAKE.CORE";

    /** The metrics measured over one column. */
    private static final Set<String> COLUMN_METRICS = new HashSet<>(Arrays.asList(
        "ACCEPTED_VALUES", "APPROX_QUANTILE_25", "APPROX_QUANTILE_50", "APPROX_QUANTILE_99", "AVG",
        "BLANK_COUNT", "BLANK_PERCENT", "CASE_FORMAT_VIOLATION_COUNT", "CASE_FORMAT_VIOLATION_PERCENT",
        "DUPLICATE_COUNT", "FRESHNESS", "FUTURE_TIMESTAMP_COUNT", "FUTURE_TIMESTAMP_PERCENT",
        "INVALID_JSON_COUNT", "INVALID_JSON_PERCENT", "INVALID_NUMERIC_TYPE_CAST_COUNT",
        "INVALID_NUMERIC_TYPE_CAST_PERCENT", "MAX", "MEDIAN", "MIN", "NEGATIVE_COUNT",
        "NEGATIVE_PERCENT", "NULL_COUNT", "NULL_PERCENT", "SPECIAL_CHARACTER_COUNT",
        "SPECIAL_CHARACTER_PERCENT", "STDDEV", "STRING_LENGTH_AVG", "STRING_LENGTH_MAX",
        "STRING_LENGTH_MIN", "UNIQUE_COUNT", "UNTRIMMED_STRING_COUNT", "UNTRIMMED_STRING_PERCENT",
        "VARIANCE", "ZERO_COUNT", "ZERO_PERCENT"));

    /** The metrics measured over the whole table, which take no column. */
    private static final Set<String> TABLE_METRICS = new HashSet<>(Arrays.asList(
        "DUPLICATE_COUNT", "FRESHNESS", "REFERENTIAL_INTEGRITY_COUNT", "ROW_COUNT",
        "SCHEMA_CHANGE_COUNT"));

    private DataMetricFunctions() {
    }

    /**
     * Whether a fully qualified name is a system metric. A bare or otherwise-qualified name is not:
     * live answers {@code Function 'NULL_COUNT' does not exist or not authorized.} to the bare form.
     */
    public static boolean exists(final String qualifiedName) {
        final String upper = qualifiedName.toUpperCase(Locale.ROOT);
        if (!upper.startsWith(SYSTEM_SCHEMA + ".")) {
            return false;
        }
        final String metric = upper.substring(SYSTEM_SCHEMA.length() + 1);
        return COLUMN_METRICS.contains(metric) || TABLE_METRICS.contains(metric);
    }

    /** The metric's bare name, for the sentences that spell it without its schema. */
    public static String bareName(final String qualifiedName) {
        final String upper = qualifiedName.toUpperCase(Locale.ROOT);
        final int dot = upper.lastIndexOf('.');
        return dot < 0 ? upper : upper.substring(dot + 1);
    }
}
