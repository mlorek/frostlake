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
 * Refuses every call written with a shape it does not take — a quantifier or a WITHIN GROUP (see {@link
 * CallShapeRules}), named arguments (see {@code NamedArgumentRefusals}) — and judges nothing else: one PHASE of the
 * plan-time walk, as {@link FunctionNameWalk} is. The shape is judged ahead of every argument type, wherever the
 * call stands in the expression: {@code ABS(ALL 1) + TRUE}, {@code TRUE + ABS(ALL 1)} and {@code ABS(ALL TRUE + 1)}
 * are each refused for the 'all', and {@code UPPER(x => 'a') || 1 + TRUE} for the named argument (live-verified).
 *
 * <p>It subclasses the printer for its TRAVERSAL only: that visitor already reaches every node of an
 * expression, so overriding the call node is the whole job. The printed string is discarded.
 */
final class CallShapeWalk extends AstPrinterVisitor {

    private final ExpressionEvaluatorVisitor context;

    CallShapeWalk(final ExpressionEvaluatorVisitor context) {
        this.context = context;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";   // a subquery's calls are judged when the subquery compiles, in its own scope
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";   // and a lambda's body is the call's that takes it
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        // The arguments first, inside-out, as the strict walk reaches them.
        final String walked = super.visitFunctionCall(expr);
        context.rejectWrittenCallShape(expr);
        return walked;
    }
}
