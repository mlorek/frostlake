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

package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The refusals of operands that read, made as the statement compiles and ahead of everything it names: a value
 * after a quantifier ({@link QuantifiedComparisonOperand}) and OVER after what is no call ({@link WindowedOperand}).
 * The account makes one of them, the first in the order it reads the statement: the refusals of a value before
 * those that name a kind of function, a statement's own text before the queries its WITH clause names, and
 * otherwise in the order written (live-verified):
 *
 * <pre>
 *   SELECT x OVER (), 1 = ANY 1 FROM t                          invalid function 'X'
 *   SELECT 1 = ANY 1, x OVER () FROM t                          Invalid query block: 1.
 *   WITH c AS (SELECT 1 = ANY 1 x) SELECT x OVER () FROM c      invalid function 'X'
 *   SELECT ABS(x) OVER (), y OVER () FROM t                     invalid function 'Y'
 *   SELECT nosuchfn(1), 1 = ANY 1                               Invalid query block: 1.
 * </pre>
 */
public final class OperandRefusals {

    private OperandRefusals() {
    }

    /**
     * Refuse the first such operand.
     *
     * @param script the parsed script, one statement of it, or an expression
     */
    public static void require(final ParseTree script) {
        final OperandRefusal refusal = first(script);
        if (refusal != null) {
            throw new RuntimeException(SqlCompilationError.of(refusal.message()));
        }
    }

    /**
     * As {@link #require}, for a statement ahead of its compilation: an operand the compilation judges first waits
     * for it (see {@link WindowedOperand#waitsForCompilation}).
     *
     * @param script the parsed script or one statement of it
     */
    public static void requireOfStatement(final ParseTree script) {
        final OperandRefusal refusal = firstOfStatement(script);
        if (refusal != null) {
            throw new RuntimeException(SqlCompilationError.of(refusal.message()));
        }
    }

    /**
     * As {@link #first}, for a statement ahead of its compilation: an operand the compilation judges first waits for
     * it (see {@link WindowedOperand#waitsForCompilation}).
     *
     * @param script the parsed script or one statement of it
     * @return the refusal, or null when the text makes none ahead of its compilation
     */
    public static OperandRefusal firstOfStatement(final ParseTree script) {
        return first(script, true);
    }

    /**
     * The first such refusal a parsed text makes.
     *
     * @param script the parsed script, one statement of it, or an expression
     * @return the refusal, or null when the text makes none
     */
    public static OperandRefusal first(final ParseTree script) {
        return first(script, false);
    }

    private static OperandRefusal first(final ParseTree script, final boolean ofStatement) {
        if (script == null) {
            return null;
        }
        OperandRefusal best = null;
        boolean bestNamed = false;
        final Deque<ParseTree> pending = new ArrayDeque<>();
        final Deque<Boolean> pendingNamed = new ArrayDeque<>();
        pending.push(script);
        pendingNamed.push(Boolean.FALSE);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            final boolean named = pendingNamed.pop() || node instanceof FrostlakeParser.CteDefinitionContext;
            OperandRefusal found = QuantifiedComparisonOperand.of(node);
            if (found == null && !(ofStatement && WindowedOperand.waitsForCompilation(node))) {
                found = WindowedOperand.of(node);
            }
            if (found != null && (best == null || precedes(found, named, best, bestNamed))) {
                best = found;
                bestNamed = named;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
                pendingNamed.push(named);
            }
        }
        return best;
    }

    /** Whether one refusal is made before another: a value's first, then a statement's own, then the earlier. */
    private static boolean precedes(final OperandRefusal one, final boolean oneNamed, final OperandRefusal other,
                                    final boolean otherNamed) {
        if (one.isOfValue() != other.isOfValue()) {
            return one.isOfValue();
        }
        if (oneNamed != otherNamed) {
            return !oneNamed;
        }
        return one.order() < other.order();
    }
}
