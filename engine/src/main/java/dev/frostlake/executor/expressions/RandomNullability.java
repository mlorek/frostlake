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

import dev.frostlake.types.VariantType;

/**
 * Whether a value built on RANDOM can never be NULL, so that a null test over it is settled without reading it.
 * RANDOM never answers NULL, and live settles {@code RANDOM(NULL) IS NOT NULL} as TRUE although reading
 * {@code RANDOM(NULL)} refuses its seed. The same holds for {@code IS [NOT] DISTINCT FROM NULL},
 * {@code EQUAL_NULL(…, NULL)} and {@code NVL2}, over RANDOM itself and over what cannot turn it into a NULL.
 * The measured forms (live-verified):
 *
 * <ul>
 *   <li>never NULL: RANDOM with no seed or a seed of a SQL type; {@code +}, {@code -}, {@code *} and the
 *       comparisons of two such values or literals; ABS of one; a COALESCE, NVL or IFNULL with one; and
 *       ZEROIFNULL of anything;</li>
 *   <li>read, so a NULL seed refuses: RANDOM with a VARIANT or a cast seed, and anything else over it —
 *       a cast, TO_VARCHAR, HASH, UNIFORM, NULLIF or IN.</li>
 * </ul>
 *
 * <p>A value that holds no RANDOM call is left to be read as before.
 */
public final class RandomNullability {

    private static final int MAY_BE_NULL = 0;
    private static final int NEVER_NULL = 1;
    private static final int NEVER_NULL_OVER_RANDOM = 2;

    private RandomNullability() {
    }

    /**
     * Whether a null test over {@code expr} is settled without reading it: the value holds a RANDOM call and
     * can never be NULL.
     *
     * @param expr the tested value
     * @param types the statement's type inferencer, which types a seed
     * @return true when the value is never NULL
     */
    public static boolean neverNull(final Expression expr, final TypeInferencer types) {
        return state(expr, types) == NEVER_NULL_OVER_RANDOM;
    }

    private static int state(final Expression expr, final TypeInferencer types) {
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).getValue() == null ? MAY_BE_NULL : NEVER_NULL;
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) expr;
            if (!keepsOperandsNotNull(operation.getOperator())) {
                return MAY_BE_NULL;
            }
            final int left = state(operation.getLeft(), types);
            final int right = state(operation.getRight(), types);
            return left == MAY_BE_NULL || right == MAY_BE_NULL ? MAY_BE_NULL : Math.max(left, right);
        }
        if (!(expr instanceof FunctionCallExpression) || ((FunctionCallExpression) expr).getNameExpression() != null) {
            return MAY_BE_NULL;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        final String name = call.getFunctionName().toUpperCase();
        final int arity = call.getArguments().size();
        switch (name) {
            case "RANDOM":
                if (arity == 0) {
                    return NEVER_NULL_OVER_RANDOM;
                }
                return arity == 1 && typedSeed(call.getArguments().get(0), types) ? NEVER_NULL_OVER_RANDOM : MAY_BE_NULL;
            case "ABS":
                return arity == 1 ? state(call.getArguments().get(0), types) : MAY_BE_NULL;
            case "ZEROIFNULL":
                return arity == 1 && holdsRandom(call.getArguments().get(0)) ? NEVER_NULL_OVER_RANDOM : NEVER_NULL;
            case "COALESCE":
            case "NVL":
            case "IFNULL":
                boolean settled = false;
                boolean overRandom = false;
                for (final Expression argument : call.getArguments()) {
                    final int each = state(argument, types);
                    settled |= each != MAY_BE_NULL;
                    overRandom |= holdsRandom(argument);
                }
                return !settled ? MAY_BE_NULL : overRandom ? NEVER_NULL_OVER_RANDOM : NEVER_NULL;
            default:
                return MAY_BE_NULL;
        }
    }

    /** Whether a RANDOM seed is of a SQL type, written as itself rather than cast: a VARIANT or a cast seed is read. */
    private static boolean typedSeed(final Expression seed, final TypeInferencer types) {
        if (seed instanceof CastExpression) {
            return false;
        }
        try {
            return !(types.infer(seed) instanceof VariantType);
        } catch (final RuntimeException untyped) {
            return false;
        }
    }

    private static boolean keepsOperandsNotNull(final BinaryOperator operator) {
        switch (operator) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                return true;
            default:
                return false;
        }
    }

    private static boolean holdsRandom(final Expression expr) {
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (call.getNameExpression() == null && "RANDOM".equalsIgnoreCase(call.getFunctionName())) {
                return true;
            }
            for (final Expression argument : call.getArguments()) {
                if (holdsRandom(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof BinaryOperationExpression) {
            return holdsRandom(((BinaryOperationExpression) expr).getLeft())
                || holdsRandom(((BinaryOperationExpression) expr).getRight());
        }
        return false;
    }
}
