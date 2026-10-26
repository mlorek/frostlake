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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The keys a ROLLUP, a CUBE or GROUPING SETS leaves out of some grouping set. Each super-group row holds NULL in
 * them (Snowflake's GROUP BY ROLLUP documentation), so a select item that is one of them, or that reads one, can
 * answer NULL whatever its column's NOT NULL says: {@code knn ... GROUP BY ROLLUP (knn)} over a NOT NULL {@code knn}
 * is nullable, as its total row's NULL is. A key every grouping set keeps, the {@code k} of
 * {@code GROUP BY k, ROLLUP (j)}, stays as its column is.
 */
final class RolledKeys {

    /** No rolled key: a select that groups by none of the three, or does not group. */
    static final RolledKeys NONE = new RolledKeys(Collections.<String>emptySet());

    /** The keys' canonical prints, with the prints of the select items they name by position or alias. */
    private final Set<String> prints;

    /** The column names among them, a qualified key's by its column alone. */
    private final Set<String> columns = new HashSet<>();

    /**
     * @param prints the rolled keys' canonical prints, as {@link AstPrinterVisitor} prints them
     */
    RolledKeys(final Set<String> prints) {
        this.prints = prints;
        for (final String print : prints) {
            final Expression key = parsed(print);
            if (key instanceof ColumnReferenceExpression) {
                columns.add(((ColumnReferenceExpression) key).getColumnName());
            }
        }
    }

    /**
     * Whether an expression written as this text is a rolled key, or a column one names.
     *
     * @param text the expression's text, or null
     * @return whether it is
     */
    boolean holds(final String text) {
        if (prints.isEmpty() || text == null) {
            return false;
        }
        final Expression expression = parsed(text);
        return expression != null && holds(expression);
    }

    /**
     * Whether the expression is a rolled key, or a column one names.
     *
     * @param expression the expression
     * @return whether it is
     */
    boolean holds(final Expression expression) {
        if (prints.isEmpty()) {
            return false;
        }
        if (expression instanceof ColumnReferenceExpression
                && columns.contains(((ColumnReferenceExpression) expression).getColumnName())) {
            return true;
        }
        return prints.contains(AstPrinterVisitor.print(expression));
    }

    private static Expression parsed(final String text) {
        try {
            return ExpressionEvaluator.parse(text);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }
}
