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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The column references an expression reads from the row it is evaluated over, in written order: a
 * subquery's names resolve in its own scope, a lambda's parameters are bound by the call that takes
 * it, and the word in a date/time unit slot names a unit ({@link DateTimeUnitSlot}), so none of them counts.
 */
public final class ColumnReferenceCollectWalk extends AstPrinterVisitor {

    private final List<ColumnReferenceExpression> references = new ArrayList<>();
    /** The parameters of the lambdas being walked, innermost first. */
    private final Deque<List<String>> lambdaParameters = new ArrayDeque<>();
    /** The words standing in a unit slot of a call being walked. */
    private final Set<Expression> unitWords = Collections.newSetFromMap(new IdentityHashMap<Expression, Boolean>());

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        // A lambda's parameter is its own, bare or as the qualifier of a field it reads.
        if (!unitWords.contains(expr)
                && !isLambdaParameter(expr.isQualified() ? expr.getTableName() : expr.getColumnName())) {
            references.add(expr);
        }
        return super.visitColumnReference(expr);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final List<Expression> args = expr.getArguments();
        final int slot = expr.getFunctionName() == null ? -1
            : DateTimeUnitSlot.positionIn(expr.getFunctionName().toUpperCase(Locale.ROOT));
        if (slot >= 0 && args != null && slot < args.size()) {
            // DATEADD(day, 1, ts) reads no column DAY, whatever the row holds under that name.
            unitWords.add(args.get(slot));
        }
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        lambdaParameters.push(expr.getParameters());
        try {
            return super.visitLambda(expr);
        } finally {
            lambdaParameters.pop();
        }
    }

    /** @return the references found, in written order */
    public List<ColumnReferenceExpression> references() {
        return references;
    }

    private boolean isLambdaParameter(final String name) {
        for (final List<String> parameters : lambdaParameters) {
            for (final String parameter : parameters) {
                if (parameter.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
