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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;

/**
 * Folds a constant part of an expression a plan writes into the value the plan writes in its place, as the account
 * computes it while the statement compiles, and a SQL function's call into its body (see {@link PlanConstantFold}).
 */
interface PlanConstants {

    /**
     * @param expression a part of an expression the plan writes
     * @return the text of the value the plan writes for it, or null where the part is no constant the plan folds
     */
    String folded(Expression expression);

    /**
     * @param call a call the plan writes, its arguments columns or constants
     * @return the body of the SQL function it calls, written in its place, or null where the call is written as it is
     */
    InlinedFunctionBody inlined(FunctionCallExpression call);

    /**
     * @param subquery a scalar subquery the plan writes
     * @return the one item written in its place, where it is FROM-less and its item calls a procedure's token, or
     *         null where the subquery is written as it is
     */
    Expression inlinedItem(SubqueryExpression subquery);
}
