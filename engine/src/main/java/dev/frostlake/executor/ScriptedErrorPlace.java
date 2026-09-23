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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The place a positioned compilation error names, moved to the text a script reports it against. A
 * query compiled out of a script reports against its own text, where the account reports against the
 * block — or, for a scripting expression, against the expression it evaluates — so the place is moved
 * with the same line/column arithmetic {@code ExpressionSource} and {@link LeadingCommentOffset} use: a
 * place on the first line shifts by a column, a later one keeps its own.
 */
public final class ScriptedErrorPlace {

    private static final Pattern PLACE = Pattern.compile("^SQL compilation error: error line (\\d+) at position (\\d+)");

    private ScriptedErrorPlace() {
    }

    /**
     * The message with its place read inside a fragment that starts at {@code line}/{@code column} of the
     * enclosing text; unchanged when it names no place.
     *
     * @param message the error's message
     * @param line    the fragment's first line in the enclosing text, 1-based
     * @param column  the fragment's first column
     * @return the message placed in the enclosing text
     */
    public static String inEnclosingText(final String message, final int line, final int column) {
        final Matcher place = message == null ? null : PLACE.matcher(message);
        if (place == null || !place.find()) {
            return message;
        }
        final int writtenLine = Integer.parseInt(place.group(1));
        final int writtenColumn = Integer.parseInt(place.group(2));
        return placed(message, place, line + writtenLine - 1,
            writtenLine == 1 ? column + writtenColumn : writtenColumn);
    }

    /**
     * The message with its place — already in the enclosing text — read from a fragment starting at
     * {@code line}/{@code column} of it, with {@code firstLineShift} added to a place on the fragment's
     * first line; unchanged when it names no place or one before the fragment.
     *
     * @param message        the error's message
     * @param line           the fragment's first line, 1-based
     * @param column         the fragment's first column
     * @param firstLineShift the columns added to a place on the fragment's first line
     * @return the message placed in the fragment
     */
    public static String inFragment(final String message, final int line, final int column,
                                    final int firstLineShift) {
        final Matcher place = message == null ? null : PLACE.matcher(message);
        if (place == null || !place.find()) {
            return message;
        }
        final int writtenLine = Integer.parseInt(place.group(1));
        final int writtenColumn = Integer.parseInt(place.group(2));
        if (writtenLine < line || writtenLine == line && writtenColumn < column) {
            return message;
        }
        final int fragmentLine = writtenLine - line + 1;
        final int fragmentColumn = writtenLine == line ? writtenColumn - column : writtenColumn;
        return placed(message, place, fragmentLine, fragmentLine == 1 ? fragmentColumn + firstLineShift : fragmentColumn);
    }

    /**
     * The place a message names, as {line, column}, or null when it names none.
     *
     * @param message the error's message
     * @return the place, or null
     */
    public static int[] placeOf(final String message) {
        final Matcher place = message == null ? null : PLACE.matcher(message);
        if (place == null || !place.find()) {
            return null;
        }
        return new int[]{Integer.parseInt(place.group(1)), Integer.parseInt(place.group(2))};
    }

    private static String placed(final String message, final Matcher place, final int line, final int column) {
        return "SQL compilation error: error line " + line + " at position " + column + message.substring(place.end());
    }
}
