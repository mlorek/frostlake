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

package dev.frostlake.parser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;

/**
 * The lines live stacks around the refusal of an IDENTIFIER() reference that is not whole, by where the reference
 * stands (all live-verified):
 *
 * <pre>
 *   … WHERE IDENTIFIER(UPPER('a')) = 1               'UPPER' twice      a WHERE, HAVING, QUALIFY or ORDER BY
 *   … WHERE IDENTIFIER(CONCAT('a', 'b')) = 1         'CONCAT' twice     tries the expression again: no ')' line
 *   SELECT 1 + IDENTIFIER(UPPER('a')) FROM t1        'UPPER', then '('  inside a select item's expression, a
 *                                                                        backwards line names the reference's '('
 *   … JOIN t1 t2 ON IDENTIFIER(UPPER('a')) = 1       'ON' alone         a join condition is refused at its ON
 * </pre>
 *
 * <p>A reference in parentheses or a CASE within those clauses stacks further lines this does not model, and is
 * left as the parser reports it; so are a FROM clause, DROP and DESCRIBE, where the parser's own line is live's.
 */
public final class OpenedReferenceLines {

    private OpenedReferenceLines() {
    }

    /**
     * The token live refuses in place of the reference's own line, or null: the ON of a join condition holding it.
     *
     * @param fault the parser's fault for the reference
     * @return the ON token, or null
     */
    static Token refusedAt(final IdentifierReferenceFault fault) {
        for (RuleContext up = fault.getCtx(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.JoinClauseContext) {
                final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) up;
                return join.ON() == null ? null : join.ON().getSymbol();
            }
            if (isBoundary(up)) {
                return null;
            }
        }
        return null;
    }

    /**
     * The token of the line live stacks after the reference's own, or null: the same token again in a clause that
     * tries its expression twice, the reference's '(' inside a select item's expression.
     *
     * @param fault the parser's fault for the reference
     * @param named the token the reference's line names
     * @return the stacked token, or null
     */
    static Token stackedAfter(final IdentifierReferenceFault fault, final Token named) {
        if (!(fault.getCtx() instanceof FrostlakeParser.OpenedIdentifierReferenceContext)) {
            return null;
        }
        final FrostlakeParser.OpenedIdentifierReferenceContext reference =
            (FrostlakeParser.OpenedIdentifierReferenceContext) fault.getCtx();
        if (triedTwice(fault)) {
            return named.getTokenIndex() == reference.LPAREN().getSymbol().getTokenIndex() + 1 ? named : null;
        }
        boolean nested = false;
        for (RuleContext up = reference.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                return nested ? reference.LPAREN().getSymbol() : null;
            }
            if (isBoundary(up) || isCase(up)) {
                return null;
            }
            nested = nested || up instanceof FrostlakeParser.ExpressionContext
                && !(up instanceof FrostlakeParser.OpenedIdentifierExprContext);
        }
        return null;
    }

    /** Whether a context is a CASE expression, whose branches stack lines this does not model. */
    private static boolean isCase(final RuleContext context) {
        return context instanceof FrostlakeParser.CaseExprContext || context instanceof FrostlakeParser.SearchedCaseExprContext
            || context instanceof FrostlakeParser.SimpleCaseExprContext;
    }

    /**
     * Whether the parser's line for the ')' closing a comma's list goes unreported: the expression tried twice, its
     * first line naming the word the parentheses open with.
     */
    static boolean silent(final IdentifierReferenceFault fault) {
        if (!fault.endsTheReport() || !triedTwice(fault)
                || !(fault.getCtx() instanceof FrostlakeParser.OpenedIdentifierReferenceContext)) {
            return false;
        }
        final Token first = ((FrostlakeParser.OpenedIdentifierReferenceContext) fault.getCtx()).openedIdentifierContent()
            .getStart();
        return first != null && first.getType() != FrostlakeLexer.STRING_LITERAL
            && first.getType() != FrostlakeLexer.DOLLAR_QUOTED_STRING && first.getType() != FrostlakeLexer.INTEGER_LITERAL
            && first.getType() != FrostlakeLexer.SESSION_VAR_REF && first.getType() != FrostlakeLexer.QUESTION
            && first.getType() != FrostlakeLexer.COLON;
    }

    /** Whether the reference stands straight in a WHERE, HAVING, QUALIFY or ORDER BY, outside brackets and CASE. */
    private static boolean triedTwice(final IdentifierReferenceFault fault) {
        for (RuleContext up = fault.getCtx(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.WhereClauseContext || up instanceof FrostlakeParser.HavingClauseContext
                    || up instanceof FrostlakeParser.QualifyClauseContext
                    || up instanceof FrostlakeParser.OrderByClauseContext) {
                return true;
            }
            if (up instanceof FrostlakeParser.ParenExprContext || isCase(up) || isBoundary(up)) {
                return false;
            }
        }
        return false;
    }

    /** Whether a context ends the search: a query, a table reference, a select list or a statement. */
    private static boolean isBoundary(final RuleContext context) {
        return context instanceof FrostlakeParser.SelectStatementContext
            || context instanceof FrostlakeParser.TableReferenceContext
            || context instanceof FrostlakeParser.SelectListContext
            || context instanceof FrostlakeParser.StatementContext
            || context instanceof ParserRuleContext && context.getParent() == null;
    }
}
