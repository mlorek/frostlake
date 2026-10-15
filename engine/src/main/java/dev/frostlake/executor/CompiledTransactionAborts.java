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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * The SYSTEM$ABORT_TRANSACTION calls a query writes, carried out while the query compiles. The account folds the
 * call into a constant before a row is read: it runs once whether or not a row ever reaches it —
 * {@code SELECT IFF(FALSE, SYSTEM$ABORT_TRANSACTION(id), 'no')}, the same call over an empty table, in a WHERE no
 * row passes and under EXPLAIN all end the transaction — and every row that does reach it reads the one answer
 * (all live-verified). Two calls written are two calls: the first ends the transaction and the second finds none.
 *
 * <p>Each call is run here, in the order written, as the query of that call alone, and its answer is kept by the
 * place the call stands at for the rows to read (see {@link QueryExecutor#compiledAbort}). A call that is refused
 * is left to the statement, which refuses it where it reads it.
 */
final class CompiledTransactionAborts {

    private static final String NAME = "SYSTEM$ABORT_TRANSACTION";

    private CompiledTransactionAborts() {
    }

    /**
     * Carry out the calls a statement of the session writes, when it is a query or the EXPLAIN of one.
     *
     * @param ctx      the statement
     * @param executor the engine's query executor
     */
    static void carryOut(final FrostlakeParser.StatementContext ctx, final QueryExecutor executor) {
        if (executor.isUnrecorded()) {
            return;
        }
        executor.forgetCompiledAborts();
        final ParseTree query = ctx.queryStatement() != null ? ctx.queryStatement()
            : ctx.explainStatement() != null ? ctx.explainStatement().selectStatement() : null;
        if (query == null) {
            return;
        }
        final List<FrostlakeParser.SystemFuncExprContext> calls = new ArrayList<>();
        collect(query, calls);
        for (final FrostlakeParser.SystemFuncExprContext call : calls) {
            final Object answer;
            try {
                final List<ResultSet> answered = executor.executeUnrecorded("SELECT "
                    + ParseTreeText.getOriginalText(call));
                answer = answered.get(0).getRows().get(0).getValue(0);
            } catch (final RuntimeException refused) {
                return;
            }
            final Token at = call.getStart();
            executor.noteCompiledAbort(at.getLine(), at.getCharPositionInLine(), answer);
        }
    }

    /** The calls of the function under {@code node}, in the order written; one inside another's argument is its own. */
    private static void collect(final ParseTree node, final List<FrostlakeParser.SystemFuncExprContext> into) {
        if (node instanceof FrostlakeParser.SystemFuncExprContext
                && NAME.equalsIgnoreCase(((FrostlakeParser.SystemFuncExprContext) node).SYSTEM_FUNC().getText())) {
            into.add((FrostlakeParser.SystemFuncExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }
}
