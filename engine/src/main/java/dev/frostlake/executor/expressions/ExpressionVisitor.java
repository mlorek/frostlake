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
 * Visitor interface for expression AST traversal
 */
public interface ExpressionVisitor<T> {
    T visitLiteral(final LiteralExpression expr);
    T visitColumnReference(final ColumnReferenceExpression expr);
    T visitBinaryOperation(final BinaryOperationExpression expr);
    T visitUnaryOperation(final UnaryOperationExpression expr);
    T visitFunctionCall(final FunctionCallExpression expr);
    T visitLambda(final LambdaExpression expr);
    T visitCaseExpression(final CaseExpression expr);
    T visitCast(final CastExpression expr);
    T visitObjectAccess(final ObjectAccessExpression expr);
    T visitArrayAccess(final ArrayAccessExpression expr);
    T visitSubquery(final SubqueryExpression expr);
    T visitExecuteImmediate(final ExecuteImmediateExpression expr);
    T visitJsonObject(final JsonObjectExpression expr);
    T visitJsonArray(final JsonArrayExpression expr);
    T visitBetween(final BetweenExpression expr);
    T visitIn(final InExpression expr);
    T visitQuantifiedComparison(final QuantifiedComparisonExpression expr);
    T visitInterval(final IntervalExpression expr);
    T visitSessionVar(final SessionVarExpression expr);
    T visitBindVariable(final BindVariableExpression expr);
    T visitSystemStreamHasData(final SystemStreamHasDataExpression expr);
    T visitSystemUserTaskCancel(final SystemUserTaskCancelExpression expr);
    T visitIsNull(final IsNullExpression expr);
    T visitTupleIn(final TupleInExpression expr);
    T visitWindowFunction(final WindowFunctionExpression expr);
    T visitSpread(final SpreadExpression expr);
}
