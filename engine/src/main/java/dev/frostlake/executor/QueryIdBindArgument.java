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

/**
 * A LAST_QUERY_ID that names the statement a DESCRIBE RESULT or a RESULT_SCAN reads, its index judged a constant
 * before any bind in it is read. Over a bind no block sets, {@code DESCRIBE RESULT LAST_QUERY_ID(:x)} and
 * {@code RESULT_SCAN(LAST_QUERY_ID(:x))} are "argument 1 to function LAST_QUERY_ID needs to be constant, found
 * 'TO_NUMBER(:x, 18, 0)'", and alike for {@code :1} and {@code ?}; a select of the call reads the bind first,
 * "Bind variable :x not set." (live-verified).
 */
final class QueryIdBindArgument {

    private QueryIdBindArgument() {
    }

    /**
     * Refuses a LAST_QUERY_ID call whose one argument is a bind no block sets.
     *
     * @param call     the call
     * @param executor the executor whose block may set the bind
     */
    static void rejectUnsetBind(final FrostlakeParser.FunctionCallExprContext call, final QueryExecutor executor) {
        if (!"LAST_QUERY_ID".equalsIgnoreCase(call.functionName().getText()) || call.functionArgList() == null
                || call.functionArgList().functionArg().size() != 1) {
            return;
        }
        final FrostlakeParser.BooleanExprContext argument = call.functionArgList().functionArg(0).booleanExpr();
        if (!(argument instanceof FrostlakeParser.ValueExprContext)) {
            return;
        }
        final FrostlakeParser.ExpressionContext bind = ((FrostlakeParser.ValueExprContext) argument).expression();
        if (!(bind instanceof FrostlakeParser.PositionalBindExprContext)
                && !(bind instanceof FrostlakeParser.BindVarExprContext && !insideBlock(executor))) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("argument 1 to function LAST_QUERY_ID needs to be constant, "
            + "found 'TO_NUMBER(" + ParseTreeText.getOriginalText(bind) + ", 18, 0)'"));
    }

    /** Whether a block runs, whose own variables a bind reads. */
    private static boolean insideBlock(final QueryExecutor executor) {
        final ProceduralExecutor procedural = executor.getProceduralExecutor();
        return procedural != null && procedural.isExecutingBlock();
    }
}
