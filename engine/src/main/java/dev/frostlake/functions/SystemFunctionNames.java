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

import java.util.Arrays;
import java.util.Collections;
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
            "SYSTEM$GET_TAG_ON_CURRENT_COLUMN",
            "SYSTEM$GET_TAG_ON_CURRENT_TABLE",
            "SYSTEM$GET_TASK_GRAPH_CONFIG",
            "SYSTEM$LAST_CHANGE_COMMIT_TIME",
            "SYSTEM$LOG",
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
        return name != null && NAMES.contains(name.toUpperCase());
    }

    /** Every {@code SYSTEM$} function name the engine implements, upper-cased and sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
