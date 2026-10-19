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
 * Whether an expression answers anew each time it is computed: it calls RANDOM, UUID_STRING without arguments, or
 * reads a sequence's NEXTVAL, or a SEQ1/SEQ2/SEQ4/SEQ8 that reads the row's place in whichever stage computes it.
 * Such a value computed twice for one row is two values, so a stage that has computed it once keeps that value
 * rather than computing it again.
 */
public final class VolatileCalls {

    private VolatileCalls() {
    }

    /**
     * @param expression the expression
     * @return true when it writes a call that answers anew each time
     */
    public static boolean writes(final Expression expression) {
        if (expression == null) {
            return false;
        }
        final VolatileCallWalk walk = new VolatileCallWalk();
        expression.accept(walk);
        return walk.found();
    }

    /**
     * @param expression the expression
     * @return true when it calls SEQ1, SEQ2, SEQ4 or SEQ8, which read the row's place (see {@link RowOrdinal})
     */
    public static boolean readsRowPlace(final Expression expression) {
        if (expression == null) {
            return false;
        }
        final VolatileCallWalk walk = new VolatileCallWalk();
        expression.accept(walk);
        return walk.readsRowPlace();
    }
}
