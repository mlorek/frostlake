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
import java.util.Locale;

/**
 * Collects the subqueries an expression evaluates whatever its row holds, in the order they are written: not one in a
 * CASE's result or a later WHEN, nor in the branches IFF, NVL2 and DECODE choose between, nor in a COALESCE, NVL or
 * IFNULL argument after the first, nor in a lambda's body. Live refuses a subquery opening with the outer query's
 * aggregate in such a place as the statement compiles, and only a branch can drop it before a row is read
 * (live-verified).
 */
final class UnguardedSubqueryWalk extends AstPrinterVisitor {

    private final List<SubqueryExpression> subqueries = new ArrayList<>();

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        subqueries.add(expr);
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        if (!expr.getWhenClauses().isEmpty()) {
            expr.getWhenClauses().get(0).getCondition().accept(this);
        }
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final int evaluated = alwaysEvaluated(expr.getFunctionName());
        if (evaluated < 0 || expr.isStar()) {
            return super.visitFunctionCall(expr);
        }
        final List<Expression> args = expr.getArguments();
        for (int i = 0; i < args.size() && i < evaluated; i++) {
            args.get(i).accept(this);
        }
        return "";
    }

    /**
     * How many leading arguments a conditional call evaluates whatever its row holds, or -1 for a call that evaluates
     * them all.
     */
    private static int alwaysEvaluated(final String functionName) {
        final String name = functionName == null ? "" : functionName.toUpperCase(Locale.ROOT);
        switch (name) {
            case "IFF":
            case "NVL2":
            case "COALESCE":
            case "NVL":
            case "IFNULL":
                return 1;
            case "DECODE":
                return 2;
            default:
                return -1;
        }
    }

    /** The subqueries found, in the order written. */
    List<SubqueryExpression> subqueries() {
        return subqueries;
    }
}
