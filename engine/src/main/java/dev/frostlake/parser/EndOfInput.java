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

/**
 * Where a statement's input ENDS, which is the anchor live gives every refusal that ran out of text —
 * the position one PAST the last character, not the last character itself. Text ending in a newline
 * therefore anchors at column 0 of the line after the last one written.
 */
public final class EndOfInput {

    private EndOfInput() {
    }

    /**
     * The 1-based line the input ends on.
     *
     * @param sql the statement text
     * @return its last line number
     */
    public static int line(final String sql) {
        int lines = 1;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * The 0-based column just past the last character on the input's last line.
     *
     * @param sql the statement text
     * @return that column
     */
    public static int position(final String sql) {
        final int lastBreak = sql.lastIndexOf('\n');
        return sql.length() - (lastBreak + 1);
    }
}
