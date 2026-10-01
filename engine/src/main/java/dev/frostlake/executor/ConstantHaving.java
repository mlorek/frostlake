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

import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.functions.FunctionRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A HAVING predicate written of constants alone: literals under operators, casts, IS [NOT] NULL, BETWEEN, an IN
 * list and scalar calls over them, reading no column, no alias, no aggregate and drawing no new value. Live settles
 * such a predicate that folds without a fault while the statement compiles, so one that is not TRUE answers no row
 * without computing a grouping key or an aggregate: {@code SELECT v/0 AS k FROM t GROUP BY k HAVING FALSE},
 * {@code SELECT SUM(v/0) FROM t HAVING 1 = 0} and {@code ... HAVING ABS(-1) = 2} answer no row, where
 * {@code HAVING TRUE} raises the key's fault. One that faults is raised where the plan evaluates it: ahead of the
 * keys when a row reaches the grouping, after the aggregates without a GROUP BY (live-verified).
 */
final class ConstantHaving {

    /** The calls that draw a new value for every evaluation, which no plan settles in advance. */
    private static final Set<String> DRAWN = new HashSet<>(Arrays.asList(
        "RANDOM", "RANDSTR", "UUID_STRING", "UNIFORM", "NORMAL", "ZIPF", "SEQ1", "SEQ2", "SEQ4", "SEQ8"));

    private ConstantHaving() {
    }

    /**
     * Whether a predicate is constant.
     *
     * @param predicate the parsed predicate
     * @param functions the registry that tells a scalar call from an aggregate
     * @return true when nothing in it reads the query or draws a value
     */
    static boolean isConstant(final Expression predicate, final FunctionRegistry functions) {
        if (predicate instanceof LiteralExpression) {
            return true;
        }
        if (predicate instanceof UnaryOperationExpression) {
            return isConstant(((UnaryOperationExpression) predicate).getOperand(), functions);
        }
        if (predicate instanceof CastExpression) {
            return isConstant(((CastExpression) predicate).getExpression(), functions);
        }
        if (predicate instanceof IsNullExpression) {
            return isConstant(((IsNullExpression) predicate).getOperand(), functions);
        }
        if (predicate instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) predicate;
            return binary.getEscape() == null && isConstant(binary.getLeft(), functions)
                && isConstant(binary.getRight(), functions);
        }
        if (predicate instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) predicate;
            return isConstant(between.getValue(), functions) && isConstant(between.getLower(), functions)
                && isConstant(between.getUpper(), functions);
        }
        if (predicate instanceof InExpression) {
            final InExpression in = (InExpression) predicate;
            if (in.getSubquery() != null || in.getValues() == null || !isConstant(in.getValue(), functions)) {
                return false;
            }
            for (final Expression value : in.getValues()) {
                if (!isConstant(value, functions)) {
                    return false;
                }
            }
            return true;
        }
        if (predicate instanceof FunctionCallExpression) {
            return isConstantCall((FunctionCallExpression) predicate, functions);
        }
        return false;
    }

    private static boolean isConstantCall(final FunctionCallExpression call, final FunctionRegistry functions) {
        if (call.getFunctionName() == null || call.getNameExpression() != null || call.isStar() || call.isDistinct()
                || functions == null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (DRAWN.contains(name) || functions.hasAggregateFunction(name) || functions.getFunction(name) == null) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!isConstant(argument, functions)) {
                return false;
            }
        }
        return true;
    }
}
