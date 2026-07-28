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

import dev.frostlake.executor.procedural.*;
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
        ProceduralBlock block = new ProceduralBlock();

        for (final FrostlakeParser.StatementContext stmtCtx : ctx.statement()) {
            Statement stmt = buildStatement(stmtCtx);
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
        // For procedural statements, convert context to Statement objects
        if (ctx.proceduralStatement() != null) {
            return buildProceduralStatement(ctx.proceduralStatement());
        }

        // For all non-procedural SQL statements, wrap in SqlStatement so they execute
        // conditionally (respecting IF/WHILE/FOR control flow).
        // Use getOriginalText to preserve whitespace correctly.
        String rawSql = visitor.getOriginalText(ctx);
        if (rawSql != null && !rawSql.isBlank()) {
            return new SqlStatement(rawSql);
        }
        return null;
    }

    /**
     * Build a Statement from ANTLR proceduralStatement context
     */
    private Statement buildProceduralStatement(final FrostlakeParser.ProceduralStatementContext ctx) {
        if (ctx.declareStatement() != null) {
            // DECLARE statements now only appear in declareSection before BEGIN, not in statement lists
            // This shouldn't be reached in normal usage
            throw new RuntimeException("DECLARE statements must appear in DECLARE section before BEGIN");
        }

        if (ctx.setStatement() != null) {
            String varName = visitor.getText(ctx.setStatement().identifier());
            BaseExpression expr = visitor.buildExpression(ctx.setStatement().expression());
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
            if (ctx.breakStatement().identifier() != null) {
                brk.setLabel(visitor.getText(ctx.breakStatement().identifier()));
            }
            return brk;
        }

        if (ctx.continueStatement() != null) {
            final Statement cont = new Statement(StatementType.CONTINUE) {};
            if (ctx.continueStatement().identifier() != null) {
                cont.setLabel(visitor.getText(ctx.continueStatement().identifier()));
            }
            return cont;
        }

        if (ctx.raiseStatement() != null) {
            if (ctx.raiseStatement().identifier() != null && ctx.raiseStatement().STRING_LITERAL() == null) {
                // RAISE exception_name - must be a user-defined exception
                String name = visitor.getText(ctx.raiseStatement().identifier());
                // Check if it's a user-defined exception
                if (visitor.getProceduralExecutor().hasException(name)) {
                    return new RaiseStatement(name);
                } else {
                    // Undefined exception - throw error immediately
                    throw new RuntimeException("Undefined exception: " + name);
                }
            } else if (ctx.raiseStatement().STRING_LITERAL() != null) {
                // RAISE 'message'
                String msg = visitor.extractStringLiteral(ctx.raiseStatement().STRING_LITERAL());
                BaseExpression message = new LiteralExpression(msg);
                return new RaiseStatement(message);
            } else {
                // RAISE without arguments
                BaseExpression message = new LiteralExpression("Error raised");
                return new RaiseStatement(message);
            }
        }

        if (ctx.callStatement() != null) {
            String procName = visitor.getText(ctx.callStatement().qualifiedName());
            List<BaseExpression> arguments = new ArrayList<>();
            List<String> argumentNames = new ArrayList<>();
            if (ctx.callStatement().callArguments() != null) {
                for (final FrostlakeParser.CallArgumentContext argCtx :
                     ctx.callStatement().callArguments().callArgument()) {
                    if (argCtx.namedArgument() != null) {
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
            FrostlakeParser.AssignmentStatementContext aCtx = ctx.assignmentStatement();
            String varName = visitor.getText(aCtx.identifier());
            if (aCtx.callStatement() != null) {
                return new SqlStatement(visitor.getOriginalText(ctx));
            }
            if (aCtx.expression() != null) {
                BaseExpression expr = visitor.buildExpression(aCtx.expression());
                return new SetStatement(varName, expr, visitor.getOriginalText(aCtx.expression()));
            }
        }

        if (ctx.letStatement() != null) {
            FrostlakeParser.LetStatementContext lCtx = ctx.letStatement();
            String varName = visitor.getText(lCtx.identifier());
            if (lCtx.expression() != null) {
                BaseExpression expr = visitor.buildExpression(lCtx.expression());
                return new SetStatement(varName, expr);
            }
        }

        // All other procedural statements: execute as raw SQL via the visitor
        String rawSql = visitor.getOriginalText(ctx);
        if (rawSql != null && !rawSql.isBlank()) {
            return new SqlStatement(rawSql);
        }

        return null;
    }

    Statement buildIfStatement(final FrostlakeParser.IfStatementContext ctx) {
        List<IfCondition> conditions = new ArrayList<>();

        // Main IF condition
        BaseExpression condition = visitor.buildExpression(ctx.booleanExpr(0));
        ProceduralBlock block = buildProceduralBlock(ctx.statementList(0));
        conditions.add(new IfCondition(condition, block));

        // ELSEIF conditions
        for (int i = 1; i < ctx.booleanExpr().size(); i++) {
            BaseExpression elseifCondition = visitor.buildExpression(ctx.booleanExpr(i));
            ProceduralBlock elseifBlock = buildProceduralBlock(ctx.statementList(i));
            conditions.add(new IfCondition(elseifCondition, elseifBlock));
        }

        // ELSE block
        ProceduralBlock elseBlock = null;
        if (ctx.ELSE() != null) {
            int elseIndex = ctx.statementList().size() - 1;
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

        List<WhenClause> whenClauses = new ArrayList<>();
        int exprIndex = hasSwitch ? 1 : 0;

        for (int i = 0; i < ctx.WHEN().size(); i++) {
            BaseExpression whenCondition = visitor.buildExpression(ctx.booleanExpr(exprIndex++));
            ProceduralBlock whenBlock = buildProceduralBlock(ctx.statementList(i));
            whenClauses.add(new WhenClause(whenCondition, whenBlock));
        }

        ProceduralBlock elseBlock = null;
        if (ctx.ELSE() != null) {
            int elseIndex = ctx.statementList().size() - 1;
            elseBlock = buildProceduralBlock(ctx.statementList(elseIndex));
        }

        return new CaseStatement(switchExpression, whenClauses, elseBlock);
    }

    Statement buildLoopStatement(final FrostlakeParser.LoopStatementContext ctx) {
        final LoopStatement stmt = new LoopStatement(buildProceduralBlock(ctx.statementList()));
        applyLoopLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildWhileStatement(final FrostlakeParser.WhileStatementContext ctx) {
        BaseExpression condition = visitor.buildExpression(ctx.booleanExpr());
        ProceduralBlock block = buildProceduralBlock(ctx.statementList());
        final WhileStatement stmt = new WhileStatement(condition, block);
        applyLoopLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildForStatement(final FrostlakeParser.ForStatementContext ctx) {
        // identifier(0) is the loop variable; a trailing identifier(1), if any, is the END-label.
        final String varName = visitor.getText(ctx.identifier(0));
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
        applyLoopLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    Statement buildRepeatStatement(final FrostlakeParser.RepeatStatementContext ctx) {
        final BaseExpression condition = visitor.buildExpression(ctx.booleanExpr());
        final ProceduralBlock block = buildProceduralBlock(ctx.statementList());
        final RepeatStatement stmt = new RepeatStatement(condition, block);
        applyLoopLabel(stmt, ctx.loopLabel());
        return stmt;
    }

    /** Copy a loop's leading label (`<name>:`) onto its statement, so labeled BREAK/CONTINUE can target it. */
    private void applyLoopLabel(final Statement stmt, final FrostlakeParser.LoopLabelContext labelCtx) {
        if (labelCtx != null) {
            stmt.setLabel(visitor.getText(labelCtx.identifier()));
        }
    }

    Statement buildReturnStatement(final FrostlakeParser.ReturnStatementContext ctx) {
        // RETURN TABLE(SELECT ...) — table-valued return produced by running the query directly.
        if (ctx.TABLE() != null && ctx.selectStatement() != null) {
            return new ReturnTableStatement(visitor.getOriginalText(ctx.selectStatement()));
        }
        BaseExpression expr = null;
        if (ctx.expression() != null) {
            expr = visitor.buildExpression(ctx.expression());
        }
        // RETURN TABLE(expression) — table-valued return from a RESULTSET value/variable.
        if (ctx.TABLE() != null) {
            return new ReturnTableStatement(expr);
        }
        return new ReturnStatement(expr);
    }
}
