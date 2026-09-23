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

import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * A LIMIT, OFFSET or FETCH count written as a bind ({@code LIMIT :n}) that nothing binds. Inside a scripting block
 * the block's variable is spelled into the text before the statement is parsed, so a bind still standing in the
 * parse tree outside a block is a client bind that was never supplied: refused while compiling as
 * {@code Bind variable :n not set.} at the colon, whether or not a row reaches it.
 *
 * <p>It ranks after the query's own names in every clause, a join condition's too, and after the ORDER BY position,
 * and ahead of everything else: the names inside the query's subqueries, an unknown function, an argument count or
 * type, a predicate's type and the grouped select list (live-verified).
 */
final class RowLimitBindRule {

    private RowLimitBindRule() {
    }

    /**
     * Refuse the first unset bind among the statement's LIMIT, OFFSET and FETCH counts, in written order.
     *
     * @param statement the statement whose row-limiting clauses are judged
     * @param executor  the executor, whose scripting state says whether a block binds the name
     */
    static void reject(final FrostlakeParser.SelectStatementContext statement, final QueryExecutor executor) {
        final ProceduralExecutor procedural = executor.getProceduralExecutor();
        if (procedural != null && procedural.isExecutingBlock()) {
            return;
        }
        rejectIn(statement.limitClause(), procedural);
        rejectIn(statement.fetchClause(), procedural);
    }

    private static void rejectIn(final ParserRuleContext clause, final ProceduralExecutor procedural) {
        if (clause == null) {
            return;
        }
        for (int i = 0; i + 1 < clause.getChildCount(); i++) {
            final ParseTree child = clause.getChild(i);
            if (!(child instanceof TerminalNode)
                    || ((TerminalNode) child).getSymbol().getType() != FrostlakeParser.COLON) {
                continue;
            }
            final String name = clause.getChild(i + 1).getText();
            if (procedural != null && procedural.hasVariable(name)) {
                continue;
            }
            final Token colon = ((TerminalNode) child).getSymbol();
            final SourcePosition at = ExpressionSource.place(colon.getLine(), colon.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "Bind variable :" + name + " not set."));
        }
    }
}
