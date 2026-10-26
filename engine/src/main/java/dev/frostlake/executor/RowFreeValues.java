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

import org.antlr.v4.runtime.ParserRuleContext;

/** Answers the value of an expression that reads no row, before any row is read. */
interface RowFreeValues {

    /**
     * Evaluates an expression that names no column and calls no function whose answer is a row's own.
     *
     * @param expression the expression as written
     * @return its value, null for a NULL
     */
    Object valueOf(final ParserRuleContext expression);

    /**
     * An expression as the plan prints it, which a refused bound is echoed as.
     *
     * @param expression the expression as written
     * @return its plan text, or the text as written, upper-cased, where the plan's printer does not read it
     */
    String planText(final ParserRuleContext expression);

    /**
     * Whether the planner folds a subquery to a constant while the statement compiles: a FROM-less constant, or a
     * MIN, MAX or COUNT a table's statistics answer (see ConstantSubqueries).
     *
     * @param subquery the subquery
     * @return whether it folds
     */
    boolean folds(final FrostlakeParser.ScalarSubqueryExprContext subquery);

    /**
     * Whether the statistics pin a column a bound reads to one value on every row, which the bound is read as (see
     * BoundColumnPins).
     *
     * @param column the column as written
     * @return whether it is pinned
     */
    boolean pinsOneValue(final FrostlakeParser.QualifiedNameExprContext column);
}
