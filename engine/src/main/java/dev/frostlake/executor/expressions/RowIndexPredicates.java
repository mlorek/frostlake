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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a BOOLEAN expression is a PREDICATE in the account's plan, which {@code SYSTEM$TYPEOF} tags
 * {@code [ROWINDEX]} rather than the {@code [SB1]} of a stored boolean. Live-verified shape by shape:
 *
 * <ul>
 *   <li>every comparison — {@code =}, {@code <>}, the orderings, [NOT] LIKE / ILIKE / RLIKE / REGEXP,
 *       LIKE ANY, IS [NOT] DISTINCT FROM, IS [NOT] NULL, [NOT] IN over a list, a tuple or a subquery,
 *       [NOT] BETWEEN, [NOT] EXISTS and a quantified ANY / ALL — over constants and columns alike;</li>
 *   <li>the predicate functions: EQUAL_NULL, IS_NULL_VALUE, STARTSWITH, ENDSWITH, CONTAINS,
 *       REGEXP_LIKE, RLIKE, LIKE, ILIKE, ARRAY_CONTAINS, ARRAYS_OVERLAP, SEARCH and the whole
 *       IS_&lt;type&gt; family;</li>
 *   <li>NOT over a predicate, and AND or OR with a predicate on either side.</li>
 * </ul>
 *
 * <p>Everything else stays [SB1]: a boolean literal or column, NOT / AND / OR over those alone, a cast,
 * TO_BOOLEAN with its TRY_ and AS_ forms, the BOOL* functions, MAP_CONTAINS_KEY, IS_ROLE_IN_SESSION, a
 * conditional (IFF, CASE, COALESCE, NVL, NULLIF, DECODE) even over predicate branches, an aggregate, a
 * window function, a scalar subquery, and a derived relation's column however it was computed.
 */
final class RowIndexPredicates {

    private static final Set<String> PREDICATE_FUNCTIONS = new HashSet<>(Arrays.asList(
        "EQUAL_NULL", "IS_NULL_VALUE", "STARTSWITH", "ENDSWITH", "CONTAINS", "REGEXP_LIKE", "RLIKE",
        "LIKE", "ILIKE", "ARRAY_CONTAINS", "ARRAYS_OVERLAP", "SEARCH",
        "IS_ARRAY", "IS_OBJECT", "IS_INTEGER", "IS_BOOLEAN", "IS_VARCHAR", "IS_CHAR", "IS_DECIMAL",
        "IS_DOUBLE", "IS_REAL", "IS_DATE", "IS_DATE_VALUE", "IS_TIME", "IS_TIMESTAMP_NTZ",
        "IS_TIMESTAMP_LTZ", "IS_TIMESTAMP_TZ", "IS_BINARY"));

    private RowIndexPredicates() {
    }

    /** Whether {@code expr} is a predicate the account tags [ROWINDEX]. */
    static boolean isPredicate(final Expression expr) {
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            switch (binary.getOperator()) {
                case EQUAL:
                case NOT_EQUAL:
                case LESS_THAN:
                case LESS_THAN_OR_EQUAL:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUAL:
                case LIKE:
                case ILIKE:
                case NOT_LIKE:
                case NOT_ILIKE:
                    return true;
                case AND:
                case OR:
                    return isPredicate(binary.getLeft()) || isPredicate(binary.getRight());
                default:
                    return false;
            }
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            if (unary.getOperator() == UnaryOperator.EXISTS) {
                return true;
            }
            return unary.getOperator() == UnaryOperator.NOT && isPredicate(unary.getOperand());
        }
        if (expr instanceof IsNullExpression || expr instanceof InExpression
                || expr instanceof TupleInExpression || expr instanceof BetweenExpression
                || expr instanceof LikeAnyAllExpression || expr instanceof QuantifiedComparisonExpression) {
            return true;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            return call.getNameExpression() == null && call.getFunctionName() != null
                && PREDICATE_FUNCTIONS.contains(call.getFunctionName().toUpperCase(Locale.ROOT));
        }
        return false;
    }
}
