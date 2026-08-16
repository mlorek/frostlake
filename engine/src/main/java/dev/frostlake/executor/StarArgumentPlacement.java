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

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * Where a star may be written as a function argument. The select list takes it, lone ({@code HASH(*)}) or
 * beside other arguments ({@code HASH(a, t.*)}), and so does GROUP BY; WHERE, HAVING, QUALIFY and ORDER BY
 * refuse it whatever the function, as the account does. The row-counting {@code COUNT(*)}, windowed or not,
 * is no star argument and keeps its own rules there. A subquery answers for itself.
 */
final class StarArgumentPlacement {

    private StarArgumentPlacement() {
    }

    /**
     * Refuses a star argument written in any of the given clauses; a null clause is skipped.
     *
     * @param clauses the WHERE, HAVING, QUALIFY and ORDER BY clauses of one SELECT
     */
    static void rejectOutsideSelectList(final ParseTree... clauses) {
        for (final ParseTree clause : clauses) {
            if (clause != null && holdsStarArgument(clause)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Use of * as a function argument is only allowed in the SELECT clause."));
            }
        }
    }

    private static boolean holdsStarArgument(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext call = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (!countsRows(call.functionName().getText(), call.DISTINCT() != null, StarArgument.of(call))) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).functionArgList() != null) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final List<FrostlakeParser.FunctionArgContext> args = call.functionArgList().functionArg();
            for (final FrostlakeParser.FunctionArgContext arg : args) {
                if (arg.STAR() != null && !(args.size() == 1
                        && countsRows(call.functionName().getText(), call.DISTINCT() != null, StarArgument.of(arg)))) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsStarArgument(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a lone star call is the row count, {@code COUNT(*)}, rather than a column list. */
    private static boolean countsRows(final String functionName, final boolean distinct, final StarArgument star) {
        return "COUNT".equalsIgnoreCase(functionName) && !distinct && star != null && star.isBare();
    }
}
