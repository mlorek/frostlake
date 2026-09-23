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

package dev.frostlake.executor.expressions;

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Where a refusal of a scalar subquery's shape ("Unsupported subquery type cannot be evaluated") is placed. A
 * subquery written as a call's whole argument list is the call's query argument, placed where that argument
 * begins, at its first parenthesis: {@code ABS((SELECT …))}, {@code COUNT(DISTINCT (SELECT …))},
 * {@code MAX(((SELECT …)))} and a window call's {@code MAX((SELECT …)) OVER ()}. Anywhere else it is placed at
 * its SELECT: beside another argument ({@code GREATEST((SELECT …), 1)}), as an operand
 * ({@code ABS((SELECT …) + 1)}), inside CAST, or unparenthesised ({@code ABS(SELECT …)}) (live-verified).
 */
public final class SubqueryAnchor {

    private SubqueryAnchor() {
    }

    /**
     * The token a refusal of a scalar subquery's shape is placed at.
     *
     * @param subquery the subquery, parentheses included
     * @return the first token of the call argument it is the whole of, or its SELECT
     */
    public static Token of(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final FrostlakeParser.FunctionArgContext argument = wholeArgument(subquery);
        return argument != null ? argument.getStart() : subquery.selectStatement().getStart();
    }

    /**
     * The call argument a subquery is the whole of, when that argument is its call's only one, however many
     * parentheses surround the subquery; null for a subquery written anywhere else.
     */
    static FrostlakeParser.FunctionArgContext wholeArgument(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        ParserRuleContext parent = subquery.getParent();
        while (parent instanceof FrostlakeParser.ValueExprContext || parent instanceof FrostlakeParser.ParenExprContext) {
            parent = parent.getParent();
        }
        if (!(parent instanceof FrostlakeParser.FunctionArgContext)
                || !(parent.getParent() instanceof FrostlakeParser.FunctionArgListContext)
                || ((FrostlakeParser.FunctionArgListContext) parent.getParent()).functionArg().size() != 1) {
            return null;
        }
        return (FrostlakeParser.FunctionArgContext) parent;
    }
}
