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

/**
 * The walk {@link VolatileCalls} takes: the printer's traversal, noting a call that answers anew each time and a call
 * that reads the row's place. A nested query's calls are its own.
 */
final class VolatileCallWalk extends AstPrinterVisitor {

    private boolean found;
    private boolean readsRowPlace;

    boolean found() {
        return found;
    }

    boolean readsRowPlace() {
        return readsRowPlace;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = expr.getFunctionName() == null ? "" : expr.getFunctionName().toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(name) || "UUID_STRING".equals(name) && expr.getArguments().isEmpty()) {
            found = true;
        }
        if ("SEQ1".equals(name) || "SEQ2".equals(name) || "SEQ4".equals(name) || "SEQ8".equals(name)) {
            found = true;
            readsRowPlace = true;
        }
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if ("NEXTVAL".equalsIgnoreCase(expr.getColumnName()) && expr.isQualified()) {
            found = true;
        }
        return super.visitColumnReference(expr);
    }
}
