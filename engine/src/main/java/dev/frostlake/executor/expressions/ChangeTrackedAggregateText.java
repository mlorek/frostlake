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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An aggregate call as the account names it when change tracking refuses a scalar aggregate (live-verified):
 *
 * <ul>
 *   <li>function names upper-cased, each column under the relation it reads, {@code SUM(A.ID + 1)},
 *       {@code COUNT(DISTINCT X.ID)}, {@code MAX(UPPER(A.S))}, {@code COUNT(*)};</li>
 *   <li>the call in the form the account computes it: AVG as the SUM it takes first, BOOLAND_AGG as
 *       {@code MIN(CAST(x AS BOOLEAN))} and BOOLOR_AGG as {@code MAX(CAST(x AS BOOLEAN))}, and LISTAGG of a column
 *       that holds no text over the column cast to {@code VARCHAR(134217728)}.</li>
 * </ul>
 */
public final class ChangeTrackedAggregateText extends AstPrinterVisitor {

    private final Map<String, String> qualifiers;
    private final Set<String> textColumns;

    /**
     * @param qualifiers  the relation each column a bare name reads belongs to, by the column's name
     * @param textColumns the columns that hold text, each as {@code QUALIFIER.COLUMN}
     */
    public ChangeTrackedAggregateText(final Map<String, String> qualifiers, final Set<String> textColumns) {
        this.qualifiers = qualifiers;
        this.textColumns = textColumns;
    }

    /**
     * The aggregate call's name in a refusal.
     *
     * @param call the call
     * @return the text
     */
    public String spell(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if (("BOOLAND_AGG".equals(name) || "BOOLOR_AGG".equals(name)) && args.size() == 1) {
            return ("BOOLAND_AGG".equals(name) ? "MIN" : "MAX") + "(CAST(" + argument(args.get(0)) + " AS BOOLEAN))";
        }
        final StringBuilder text = new StringBuilder("AVG".equals(name) ? "SUM" : name).append('(');
        if (call.isDistinct()) {
            text.append("DISTINCT ");
        }
        if (call.isStar()) {
            return text.append("*)").toString();
        }
        for (int i = 0; i < args.size(); i++) {
            final String spelled = argument(args.get(i));
            text.append(i == 0 ? "" : ", ").append("LISTAGG".equals(name) && i == 0 && !holdsText(args.get(i))
                ? "CAST(" + spelled + " AS VARCHAR(134217728))" : spelled);
        }
        return text.append(')').toString();
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        if (expr.isQualified()) {
            return expr.getTableName() + "." + expr.getColumnName();
        }
        final String qualifier = qualifiers.get(expr.getColumnName());
        return qualifier == null ? expr.getColumnName() : qualifier + "." + expr.getColumnName();
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final StringBuilder text = new StringBuilder(expr.getFunctionName().toUpperCase(Locale.ROOT)).append('(');
        if (expr.isDistinct()) {
            text.append("DISTINCT ");
        }
        if (expr.isStar()) {
            return text.append("*)").toString();
        }
        for (int i = 0; i < expr.getArguments().size(); i++) {
            text.append(i == 0 ? "" : ", ").append(argument(expr.getArguments().get(i)));
        }
        return text.append(')').toString();
    }

    /** An argument as it stands in the call: an operation without the parentheses an operand of another takes. */
    private String argument(final Expression arg) {
        if (arg instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) arg;
            return operation.getLeft().accept(this) + " " + symbol(operation.getOperator()) + " "
                + operation.getRight().accept(this);
        }
        return arg.accept(this);
    }

    /** Whether an argument is a column known to hold text. */
    private boolean holdsText(final Expression arg) {
        return arg instanceof ColumnReferenceExpression && textColumns.contains(arg.accept(this));
    }
}
