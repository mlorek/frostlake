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

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The lines live reports for a DECLARE section whose cursor reads a SHOW: the account reads SHOW as the name of the
 * cursor's source and refuses the word after it, then reads what follows as further declarations, a name and a type
 * each — so in every run of plain words the third is refused, and the count starts again after it or after anything
 * that is no plain word: {@code DECLARE c CURSOR FOR SHOW TABLES IN a b c d e f;} is refused at the TABLES, the c and
 * the f, and {@code SHOW x y z} at the x and the z (live-verified). A word the account reserves (the reference page's
 * list, where DATABASE, SCHEMA and VIEW are reserved in a SHOW) is no plain word.
 */
final class CursorShowLines {

    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "ACCOUNT", "ALL", "ALTER", "AND", "ANY", "AS", "BETWEEN", "BY", "CASE", "CAST", "CHECK", "COLUMN", "CONNECT",
        "CONNECTION", "CONSTRAINT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
        "CURRENT_USER", "DATABASE", "DELETE", "DISTINCT", "DROP", "ELSE", "EXISTS", "FALSE", "FOLLOWING", "FOR", "FROM",
        "FULL", "GRANT", "GROUP", "GSCLUSTER", "HAVING", "ILIKE", "IN", "INCREMENT", "INNER", "INSERT", "INTERSECT",
        "INTO", "IS", "ISSUE", "JOIN", "LATERAL", "LEFT", "LIKE", "LOCALTIME", "LOCALTIMESTAMP", "MINUS", "NATURAL",
        "NOT", "NULL", "OF", "ON", "OR", "ORDER", "ORGANIZATION", "QUALIFY", "REGEXP", "REVOKE", "RIGHT", "RLIKE",
        "ROW", "ROWS", "SAMPLE", "SCHEMA", "SELECT", "SET", "SOME", "START", "TABLE", "TABLESAMPLE", "THEN", "TO",
        "TRIGGER", "TRUE", "TRY_CAST", "UNION", "UNIQUE", "UPDATE", "USING", "VALUES", "VIEW", "WHEN", "WHENEVER",
        "WHERE", "WINDOW", "WITH"));

    private CursorShowLines() {
    }

    /**
     * The lines for a block that opens with {@code DECLARE name CURSOR FOR SHOW} and a word, or null when the block is
     * of another shape, when the declaration has no semicolon, or when a fault lies past that semicolon.
     *
     * @param spoken  the default-channel tokens, end of input included
     * @param faults  the token indexes of the statement's faults, in order
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults) {
        if (spoken.size() < 7 || spoken.get(0).getType() != FrostlakeLexer.DECLARE
                || spoken.get(2).getType() != FrostlakeLexer.CURSOR || spoken.get(3).getType() != FrostlakeLexer.FOR
                || spoken.get(4).getType() != FrostlakeLexer.SHOW || !isPlainWord(spoken.get(1))
                || !isPlainWord(spoken.get(5))) {
            return null;
        }
        int end = -1;
        for (int i = 5; i < spoken.size() && end < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                end = i;
            }
        }
        if (end < 0) {
            return null;
        }
        final int first = spoken.get(5).getTokenIndex();
        final int last = spoken.get(end).getTokenIndex();
        for (final Integer fault : faults) {
            if (fault.intValue() < first || fault.intValue() > last) {
                return null;
            }
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(5)));
        int run = 0;
        for (int i = 5; i < end; i++) {
            if (!isPlainWord(spoken.get(i))) {
                run = 0;
            } else if (++run == 3) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                run = 0;
            }
        }
        return lines;
    }

    /** Whether a token is a word the account may read as a name: a quoted name, or a word it does not reserve. */
    private static boolean isPlainWord(final Token token) {
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return true;
        }
        final String text = token.getText();
        if (token.getType() == Token.EOF || text == null || text.isEmpty()
                || !(Character.isLetter(text.charAt(0)) || text.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }
        return !RESERVED.contains(text.toUpperCase(Locale.ROOT));
    }
}
