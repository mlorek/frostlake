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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.functions.FunctionRegistry;

import java.util.Map;
import java.util.Set;

/**
 * What a virtual column's expression cannot hold. The column's value follows the row alone, so its expression reads
 * no query, no bind variable, no session variable and no column by its place, and calls no aggregate, no window
 * function, no user's function and no function whose answer moves between two calls: {@code b INT AS (SUM(a))} is
 * " Invalid usage of SUM aggregate function in B virtual column definition.", {@code a NUMBER AS (f1())} over a
 * user's F1 "Invalid usage of f1 user defined function in A virtual column definition." and {@code j INT AS
 * (RANDOM())} "Invalid usage of RANDOM non deterministic function in J virtual column definition." (live-verified).
 * A call is refused where it stands, anything else at the column's name.
 */
public final class VirtualColumnUsage {

    private VirtualColumnUsage() {
    }

    /**
     * The first part of an expression a virtual column's definition cannot hold, in the words its refusal gives it.
     *
     * @param expression the expression
     * @param functions  the built-in functions
     * @param scope      the evaluator over the columns the definition reads
     * @param spellings  each function name the expression writes, by its canonical name, as written
     * @return what the refusal names after "Invalid usage of" - {@code SUM aggregate function}, {@code subquery} -
     *         or null when the expression holds nothing a definition cannot
     */
    public static String refused(final Expression expression, final FunctionRegistry functions,
                                 final ExpressionEvaluator scope, final Map<String, String> spellings) {
        final VirtualColumnUsageWalk walk = new VirtualColumnUsageWalk(functions, scope, spellings);
        expression.accept(walk);
        return walk.refused();
    }

    /**
     * Where the refused part of an expression sits in its text: the call a function's refusal names, or null for a
     * part refused at the column's name, and for an expression that holds nothing a definition cannot.
     *
     * @param expression the expression
     * @param functions  the built-in functions
     * @param scope      the evaluator over the columns the definition reads
     * @param spellings  each function name the expression writes, by its canonical name, as written
     * @return the call's place within the expression's text, or null
     */
    public static SourcePosition refusedAt(final Expression expression, final FunctionRegistry functions,
                                           final ExpressionEvaluator scope, final Map<String, String> spellings) {
        final VirtualColumnUsageWalk walk = new VirtualColumnUsageWalk(functions, scope, spellings);
        expression.accept(walk);
        return walk.refusedAt();
    }

    /**
     * The columns an expression reads, by the names its references give them.
     *
     * @param expression the expression
     * @return the names, in the order first read
     */
    public static Set<String> columnsRead(final Expression expression) {
        final ColumnsReadWalk walk = new ColumnsReadWalk();
        expression.accept(walk);
        return walk.read();
    }
}
