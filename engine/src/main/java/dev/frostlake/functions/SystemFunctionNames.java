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

import dev.frostlake.executor.SqlCompilationError;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The {@code SYSTEM$} built-ins the engine evaluates itself. They are not in the {@code FunctionRegistry}
 * maps — {@code SystemFunctionEvaluator} (expression-AST path) and {@code VisitorExpressionBuilder}
 * (parse-tree path) switch on the name — so without this set {@code SHOW FUNCTIONS} could only list them
 * from a hand-maintained copy, which is exactly how it fell behind.
 *
 * <p>This set is <strong>load-bearing for dispatch</strong>: both evaluators reject a name that is not
 * declared here before reaching their switch, so a new {@code case} that is not declared here does not
 * work, and {@code SHOW FUNCTIONS} enumerates the same set through
 * {@link FunctionRegistry#allDispatchableNames()}.
 *
 * <p>{@code SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS} is deliberately absent: it is a registered table
 * function, so the registry already contributes it.
 *
 * <p>Snowflake lists {@code SYSTEM$} routines under SHOW FUNCTIONS, not SHOW PROCEDURES (live-verified)
 * {@code SHOW BUILTIN FUNCTIONS LIKE 'SYSTEM$WAIT'} returns
 * {@code SYSTEM$WAIT(NUMBER, DEFAULT VARCHAR) RETURN VARCHAR} with {@code is_builtin = 'Y'}). Its own
 * listing is incomplete — SYSTEM$TYPEOF and SYSTEM$STREAM_HAS_DATA both work on a real account yet are
 * not among the 156 SYSTEM$ names it returns — so this set describes Frostlake, not that listing.
 */
public final class SystemFunctionNames {

    /** The names a block's logging statements are written with. */
    private static final Set<String> LOGGING = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "SYSTEM$LOG", "SYSTEM$LOG_TRACE", "SYSTEM$LOG_DEBUG", "SYSTEM$LOG_INFO", "SYSTEM$LOG_WARN",
        "SYSTEM$LOG_ERROR", "SYSTEM$LOG_FATAL")));

    private static final SortedSet<String> NAMES = Collections.unmodifiableSortedSet(
        new TreeSet<String>(Arrays.asList(
            "SYSTEM$ABORT_SESSION",
            "SYSTEM$ABORT_TRANSACTION",
            "SYSTEM$ALLOWLIST",
            "SYSTEM$AUTO_REFRESH_STATUS",
            "SYSTEM$CANCEL_ALL_QUERIES",
            "SYSTEM$CANCEL_QUERY",
            "SYSTEM$CLUSTERING_DEPTH",
            "SYSTEM$CLUSTERING_INFORMATION",
            "SYSTEM$CLUSTERING_RATIO",
            "SYSTEM$CURRENT_USER_TASK_NAME",
            "SYSTEM$EXTERNAL_TABLE_PIPE_STATUS",
            "SYSTEM$GENERATE_SCIM_ACCESS_TOKEN",
            "SYSTEM$GET_PREDECESSOR_RETURN_VALUE",
            "SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO",
            "SYSTEM$GET_TAG",
            "SYSTEM$GET_TAG_ALLOWED_VALUES",
            "SYSTEM$GET_TAG_ON_CURRENT_COLUMN",
            "SYSTEM$GET_TAG_ON_CURRENT_TABLE",
            "SYSTEM$GET_TASK_GRAPH_CONFIG",
            "SYSTEM$LAST_CHANGE_COMMIT_TIME",
            "SYSTEM$PIPE_STATUS",
            "SYSTEM$QUERY_REFERENCE",
            "SYSTEM$REFERENCE",
            "SYSTEM$SET_RETURN_VALUE",
            "SYSTEM$STREAM_BACKLOG",
            "SYSTEM$STREAM_GET_TABLE_TIMESTAMP",
            "SYSTEM$STREAM_HAS_DATA",
            "SYSTEM$TASK_DEPENDENTS_ENABLE",
            "SYSTEM$TASK_RUNTIME_INFO",
            "SYSTEM$TYPEOF",
            "SYSTEM$VALIDATE_STORAGE_INTEGRATION",
            "SYSTEM$VERIFY_CATALOG_INTEGRATION",
            "SYSTEM$VERIFY_EXTERNAL_VOLUME",
            "SYSTEM$WAIT",
            "SYSTEM$WHITELIST"
        )));

    private SystemFunctionNames() {
    }

    /** Whether the engine implements this {@code SYSTEM$} name (case-insensitive). */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Whether the name is one of a block's logging statements, {@code SYSTEM$LOG} and {@code SYSTEM$LOG_<level>}.
     * They stand as statements in a block and are no functions: a query or a CALL that names one is refused as
     * naming an unknown function (live-verified).
     *
     * @param name the name as written
     * @return whether it is a logging statement's name
     */
    public static boolean isLoggingStatement(final String name) {
        return name != null && LOGGING.contains(name.toUpperCase(Locale.ROOT));
    }

    /**
     * The refusal of a logging statement's name written where a function is called.
     *
     * @param name the name as written
     * @return the refusal
     */
    public static RuntimeException unknownLoggingFunction(final String name) {
        return new RuntimeException(SqlCompilationError.of("Unknown function " + name.toUpperCase(Locale.ROOT)));
    }

    /**
     * The refusal of a statement written as a logging statement under a name that names none, in the account's
     * own words for it, which run the name into the sentence and leave a placeholder unfilled (live-verified).
     *
     * @param name the name, upper-cased
     * @return the refusal's text
     */
    public static String unknownLoggingStatement(final String name) {
        return SqlCompilationError.of("Unknown function" + name + " does not match any known functions {1}");
    }

    /** Every {@code SYSTEM$} function name the engine implements, upper-cased and sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
