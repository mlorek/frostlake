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

import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.CallStatement;
import dev.frostlake.executor.procedural.CaseStatement;
import dev.frostlake.executor.procedural.ExecuteImmediateExpression;
import dev.frostlake.executor.procedural.ForStatement;
import dev.frostlake.executor.procedural.IfCondition;
import dev.frostlake.executor.procedural.IfStatement;
import dev.frostlake.executor.procedural.LiteralExpression;
import dev.frostlake.executor.procedural.LoopStatement;
import dev.frostlake.executor.procedural.ProceduralBlock;
import dev.frostlake.executor.procedural.RaiseStatement;
import dev.frostlake.executor.procedural.RepeatStatement;
import dev.frostlake.executor.procedural.ReturnStatement;
import dev.frostlake.executor.procedural.ReturnTableStatement;
import dev.frostlake.executor.procedural.SetStatement;
import dev.frostlake.executor.procedural.SqlStatement;
import dev.frostlake.executor.procedural.Statement;
import dev.frostlake.executor.procedural.StatementType;
import dev.frostlake.executor.procedural.WhenClause;
import dev.frostlake.executor.procedural.WhileStatement;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the procedural-statement hierarchy (ProceduralBlock / IfStatement / CaseStatement /
 * LoopStatement / WhileStatement / ForStatement / …) from the ANTLR parse tree. Extracted from
 * {@link SQLCommandVisitor}; expression building and text/identifier helpers are reached through
 * the {@code visitor} back-reference so there is a single source of truth for those.
 */
public class ProceduralBlockBuilder {

    private final SQLCommandVisitor visitor;

    ProceduralBlockBuilder(final SQLCommandVisitor visitor) {
        this.visitor = visitor;
    }

    ProceduralBlock buildProceduralBlock(final FrostlakeParser.StatementListContext ctx) {
        final ProceduralBlock block = new ProceduralBlock();

        for (final FrostlakeParser.StatementContext stmtCtx : ctx.statement()) {
            final Statement stmt = buildStatement(stmtCtx);
            if (stmt != null) {
                block.addStatement(stmt);
            }
        }

        return block;
    }

    /**
     * Build a Statement from ANTLR statement context
     */
    private Statement buildStatement(final FrostlakeParser.StatementContext ctx) {
        final Statement statement = buildStatementOf(ctx);
        if (statement != null) {
            // Every statement carries where it stands, so an error escaping the block can name the
            // statement that failed ("… on line L at position P"), as live does.
            statement.setSourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        }
        return statement;
    }

    private Statement buildStatementOf(final FrostlakeParser.StatementContext ctx) {
        // For procedural statements, convert context to Statement objects
        if (ctx.proceduralStatement() != null) {
            return buildProceduralStatement(ctx.proceduralStatement());
        }

        // For all non-procedural SQL statements, wrap in SqlStatement so they execute
        // conditionally (respecting IF/WHILE/FOR control flow).
        // Use getOriginalText to preserve whitespace correctly.
        final String rawSql = visitor.getOriginalText(ctx);
        if (rawSql != null && !rawSql.isBlank()) {
            return new SqlStatement(rawSql);
        }
        return null;
    }

    /**
     * Build a Statement from ANTLR proceduralStatement context
     */
    private Statement buildProceduralStatement(final FrostlakeParser.ProceduralStatementContext ctx) {
        if (ctx.setStatement() != null) {
            // SET is a session variable, which a procedure may not touch. The block is built while
            // the CALL is running, so the depth check sees the procedure it belongs to.
            visitor.rejectInsideProcedure("SET");
            final String varName = visitor.getText(ctx.setStatement().identifier());
            final BaseExpression expr = visitor.buildExpression(ctx.setStatement().expression());
            return new SetStatement(varName, expr);
        }

        if (ctx.ifStatement() != null) {
            return buildIfStatement(ctx.ifStatement());
        }

        if (ctx.caseStatement() != null) {
            return buildCaseStatement(ctx.caseStatement());
        }

        if (ctx.loopStatement() != null) {
            return buildLoopStatement(ctx.loopStatement());
        }

        if (ctx.whileStatement() != null) {
            return buildWhileStatement(ctx.whileStatement());
        }

        if (ctx.repeatStatement() != null) {
            return buildRepeatStatement(ctx.repeatStatement());
        }

        if (ctx.forStatement() != null) {
            return buildForStatement(ctx.forStatement());
        }

        if (ctx.returnStatement() != null) {
            return buildReturnStatement(ctx.returnStatement());
        }

        if (ctx.breakStatement() != null) {
            final Statement brk = new Statement(StatementType.BREAK) {};
            applyTrailingLabel(brk, ctx.breakStatement().loopLabel());
            return brk;
        }

        if (ctx.continueStatement() != null) {
            final Statement cont = new Statement(StatementType.CONTINUE) {};
            applyTrailingLabel(cont, ctx.continueStatement().loopLabel());
            return cont;
        }

        if (ctx.raiseStatement() != null) {
            if (ctx.raiseStatement().identifier() != null && ctx.raiseStatement().STRING_LITERAL() == null) {
                // RAISE exception_name - must be a user-defined exception
                final String name = visitor.getText(ctx.raiseStatement().identifier());
                // Check if it's a user-defined exception
                if (visitor.getProceduralExecutor().hasException(name)) {
                    final RaiseStatement raise = new RaiseStatement(name);
                    raise.setSourcePosition(ctx.raiseStatement().getStart().getLine(),
                        ctx.raiseStatement().getStart().getCharPositionInLine());
                    return raise;
                } else {
                    // Undefined exception - throw error immediately
                    throw new RuntimeException("Undefined exception: " + name);
                }
            } else if (ctx.raiseStatement().STRING_LITERAL() != null) {
                // RAISE 'message'
                final String msg = visitor.extractStringLiteral(ctx.raiseStatement().STRING_LITERAL());
                final BaseExpression message = new LiteralExpression(msg);
                return new RaiseStatement(message);
            } else {
                // RAISE without arguments
                final BaseExpression message = new LiteralExpression("Error raised");
                return new RaiseStatement(message);
            }
        }

        if (ctx.callStatement() != null) {
            final String procName = visitor.getText(ctx.callStatement().qualifiedName());
            final List<BaseExpression> arguments = new ArrayList<>();
            final List<String> argumentNames = new ArrayList<>();
            if (ctx.callStatement().callArguments() != null) {
                for (final FrostlakeParser.CallArgumentContext argCtx :
                     ctx.callStatement().callArguments().callArgument()) {
                    if (argCtx.namedArgument() != null) {
                        if (argCtx.namedArgument().expression() == null) {
                            throw new RuntimeException(
                                "A bare subquery CALL argument is not supported; parenthesize it: (SELECT ...)");
                        }
                        arguments.add(visitor.buildExpression(argCtx.namedArgument().expression()));
                        argumentNames.add(visitor.getText(argCtx.namedArgument().identifier()));
                    } else {
                        arguments.add(visitor.buildExpression(argCtx.expression()));
                        argumentNames.add(null);
                    }
                }
            }
            return new CallStatement(procName, arguments, argumentNames);
        }

        if (ctx.asyncStatement() != null) {
            // ASYNC (<stmt>) — the engine is single-threaded, so run the wrapped statement synchronously.
            final FrostlakeParser.AsyncStatementContext async = ctx.asyncStatement();
            final String innerSql = async.dmlStatement() != null
                ? visitor.getOriginalText(async.dmlStatement())
                : visitor.getOriginalText(async.callStatement());
            return new SqlStatement(innerSql);
        }

        if (ctx.awaitStatement() != null) {
            // AWAIT [ALL | <name>] — the ASYNC statements already ran synchronously, so this is a no-op.
            return null;
        }

        if (ctx.assignmentStatement() != null) {
            final FrostlakeParser.AssignmentStatementContext aCtx = ctx.assignmentStatement();
            final String varName = visitor.getText(aCtx.identifier());
            if (aCtx.callStatement() != null) {
                return new SqlStatement(visitor.getOriginalText(ctx));
            }
            if (aCtx.executeImmediateStatement() != null) {
                // rs := (EXECUTE IMMEDIATE :stmt [USING (...)]) — build the deferred dynamic-SQL
                // expression so the statement runs when the block executes it.
                final FrostlakeParser.ExecuteImmediateStatementContext ei = aCtx.executeImmediateStatement();
                final BaseExpression eiSql = visitor.buildExpression(ei.expression());
                final List<BaseExpression> eiBinds = new ArrayList<>();
                if (ei.expressionList() != null) {
                    for (final FrostlakeParser.ExpressionContext bind : ei.expressionList().expression()) {
                        eiBinds.add(visitor.buildExpression(bind));
                    }
                }
                return new SetStatement(varName, new ExecuteImmediateExpression(eiSql, eiBinds));
            }
            if (aCtx.expression() != null) {
                final BaseExpression expr = visitor.buildExpression(aCtx.expression());
                return new SetStatement(varName, expr, visitor.getOriginalText(aCtx.expression()));
            }
        }

        if (ctx.letStatement() != null) {
            final FrostlakeParser.LetStatementContext lCtx = ctx.letStatement();
            final String varName = visitor.getText(lCtx.identifier());
            if (lCtx.expression() != null) {
                final BaseExpression expr = visitor.buildExpression(lCtx.expression());
                return new SetStatement(varName, expr);
            }
        }

        // All other procedural statements: execute as raw SQL via the visitor
        final String rawSql = visitor.getOriginalText(ctx);
        if (rawSql != null && !rawSql.isBlank()) {
            return new SqlStatement(rawSql);
        }

        return null;
    }

    Statement buildIfStatement(final FrostlakeParser.IfStatementContext ctx) {
        final List<IfCondition> conditions = new ArrayList<>();

        // Main IF condition
        final BaseExpression condition = visitor.buildExpression(ctx.booleanExpr(0));
        final ProceduralBlock block = buildProceduralBlock(ctx.statementList(0));
        conditions.add(new IfCondition(condition, block));

        // ELSEIF conditions
        for (int i = 1; i < ctx.booleanExpr().size(); i++) {
            final BaseExpression elseifCondition = visitor.buildExpression(ctx.booleanExpr(i));
            final ProceduralBlock elseifBlock = buildProceduralBlock(ctx.statementList(i));
            conditions.add(new IfCondition(elseifCondition, elseifBlock));
        }

        // ELSE block
        ProceduralBlock elseBlock = null;
        if (ctx.ELSE() != null) {
            final int elseIndex = ctx.statementList().size() - 1;
            elseBlock = buildProceduralBlock(ctx.statementList(elseIndex));
        }

        return new IfStatement(conditions, elseBlock);
    }

    Statement buildCaseStatement(final FrostlakeParser.CaseStatementContext ctx) {
        // A leading switch operand (simple CASE) exists only when there are MORE booleanExprs than WHEN
        // branches: the WHEN conditions are booleanExprs too, so a bare isEmpty() check always sees the
        // WHEN conditions, misfires on searched CASE, and shifts the WHEN-condition indices by one —
        // reading one past the end (null) and NPE-ing in buildExpression.
        final boolean hasSwitch = ctx.booleanExpr().size() > ctx.WHEN().size();
        final BaseExpression switchExpression = hasSwitch ? visitor.buildExpression(ctx.booleanExpr(0)) : null;

        final List<WhenClause> whenClauses = new ArrayList<>();
        int exprIndex = hasSwitch ? 1 : 0;

        for (int i = 0; i < ctx.WHEN().size(); i++) {
            final BaseExpression whenCondition = visitor.buildExpression(ctx.booleanExpr(exprIndex++));
            final ProceduralBlock whenBlock = buildProceduralBlock(ctx.statementList(i));
            whenClauses.add(new WhenClause(whenCondition, whenBlock));
        }

        ProceduralBlock elseBlock = null;
        if (ctx.ELSE() != null) {
            final int elseIndex = ctx.statementList().size() - 1;
            elseBlock = buildProceduralBlock(ctx.statementList(elseIndex));
        }

        return new CaseStatement(switchExpression, whenClauses, elseBlock);
    }

    Statement buildLoopStatement(final FrostlakeParser.LoopStatementContext ctx) {
        final LoopStatement stmt = new LoopStatement(buildProceduralBlock(ctx.statementList()));
        applyTrailingLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildWhileStatement(final FrostlakeParser.WhileStatementContext ctx) {
        final BaseExpression condition = visitor.buildExpression(ctx.booleanExpr());
        final ProceduralBlock block = buildProceduralBlock(ctx.statementList());
        final WhileStatement stmt = new WhileStatement(condition, block);
        applyTrailingLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildForStatement(final FrostlakeParser.ForStatementContext ctx) {
        // The identifier is the loop variable; a trailing loopLabel, if any, is the loop's label.
        final String varName = visitor.getText(ctx.identifier());
        final ProceduralBlock block = buildProceduralBlock(ctx.statementList());
        final ForStatement stmt;
        if (ctx.TO() != null) {
            // FOR i IN [REVERSE] start TO end DO … — integer range.
            final BaseExpression start = visitor.buildExpression(ctx.expression(0));
            final BaseExpression end = visitor.buildExpression(ctx.expression(1));
            stmt = new ForStatement(varName, start, end, ctx.REVERSE() != null, block);
        } else {
            stmt = new ForStatement(varName, visitor.buildExpression(ctx.expression(0)), block);
        }
        applyTrailingLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildRepeatStatement(final FrostlakeParser.RepeatStatementContext ctx) {
        final BaseExpression condition = visitor.buildExpression(ctx.booleanExpr());
        final ProceduralBlock block = buildProceduralBlock(ctx.statementList());
        final RepeatStatement stmt = new RepeatStatement(condition, block);
        applyTrailingLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    /** Copy a loop's TRAILING label (`END LOOP <name>`) onto its statement so BREAK/CONTINUE can target it. */
    /** A loop label is its own name position — it admits INNER, which a plain identifier may not. */
    private void applyTrailingLabel(final Statement stmt, final FrostlakeParser.LoopLabelContext labelCtx) {
        if (labelCtx != null) {
            stmt.setLabel(visitor.getText(labelCtx));
        }
    }

    Statement buildReturnStatement(final FrostlakeParser.ReturnStatementContext ctx) {
        BaseExpression expr = null;
        if (ctx.expression() != null) {
            expr = visitor.buildExpression(ctx.expression());
        }
        // RETURN TABLE(expression) — table-valued return from a RESULTSET value/variable. A direct
        // query inside TABLE() is not a form live accepts, and the grammar no longer parses it.
        if (ctx.TABLE() != null) {
            return new ReturnTableStatement(expr);
        }
        return new ReturnStatement(expr);
    }
}
