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
 * Refuses every call whose NAME resolves to nothing, and judges nothing else — one PHASE of the
 * plan-time walk rather than a check of its own.
 *
 * <p>The phases exist because live orders its refusals by KIND, not by where they stand in the
 * statement (measured in both directions): an invalid identifier outranks an unknown function, and an
 * unknown function outranks every argument-type, arity and semi-structured complaint. Running the
 * whole strict walk item by item cannot produce that order — whichever item comes first wins — so the
 * select list is walked once per kind instead.
 *
 * <p>It subclasses the printer for its TRAVERSAL only: that visitor already reaches every node of an
 * expression, so overriding the call node is the whole job. The printed string is discarded.
 */
final class FunctionNameWalk extends AstPrinterVisitor {

    private final ExpressionEvaluatorVisitor context;

    FunctionNameWalk(final ExpressionEvaluatorVisitor context) {
        this.context = context;
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
        context.requireResolvableFunctionName(expr);
        return super.visitFunctionCall(expr);
    }
}
