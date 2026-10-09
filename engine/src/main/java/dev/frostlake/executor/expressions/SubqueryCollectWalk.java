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

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the subqueries an expression holds, in the order they are written, without descending into
 * them: a subquery nested in one of them compiles with that one. It notes a ROW written as a parenthesized
 * list too.
 */
final class SubqueryCollectWalk extends AstPrinterVisitor {

    private final List<SubqueryExpression> subqueries = new ArrayList<>();
    private boolean row;

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        row = row || expr instanceof RowExpression;
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        subqueries.add(expr);
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";   // a lambda's body is bound by the call that takes it
    }

    /** Whether a ROW written as a parenthesized list was found. */
    boolean holdsRow() {
        return row;
    }

    /** The subqueries found, in the order written. */
    List<SubqueryExpression> subqueries() {
        return subqueries;
    }
}
