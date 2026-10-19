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

import dev.frostlake.executor.expressions.SourcePosition;

/**
 * A statement that BEGINS with comments reports its positions from the first real token, not from the
 * top of the text. Measured across nine shapes, and the rule is one line: the first token after the
 * leading comments becomes line 1, position 0, and everything after it shifts by the same amount.
 *
 * <pre>
 *   NULL                        line 1 position 0
 *      NULL                     line 1 position 3   whitespace ALONE still counts
 *   &#47;* c *&#47; NULL              line 1 position 0
 *      &#47;* c *&#47;   NULL         line 1 position 0   the whitespace around it goes too
 *   &#47;* c *&#47;\nNULL             line 1 position 0   the newline does not advance the line
 *   &#47;* a\nb *&#47; NULL           line 1 position 0   nor does one INSIDE the comment
 *   -- c\nNULL                  line 1 position 0
 *   &#47;* c *&#47; SELECT UPPER(o)   line 1 position 7   the offset SHIFTS, it is not zeroed
 *   SELECT &#47;* c *&#47; UPPER(o)   line 1 position 15  an INTERIOR comment counts normally
 * </pre>
 *
 * <p>The last two are what make this a re-basing rather than a stripping: only text BEFORE the first
 * token disappears, and a comment between tokens is ordinary text that pushes the offset along.
 */
public final class LeadingCommentOffset {

    private static final ThreadLocal<SourcePosition> ORIGIN = new ThreadLocal<SourcePosition>();

    /** The text running with its binds written out, whose places are told in the text as written. */
    private static final ThreadLocal<BoundText> BOUND = new ThreadLocal<BoundText>();

    private LeadingCommentOffset() {
    }

    /**
     * Where the statement's first real token begins, or null when nothing needs re-basing — which is
     * every statement that does not open with a comment, whitespace included.
     *
     * @param sql the statement's text
     * @return the first token's line and column, or null
     */
    public static SourcePosition of(final String sql) {
        if (sql == null) {
            return null;
        }
        int index = 0;
        int line = 1;
        int column = 0;
        boolean sawComment = false;
        while (index < sql.length()) {
            final char c = sql.charAt(index);
            if (c == '\n') {
                line++;
                column = 0;
                index++;
            } else if (Character.isWhitespace(c)) {
                column++;
                index++;
            } else if (c == '-' && index + 1 < sql.length() && sql.charAt(index + 1) == '-') {
                sawComment = true;
                while (index < sql.length() && sql.charAt(index) != '\n') {
                    index++;
                    column++;
                }
            } else if (c == '/' && index + 1 < sql.length() && sql.charAt(index + 1) == '*') {
                sawComment = true;
                index += 2;
                column += 2;
                while (index < sql.length()
                        && !(sql.charAt(index) == '*' && index + 1 < sql.length()
                            && sql.charAt(index + 1) == '/')) {
                    if (sql.charAt(index) == '\n') {
                        line++;
                        column = 0;
                    } else {
                        column++;
                    }
                    index++;
                }
                index += 2;
                column += 2;
            } else {
                return sawComment ? new SourcePosition(line, column) : null;
            }
        }
        return null;
    }

    /**
     * Note this statement's re-basing origin for the rest of its execution.
     *
     * @param origin the first real token's place, or null for no re-basing
     * @return whatever origin it displaced, for {@link #end}
     */
    public static SourcePosition begin(final SourcePosition origin) {
        final SourcePosition previous = ORIGIN.get();
        ORIGIN.set(origin);
        return previous;
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
     * A statement-relative place re-based past the leading comments, or unchanged when there are none.
     * The arithmetic is {@link dev.frostlake.executor.expressions.ExpressionSource#resolve}'s, inverted:
     * a place on the origin's own line loses the origin's column, a later line keeps its column, and the
     * line count drops by however many lines the comments occupied.
     *
     * @param line     the place's line, 1-based
     * @param position its column
     * @return the two re-based, as {line, position}
     */
    public static int[] rebase(final int line, final int position) {
        final SourcePosition origin = ORIGIN.get();
        final BoundText bound = BOUND.get();
        // Only the statement the binds were written into is moved: one it runs has an origin of its own.
        return rebase(origin, line, bound != null && bound.origin() == origin ? bound.written(line, position)
            : position);
    }

    /**
     * Whether a place of the statement that runs is where one of its binds was written out (see {@link BoundText}).
     *
     * @param line     the place's line in the text that runs, 1-based
     * @param position its column there
     * @return whether a written-out bind begins at the place
     */
    public static boolean beginsABind(final int line, final int position) {
        final BoundText bound = BOUND.get();
        return bound != null && bound.origin() == ORIGIN.get() && bound.beginsABind(line, position);
    }

    /**
     * Note the text about to run with its binds written out.
     *
     * @param bound the text, or null for none
     * @return whatever it displaced, for {@link #unbind}
     */
    public static BoundText bind(final BoundText bound) {
        final BoundText previous = BOUND.get();
        BOUND.set(bound);
        return previous;
    }

    /** Put back whatever {@link #bind} displaced; null clears. */
    public static void unbind(final BoundText previous) {
        if (previous == null) {
            BOUND.remove();
        } else {
            BOUND.set(previous);
        }
    }

    /**
     * {@link #rebase(int, int)} against an origin given, as {@link #of} reads one from a text.
     *
     * @param origin   the first real token's place, or null for no re-basing
     * @param line     the place's line, 1-based
     * @param position its column
     * @return the two re-based, as {line, position}
     */
    public static int[] rebase(final SourcePosition origin, final int line, final int position) {
        if (origin == null || line < origin.getLine()) {
            return new int[]{line, position};
        }
        final int rebasedColumn = line == origin.getLine()
            ? position - origin.getCharPositionInLine() : position;
        return new int[]{line - origin.getLine() + 1, Math.max(0, rebasedColumn)};
    }
}
