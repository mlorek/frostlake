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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.task.IntervalSchedule;
import dev.frostlake.task.TaskGraphConfig;

import java.time.ZoneId;
import java.util.Locale;

/**
 * The values a parameter set on a task may take. A value of the right kind can still be refused: a number
 * outside the parameter's range, a word outside its vocabulary, a time zone the account does not know. Each
 * refusal is the account's sentence, and several parameters have one of their own.
 *
 * <p>A number is echoed with its thousands grouped ({@code [99,999,999]}), a word as the parameter would hold
 * it. A vocabulary is matched ignoring case unless noted, and the value is kept as written.
 */
final class TaskParameterValues {

    private static final String[] LOG_LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF"};
    private static final String[] TRACE_LEVELS = {"ALWAYS", "ON_EVENT", "PROPAGATE", "OFF"};
    private static final String[] METRIC_LEVELS = {"ALL", "NONE"};
    private static final String[] RESULT_FORMATS = {"JSON", "ARROW", "ARROW_FORCE"};
    private static final String[] TIMESTAMP_MAPPINGS = {"TIMESTAMP_LTZ", "TIMESTAMP_NTZ", "TIMESTAMP_TZ"};
    private static final String[] GEO_FORMATS = {"GEOJSON", "WKT", "WKB", "EWKT", "EWKB"};
    /** The isolation levels the account knows and refuses as unsupported; READ COMMITTED is the one it runs. */
    private static final String[] OTHER_ISOLATION_LEVELS = {"READ UNCOMMITTED", "REPEATABLE READ", "SERIALIZABLE",
        "SNAPSHOT"};
    /** Time zone names the zone database keeps that the JDK's zone list leaves out; matched exactly. */
    private static final String[] LEGACY_ZONES = {"EST", "MST", "HST", "ROC"};
    /** The longest TARGET_COMPLETION_INTERVAL: a day. */
    private static final long MAXIMUM_COMPLETION_INTERVAL_SECONDS = 24L * 60L * 60L;
    /** LANGUAGE is matched exactly. */
    private static final String[] LANGUAGES = {"ja", "en", "fr-FR"};

    private static final String S3_VPCE_ADVICE = " Please refer to "
        + "https://docs.snowflake.com/en/user-guide/private-internal-stages-aws#snowflake-configuration for examples";

    private TaskParameterValues() {
    }

    /**
     * Refuse a number outside the parameter's range. A parameter without a range takes any number.
     *
     * @param key   the parameter's name, upper case
     * @param value the number
     */
    static void requireNumber(final String key, final long value) {
        switch (key) {
            case "CLIENT_PREFETCH_THREADS":
                requireBetween(key, value, 1, 10);
                return;
            case "CLIENT_RESULT_CHUNK_SIZE":
                requireBetween(key, value, 16, 160);
                return;
            case "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY":
                requireBetween(key, value, 900, 3600);
                return;
            case "DYNAMIC_TABLES_VIEW_VERSION":
                requireBetween(key, value, 9, 13);
                return;
            case "DYNAMIC_TABLE_GRAPH_HISTORY_VIEW_VERSION":
                requireBetween(key, value, 9, 11);
                return;
            case "DYNAMIC_TABLE_REFRESH_HISTORY_VIEW_VERSION":
                requireBetween(key, value, 12, 17);
                return;
            case "HYBRID_TABLE_LOCK_TIMEOUT":
            case "MULTI_STATEMENT_COUNT":
            case "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS":
            case "SUSPEND_TASK_AFTER_NUM_FAILURES":
            case "TASK_AUTO_RETRY_ATTEMPTS":
                requireBetween(key, value, 0, Long.MAX_VALUE);
                return;
            case "ICEBERG_TIMESTAMP_DEFAULT_PRECISION":
                if (value != 6 && value != 9) {
                    throw invalidNumber(key, value);
                }
                return;
            case "SNOWPARK_REQUEST_TIMEOUT_IN_SECONDS":
                requireBetween(key, value, 1, 604799);
                return;
            case "STATEMENT_TIMEOUT_IN_SECONDS":
                if (value < 0 || value > 604800) {
                    throw new RuntimeException("SQL compilation error: \nparameter value out of range: "
                        + grouped(value) + ". Must be between 0 and 604,800.");
                }
                return;
            case "TWO_DIGIT_CENTURY_START":
                requireBetween(key, value, 1900, 2100);
                return;
            case "WEEK_OF_YEAR_POLICY":
                requireBetween(key, value, 0, 1);
                return;
            case "WEEK_START":
                requireBetween(key, value, 0, 7);
                return;
            case "USER_TASK_TIMEOUT_MS":
                requireBetween(key, value, 0, 604800000L);
                return;
            case "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS":
                requireBetween(key, value, 10, 604800);
                return;
            default:
                return;
        }
    }

    /**
     * Refuse a word outside the parameter's vocabulary. A parameter without one takes any text.
     *
     * @param key  the parameter's name, upper case
     * @param text the value as the parameter would hold it
     */
    static void requireText(final String key, final String text) {
        switch (key) {
            case "QUERY_RESULT_FORMAT":
            case "JDBC_QUERY_RESULT_FORMAT":
            case "ODBC_QUERY_RESULT_FORMAT":
            case "PYTHON_CONNECTOR_QUERY_RESULT_FORMAT":
            case "GO_QUERY_RESULT_FORMAT":
            case "C_API_QUERY_RESULT_FORMAT":
            case "DOTNET_QUERY_RESULT_FORMAT":
            case "UI_QUERY_RESULT_FORMAT":
                requireOneOf(key, text, RESULT_FORMATS);
                return;
            case "SQL_API_QUERY_RESULT_FORMAT":
                requireOneOf(key, text, new String[] {"JSON"});
                return;
            case "CLIENT_TIMESTAMP_TYPE_MAPPING":
            case "TIMESTAMP_TYPE_MAPPING":
                requireOneOf(key, text, TIMESTAMP_MAPPINGS);
                return;
            case "UNSUPPORTED_DDL_ACTION":
                requireOneOf(key, text, new String[] {"IGNORE", "FAIL"});
                return;
            case "ACTIVE_PYTHON_PROFILER":
                requireOneOf(key, text, new String[] {"", "LINE", "MEMORY"});
                return;
            case "TRANSACTION_DEFAULT_ISOLATION_LEVEL":
                requireIsolationLevel(text);
                return;
            case "WORKSPACE_USER_SETTINGS":
                if (!TaskGraphConfig.isObject(text)) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
                }
                return;
            case "GEOGRAPHY_OUTPUT_FORMAT":
            case "GEOMETRY_OUTPUT_FORMAT":
                if (!matches(text, GEO_FORMATS)) {
                    throw new RuntimeException("Invalid output format for type GEOGRAPHY: '" + text
                        + "'. Supported formats are GeoJSON, WKT, WKB");
                }
                return;
            case "METRIC_LEVEL":
                requireLevel("metric level", text, METRIC_LEVELS);
                return;
            case "LOG_EVENT_LEVEL":
                requireLevel("log event level", text, LOG_LEVELS);
                return;
            case "LOG_LEVEL":
                requireLevel("log level", text, LOG_LEVELS);
                return;
            case "TRACE_LEVEL":
                requireLevel("trace level", text, TRACE_LEVELS);
                return;
            case "LANGUAGE":
                if (matchesExactly(text, LANGUAGES)) {
                    return;
                }
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text,
                    "Snowflake only supports [ja, en, fr-FR]"));
            case "BINARY_INPUT_FORMAT":
                if (!matches(text, new String[] {"HEX", "BASE64", "UTF-8", "UTF8"})) {
                    throw new RuntimeException(SqlCompilationError.of("Invalid binary format string '" + text
                        + "': Must be 'HEX', 'BASE64', or 'UTF-8'"));
                }
                return;
            case "BINARY_OUTPUT_FORMAT":
                if (!matches(text, new String[] {"HEX", "BASE64"})) {
                    throw new RuntimeException(SqlCompilationError.of("Invalid binary format string '" + text
                        + "': Must be 'HEX' or 'BASE64'"));
                }
                return;
            case "DEFAULT_NULL_ORDERING":
                if (!matches(text, new String[] {"LAST", "FIRST"})) {
                    throw new RuntimeException(SqlCompilationError.of("invalid value [" + text
                        + "] for parameter 'DEFAULT_NULL_ORDERING'. Accepted values: 'LAST', 'FIRST'.'"));
                }
                return;
            case "PYTHON_PROFILER_TARGET_STAGE":
                requireStageName(text);
                return;
            case "S3_STAGE_VPCE_DNS_NAME":
                requireVpceName(key, text);
                return;
            case "TIMEZONE":
                if (!ZoneId.getAvailableZoneIds().contains(text) && !matchesExactly(text, LEGACY_ZONES)) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
                }
                return;
            case "TARGET_COMPLETION_INTERVAL":
                requireCompletionInterval(key, text);
                return;
            case "SERVERLESS_TASK_MIN_STATEMENT_SIZE":
            case "SERVERLESS_TASK_MAX_STATEMENT_SIZE":
            case "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE":
                if (sizeRank(text) < 0) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
                }
                return;
            default:
                return;
        }
    }

    /**
     * A warehouse size's place from XSMALL up, in any of the spellings the account takes (X-SMALL, X2LARGE,
     * 2X-LARGE, XXLARGE …, in any case), or -1 when it names no size.
     *
     * @param size the size as written
     * @return its rank, or -1
     */
    static int sizeRank(final String size) {
        if (size == null) {
            return -1;
        }
        switch (size.toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "XSMALL":
            case "X_SMALL":
                return 0;
            case "SMALL":
                return 1;
            case "MEDIUM":
                return 2;
            case "LARGE":
                return 3;
            case "XLARGE":
            case "X_LARGE":
                return 4;
            case "XXLARGE":
            case "X2LARGE":
            case "2X_LARGE":
                return 5;
            case "XXXLARGE":
            case "X3LARGE":
            case "3X_LARGE":
                return 6;
            case "X4LARGE":
            case "4X_LARGE":
                return 7;
            case "X5LARGE":
            case "5X_LARGE":
                return 8;
            case "X6LARGE":
            case "6X_LARGE":
                return 9;
            default:
                return -1;
        }
    }

    private static void requireBetween(final String key, final long value, final long min, final long max) {
        if (value < min || value > max) {
            throw invalidNumber(key, value);
        }
    }

    private static RuntimeException invalidNumber(final String key, final long value) {
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(grouped(value), key));
    }

    /** A number with its thousands grouped by commas, the way the account echoes one. */
    private static String grouped(final long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static void requireOneOf(final String key, final String text, final String[] allowed) {
        if (!matches(text, allowed)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
        }
    }

    private static void requireLevel(final String kind, final String text, final String[] allowed) {
        if (!matches(text, allowed)) {
            throw new RuntimeException("Invalid value for " + kind + ". Allowed values are "
                + String.join(",", allowed) + ".");
        }
    }

    private static void requireIsolationLevel(final String text) {
        if ("READ COMMITTED".equalsIgnoreCase(text)) {
            return;
        }
        if (matches(text, OTHER_ISOLATION_LEVELS)) {
            throw new RuntimeException("Unsupported feature 'transaction isolation level " + text + "'.");
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text,
            "TRANSACTION_DEFAULT_ISOLATION_LEVEL"));
    }

    /** An empty stage, or a fully qualified one: three dot-separated parts, an {@code @} before them allowed. */
    private static void requireStageName(final String text) {
        if (text.isEmpty()) {
            return;
        }
        final String name = text.startsWith("@") ? text.substring(1) : text;
        int dots = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '.') {
                dots++;
            }
        }
        if (dots != 2) {
            throw new RuntimeException("Invalid stage '" + text + "'. Please add a fully qualified name.");
        }
    }

    /**
     * An empty name, or an S3 interface endpoint's DNS name: {@code vpce-<id>.s3.<region>.vpce.amazonaws.com},
     * optionally behind {@code *.} or {@code bucket.}.
     */
    private static void requireVpceName(final String key, final String text) {
        if (text.isEmpty()) {
            return;
        }
        if (!text.endsWith("vpce.amazonaws.com")) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text,
                "The value for S3 VPCE DNS name must end with vpce.amazonaws.com" + S3_VPCE_ADVICE));
        }
        final String endpoint = text.startsWith("*.") ? text.substring(2)
            : text.startsWith("bucket.") ? text.substring("bucket.".length()) : text;
        if (!endpoint.matches("vpce-[^.]+\\.s3\\.[^.]+\\.vpce\\.amazonaws\\.com")) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text,
                "The value for S3 VPCE DNS name must either be in the pattern of "
                    + "vpce-<vpceid>.s3.<region>.vpce.amazonaws.com or start with *. or bucket. then followed by "
                    + "this pattern." + S3_VPCE_ADVICE));
        }
    }

    /**
     * An interval written as a schedule's is ({@code '<n> SECOND[S]'}, {@code '<n> S'}, {@code '<n> MINUTE[S]'},
     * {@code '<n> M'}, {@code '<n> HOUR[S]'}, {@code '<n> H'}, read by {@code IntervalSchedule}) of at least ten
     * seconds and at most a day; anything else is refused with the parameter's one sentence.
     */
    private static void requireCompletionInterval(final String key, final String text) {
        final Long millis = IntervalSchedule.millis(text);
        if (millis == null || millis < IntervalSchedule.MINIMUM_SECONDS * 1000L
                || millis > MAXIMUM_COMPLETION_INTERVAL_SECONDS * 1000L) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, key));
        }
    }

    private static boolean matchesExactly(final String text, final String[] allowed) {
        for (final String candidate : allowed) {
            if (candidate.equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(final String text, final String[] allowed) {
        for (final String candidate : allowed) {
            if (candidate.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }
}
