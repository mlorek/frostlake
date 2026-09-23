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
 * Which BOOLEAN expressions are PREDICATES. A predicate is a BOOLEAN that never becomes text: handed to
 * a function that reads text, or to {@code ||} or LIKE, it is refused while the statement compiles —
 * {@code POSITION('x', 'b' IN (SELECT 'abc'))} is "Invalid argument types for function 'POSITION':
 * (VARCHAR(1), BOOLEAN)" — where a BOOLEAN value in the same place is read as its text,
 * {@code UPPER(TRUE)} being 'TRUE'. Live-verified shape by shape:
 *
 * <pre>
 *   predicates                                        BOOLEAN values
 *   a comparison, [NOT] IN, [NOT] BETWEEN,            a literal, a column, a scalar subquery
 *     [NOT] LIKE / ILIKE / RLIKE / REGEXP, LIKE ANY,  a CAST or :: to BOOLEAN, TO_BOOLEAN, TRY_TO_BOOLEAN
 *     IS [NOT] NULL, IS [NOT] DISTINCT FROM,          IFF, CASE, COALESCE, NVL, NULLIF, DECODE, GREATEST
 *     [NOT] EXISTS, = ANY / &gt; ALL                     BOOLAND, BOOLOR, BOOLXOR, BOOLNOT
 *   NOT, AND or OR over a predicate                   NOT, AND or OR over values only
 *   EQUAL_NULL, CONTAINS, STARTSWITH, ENDSWITH,       MAP_CONTAINS_KEY, IS_ROLE_IN_SESSION,
 *     REGEXP_LIKE, RLIKE, LIKE, ILIKE, SEARCH,          IS_DATABASE_ROLE_IN_SESSION
 *     ARRAY_CONTAINS, ARRAYS_OVERLAP, the IS_ type     an aggregate or a window over either kind
 *     tests (IS_ARRAY … IS_NULL_VALUE)
 * </pre>
 */
final class PredicateExpressions {

    /** The BOOLEAN-returning functions live treats as predicates, each measured. */
    private static final Set<String> PREDICATE_FUNCTIONS = Set.of(
        "EQUAL_NULL", "CONTAINS", "STARTSWITH", "ENDSWITH", "REGEXP_LIKE", "RLIKE", "LIKE", "ILIKE",
        "SEARCH", "ARRAY_CONTAINS", "ARRAYS_OVERLAP", "IS_ARRAY", "IS_OBJECT", "IS_VARCHAR", "IS_CHAR",
        "IS_DECIMAL", "IS_DOUBLE", "IS_REAL", "IS_BINARY", "IS_DATE", "IS_DATE_VALUE", "IS_TIME",
        "IS_TIMESTAMP_LTZ", "IS_TIMESTAMP_NTZ", "IS_TIMESTAMP_TZ", "IS_INTEGER", "IS_BOOLEAN",
        "IS_NULL_VALUE");

    private PredicateExpressions() {
    }

    /**
     * Whether an expression is a predicate rather than a BOOLEAN value.
     *
     * @param expression the expression
     * @return true for a predicate
     */
    static boolean isPredicate(final Expression expression) {
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            switch (binary.getOperator()) {
                case EQUAL: case NOT_EQUAL: case LESS_THAN: case LESS_THAN_OR_EQUAL:
                case GREATER_THAN: case GREATER_THAN_OR_EQUAL:
                case LIKE: case ILIKE: case NOT_LIKE: case NOT_ILIKE:
                    return true;
                case AND: case OR:
                    return isPredicate(binary.getLeft()) || isPredicate(binary.getRight());
                default:
                    return false;
            }
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.EXISTS
                || unary.getOperator() == UnaryOperator.NOT && isPredicate(unary.getOperand());
        }
        if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            return call.getNameExpression() == null && call.getFunctionName() != null
                && PREDICATE_FUNCTIONS.contains(call.getFunctionName().toUpperCase(Locale.ROOT));
        }
        return expression instanceof InExpression || expression instanceof TupleInExpression
            || expression instanceof BetweenExpression || expression instanceof IsNullExpression
            || expression instanceof LikeAnyAllExpression || expression instanceof QuantifiedComparisonExpression;
    }
}
