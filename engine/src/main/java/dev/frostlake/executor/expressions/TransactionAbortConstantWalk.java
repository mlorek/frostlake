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

import java.util.Locale;
import java.util.Set;

/**
 * Whether SYSTEM$ABORT_TRANSACTION's argument is the constant the call needs (live-verified): a column, a
 * subquery, and a function that answers from the session or by chance — CURRENT_TRANSACTION(), LAST_TRANSACTION(),
 * CURRENT_SESSION(), RANDOM(), GETVARIABLE() — are none, wherever in the argument they stand.
 */
final class TransactionAbortConstantWalk extends AstPrinterVisitor {

    private static final Set<String> NOT_CONSTANT = Set.of("CURRENT_TRANSACTION", "LAST_TRANSACTION",
        "CURRENT_SESSION", "RANDOM", "GETVARIABLE");

    private boolean found;

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        found = true;
        return super.visitColumnReference(expr);
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        found = true;
        return super.visitSubquery(expr);
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if (NOT_CONSTANT.contains(expr.getFunctionName().toUpperCase(Locale.ROOT))) {
            found = true;
        }
        return super.visitFunctionCall(expr);
    }

    boolean found() {
        return found;
    }
}
