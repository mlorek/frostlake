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
 * Finds the first column an expression names, in the order written. It subclasses the printer for its traversal
 * only; the printed string is discarded.
 */
final class FirstColumnWalk extends AstPrinterVisitor {

    private ColumnReferenceExpression column;

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (column == null) {
            column = expr;
        }
        return super.visitColumnReference(expr);
    }

    /** The first column named, or null when the expression names none. */
    ColumnReferenceExpression column() {
        return column;
    }
}
