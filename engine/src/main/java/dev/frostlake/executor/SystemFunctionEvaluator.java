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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates {@code SYSTEM$FUNCNAME(...)} built-in system functions invoked from expression context.
 * Extracted from {@link QueryExecutor}; depends only on the catalog, storage engine, and transaction
 * manager (no QueryExecutor back-pointer).
 */
public class SystemFunctionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(SystemFunctionEvaluator.class);

    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final TransactionManager transactionManager;

    public SystemFunctionEvaluator(final Catalog catalog, final StorageEngine storageEngine,
                                   final TransactionManager transactionManager) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
        this.transactionManager = transactionManager;
    }

    /** Evaluate a SYSTEM$FUNCNAME call from expression context (no ANTLR ctx available). */
    public Object evaluateSystemFunction(final String funcName, final List<Object> args) {
        // SystemFunctionNames is the one place these names are declared, and the one place SHOW FUNCTIONS
        // reads them from (through FunctionRegistry.allDispatchableNames()) — they are in none of the
        // registry maps. Checking it here, ahead of the switch, keeps the two in step: a case added below
        // but not declared there does not dispatch, so the listing cannot fall behind unnoticed.
        if (!SystemFunctionNames.contains(funcName)) {
            throw new RuntimeException("Unsupported system function: " + funcName);
        }
        switch (funcName.toUpperCase()) {
            case "SYSTEM$TYPEOF": {
                if (!args.isEmpty() && args.get(0) != null) {
                    final Object v = args.get(0);
                    if (v instanceof Boolean) return "BOOLEAN[LOB]";
                    if (v instanceof Long || v instanceof Integer) return "INTEGER[LOB]";
                    if (v instanceof Double || v instanceof BigDecimal) return "FLOAT[LOB]";
                    if (v instanceof LocalDateTime || v instanceof LocalDate) return "TIMESTAMP_NTZ[LOB]";
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
                return System.currentTimeMillis();
            case "SYSTEM$WAIT": {
                if (!args.isEmpty() && args.get(0) instanceof Number) {
                    final long ms = (long)(((Number) args.get(0)).doubleValue() * 1000);
                    if (ms > 0 && ms <= 30_000) {
                        try { Thread.sleep(ms); } catch (final InterruptedException ignored) {}
                    }
                }
                return "waited";
            }
            case "SYSTEM$LOG":
                if (args.size() >= 2) logger.info("SYSTEM$LOG [{}]: {}", args.get(0), args.get(1));
                else if (!args.isEmpty()) logger.info("SYSTEM$LOG: {}", args.get(0));
                return null;
            case "SYSTEM$CLUSTERING_DEPTH": {
                final String t = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"average_depth\": 1.0, \"table_name\": \"" + t + "\"}";
            }
            case "SYSTEM$CLUSTERING_INFORMATION": {
                final String t = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"clustering_key\": null, \"total_partition_count\": 1, \"average_depth\": 1.0, \"table_name\": \"" + t + "\"}";
            }
            case "SYSTEM$CLUSTERING_RATIO":
                return 1.0;
            case "SYSTEM$ABORT_TRANSACTION":
                try { transactionManager.rollback(); } catch (final Exception ignored) {}
                return "Transaction aborted.";
            case "SYSTEM$ABORT_SESSION":
                return "Session aborted.";
            case "SYSTEM$CANCEL_QUERY":
            case "SYSTEM$CANCEL_ALL_QUERIES":
                return "Query cancelled.";
            case "SYSTEM$STREAM_BACKLOG": {
                if (!args.isEmpty() && args.get(0) != null) {
                    final String name = args.get(0).toString().replaceAll("^'|'$", "").toUpperCase();
                    final String dbN = catalog.getCurrentDatabase();
                    final String scN = catalog.getCurrentSchema();
                    if (dbN != null && scN != null) {
                        try {
                            final TableStorage ts =
                                storageEngine.getTableStorage(dbN + "." + scN + "." + name);
                            return ts != null ? (long) ts.getRowCount() : 0L;
                        } catch (final Exception ignored) {}
                    }
                }
                return 0L;
            }
            case "SYSTEM$STREAM_GET_TABLE_TIMESTAMP":
                return StatementClock.now();
            case "SYSTEM$CURRENT_USER_TASK_NAME": {
                final Task current = TaskScheduler.currentTask();
                return current != null ? current.getName() : null;
            }
            case "SYSTEM$GET_PREDECESSOR_RETURN_VALUE": {
                // The task-graph value SET by this task's (first) predecessor in its latest run.
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
            case "SYSTEM$GET_TASK_GRAPH_CONFIG":
                return "{}";
            case "SYSTEM$PIPE_STATUS": {
                final String pipeName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                // Reflect the pipe's actual state (RUNNING/PAUSED); getPipe throws on an unknown pipe, matching Snowflake.
                if (catalog != null && !pipeName.isEmpty()) {
                    return catalog.getPipe(pipeName).getStatusJson();
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
            case "SYSTEM$GET_TAG": {
                if (args.size() >= 3 && args.get(0) != null && args.get(1) != null) {
                    final String tagName = args.get(0).toString().replaceAll("^'|'$", "");
                    // A string naming an object is an identifier reference: unquoted folds up, quoted keeps case.
                    final String objectName = SqlIdentifiers.canonicalText(
                        args.get(1).toString().replaceAll("^'|'$", ""));
                    final String domain = args.get(2) == null ? null : args.get(2).toString().replaceAll("^'|'$", "");
                    return catalog.getObjectTagValue(tagName, objectName, domain);
                }
                return null;
            }
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
        enabled.add(rootName.toUpperCase());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (final Task task : tasks) {
                if (enabled.contains(task.getName().toUpperCase())) {
                    continue;
                }
                for (final String predecessor : task.getPredecessors()) {
                    final int dot = predecessor.lastIndexOf('.');
                    final String bare = dot >= 0 ? predecessor.substring(dot + 1) : predecessor;
                    if (enabled.contains(bare.toUpperCase())) {
                        task.setState(TaskState.STARTED);
                        enabled.add(task.getName().toUpperCase());
                        changed = true;
                        break;
                    }
                }
            }
        }
    }
}
