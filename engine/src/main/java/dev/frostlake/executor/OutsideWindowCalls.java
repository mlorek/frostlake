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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The refusal of a window call written where no window is evaluated, a virtual column's expression among them, in
 * the sentence the account gives a window outside the clauses that take one, the call echoed as the plan prints it
 * over the table it reads (live-verified): {@code j INT AS (ROW_NUMBER() OVER (ORDER BY i))} in a table T is
 * {@code Window function [ROW_NUMBER() OVER (ORDER BY T.I ASC NULLS LAST)] appears outside of SELECT, QUALIFY, and
 * ORDER BY clauses.}
 */
public final class OutsideWindowCalls {

    private OutsideWindowCalls() {
    }

    /**
     * The refusal of the first window call within a parse tree, or null when it holds none.
     *
     * @param within   the parse tree
     * @param table    the table its columns are read from, which qualifies them in the echo
     * @param executor the executor whose functions the call names
     * @return the refusal, or null
     */
    public static RuntimeException refusalOf(final ParseTree within, final Table table, final QueryExecutor executor) {
        final FrostlakeParser.FunctionCallExprContext call = firstWindowCall(within);
        if (call == null) {
            return null;
        }
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(table.getName(), table);
        final List<Table> tables = new ArrayList<>();
        tables.add(table);
        final PlanEcho echo = new PlanEcho(table, aliases, tables, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        return new RuntimeException(SqlCompilationError.of("Window function [" + echo.printWindowCall(call)
            + "] appears outside of SELECT, QUALIFY, and ORDER BY clauses."));
    }

    private static FrostlakeParser.FunctionCallExprContext firstWindowCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext && !OverCallRules.isTableFunctionCall(node)
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            return (FrostlakeParser.FunctionCallExprContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.FunctionCallExprContext found = firstWindowCall(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
