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
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.BinaryExpression;
import dev.frostlake.executor.procedural.FunctionCallExpression;
import dev.frostlake.executor.procedural.LiteralExpression;
import dev.frostlake.executor.procedural.SessionVarRefExpression;
import dev.frostlake.executor.procedural.SqlScalarExpression;
import dev.frostlake.executor.procedural.SubqueryExpression;
import dev.frostlake.executor.procedural.UnaryExpression;
import dev.frostlake.executor.procedural.VariableExpression;
import dev.frostlake.functions.SystemFunctionNames;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.task.UserTaskCancellation;
import dev.frostlake.types.NumericLiteralTypes;
import org.antlr.v4.runtime.misc.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
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

    /**
     * A SYSTEM$ argument evaluated in place: a value expression as always, and NOT, AND and OR over such
     * values in three-valued logic (FALSE AND NULL is FALSE, TRUE OR NULL is TRUE, NOT NULL is NULL).
     */
    private Object evaluateBooleanArgument(final FrostlakeParser.BooleanExprContext ctx,
                                           final boolean scriptingNamesVisible) {
        final FrostlakeParser.ExpressionContext value = unwrapValue(ctx);
        if (value != null) {
            return evaluateExpression(value, scriptingNamesVisible);
        }
        if (ctx instanceof FrostlakeParser.NotExprContext) {
            final Boolean operand = truthOf(evaluateBooleanArgument(
                ((FrostlakeParser.NotExprContext) ctx).booleanExpr(), scriptingNamesVisible));
            return operand == null ? null : Boolean.valueOf(!operand.booleanValue());
        }
        final boolean conjunction = ctx instanceof FrostlakeParser.AndExprContext;
        final List<FrostlakeParser.BooleanExprContext> sides = conjunction
            ? ((FrostlakeParser.AndExprContext) ctx).booleanExpr()
            : ((FrostlakeParser.OrExprContext) ctx).booleanExpr();
        final Boolean left = truthOf(evaluateBooleanArgument(sides.get(0), scriptingNamesVisible));
        final Boolean right = truthOf(evaluateBooleanArgument(sides.get(1), scriptingNamesVisible));
        final Boolean decisive = conjunction ? Boolean.FALSE : Boolean.TRUE;
        if (decisive.equals(left) || decisive.equals(right)) {
            return decisive;
        }
        return left == null || right == null ? null : Boolean.valueOf(!decisive.booleanValue());
    }

    /** A boolean operand's truth: a BOOLEAN or NULL, and anything else is not a boolean. */
    private static Boolean truthOf(final Object value) {
        if (value == null || value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new RuntimeException("Boolean value '" + value + "' is not recognized");
    }

    /** Peel a booleanExpr to its underlying value expression (null if it is a boolean AND/OR/NOT). */
    private FrostlakeParser.ExpressionContext unwrapValue(final FrostlakeParser.BooleanExprContext be) {
        return be instanceof FrostlakeParser.ValueExprContext ? ((FrostlakeParser.ValueExprContext) be).expression() : null;
    }

    /** Boolean tier (OR/AND/NOT) over the value/predicate `expression` below. */
    public BaseExpression buildExpression(final FrostlakeParser.BooleanExprContext ctx) {
        final BaseExpression built = buildBooleanExpression(ctx);
        if (built != null && built.getSourceLine() < 0 && ctx != null) {
            built.setSourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return built;
    }

    private BaseExpression buildBooleanExpression(final FrostlakeParser.BooleanExprContext ctx) {
        if (ctx instanceof FrostlakeParser.NotExprContext) {
            return new UnaryExpression(UnaryOperator.NOT, buildExpression(((FrostlakeParser.NotExprContext) ctx).booleanExpr()));
        }
        if (ctx instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext b = (FrostlakeParser.AndExprContext) ctx;
            return new BinaryExpression(buildExpression(b.booleanExpr(0)), BinaryOperator.AND, buildExpression(b.booleanExpr(1)));
        }
        if (ctx instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext b = (FrostlakeParser.OrExprContext) ctx;
            return new BinaryExpression(buildExpression(b.booleanExpr(0)), BinaryOperator.OR, buildExpression(b.booleanExpr(1)));
        }
        return buildExpression(((FrostlakeParser.ValueExprContext) ctx).expression());
    }

    /**
     * Build an Expression from ANTLR expression context
     */
    public BaseExpression buildExpression(final FrostlakeParser.ExpressionContext ctx) {
        final BaseExpression built = buildValueExpression(ctx);
        if (built != null && built.getSourceLine() < 0 && ctx != null) {
            built.setSourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return built;
    }

    private BaseExpression buildValueExpression(final FrostlakeParser.ExpressionContext ctx) {
        if (ctx instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) ctx).literal();
            // A numeric literal is typed in a scripting expression exactly as it is in a query: an EXACT
            // number, its trailing zeros no part of the value and an exponent a way of writing a fixed-point
            // one — RETURN 1.0 is 1 and 1.10 is 1.1 (live-verified), 1.7777 + 1 is 2.7777 and not a double's
            // 2.7777000000000003 — and past the exact range the double that makes it legal at all.
            if (literal.FLOAT_LITERAL() != null) {
                final BigDecimal exact = NumericLiteralTypes.exactValue(literal.FLOAT_LITERAL().getText());
                return new LiteralExpression(NumericLiteralTypes.exceedsExactRange(exact)
                    ? Double.valueOf(exact.doubleValue()) : exact);
            }
            return new LiteralExpression(visitor.parseLiteral(literal));
        }

        if (ctx instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String name = visitor.getText(((FrostlakeParser.QualifiedNameExprContext) ctx).qualifiedName());
            return new VariableExpression(name);
        }

        if (ctx instanceof FrostlakeParser.CurrentTimestampExprContext) { return new FunctionCallExpression("CURRENT_TIMESTAMP", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentDateExprContext) { return new FunctionCallExpression("CURRENT_DATE", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentTimeExprContext) { return new FunctionCallExpression("CURRENT_TIME", Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentUserExprContext) { return new FunctionCallExpression("CURRENT_USER", Collections.emptyList()); }

        if (ctx instanceof FrostlakeParser.BindVarExprContext) {
            // :varname — procedural variable binding, treat as variable reference
            final String varName = visitor.getText(((FrostlakeParser.BindVarExprContext) ctx).identifier());
            return new VariableExpression(varName, true);
        }

        if (ctx instanceof FrostlakeParser.SessionVarExprContext) {
            // $varname — treat as a session variable reference
            final String token = ((FrostlakeParser.SessionVarExprContext) ctx).SESSION_VAR_REF().getText();
            final String varName = token.substring(1); // strip leading $
            return new SessionVarRefExpression(varName);
        }

        if (ctx instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) ctx;
            final String funcName = funcCtx.functionName().getText();
            // For COUNT(*) and similar, use special marker
            final List<BaseExpression> args = new ArrayList<>();
            args.add(new LiteralExpression("*"));
            return new FunctionCallExpression(funcName, args);
        }

        if (ctx instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) ctx;
            final String funcName = funcCtx.functionName().getText();
            final List<BaseExpression> args = new ArrayList<>();

            if (funcCtx.functionArgList() != null) {
                for (final FrostlakeParser.BooleanExprContext argCtx : ParseTreeText.functionBooleanArgs(funcCtx.functionArgList())) {
                    args.add(buildExpression(argCtx));
                }
            }

            return new FunctionCallExpression(funcName, args);
        }

        if (ctx instanceof FrostlakeParser.ConcatExprContext) {
            final FrostlakeParser.ConcatExprContext concatCtx = (FrostlakeParser.ConcatExprContext) ctx;
            final BaseExpression left = buildExpression(concatCtx.expression(0));
            final BaseExpression right = buildExpression(concatCtx.expression(1));
            return new BinaryExpression(left, BinaryOperator.CONCAT, right);
        }

        if (ctx instanceof FrostlakeParser.MultiplicativeExprContext) {
            final FrostlakeParser.MultiplicativeExprContext binCtx = (FrostlakeParser.MultiplicativeExprContext) ctx;
            final BaseExpression left = buildExpression(binCtx.expression(0));
            final BaseExpression right = buildExpression(binCtx.expression(1));
            final String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.AdditiveExprContext) {
            final FrostlakeParser.AdditiveExprContext binCtx = (FrostlakeParser.AdditiveExprContext) ctx;
            final BaseExpression left = buildExpression(binCtx.expression(0));
            final BaseExpression right = buildExpression(binCtx.expression(1));
            final String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext binCtx = (FrostlakeParser.ComparisonExprContext) ctx;
            final BaseExpression left = buildExpression(binCtx.expression(0));
            final BaseExpression right = buildExpression(binCtx.expression(1));
            final String operator = binCtx.op.getText();
            return new BinaryExpression(left, BinaryOperator.fromSymbol(operator), right);
        }

        if (ctx instanceof FrostlakeParser.ParenExprContext) {
            return buildExpression(((FrostlakeParser.ParenExprContext) ctx).booleanExpr());
        }

        if (ctx instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext unaryCtx = (FrostlakeParser.UnaryExprContext) ctx;
            final BaseExpression operand = buildExpression(unaryCtx.expression());
            // Grammar: op is PLUS or MINUS. Unary plus is the identity, so return the operand directly and
            // reserve UnaryExpression for the operators that map to a UnaryOperator constant.
            if ("+".equals(unaryCtx.op.getText())) {
                return operand;
            }
            return new UnaryExpression(UnaryOperator.NEGATE, operand);
        }

        if (ctx instanceof FrostlakeParser.ExistsExprContext) {
            final FrostlakeParser.ExistsExprContext existsCtx = (FrostlakeParser.ExistsExprContext) ctx;
            // Get original text with whitespace preserved from token stream
            final FrostlakeParser.SelectStatementContext selectCtx = existsCtx.selectStatement();
            final String subquery = selectCtx.start.getInputStream().getText(
                new Interval(
                    selectCtx.start.getStartIndex(),
                    selectCtx.stop.getStopIndex()
                )
            );
            final SubqueryExpression subqueryExpr = new SubqueryExpression(subquery);
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
     * Simplified expression evaluation for immediate values (like DEFAULT), in a Snowflake Scripting
     * expression context — a bare name may be a scripting variable here (a {@code LET} / {@code :=}
     * right-hand side, an {@code IF} condition, {@code RETURN <expr>}, a {@code DECLARE … DEFAULT}).
     */
    public Object evaluateExpression(final FrostlakeParser.ExpressionContext ctx) {
        try {
            return evaluateExpression(ctx, true);
        } catch (final RuntimeException failed) {
            // Note WHERE the expression stood as the failure leaves it, so an uncaught wrapper can
            // name the EXPRESSION_ERROR kind at the expression's own offset — live's split between a
            // fault raised evaluating an expression and one raised running a statement. Recorded only
            // inside a block, where a wrapper exists to read it; the record is first-wins, so this
            // (innermost) one beats the statement record taken as the failure unwinds.
            final ProceduralExecutor procedural = visitor.getProceduralExecutor();
            if (procedural != null && procedural.getBlockDepth() > 0 && ctx != null) {
                procedural.recordExpressionFailure(ctx.getStart().getLine(),
                    ctx.getStart().getCharPositionInLine());
            }
            throw failed;
        }
    }

    /**
     * Evaluate an expression that sits in an <b>embedded SQL statement</b> rather than in a scripting
     * expression (a CALL argument — Snowflake dispatches CALL as SQL — or a session {@code SET} value).
     * A bare name is an identifier there, never a stored-procedure parameter / {@code DECLARE}d /
     * {@code LET} variable: Snowflake answers {@code invalid identifier '<NAME>'} and requires the
     * {@code :name} bind form. Session variables stay visible, as they are in Snowflake SQL.
     */
    public Object evaluateSqlExpression(final FrostlakeParser.ExpressionContext ctx) {
        return evaluateExpression(ctx, false);
    }

    private Object evaluateExpression(final FrostlakeParser.ExpressionContext ctx,
                                      final boolean scriptingNamesVisible) {
        if (ctx instanceof FrostlakeParser.LiteralExprContext) {
            return visitor.parseLiteral(((FrostlakeParser.LiteralExprContext) ctx).literal());
        }
        if (ctx instanceof FrostlakeParser.SystemUserTaskCancelExprContext) {
            final FrostlakeParser.SystemUserTaskCancelExprContext sctx = (FrostlakeParser.SystemUserTaskCancelExprContext) ctx;
            final Object nameVal = evaluateExpression(sctx.expression(), scriptingNamesVisible);
            return UserTaskCancellation.cancel(queryExecutor.getCatalog(),
                queryExecutor.getTaskScheduler(), nameVal == null ? null : nameVal.toString());
        }
        if (ctx instanceof FrostlakeParser.SystemStreamHasDataExprContext) {
            final FrostlakeParser.SystemStreamHasDataExprContext sshd = (FrostlakeParser.SystemStreamHasDataExprContext) ctx;
            if (sshd.expressionList() == null || sshd.expressionList().expression().size() != 1) {
                final int given = sshd.expressionList() == null ? 0
                    : sshd.expressionList().expression().size();
                throw new RuntimeException(SqlCompilationError.at(sshd.getStart().getLine(),
                    sshd.getStart().getCharPositionInLine(), (given < 1
                        ? "not enough arguments for function [" + ParseTreeText.getOriginalText(sshd)
                            + "], expected 1, got " + given
                        : "too many arguments for function [" + ParseTreeText.getOriginalText(sshd)
                            + "] expected 1, got " + given)));
            }
            final Object nameVal = evaluateExpression(sshd.expressionList().expression(0),
                scriptingNamesVisible);
            final String streamName = nameVal != null ? nameVal.toString().toUpperCase().replaceAll("^'|'$", "") : "";
            try {
                final Catalog cat = queryExecutor.getCatalog();
                final String dbN = cat.getCurrentDatabase();
                final String scN = cat.getCurrentSchema();
                if (dbN == null || scN == null) return false;
                final Stream stream = cat.getDatabase(dbN).getSchema(scN).getStream(streamName);
                if (stream == null) return false;
                // Pending changes live as the stream's unconsumed NET records (consolidated deltas),
                // not in any table storage — the old storage probe always answered false.
                return !stream.getUnconsumedNetRecords().isEmpty();
            } catch (final Exception e) {
                return false;
            }
        }
        if (ctx instanceof FrostlakeParser.SystemFuncExprContext) {
            return evaluateSystemFunc((FrostlakeParser.SystemFuncExprContext) ctx, scriptingNamesVisible);
        }
        if (ctx instanceof FrostlakeParser.ConcatExprContext) {
            final FrostlakeParser.ConcatExprContext cc = (FrostlakeParser.ConcatExprContext) ctx;
            final Object left = evaluateExpression(cc.expression(0), scriptingNamesVisible);
            final Object right = evaluateExpression(cc.expression(1), scriptingNamesVisible);
            if (left == null || right == null) return null;
            return left.toString() + right.toString();
        }
        if (ctx instanceof FrostlakeParser.CurrentTimestampExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_TIMESTAMP").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentDateExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_DATE").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentTimeExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_TIME").evaluate(Collections.emptyList()); }
        if (ctx instanceof FrostlakeParser.CurrentUserExprContext) { return queryExecutor.getFunctionRegistry().getFunction("CURRENT_USER").evaluate(Collections.emptyList()); }

        if (ctx instanceof FrostlakeParser.BindVarExprContext) {
            // :varname — procedural variable binding, treat as variable reference
            final String varName = visitor.getText(((FrostlakeParser.BindVarExprContext) ctx).identifier());
            if (visitor.getProceduralExecutor() != null) {
                final Object v = visitor.getProceduralExecutor().getVariable(varName);
                if (v != null) return v;
            }
            return null;
        }
        if (ctx instanceof FrostlakeParser.SessionVarExprContext) {
            final String token = ((FrostlakeParser.SessionVarExprContext) ctx).SESSION_VAR_REF().getText();
            final String name = token.substring(1).toUpperCase(); // strip leading $
            final SecurityManager sm = queryExecutor.getSecurityManager();
            return sm != null ? sm.getSessionContext().getSessionVariable(name)
                              : queryExecutor.getSessionVariables().get(name);
        }
        if (ctx instanceof FrostlakeParser.QualifiedNameExprContext) {
            // This might be a variable reference
            final String varName = ((FrostlakeParser.QualifiedNameExprContext) ctx).qualifiedName().getText();
            if (scriptingNamesVisible && visitor.getProceduralExecutor() != null) {
                final Object varValue = visitor.getProceduralExecutor().getVariable(varName);
                if (varValue != null) {
                    return varValue;
                }
            }
            // Fall back to session variables — those ARE referenceable from SQL in Snowflake.
            final SecurityManager sm = queryExecutor.getSecurityManager();
            final Object sessionVal = sm != null
                ? sm.getSessionContext().getSessionVariable(varName)
                : queryExecutor.getSessionVariables().get(varName.toUpperCase());
            if (sessionVal != null) {
                return sessionVal;
            }
            if (!scriptingNamesVisible) {
                throw new RuntimeException("invalid identifier '" + varName.toUpperCase() + "'");
            }
        }
        if (ctx instanceof FrostlakeParser.ParenExprContext) {
            // Recursively evaluate the expression inside parentheses
            return evaluateExpression(
                unwrapValue(((FrostlakeParser.ParenExprContext) ctx).booleanExpr()), scriptingNamesVisible);
        }
        if (ctx instanceof FrostlakeParser.UnaryExprContext) {
            // Handle unary expressions like -20002
            final FrostlakeParser.UnaryExprContext unaryCtx = (FrostlakeParser.UnaryExprContext) ctx;
            final Object operand = evaluateExpression(unaryCtx.expression(), scriptingNamesVisible);
            final String operator = unaryCtx.op.getText();

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
        // For any other construct (CAST / CASE / a variant path o:a:b / array access / IN / BETWEEN / …):
        // in a SCRIPTING expression, build it into the procedural AST and evaluate through the
        // ProceduralExecutor, which supplies the current scripting variables as a resolution context (the
        // same path buildExpression's own fallback uses). A bare "SELECT <text>" cannot see procedural
        // variables, so e.g. a DECLARE initializer that is a variant path over a script variable
        // (v := o:result:code) silently returned its own literal text "o:result:code".
        if (scriptingNamesVisible && visitor.getProceduralExecutor() != null) {
            return visitor.getProceduralExecutor().evaluateExpression(buildExpression(ctx));
        }
        // A SQL context (or DDL-time constant evaluation): evaluate via SELECT <expr> at runtime, with no
        // scripting names in scope — a bare one surfaces Snowflake's identifier error from the query layer.
        final String exprText = visitor.getOriginalText(ctx);
        if (exprText != null && !exprText.isBlank() && queryExecutor != null) {
            if (!scriptingNamesVisible) {
                // In a SQL context the failure IS the answer, so it propagates rather than degrading to
                // the expression's own text (which would silently pass "v" along as a string).
                return firstCellOfSelect(exprText);
            }
            try {
                return firstCellOfSelect(exprText);
            } catch (final Exception ignored) {
                // best-effort in a scripting context: fall through to the raw text
            }
        }
        return ctx.getText();
    }

    /** The first cell of {@code SELECT <exprText>}, or null when the query yields no row. */
    private Object firstCellOfSelect(final String exprText) {
        final List<ResultSet> results = queryExecutor.execute("SELECT " + exprText);
        return !results.isEmpty() && results.get(0).getRowCount() > 0
            ? results.get(0).getRows().get(0).getValue(0) : null;
    }

    /**
     * Evaluate a SYSTEM$FUNCNAME(...) expression.
     * Handles general SYSTEM$ functions not covered by dedicated grammar rules.
     */
    private Object evaluateSystemFunc(final FrostlakeParser.SystemFuncExprContext ctx,
                                      final boolean scriptingNamesVisible) {
        final String rawName = ctx.SYSTEM_FUNC().getText().toUpperCase(); // e.g. SYSTEM$WAIT
        // Same guard as SystemFunctionEvaluator: SystemFunctionNames is the single declaration of the
        // SYSTEM$ family, shared with SHOW FUNCTIONS via FunctionRegistry.allDispatchableNames(). This
        // parse-tree path and the expression-AST path both consult it, so neither switch can grow a name
        // the listing does not know about.
        if (!SystemFunctionNames.contains(rawName)) {
            throw new RuntimeException("Unsupported system function: " + rawName);
        }
        final List<Object> args = new ArrayList<>();
        if (ctx.booleanExprList() != null) {
            for (final FrostlakeParser.BooleanExprContext argCtx : ctx.booleanExprList().booleanExpr()) {
                args.add(evaluateBooleanArgument(argCtx, scriptingNamesVisible));
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
                        final Catalog cat = queryExecutor.getCatalog();
                        final String dbN = cat.getCurrentDatabase();
                        final String scN = cat.getCurrentSchema();
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
                    final String streamName = args.get(0) != null ? args.get(0).toString().replaceAll("^'|'$", "").toUpperCase() : "";
                    try {
                        final Catalog cat = queryExecutor.getCatalog();
                        final String dbN = cat.getCurrentDatabase();
                        final String scN = cat.getCurrentSchema();
                        if (dbN != null && scN != null) {
                            final String fq = dbN + "." + scN + "." + streamName;
                            final TableStorage ts =
                                queryExecutor.getStorageEngine().getTableStorage(fq);
                            return ts != null ? (long) ts.getRowCount() : 0L;
                        }
                    } catch (final Exception ignored) {}
                }
                return 0L;
            }

            case "SYSTEM$STREAM_GET_TABLE_TIMESTAMP": {
                return StatementClock.now();
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
                final String tblName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"average_depth\": 1.0, \"table_name\": \"" + tblName + "\"}";
            }
            case "SYSTEM$CLUSTERING_INFORMATION": {
                final String tblName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
                return "{\"clustering_key\": null, \"total_partition_count\": 1, \"average_depth\": 1.0, \"table_name\": \"" + tblName + "\"}";
            }
            case "SYSTEM$CLUSTERING_RATIO":
                return 1.0;

            // ── Tags ───────────────────────────────────────────────────────────
            case "SYSTEM$GET_TAG": {
                if (args.size() >= 3 && args.get(0) != null && args.get(1) != null) {
                    final String tagName = args.get(0).toString().replaceAll("^'|'$", "");
                    final String objectName = args.get(1).toString().replaceAll("^'|'$", "");
                    final String domain = args.get(2) == null ? null : args.get(2).toString().replaceAll("^'|'$", "");
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
                    final Object v = args.get(0);
                    if (v instanceof Boolean) return "BOOLEAN[LOB]";
                    if (v instanceof Long || v instanceof Integer) return "INTEGER[LOB]";
                    if (v instanceof Double || v instanceof BigDecimal) return "FLOAT[LOB]";
                    if (v instanceof LocalDateTime || v instanceof LocalDate) return "TIMESTAMP_NTZ[LOB]";
                    // The two zoned types are their own answers rather than falling through to the
                    // text branch below, which read them as VARCHAR because their toString starts with
                    // a digit. The width and the storage tag this still gets wrong are a separate job.
                    if (v instanceof ZonedDateTime) return "TIMESTAMP_TZ[LOB]";
                    if (v instanceof OffsetDateTime) return "TIMESTAMP_LTZ[LOB]";
                    final String s = v.toString().trim();
                    if (s.startsWith("{")) return "OBJECT[LOB]";
                    if (s.startsWith("[")) return "ARRAY[LOB]";
                    return "VARCHAR[LOB]";
                }
                return "NULL[LOB]";
            }

            case "SYSTEM$GENERATE_SCIM_ACCESS_TOKEN":
                return "{\"token\": \"" + UUID.randomUUID() + "\", \"expires_at\": null}";

            case "SYSTEM$PIPE_STATUS": {
                final String pipeName = args.isEmpty() || args.get(0) == null ? "" : args.get(0).toString().replaceAll("^'|'$", "");
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
                    final long ms = (long)(((Number) args.get(0)).doubleValue() * 1000);
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
