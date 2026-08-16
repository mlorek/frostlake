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

/**
 * Refuses every column reference that resolves to nothing, and judges nothing else — the FIRST phase
 * of the plan-time walk, ahead of unknown function names and ahead of every argument-type check,
 * because that is the order live reports them in whichever way round they are written.
 *
 * @see FunctionNameWalk for the phase that follows this one, and for why the phases exist at all
 */
final class ColumnScopeWalk extends AstPrinterVisitor {

    private final ExpressionEvaluatorVisitor context;

    ColumnScopeWalk(final ExpressionEvaluatorVisitor context) {
        this.context = context;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        context.validateColumnReferenceScope(expr);
        return super.visitColumnReference(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";   // a subquery's names resolve in its OWN scope, not this statement's
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";   // and a lambda's parameters are bound by the call that takes it
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        // A call's ARGUMENTS are where the date/time-unit barewords are exempt, so the phase has to
        // descend in argument position the way the combined walk does.
        final boolean enclosing = context.beginFunctionArgumentScope();
        try {
            return super.visitFunctionCall(expr);
        } finally {
            context.endFunctionArgumentScope(enclosing);
        }
    }
}
