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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text of a statement a block runs, as it stands once its binds are written out. A refusal is placed in the
 * statement as the block wrote it (live-verified): {@code SELECT :s + 1} over a BINARY variable is refused at the
 * {@code +} of that text, wherever the value written in the bind's place has pushed it. So a place found in the
 * text that ran is moved back over every bind before it on its line, and a place inside a bind's own text is the
 * bind's colon.
 *
 * <p>A RESULTSET's query stands in parentheses, and a refusal of a call's or an operator's arguments in it counts
 * its first line from the opening one: the call in {@code res := (SELECT ABS(:s))} over a BOOLEAN variable is at
 * position 8. An identifier refused there keeps its own column, {@code (SELECT missing FROM t)} position 7 (both
 * live-verified).
 */
public final class BoundText {

    /** A refusal of a call's or an operator's arguments, placed on a statement's first line. */
    private static final Pattern ARGUMENTS_REFUSED = Pattern.compile(
        "^(SQL compilation error: error line 1 at position )(\\d+)(\\n(?:Invalid argument types for function "
            + "|too many arguments for function |not enough arguments for function ))");

    /** The echo of a call an argument-count refusal names, between its brackets. */
    private static final Pattern ARITY_ECHO = Pattern.compile(
        "(too many arguments for function |not enough arguments for function )\\[(.*)\\]((?:,)? expected \\d+, got "
            + "\\d+)$", Pattern.DOTALL);

    /** A bind written out as a value cast to the variable's type, {@code (1)::NUMBER(38,0)}. */
    private static final Pattern WRITTEN_CAST = Pattern.compile("\\((.*)\\)::([A-Z_]+(?:\\(\\d+(?:,\\d+)?\\))?)");

    /** A refusal placed in a statement's text: the line, then the position. */
    private static final Pattern PLACED = Pattern.compile(
        "^(SQL compilation error: error line )(\\d+)( at position )(-?\\d+)(?=\\n)");

    private final SourcePosition origin;
    private final List<int[]> binds;

    /**
     * A text that ran in place of the one a block wrote.
     *
     * @param origin the origin the text runs under, which tells this statement's places from another's
     * @param binds  each bind written out: its line, its column, its written length and the length of what stands
     *               in its place, in the order written
     */
    public BoundText(final SourcePosition origin, final List<int[]> binds) {
        this.origin = origin;
        this.binds = binds;
    }

    /**
     * A refusal of a call's or an operator's arguments, counted {@code lead} columns further in on its first line;
     * any other refusal as it is.
     *
     * @param refused the refusal
     * @param lead    the columns to count before the statement's own first one
     * @return the refusal to raise
     */
    public static RuntimeException led(final RuntimeException refused, final int lead) {
        final String message = refused.getMessage();
        final Matcher place = message == null || refused.getClass() != RuntimeException.class ? null
            : ARGUMENTS_REFUSED.matcher(message);
        if (place == null || !place.find()) {
            return refused;
        }
        return new RuntimeException(place.group(1) + (Integer.parseInt(place.group(2)) + lead) + place.group(3)
            + message.substring(place.end()), refused.getCause());
    }

    /**
     * A placed refusal of a query assigned in parentheses, counted from the opening parenthesis: {@code lines}
     * lines further down, and on the query's first line {@code columns} columns further in — {@code res := (SELECT
     * missing FROM t)} names the identifier at position 8, and with the query on the next line at line 2
     * (live-verified). A refusal placed nowhere, or not placed at all, is as it is.
     *
     * @param refused the refusal
     * @param lines   the lines between the parenthesis and the query
     * @param columns the columns the query's first line counts before its own
     * @return the refusal to raise
     */
    public static RuntimeException displaced(final RuntimeException refused, final int lines, final int columns) {
        final String message = refused.getMessage();
        // A name the block itself does not declare is placed in the block by a rule of its own.
        final boolean statements = refused.getClass() == RuntimeException.class
            || refused.getClass() == InvalidQualifierException.class;
        final Matcher place = message == null || !statements ? null : PLACED.matcher(message);
        if (place == null || !place.find() || Integer.parseInt(place.group(2)) < 1) {
            return refused;
        }
        final int line = Integer.parseInt(place.group(2));
        final int position = Integer.parseInt(place.group(4));
        return new RuntimeException(place.group(1) + (line + lines) + place.group(3)
            + (line == 1 ? position + columns : position) + message.substring(place.end()), refused.getCause());
    }

    /**
     * A refusal of a call's argument count, its echo spelling each bind as the block wrote it: {@code res := (SELECT
     * ABS(:s, 1) FROM t)} is {@code too many arguments for function [ABS(:s, 1)]}, where the text that ran holds the
     * value written in the bind's place (live-verified). Each bind's written-out value is put back where the echo
     * spells it, the binds in the order written; any other refusal is as it is.
     *
     * @param refused the refusal
     * @param written the text as the block wrote it
     * @param ran     the text that ran, each bind written out
     * @param binds   each bind written out, as the substitution noted it
     * @return the refusal to raise
     */
    public static RuntimeException echoedAsWritten(final RuntimeException refused, final String written,
                                                   final String ran, final List<int[]> binds) {
        final String message = refused.getMessage();
        final Matcher echo = message == null || refused.getClass() != RuntimeException.class ? null
            : ARITY_ECHO.matcher(message);
        if (echo == null || !echo.find() || binds.isEmpty()) {
            return refused;
        }
        final int[] writtenStarts = lineStarts(written);
        final int[] ranStarts = lineStarts(ran);
        final StringBuilder spelled = new StringBuilder(echo.group(2));
        int from = 0;
        int shift = 0;
        int line = -1;
        for (final int[] bind : binds) {
            if (bind[0] != line) {
                line = bind[0];
                shift = 0;
            }
            if (line > writtenStarts.length || line > ranStarts.length) {
                return refused;
            }
            final int at = writtenStarts[line - 1] + bind[1];
            final int ranAt = ranStarts[line - 1] + bind[1] + shift;
            shift += bind[3] - bind[2];
            if (at + bind[2] > written.length() || ranAt + bind[3] > ran.length()) {
                return refused;
            }
            final String value = ran.substring(ranAt, ranAt + bind[3]);
            // A number is written out as (1)::NUMBER(38,0), which the echo spells as the cast it is.
            final Matcher cast = WRITTEN_CAST.matcher(value);
            final String echoed = cast.matches() ? "CAST(" + cast.group(1) + " AS " + cast.group(2) + ")" : value;
            final int found = spelled.indexOf(echoed, from);
            if (found < 0) {
                continue;
            }
            final String bindText = written.substring(at, at + bind[2]);
            spelled.replace(found, found + echoed.length(), bindText);
            from = found + bindText.length();
        }
        return new RuntimeException(message.substring(0, echo.start(2)) + spelled + message.substring(echo.end(2)),
            refused.getCause());
    }

    /** The offset each line of a text starts at. */
    private static int[] lineStarts(final String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        final int[] starts = new int[lines];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[line++] = i + 1;
            }
        }
        return starts;
    }

    /** The origin the text runs under. */
    public SourcePosition origin() {
        return origin;
    }

    /**
     * Whether a place in the text that ran is where a bind was written out: the first column of what stands in
     * the bind's place.
     *
     * @param line   the place's line, 1-based
     * @param column its column in the text that ran
     * @return whether a written-out bind begins there
     */
    public boolean beginsABind(final int line, final int column) {
        int shift = 0;
        for (final int[] bind : binds) {
            if (bind[0] != line) {
                continue;
            }
            if (bind[1] + shift == column) {
                return true;
            }
            shift += bind[3] - bind[2];
        }
        return false;
    }

    /**
     * The column, in the text as written, of a place in the text that ran.
     *
     * @param line   the place's line, 1-based
     * @param column its column in the text that ran
     * @return its column in the text the block wrote
     */
    public int written(final int line, final int column) {
        int shift = 0;
        for (final int[] bind : binds) {
            if (bind[0] != line) {
                continue;
            }
            final int ran = bind[1] + shift;
            if (column < ran) {
                break;
            }
            if (column < ran + bind[3]) {
                return bind[1];
            }
            shift += bind[3] - bind[2];
        }
        return column - shift;
    }
}
