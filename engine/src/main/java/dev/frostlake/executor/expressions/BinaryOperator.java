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
 * The operator of a {@link BinaryOperationExpression} — arithmetic, comparison, logical, and string operators.
 */
public enum BinaryOperator {
    // Arithmetic
    ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
    // Comparison
    EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL,
    // Logical
    AND, OR,
    // String
    CONCAT, LIKE, ILIKE, NOT_LIKE, NOT_ILIKE;

    /**
     * Map an operator symbol as it appears in SQL text ({@code "+"}, {@code "="}, {@code "AND"}, {@code "||"},
     * …) to its operator. Covers the arithmetic, comparison, logical and concat symbols; the LIKE/ILIKE
     * variants are produced by the grammar directly rather than from a symbol.
     */
    public static BinaryOperator fromSymbol(final String symbol) {
        switch (symbol) {
            case "+": return ADD;
            case "-": return SUBTRACT;
            case "*": return MULTIPLY;
            case "/": return DIVIDE;
            case "%": return MODULO;
            case "=": case "==": return EQUAL;
            case "!=": case "<>": return NOT_EQUAL;
            case "<": return LESS_THAN;
            case "<=": return LESS_THAN_OR_EQUAL;
            case ">": return GREATER_THAN;
            case ">=": return GREATER_THAN_OR_EQUAL;
            case "AND": case "&&": return AND;
            case "OR": return OR;
            case "||": return CONCAT;
            default: throw new RuntimeException("Unknown operator: " + symbol);
        }
    }
}
