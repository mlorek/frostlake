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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Where an INTO clause may stand. Every query block reads one after its select list, as the account's parser does,
 * and the compiler refuses each but the INTO of a Snowflake Scripting block's own SELECT … INTO statement with "INTO
 * clause is not allowed in this context", at the SELECT of the query block that carries it, while the statement
 * compiles and before any name in it is judged (live-verified): in a set operation, a derived table, a subquery, a CTE,
 * CREATE TABLE … AS, CREATE VIEW, INSERT … SELECT, EXPLAIN, an UPDATE, DELETE or MERGE, and in a block's RESULTSET,
 * cursor, RETURN, assignment, condition and FOR source alike. The first one in the text is the one refused — in a
 * block, the one whose INTO its compile reaches first (see {@link #rejectInBlockAt}).
 *
 * <p>A block's own SELECT … INTO places one INTO clause by the statement around it: an INTO nested in its select list
 * is refused at the statement's SELECT, while one nested in its WITH, FROM, WHERE or ORDER BY is refused at its own
 * query's SELECT (live-verified).
 */
public final class IntoClausePlacement {

    private IntoClausePlacement() {
    }

    /**
     * Refuse the INTO clause {@code keyword} opens, when it stands in a Snowflake Scripting block that is compiling:
     * every INTO clause but the one a block's own SELECT … INTO statement reads there. A block judges its INTO clauses
     * in the order it is written, together with its declared types, integer literals, DECLARE sections and routine
     * statements (see RoutineStatementRules): a clause is refused when that order reaches its INTO, after a type or a
     * literal written before that INTO in the same statement and before one written after it, and of two nested
     * clauses the one whose INTO comes first (all live-verified). The blocks nested in the block, and a block a
     * RESULTSET is filled from, are judged with it; a task's body compiles when the task runs (live-verified: a block
     * creates a task whose body's query carries an INTO clause). A statement written SELECT … INTO over a hierarchical
     * query is no SELECT … INTO of the block's: it is a query the block runs, and it is refused when it runs, at its
     * own SELECT (live-verified).
     *
     * @param keyword a token of the block
     */
    public static void rejectInBlockAt(final TerminalNode keyword) {
        if (keyword.getSymbol().getType() != FrostlakeLexer.INTO
                || !(keyword.getParent() instanceof FrostlakeParser.IntoClauseContext)
                || !(keyword.getParent().getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return;
        }
        final FrostlakeParser.SelectClauseContext clause =
            (FrostlakeParser.SelectClauseContext) keyword.getParent().getParent();
        Token select = clause.SELECT().getSymbol();
        ParseTree below = clause;
        for (ParseTree up = clause.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.TaskBodyContext || isHierarchicalSelectInto(up)) {
                return;
            }
            if (up instanceof FrostlakeParser.SelectIntoStatementContext
                    && below == ((FrostlakeParser.SelectIntoStatementContext) up).selectList()) {
                select = ((FrostlakeParser.SelectIntoStatementContext) up).SELECT().getSymbol();
            }
            below = up;
        }
        throw refusal(select);
    }

    /**
     * Refuse a statement run on its own — at the top level, as EXECUTE IMMEDIATE's text or as a task's body — that
     * carries a bind with a quoted name, as the syntax error it is (see {@link QuotedBindName}), and then one that
     * carries an INTO clause, at the first misplaced clause. Only a block's SELECT … INTO, run for the block, may carry
     * one. A block inside the statement and a task's body are left to their own compilation: an anonymous block's when
     * it runs, a procedure body's when the procedure is created, a task body's when the task runs (live-verified:
     * CREATE TASK takes a body whose query carries an INTO clause).
     *
     * @param statement      the statement
     * @param ownIntoAllowed whether the statement is a block's SELECT … INTO, run for the block
     */
    public static void rejectInStatement(final ParseTree statement, final boolean ownIntoAllowed) {
        final Token quoted = QuotedBindName.first(statement);
        if (quoted != null) {
            throw QuotedBindName.refusal(quoted);
        }
        final Token select = firstMisplaced(statement, ownIntoAllowed, true);
        if (select != null) {
            throw refusal(select);
        }
    }

    /**
     * The SELECT of the first misplaced INTO clause under {@code node}, in the order of the text, or null.
     *
     * @param statementIntoAllowed whether a SELECT … INTO statement under {@code node} may carry its INTO clause
     * @param skipBlocks           whether blocks and task bodies are left alone
     */
    private static Token firstMisplaced(final ParseTree node, final boolean statementIntoAllowed,
                                        final boolean skipBlocks) {
        if (node instanceof FrostlakeParser.TaskBodyContext || node instanceof FrostlakeParser.ResultSetBlockContext
                || skipBlocks && node instanceof FrostlakeParser.BeginEndBlockContext
                || !skipBlocks && isHierarchicalSelectInto(node)) {
            return null;
        }
        if (node instanceof FrostlakeParser.SelectIntoStatementContext) {
            final FrostlakeParser.SelectIntoStatementContext into = (FrostlakeParser.SelectIntoStatementContext) node;
            final Token select = into.SELECT().getSymbol();
            if (!statementIntoAllowed) {
                return select;
            }
            for (int i = 0; i < into.getChildCount(); i++) {
                final ParseTree part = into.getChild(i);
                final Token nested = firstMisplaced(part, true, skipBlocks);
                if (nested != null) {
                    return part == into.selectList() ? select : nested;
                }
            }
            return null;
        }
        if (node instanceof FrostlakeParser.SelectClauseContext
                && ((FrostlakeParser.SelectClauseContext) node).intoClause() != null) {
            return ((FrostlakeParser.SelectClauseContext) node).SELECT().getSymbol();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token nested = firstMisplaced(node.getChild(i), statementIntoAllowed, skipBlocks);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Refuse, when it runs, a statement of a block written SELECT … INTO over a hierarchical query: the account runs
     * it as a query and refuses its INTO clause at its own SELECT, in the statement's own text (live-verified: error
     * line 1 at position 0, or 23 past a WITH clause, inside an uncaught STATEMENT_ERROR at the statement).
     *
     * @param query the query statement about to run
     */
    public static void rejectHierarchicalSelectInto(final FrostlakeParser.QueryStatementContext query) {
        if (isHierarchicalSelectInto(query.getParent())) {
            throw refusal(query.selectStatement().selectOperand(0).selectClause().SELECT().getSymbol());
        }
    }

    /**
     * Whether {@code node} is a statement of a block written SELECT … INTO over a hierarchical query: one query block,
     * its INTO clause beside a CONNECT BY.
     */
    private static boolean isHierarchicalSelectInto(final ParseTree node) {
        if (!(node instanceof FrostlakeParser.StatementContext) || !(node.getParent() instanceof FrostlakeParser.StatementListContext)
                || ((FrostlakeParser.StatementContext) node).queryStatement() == null) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query =
            ((FrostlakeParser.StatementContext) node).queryStatement().selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = query.selectOperand(0).selectClause();
        return clause.intoClause() != null && clause.connectByClause() != null;
    }

    private static RuntimeException refusal(final Token select) {
        return new RuntimeException(SqlCompilationError.intoClauseNotAllowed(select.getLine(),
            select.getCharPositionInLine()));
    }
}
