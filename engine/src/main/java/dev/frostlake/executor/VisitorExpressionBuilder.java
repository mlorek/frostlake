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

import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.procedural.*;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.task.TaskScheduler;
import org.antlr.v4.runtime.misc.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Builds and evaluates SQL expressions on behalf of {@link SQLCommandVisitor}: the procedural
 * expression AST ({@code buildExpression}), constant / DDL-time evaluation ({@code evaluateExpression}),
 * and the {@code SYSTEM$} built-in family ({@code evaluateSystemFunc}). Extracted from the visitor;
 * identifier/text and literal helpers are reached through the {@code visitor} back-reference and
 * engine state through {@code queryExecutor}.
 */
public class VisitorExpressionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(VisitorExpressionBuilder.class);

    private final SQLCommandVisitor visitor;
    private final QueryExecutor queryExecutor;

    VisitorExpressionBuilder(final SQLCommandVisitor visitor, final QueryExecutor queryExecutor) {
        this.visitor = visitor;
        this.queryExecutor = queryExecutor;
    }

    /** Peel a booleanExpr to its underlying value expression (null if it is a boolean AND/OR/NOT). */
    private FrostlakeParser.ExpressionContext unwrapValue(final FrostlakeParser.BooleanExprContext be) {
        return be instanceof FrostlakeParser.ValueExprContext ? ((FrostlakeParser.ValueExprContext) be).expression() : null;
    }

    /** Boolean tier (OR/AND/NOT) over the value/predicate `expression` below. */
    public BaseExpression buildExpression(final FrostlakeParser.BooleanExprContext ctx) {
        if (ctx instanceof FrostlakeParser.NotExprContext) {
            return new UnaryExpression(UnaryOperator.NOT, buildExpression(((FrostlakeParser.NotExprContext) ctx).booleanExpr()));
        }
        if (ctx instanceof FrostlakeParser.AndExprContext) {
            FrostlakeParser.AndExprContext b = (FrostlakeParser.AndExprContext) ctx;
            return new BinaryExpression(buildExpression(b.booleanExpr(0)), BinaryOperator.AND, buildExpression(b.booleanExpr(1)));
        }
        if (ctx instanceof FrostlakeParser.OrExprContext) {
            FrostlakeParser.OrExprContext b = (FrostlakeParser.OrExprContext) ctx;
            return new BinaryExpression(buildExpression(b.booleanExpr(0)), BinaryOperator.OR, buildExpression(b.booleanExpr(1)));
        }
        return buildExpression(((FrostlakeParser.ValueExprContext) ctx).expression());
    }

    /**
     * Build an Expression from ANTLR expression context
     */
    public BaseExpression buildExpression(final FrostlakeParser.ExpressionContext ctx) {
        if (ctx instanceof FrostlakeParser.LiteralExprContext) {
            Object value = visitor.parseLiteral(((FrostlakeParser.LiteralExprContext) ctx).literal());
            return new LiteralExpression(value);
        }

        if (ctx instanceof FrostlakeParser.QualifiedNameExprContext) {
            String name = visitor.getText(((FrostlakeParser.QualifiedNameExprContext) ctx).qualifiedName());
            return new VariableExpression(name);
        }

        if (ctx instanceof FrostlakeParser.CurrentTimestampExprContext) { return new FunctionCallExpression("CURRENT_TIMESTAMP", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentDateExprContext) { return new FunctionCallExpression("CURRENT_DATE", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentTimeExprContext) { return new FunctionCallExpression("CURRENT_TIME", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentUserExprContext) { return new FunctionCallExpression("CURRENT_USER", Collections.emptyList()); }

        if (ctx instanceof FrostlakeParser.BindVarExprContext) {
            // :varname — procedural variable binding, treat as variable reference
            String varName = visitor.getText(((FrostlakeParser.BindVarExprContext) ctx).identifier());
            return new VariableExpression(varName);
        }

        if (ctx instanceof FrostlakeParser.SessionVarExprContext) {
            // $varname — treat as a session variable reference
            String token = ((FrostlakeParser.SessionVarExprContext) ctx).SESSION_VAR_REF().getText();
            String varName = token.substring(1); // strip leading $
            return new SessionVarRefExpression(varName);
        }

        if (ctx instanceof FrostlakeParser.FunctionCallStarExprContext) {
            FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) ctx;
            String funcName = funcCtx.functionName().getText();
            // For COUNT(*) and similar, use special marker
            List<BaseExpression> args = new ArrayList<>();
            args.add(new LiteralExpression("*"));
            return new FunctionCallExpression(funcName, args);
        }

        if (ctx instanceof FrostlakeParser.FunctionCallExprContext) {
            FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) ctx;
            String funcName = funcCtx.functionName().getText();
            List<BaseExpression> args = new ArrayList<>();

            if (funcCtx.functionArgList() != null) {
                for (final FrostlakeParser.BooleanExprContext argCtx : ParseTreeText.functionBooleanArgs(funcCtx.functionArgList())) {
                    args.add(buildExpression(argCtx));
                }
            }

            return new FunctionCallExpression(funcName, args);
        }

        if (ctx instanceof FrostlakeParser.ExecuteImmediateExprContext) {
            FrostlakeParser.ExecuteImmediateExprContext execCtx = (FrostlakeParser.ExecuteImmediateExprContext) ctx;
            BaseExpression sqlExpr = buildExpression(execCtx.expression());
            List<BaseExpression> binds = new ArrayList<>();
            if (execCtx.expressionList() != null) {
                for (final FrostlakeParser.ExpressionContext bindCtx : execCtx.expressionList().expression()) {
                    binds.add(buildExpression(bindCtx));
                }
            }
            return new ExecuteImmediateExpression(sqlExpr, binds);
        }

        if (ctx instanceof FrostlakeParser.ConcatExprContext) {
            FrostlakeParser.ConcatExprContext concatCtx = (FrostlakeParser.ConcatExprContext) ctx;
            BaseExpression left = buildExpression(concatCtx.expression(0));
            BaseExpression right = buildExpression(concatCtx.expression(1));
            return new BinaryExpression(left, BinaryOperator.CONCAT, right);
        }

        if (ctx instanceof FrostlakeParser.MultiplicativeExprContext) {
            FrostlakeParser.MultiplicativeExprContext binCtx = (FrostlakeParser.MultiplicativeExprContext) ctx;
            BaseExpression left = buildExpression(binCtx.expression(0));
            BaseExpression right = buildExpression(binCtx.expression(1));
            String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.AdditiveExprContext) {
            FrostlakeParser.AdditiveExprContext binCtx = (FrostlakeParser.AdditiveExprContext) ctx;
            BaseExpression left = buildExpression(binCtx.expression(0));
            BaseExpression right = buildExpression(binCtx.expression(1));
            String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.ComparisonExprContext) {
            FrostlakeParser.ComparisonExprContext binCtx = (FrostlakeParser.ComparisonExprContext) ctx;
            BaseExpression left = buildExpression(binCtx.expression(0));
            BaseExpression right = buildExpression(binCtx.expression(1));
            String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.ParenExprContext) {
            return buildExpression(((FrostlakeParser.ParenExprContext) ctx).booleanExpr());
        }

        if (ctx instanceof FrostlakeParser.UnaryExprContext) {
            FrostlakeParser.UnaryExprContext unaryCtx = (FrostlakeParser.UnaryExprContext) ctx;
            BaseExpression operand = buildExpression(unaryCtx.expression());
            // Grammar: op is PLUS or MINUS. Unary plus is the identity, so return the operand directly and
            // reserve UnaryExpression for the operators that map to a UnaryOperator constant.
            if ("+".equals(unaryCtx.op.getText())) {
                return operand;
            }
            return new UnaryExpression(UnaryOperator.NEGATE, operand);
        }

        if (ctx instanceof FrostlakeParser.ExistsExprContext) {
            FrostlakeParser.ExistsExprContext existsCtx = (FrostlakeParser.ExistsExprContext) ctx;
            // Get original text with whitespace preserved from token stream
            FrostlakeParser.SelectStatementContext selectCtx = existsCtx.selectStatement();
            String subquery = selectCtx.start.getInputStream().getText(
                new Interval(
                    selectCtx.start.getStartIndex(),
                    selectCtx.stop.getStopIndex()
                )
            );
            SubqueryExpression subqueryExpr = new SubqueryExpression(subquery);
            return new UnaryExpression(UnaryOperator.EXISTS, subqueryExpr);
        }

        // Fallback: a construct the procedural builder doesn't model with a dedicated node (CAST / CASE /
        // IN / BETWEEN / LIKE / …). Parse its original text into the query-side AST and wrap it as a
        // SqlScalarExpression, so ProceduralExecutor evaluates it through the shared ExpressionEvaluator
        // with the current procedural variables supplied as a resolution context. The previous fallback
        // ran a standalone "SELECT <text>", which could not see scripting variables — so e.g. w::VARCHAR
        // or CASE … w … END over a script variable silently resolved to NULL.
        final String exprSql = visitor.getOriginalText(ctx);
        return new SqlScalarExpression(ExpressionEvaluator.parse(exprSql));
    }

    /**
     * Simplified expression evaluation for immediate values (like DEFAULT)
     */
    public Object evaluateExpression(final FrostlakeParser.ExpressionContext ctx) {
        if (ctx instanceof FrostlakeParser.LiteralExprContext) {
            return visitor.parseLiteral(((FrostlakeParser.LiteralExprContext) ctx).literal());
        }
        if (ctx instanceof FrostlakeParser.SystemUserTaskCancelExprContext) {
            FrostlakeParser.SystemUserTaskCancelExprContext sctx = (FrostlakeParser.SystemUserTaskCancelExprContext) ctx;
            Object nameVal = evaluateExpression(sctx.expression());
            String taskName = nameVal != null ? nameVal.toString().toUpperCase().replaceAll("^'|'$", "") : "";
            try {
                Catalog cat = queryExecutor.getCatalog();
                String dbN = cat.getCurrentDatabase(), scN = cat.getCurrentSchema();
                if (dbN == null || scN == null) return "Task not found";
                Task task = cat.getDatabase(dbN).getSchema(scN).getTask(taskName);
                if (task == null) return "Task not found: " + taskName;
                task.setState(TaskState.SUSPENDED);
                return "Task " + taskName + ": cancelled";
            } catch (final Exception e) {
                return "Error: " + e.getMessage();
            }
        }
        if (ctx instanceof FrostlakeParser.SystemStreamHasDataExprContext) {
            FrostlakeParser.SystemStreamHasDataExprContext sshd = (FrostlakeParser.SystemStreamHasDataExprContext) ctx;
            Object nameVal = evaluateExpression(sshd.expression());
            String streamName = nameVal != null ? nameVal.toString().toUpperCase().replaceAll("^'|'$", "") : "";
            try {
                Catalog cat = queryExecutor.getCatalog();
                String dbN = cat.getCurrentDatabase(), scN = cat.getCurrentSchema();
                if (dbN == null || scN == null) return false;
                Stream stream = cat.getDatabase(dbN).getSchema(scN).getStream(streamName);
                if (stream == null) return false;
                // Pending changes live as the stream's unconsumed NET records (consolidated deltas),
                // not in any table storage — the old storage probe always answered false.
                return !stream.getUnconsumedNetRecords().isEmpty();
            } catch (final Exception e) {
                return false;
            }
        }
        if (ctx instanceof FrostlakeParser.SystemFuncExprContext) {
            return evaluateSystemFunc((FrostlakeParser.SystemFuncExprContext) ctx);
        }
        if (ctx instanceof FrostlakeParser.ConcatExprContext) {
            FrostlakeParser.ConcatExprContext cc = (FrostlakeParser.ConcatExprContext) ctx;
            Object left = evaluateExpression(cc.expression(0));
            Object right = evaluateExpression(cc.expression(1));
            if (left == null || right == null) return null;
            return left.toString() + right.toString();
        }
        if (ctx instanceof FrostlakeParser.CurrentTimestampExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_TIMESTAMP").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentDateExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_DATE").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentTimeExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_TIME").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentUserExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_USER").evaluate(Collections.emptyList()); }

        if (ctx instanceof FrostlakeParser.BindVarExprContext) {
            // :varname — procedural variable binding, treat as variable reference
            String varName = visitor.getText(((FrostlakeParser.BindVarExprContext) ctx).identifier());
            if (visitor.getProceduralExecutor() != null) {
                Object v = visitor.getProceduralExecutor().getVariable(varName);
                if (v != null) return v;
            }
            return null;
        }
        if (ctx instanceof FrostlakeParser.SessionVarExprContext) {
            String token = ((FrostlakeParser.SessionVarExprContext) ctx).SESSION_VAR_REF().getText();
            String name = token.substring(1).toUpperCase(); // strip leading $
            SecurityManager sm = queryExecutor.getSecurityManager();
            return sm != null ? sm.getSessionContext().getSessionParameter(name)
                              : queryExecutor.getSessionVariables().get(name);
        }
        if (ctx instanceof FrostlakeParser.QualifiedNameExprContext) {
            // This might be a variable reference
            String varName = ((FrostlakeParser.QualifiedNameExprContext) ctx).qualifiedName().getText();
            if (visitor.getProceduralExecutor() != null) {
                Object varValue = visitor.getProceduralExecutor().getVariable(varName);
                if (varValue != null) {
                    return varValue;
                }
            }
            // Fall back to session variables
            SecurityManager sm = queryExecutor.getSecurityManager();
            Object sessionVal = sm != null
                ? sm.getSessionContext().getSessionParameter(varName)
                : queryExecutor.getSessionVariables().get(varName.toUpperCase());
            if (sessionVal != null) {
                return sessionVal;
            }
        }
        if (ctx instanceof FrostlakeParser.ParenExprContext) {
            // Recursively evaluate the expression inside parentheses
            return evaluateExpression(unwrapValue(((FrostlakeParser.ParenExprContext) ctx).booleanExpr()));
        }
        if (ctx instanceof FrostlakeParser.UnaryExprContext) {
            // Handle unary expressions like -20002
            FrostlakeParser.UnaryExprContext unaryCtx = (FrostlakeParser.UnaryExprContext) ctx;
            Object operand = evaluateExpression(unaryCtx.expression());
            String operator = unaryCtx.op.getText();

            if (operator.equals("-") && operand instanceof Number) {
                if (operand instanceof Long) {
                    return -((Long) operand);
                } else if (operand instanceof Integer) {
                    return -((Integer) operand);
                } else if (operand instanceof Double) {
                    return -((Double) operand);
                }
            } else if (operator.equals("+") && operand instanceof Number) {
                return operand;
            }
            // For other cases, fall through
        }
        if (ctx instanceof FrostlakeParser.ExecuteImmediateExprContext) {
            // Build the expression and evaluate it via ProceduralExecutor
            BaseExpression expr = buildExpression(ctx);
            if (visitor.getProceduralExecutor() != null) {
                return visitor.getProceduralExecutor().evaluateExpression(expr);
            }
        }
        // For any other construct (CAST / CASE / a variant path o:a:b / array access / IN / BETWEEN / …):
        // when a procedural context is active, build it into the procedural AST and evaluate through the
        // ProceduralExecutor, which supplies the current scripting variables as a resolution context (the
        // same path buildExpression's own fallback uses). A bare "SELECT <text>" cannot see procedural
        // variables, so e.g. a DECLARE initializer that is a variant path over a script variable
        // (v := o:result:code) silently returned its own literal text "o:result:code".
        if (visitor.getProceduralExecutor() != null) {
            return visitor.getProceduralExecutor().evaluateExpression(buildExpression(ctx));
        }
        // No procedural context (e.g. DDL-time constant evaluation): evaluate via SELECT <expr> at runtime.
        String exprText = visitor.getOriginalText(ctx);
        if (exprText != null && !exprText.isBlank() && queryExecutor != null) {
            try {
                List<ResultSet> results =
                    queryExecutor.execute("SELECT " + exprText);
                if (!results.isEmpty() && results.get(0).getRowCount() > 0) {
                    return results.get(0).getRows().get(0).getValue(0);
                }
                return null;
            } catch (final Exception ignored) {}
        }
        return ctx.getText();
    }

    /**
     * Evaluate a SYSTEM$FUNCNAME(...) expression.
     * Handles general SYSTEM$ functions not covered by dedicated grammar rules.
     */
    private Object evaluateSystemFunc(final FrostlakeParser.SystemFuncExprContext ctx) {
        String rawName = ctx.SYSTEM_FUNC().getText().toUpperCase(); // e.g. SYSTEM$WAIT
        List<Object> args = new ArrayList<>();
        if (ctx.expressionList() != null) {
            for (final FrostlakeParser.ExpressionContext argCtx : ctx.expressionList().expression()) {
                args.add(evaluateExpression(argCtx));
            }
        }

        switch (rawName) {
            // ── Task / scheduling ──────────────────────────────────────────────
            case "SYSTEM$CURRENT_USER_TASK_NAME": {
                final Task currentTask = TaskScheduler.currentTask();
                return currentTask != null ? currentTask.getName() : null;
            }

            case "SYSTEM$SET_RETURN_VALUE": {
                // Store the return value on the currently executing task (readable by successors).
                final Task currentTask = TaskScheduler.currentTask();
                final Object value = args.isEmpty() ? null : args.get(0);
                if (currentTask != null) {
                    currentTask.setLastReturnValue(
                        value != null ? value.toString().replaceAll("^'|'$", "") : null);
                }
                return value;
            }

            case "SYSTEM$GET_PREDECESSOR_RETURN_VALUE": {
                final Task currentTask = TaskScheduler.currentTask();
                if (currentTask != null && !currentTask.getPredecessors().isEmpty()) {
                    String predName = currentTask.getPredecessors().get(0);
                    final int dot = predName.lastIndexOf('.');
                    if (dot >= 0) {
                        predName = predName.substring(dot + 1);
                    }
                    try {
                        final Catalog cat = queryExecutor.getCatalog();
                        final Task predecessor = cat.getDatabase(cat.getCurrentDatabase())
                            .getSchema(cat.getCurrentSchema()).getTask(predName);
                        return predecessor != null ? predecessor.getLastReturnValue() : null;
                    } catch (final Exception ignored) {
                        return null;
                    }
                }
                return null;
            }

            case "SYSTEM$TASK_DEPENDENTS_ENABLE": {
                // Resume the named root's transitive dependents (only that subtree).
                if (!args.isEmpty() && args.get(0) != null) {
                    try {
                        Catalog cat = queryExecutor.getCatalog();
                        String dbN = cat.getCurrentDatabase(), scN = cat.getCurrentSchema();
                        if (dbN != null && scN != null) {
                            SystemFunctionEvaluator.enableTaskDependents(
                                args.get(0).toString().replaceAll("^'|'$", ""),
                                cat.getDatabase(dbN).getSchema(scN).getTasks());
                        }
                    } catch (final Exception ignored) {}
                }
                return "Statement executed successfully.";
            }

            case "SYSTEM$TASK_RUNTIME_INFO":
                return "{}";

            case "SYSTEM$GET_TASK_GRAPH_CONFIG":
                return "{}";

            // ── Stream ─────────────────────────────────────────────────────────
            case "SYSTEM$STREAM_BACKLOG": {
                if (!args.isEmpty()) {
                    String streamName = args.get(0) != null ? args.get(0).toString().replaceAll("^'|'$", "").toUpperCase() : "";
                    try {
                        Catalog cat = queryExecutor.getCatalog();
                        String dbN = cat.getCurrentDatabase(), scN = cat.getCurrentSchema();
                        if (dbN != null && scN != null) {
                            String fq = dbN + "." + scN + "." + streamName;
                            StorageEngine.TableStorage ts =
                                queryExecutor.getStorageEngine().getTableStorage(fq);
                            return ts != null ? (long) ts.getRowCount() : 0L;
                        }
                    } catch (final Exception ignored) {}
                }
                return 0L;
            }

            case "SYSTEM$STREAM_GET_TABLE_TIMESTAMP": {
                return LocalDateTime.now().toString();
            }

            // ── Session / transaction ──────────────────────────────────────────
            case "SYSTEM$ABORT_SESSION":
                return "Session aborted.";

            case "SYSTEM$ABORT_TRANSACTION":
                try { queryExecutor.getTransactionManager().rollback(); } catch (final Exception ignored) {}
                return "Transaction aborted.";

            case "SYSTEM$CANCEL_QUERY":
            case "SYSTEM$CANCEL_ALL_QUERIES":
                return "Query cancelled.";

            // ── Clustering ─────────────────────────────────────────────────────
            case "SYSTEM$CLUSTERING_DEPTH": {
                String tblName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"average_depth\": 1.0, \"table_name\": \"" + tblName + "\"}";
            }
            case "SYSTEM$CLUSTERING_INFORMATION": {
                String tblName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"clustering_key\": null, \"total_partition_count\": 1, \"average_depth\": 1.0, \"table_name\": \"" + tblName + "\"}";
            }
            case "SYSTEM$CLUSTERING_RATIO":
                return 1.0;

            // ── Tags ───────────────────────────────────────────────────────────
            case "SYSTEM$GET_TAG": {
                if (args.size() >= 3 && args.get(0) != null && args.get(1) != null) {
                    String tagName = args.get(0).toString().replaceAll("^'|'$", "");
                    String objectName = args.get(1).toString().replaceAll("^'|'$", "");
                    String domain = args.get(2) == null ? null : args.get(2).toString().replaceAll("^'|'$", "");
                    return queryExecutor.getCatalog().getObjectTagValue(tagName, objectName, domain);
                }
                return null;
            }
            case "SYSTEM$GET_TAG_ON_CURRENT_COLUMN":
            case "SYSTEM$GET_TAG_ON_CURRENT_TABLE":
                return null;

            // ── Info / platform ────────────────────────────────────────────────
            case "SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO":
                return "{\"cloud\": \"aws\", \"region\": \"us-east-1\"}";

            case "SYSTEM$ALLOWLIST":
            case "SYSTEM$WHITELIST":
                return "[]";

            case "SYSTEM$LAST_CHANGE_COMMIT_TIME":
                return System.currentTimeMillis();

            case "SYSTEM$TYPEOF": {
                if (!args.isEmpty() && args.get(0) != null) {
                    Object v = args.get(0);
                    if (v instanceof Boolean) return "BOOLEAN[LOB]";
                    if (v instanceof Long || v instanceof Integer) return "INTEGER[LOB]";
                    if (v instanceof Double || v instanceof BigDecimal) return "FLOAT[LOB]";
                    if (v instanceof LocalDateTime || v instanceof LocalDate) return "TIMESTAMP_NTZ[LOB]";
                    String s = v.toString().trim();
                    if (s.startsWith("{")) return "OBJECT[LOB]";
                    if (s.startsWith("[")) return "ARRAY[LOB]";
                    return "VARCHAR[LOB]";
                }
                return "NULL[LOB]";
            }

            case "SYSTEM$GENERATE_SCIM_ACCESS_TOKEN":
                return "{\"token\": \"" + UUID.randomUUID() + "\", \"expires_at\": null}";

            case "SYSTEM$PIPE_STATUS": {
                String pipeName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                // Reflect the pipe's actual state (RUNNING/PAUSED); getPipe throws on an unknown pipe, matching Snowflake.
                if (queryExecutor != null && !pipeName.isEmpty()) {
                    return queryExecutor.getCatalog().getPipe(pipeName).getStatusJson();
                }
                return "{\"executionState\": \"RUNNING\", \"pendingFileCount\": 0, \"notificationChannelName\": null}";
            }

            case "SYSTEM$EXTERNAL_TABLE_PIPE_STATUS":
                return "{\"executionState\": \"RUNNING\"}";

            case "SYSTEM$AUTO_REFRESH_STATUS":
                return "{\"state\": \"Active\"}";

            case "SYSTEM$QUERY_REFERENCE":
                return UUID.randomUUID().toString();

            case "SYSTEM$REFERENCE":
                return args.isEmpty() ? null : args.get(0);

            // ── Logging ────────────────────────────────────────────────────────
            case "SYSTEM$LOG": {
                // SYSTEM$LOG(level, message) — emit to logger, return null
                if (args.size() >= 2) {
                    logger.info("SYSTEM$LOG [{}]: {}", args.get(0), args.get(1));
                } else if (!args.isEmpty()) {
                    logger.info("SYSTEM$LOG: {}", args.get(0));
                }
                return null;
            }

            // ── Wait ───────────────────────────────────────────────────────────
            case "SYSTEM$WAIT": {
                if (!args.isEmpty() && args.get(0) instanceof Number) {
                    long ms = (long)(((Number) args.get(0)).doubleValue() * 1000);
                    if (ms > 0 && ms <= 30_000) { // cap at 30s for safety
                        try { Thread.sleep(ms); } catch (final InterruptedException ignored) {}
                    }
                }
                return "waited";
            }

            case "SYSTEM$VALIDATE_STORAGE_INTEGRATION":
            case "SYSTEM$VERIFY_EXTERNAL_VOLUME":
            case "SYSTEM$VERIFY_CATALOG_INTEGRATION":
                return "{\"status\": \"OK\"}";

            default:
                throw new RuntimeException("Unsupported system function: " + rawName);
        }
    }
}
