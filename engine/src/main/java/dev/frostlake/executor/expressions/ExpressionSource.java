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
 * Where the expression currently being evaluated started inside its statement — the missing half of a
 * reported position.
 *
 * <p>Operators are handed expressions as extracted TEXT, so by the time one is parsed nothing in the
 * AST knows where that text came from. {@link SourcePosition} therefore records only a
 * fragment-relative place (which keeps the by-text AST cache correct), and this class supplies the
 * origin to add back: the statement line the fragment starts on, and the column it starts at.
 *
 * <p>Scoped to the thread, like the statement clock, because a session's statements run on the
 * caller's thread. An expression evaluated with no origin set — a synthesised one, say, such as the
 * {@code t.a} that star-expansion invents and that never appeared in the source — reports no position
 * at all, which is the honest answer for text the user never wrote.
 */
public final class ExpressionSource {

    private static final ThreadLocal<SourcePosition> ORIGIN = new ThreadLocal<SourcePosition>();

    private ExpressionSource() {
    }

    /**
     * Note where the fragment about to be evaluated begins, returning whatever origin it displaced so
     * a nested evaluation can put it back.
     */
    public static SourcePosition begin(final SourcePosition origin) {
        final SourcePosition previous = ORIGIN.get();
        ORIGIN.set(origin);
        return previous;
    }

    /**
     * Note a fragment that lives INSIDE the fragment already in force, composing the two origins.
     *
     * <p>Needed wherever a body is re-parsed from its own text and then walked item by item: a view's
     * definition sits at some offset in the CREATE statement, and each select item sits at some offset
     * in that definition, so a node's place in the STATEMENT is the two composed. {@link #begin}
     * replaces the origin outright, which is right for one level and silently wrong for two — the inner
     * origin would win and the body's own offset would vanish.
     *
     * <p>Composition is {@link #resolve}'s arithmetic applied to the origin itself, so the line/column
     * rule is stated in exactly one place: a fragment starting on the outer fragment's FIRST line is
     * displaced by its column, one starting later keeps its own.
     *
     * @param within where the inner fragment begins, relative to the fragment currently in force
     * @return whatever origin it displaced, for {@link #end}
     */
    public static SourcePosition beginNested(final SourcePosition within) {
        final SourcePosition composed = ORIGIN.get() == null ? within : resolve(within);
        return begin(composed);
    }

    /** Put back whatever {@link #begin} displaced; null clears. */
    public static void end(final SourcePosition previous) {
        if (previous == null) {
            ORIGIN.remove();
        } else {
            ORIGIN.set(previous);
        }
    }

    /**
     * The statement-relative place of a fragment-relative one, or null when no origin is set.
     *
     * <p>A node on the fragment's FIRST line is offset by the origin's column; a node on a later line
     * keeps its own column, because the origin's column only displaces the line it starts on.
     */
    public static SourcePosition resolve(final SourcePosition withinFragment) {
        final SourcePosition origin = ORIGIN.get();
        if (origin == null || withinFragment == null) {
            return null;
        }
        final int line = origin.getLine() + withinFragment.getLine() - 1;
        final int column = withinFragment.getLine() == 1
            ? origin.getCharPositionInLine() + withinFragment.getCharPositionInLine()
            : withinFragment.getCharPositionInLine();
        return new SourcePosition(line, column);
    }

    /** Whether an origin is set — for callers that only build a message when one can be positioned. */
    public static boolean hasOrigin() {
        return ORIGIN.get() != null;
    }
}
