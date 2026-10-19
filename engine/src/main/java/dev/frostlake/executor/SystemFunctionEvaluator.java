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

import dev.frostlake.functions.SystemFunctionNames;
import dev.frostlake.functions.table.QueryRunner;
import dev.frostlake.functions.table.TagReferencesFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.security.SessionDirectory;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.task.TaskGraphConfig;
import dev.frostlake.task.TaskOnlyFunctions;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates {@code SYSTEM$FUNCNAME(...)} built-in system functions invoked from expression context.
 * Extracted from {@link QueryExecutor}; depends only on the catalog, storage engine, transaction
 * manager and the directory of abortable sessions (no QueryExecutor back-pointer).
 */
public class SystemFunctionEvaluator {

    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final TransactionManager transactionManager;
    private final SessionDirectory sessionDirectory;
    // What runs a query a function compiles of its own; installed by the executor once it can run one.
    private QueryRunner queryRunner;

    public SystemFunctionEvaluator(final Catalog catalog, final StorageEngine storageEngine,
                                   final TransactionManager transactionManager,
                                   final SessionDirectory sessionDirectory) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
        this.transactionManager = transactionManager;
        this.sessionDirectory = sessionDirectory;
    }

    /**
     * Installs what runs a query a function compiles of its own, SYSTEM$CLUSTERING_DEPTH's predicate's.
     *
     * @param runner the runner
     */
    public void setQueryRunner(final QueryRunner runner) {
        this.queryRunner = runner;
    }

    /** Evaluate a SYSTEM$FUNCNAME call from expression context (no ANTLR ctx available). */
    public Object evaluateSystemFunction(final String funcName, final List<Object> args) {
        // SystemFunctionNames is the one place these names are declared, and the one place SHOW FUNCTIONS
        // reads them from (through FunctionRegistry.allDispatchableNames()) — they are in none of the
        // registry maps. Checking it here, ahead of the switch, keeps the two in step: a case added below
        // but not declared there does not dispatch, so the listing cannot fall behind unnoticed.
        if (SystemFunctionNames.isLoggingStatement(funcName)) {
            throw SystemFunctionNames.unknownLoggingFunction(funcName);
        }
        if (!SystemFunctionNames.contains(funcName)) {
            throw new RuntimeException("Unsupported system function: " + funcName);
        }
        switch (funcName.toUpperCase(Locale.ROOT)) {
            case "SYSTEM$TYPEOF": {
                if (!args.isEmpty() && args.get(0) != null) {
                    final Object v = args.get(0);
                    if (v instanceof Boolean) return "BOOLEAN[LOB]";
                    if (v instanceof Long || v instanceof Integer) return "INTEGER[LOB]";
                    if (v instanceof Double || v instanceof BigDecimal) return "FLOAT[LOB]";
                    if (v instanceof LocalDateTime || v instanceof LocalDate) return "TIMESTAMP_NTZ[LOB]";
                    // The zoned classes, which fell through to the text branch below because their
                    // toString starts with a digit — so a TIMESTAMP_LTZ read back as VARCHAR. This
                    // switch is the one a SELECT reaches; VisitorExpressionBuilder holds a second copy
                    // for the parse-tree path, and both have to know the same classes.
                    if (v instanceof ZonedDateTime) return "TIMESTAMP_TZ[LOB]";
                    if (v instanceof OffsetDateTime) return "TIMESTAMP_LTZ[LOB]";
                    final String s = v.toString().trim();
                    if (s.startsWith("{")) return "OBJECT[LOB]";
                    if (s.startsWith("[")) return "ARRAY[LOB]";
                    return "VARCHAR[LOB]";
                }
                return "NULL[LOB]";
            }
            case "SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO":
                return "{\"cloud\": \"aws\", \"region\": \"us-east-1\"}";
            case "SYSTEM$ALLOWLIST":
            case "SYSTEM$WHITELIST":
                return "[]";
            case "SYSTEM$LAST_CHANGE_COMMIT_TIME":
                return ChangeTimes.lastChangeCommitTime(catalog, args.isEmpty() ? null : args.get(0));
            case "SYSTEM$WAIT":
                return SystemWait.waitFor(args);
            case "SYSTEM$CLUSTERING_DEPTH":
                return ClusteringStatistics.depth(catalog, storageEngine, args, queryRunner);
            case "SYSTEM$CLUSTERING_INFORMATION":
                return ClusteringStatistics.information(catalog, storageEngine, args);
            case "SYSTEM$CLUSTERING_RATIO":
                return ClusteringStatistics.ratio(catalog, storageEngine, args);
            case "SYSTEM$ABORT_TRANSACTION":
                return abortTransaction(args);
            case "SYSTEM$ABORT_SESSION":
                return SessionAbort.abort(args.isEmpty() ? null : args.get(0), sessionDirectory);
            case "SYSTEM$CANCEL_QUERY":
                return cancelQuery(args.isEmpty() ? null : args.get(0));
            case "SYSTEM$CANCEL_ALL_QUERIES":
                return "Query cancelled.";
            case "SYSTEM$REFERENCE":
                return ObjectReferenceTokens.create(catalog, args);
            case "SYSTEM$STREAM_GET_TABLE_TIMESTAMP":
                return ChangeTimes.streamTableTimestamp(catalog, storageEngine, args.isEmpty() ? null : args.get(0));
            case "SYSTEM$CURRENT_USER_TASK_NAME": {
                final Task current = TaskScheduler.currentTask();
                // Outside a task the name is empty, not NULL (live-verified).
                return current != null ? current.getName() : "";
            }
            case "SYSTEM$GET_PREDECESSOR_RETURN_VALUE": {
                // The task-graph value SET by this task's (first) predecessor in its latest run.
                TaskOnlyFunctions.requireTask("SYSTEM$GET_PREDECESSOR_RETURN_VALUE");
                final Task current = TaskScheduler.currentTask();
                if (current != null && !current.getPredecessors().isEmpty() && catalog != null) {
                    String predName = current.getPredecessors().get(0);
                    final int dot = predName.lastIndexOf('.');
                    if (dot >= 0) {
                        predName = predName.substring(dot + 1);
                    }
                    try {
                        final Task predecessor = catalog.getDatabase(catalog.getCurrentDatabase())
                            .getSchema(catalog.getCurrentSchema()).getTask(predName);
                        return predecessor != null ? predecessor.getLastReturnValue() : null;
                    } catch (final Exception ignored) {
                        return null;
                    }
                }
                return null;
            }
            case "SYSTEM$SET_RETURN_VALUE": {
                final Task current = TaskScheduler.currentTask();
                final Object value = args.isEmpty() ? null : args.get(0);
                if (current != null) {
                    current.setLastReturnValue(
                        value != null ? value.toString().replaceAll("^'|'$", "") : null);
                }
                return value;
            }
            case "SYSTEM$TASK_RUNTIME_INFO":
                TaskOnlyFunctions.requireTask("SYSTEM$TASK_RUNTIME_INFO");
                return "{}";
            case "SYSTEM$GET_TASK_GRAPH_CONFIG":
                return TaskGraphConfig.readInTask(args);
            case "SYSTEM$PIPE_STATUS": {
                final String pipeName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                // Reflect the pipe's actual state (RUNNING/PAUSED); getPipe throws on an unknown pipe, matching Snowflake.
                if (catalog != null && !pipeName.isEmpty()) {
                    final Pipe pipe;
                    try {
                        // The argument names the pipe as an identifier does: quoted parts kept, the rest folded.
                        pipe = catalog.getPipe(SqlIdentifiers.canonicalText(pipeName));
                    } catch (final RuntimeException missing) {
                        // A pipe that is not there is named as the argument writes it, with no privilege
                        // sentence (live-verified).
                        throw new RuntimeException(SqlCompilationError.of("Pipe '" + pipeName
                            + "' does not exist or not authorized."));
                    }
                    return pipe.getStatusJson();
                }
                return "{\"executionState\": \"RUNNING\", \"pendingFileCount\": 0}";
            }
            case "SYSTEM$EXTERNAL_TABLE_PIPE_STATUS":
                return "{\"executionState\": \"RUNNING\", \"pendingFileCount\": 0}";
            case "SYSTEM$AUTO_REFRESH_STATUS":
                return "{\"state\": \"Active\"}";
            case "SYSTEM$QUERY_REFERENCE":
                return UUID.randomUUID().toString();
            case "SYSTEM$GENERATE_SCIM_ACCESS_TOKEN":
                return "{\"token\": \"" + UUID.randomUUID() + "\", \"expires_at\": null}";
            case "SYSTEM$GET_TAG":
                return TagReferencesFunction.systemGetTag(catalog, args);
            case "SYSTEM$GET_TAG_ALLOWED_VALUES":
                return TagReferencesFunction.systemGetTagAllowedValues(catalog, args);
            case "SYSTEM$GET_TAG_ON_CURRENT_COLUMN":
            case "SYSTEM$GET_TAG_ON_CURRENT_TABLE":
                return null;
            case "SYSTEM$VALIDATE_STORAGE_INTEGRATION":
            case "SYSTEM$VERIFY_EXTERNAL_VOLUME":
            case "SYSTEM$VERIFY_CATALOG_INTEGRATION":
                return "{\"status\": \"OK\"}";
            case "SYSTEM$TASK_DEPENDENTS_ENABLE": {
                if (!args.isEmpty() && args.get(0) != null) {
                    final String dbN = catalog.getCurrentDatabase();
                    final String scN = catalog.getCurrentSchema();
                    if (dbN != null && scN != null) {
                        try {
                            enableTaskDependents(
                                args.get(0).toString().replaceAll("^'|'$", ""),
                                catalog.getDatabase(dbN).getSchema(scN).getTasks());
                        } catch (final Exception ignored) {}
                    }
                }
                return "Statement executed successfully.";
            }
            default:
                throw new RuntimeException("Unsupported system function: " + funcName);
        }
    }

    /**
     * Resume (STARTED) every task in the given list that is a transitive dependent of {@code root}
     * — the named root's subtree only, matching predecessor references by bare task name. The root
     * itself is not resumed (Snowflake: it is resumed separately with ALTER TASK … RESUME).
     */
    public static void enableTaskDependents(final String root, final List<Task> tasks) {
        String rootName = root;
        final int rootDot = rootName.lastIndexOf('.');
        if (rootDot >= 0) {
            rootName = rootName.substring(rootDot + 1);
        }
        final Set<String> enabled = new HashSet<>();
        enabled.add(rootName.toUpperCase(Locale.ROOT));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (final Task task : tasks) {
                if (enabled.contains(task.getName().toUpperCase(Locale.ROOT))) {
                    continue;
                }
                for (final String predecessor : task.getPredecessors()) {
                    final int dot = predecessor.lastIndexOf('.');
                    final String bare = dot >= 0 ? predecessor.substring(dot + 1) : predecessor;
                    if (enabled.contains(bare.toUpperCase(Locale.ROOT))) {
                        task.setState(TaskState.STARTED);
                        enabled.add(task.getName().toUpperCase(Locale.ROOT));
                        changed = true;
                        break;
                    }
                }
            }
        }
    }

    /**
     * SYSTEM$ABORT_TRANSACTION(&lt;id&gt;) for a caller that hands over the argument's value alone: abort the
     * transaction the id NAMES — the public id SHOW TRANSACTIONS lists and CURRENT_TRANSACTION() answers —
     * whichever session holds it. See {@link TransactionAbort}.
     */
    private Object abortTransaction(final List<Object> args) {
        return TransactionAbort.answer(TransactionAbort.byValue(args.isEmpty() ? null : args.get(0)),
            transactionManager, false);
    }

    /**
     * SYSTEM$CANCEL_QUERY: the running statement its query ID names is canceled, from any session; an ID that names no
     * running statement answers that it is not executing, and a text that is no UUID that it is invalid
     * (live-verified).
     */
    private static String cancelQuery(final Object id) {
        if (id == null) {
            return null;
        }
        final String text = id.toString().trim();
        try {
            UUID.fromString(text);
        } catch (final IllegalArgumentException notAUuid) {
            return "invalid UUID";
        }
        return StatementCancellation.cancelRunning(text) ? "Query cancelled."
            : "Identified SQL statement is not currently executing.";
    }
}
