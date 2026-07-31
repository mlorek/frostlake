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
 * The expression renderer for STRICTNESS messages. Snowflake renders the offending call from its
 * analysed plan, not from the source text: a bare column reference comes out QUALIFIED with its
 * source relation — live, {@code TO_VARCHAR(f)} over table {@code fk} is
 * "invalid type [TO_VARCHAR(FK.F)] for parameter 'TO_VARCHAR'" and {@code CAST(so AS VARCHAR)} over
 * {@code stt} renders {@code CAST(STT.SO AS VARCHAR(134217728))}. Everything else prints exactly as
 * {@link AstPrinterVisitor} does, which is why this is a subclass overriding only the column node.
 *
 * <p>The qualifier comes from the EVALUATION context ({@link ExpressionEvaluatorVisitor}) — the same
 * resolution the value read uses — and is omitted when the owner is a synthetic relation (a derived
 * table, the FROM-less DUMMY), whose invented name live would never print.
 */
final class StrictMessagePrinter extends AstPrinterVisitor {

    private final ExpressionEvaluatorVisitor context;

    StrictMessagePrinter(final ExpressionEvaluatorVisitor context) {
        this.context = context;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (expr.isQualified()) {
            return expr.getTableName().toUpperCase() + "." + expr.getColumnName().toUpperCase();
        }
        final String qualifier = context.strictMessageQualifier(expr);
        return qualifier != null
            ? qualifier + "." + expr.getColumnName().toUpperCase()
            : expr.getColumnName();
    }
}
