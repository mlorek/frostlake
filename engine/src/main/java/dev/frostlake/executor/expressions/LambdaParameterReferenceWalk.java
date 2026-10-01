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

import java.util.List;

/**
 * Whether an expression inside a lambda's body reads one of the lambda's own parameters — an unqualified
 * name spelled like a parameter, which the call taking the lambda binds rather than the statement's tables.
 *
 * <p>It subclasses the printer for its TRAVERSAL only; the printed string is discarded.
 */
final class LambdaParameterReferenceWalk extends AstPrinterVisitor {

    private final List<String> parameters;
    private boolean found;

    private LambdaParameterReferenceWalk(final List<String> parameters) {
        this.parameters = parameters;
    }

    /**
     * Whether {@code expression} reads any of {@code parameters}.
     *
     * @param expression the expression, part of a lambda's body
     * @param parameters the lambda's parameter names
     * @return true when an unqualified name in it is one of the parameters
     */
    static boolean reads(final Expression expression, final List<String> parameters) {
        final LambdaParameterReferenceWalk walk = new LambdaParameterReferenceWalk(parameters);
        expression.accept(walk);
        return walk.found;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (!expr.isQualified()) {
            for (final String parameter : parameters) {
                if (parameter.equalsIgnoreCase(expr.getColumnName())) {
                    found = true;
                }
            }
        }
        return super.visitColumnReference(expr);
    }
}
