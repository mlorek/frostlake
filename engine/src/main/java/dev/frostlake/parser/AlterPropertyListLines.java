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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a fault in the property list of an ALTER SCHEMA, an ALTER DATABASE or an ALTER
 * VIEW. It tells the list's first name before anything else; ALTER TABLE and ALTER TASK do not (all live-verified):
 *
 * <pre>
 *   ALTER DATABASE d SET DATA_RETENTION_TIME_IN_DAYS = 1,        the first name alone
 *   ALTER DATABASE d UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS x     the first name alone
 *   ALTER VIEW v SET COMMENT = 'x',                               the first name alone
 *   ALTER SCHEMA s UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS x        the first name, then the parse's own lines
 * </pre>
 *
 * <p>After the first name, ALTER SCHEMA's SET reads its list again as properties joined by commas. Each property's
 * value is told, and a comma not followed by a name tells the token after it. A property not followed by a comma
 * ends the list. A comma that ends the statement then tells what follows it:
 *
 * <pre>
 *   SET DATA_RETENTION_TIME_IN_DAYS = 1,                      the name, '1', then the end of input
 *   SET COMMENT = 'x', DATA_RETENTION_TIME_IN_DAYS = 1,       the name, ''x'', '1', then the end of input
 *   SET DATA_RETENTION_TIME_IN_DAYS = 1,, COMMENT = 'x'       the name, '1', then the second ','
 *   SET DATA_RETENTION_TIME_IN_DAYS = ,                       the name, ',', then the end of input
 *   SET DATA_RETENTION_TIME_IN_DAYS = 1 x                     the name and '1'
 *   SET DATA_RETENTION_TIME_IN_DAYS 1                         the name alone
 *   SET DATA_RETENTION_TIME_IN_DAYS = 1 MAX_DATA_EXTENSION_TIME_IN_DAYS = 2,
 *                                                             the name, '1', then the end of input
 * </pre>
 *
 * <p>A list that opens with no name — {@code SET , COMMENT = 'x'} — is told as the parse tells it.
 */
final class AlterPropertyListLines {

    private AlterPropertyListLines() {
    }

    /**
     * The lines for a statement whose first fault stands in such a list, or {@code lines} unchanged for any other.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param lines      the lines the parse reports
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (faultIndex < 0 ? spoken.get(i).getType() == Token.EOF : spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        int start = at - 1;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (at < 0 || start < 0 || start + 2 >= at || spoken.get(start).getType() != FrostlakeLexer.ALTER) {
            return lines;
        }
        final int kind = spoken.get(start + 1).getType();
        if (kind != FrostlakeLexer.SCHEMA && kind != FrostlakeLexer.DATABASE && kind != FrostlakeLexer.VIEW) {
            return lines;
        }
        int verb = -1;
        for (int i = start + 2; i < at && verb < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SET || type == FrostlakeLexer.UNSET) {
                verb = i;
            } else if (type != FrostlakeLexer.DOT && type != FrostlakeLexer.IF && type != FrostlakeLexer.EXISTS
                    && !isName(spoken.get(i))) {
                return lines;
            }
        }
        if (verb < 0 || !isName(spoken.get(verb + 1)) || spoken.get(verb + 1).getType() == FrostlakeLexer.TAG) {
            return lines;
        }
        final boolean unset = spoken.get(verb).getType() == FrostlakeLexer.UNSET;
        final Token first = spoken.get(verb + 1);
        // A database's or a view's SET is told so where a property is written with its '=', and a view's UNSET not.
        if (kind == FrostlakeLexer.VIEW && unset || kind != FrostlakeLexer.SCHEMA && !unset
                && spoken.get(verb + 2).getType() != FrostlakeLexer.EQ) {
            return lines;
        }
        if (kind != FrostlakeLexer.SCHEMA) {
            return Collections.singletonList(SyntaxErrorListener.sentence(first));
        }
        final List<String> told = new ArrayList<>();
        told.add(SyntaxErrorListener.sentence(first));
        if (unset) {
            told.addAll(lines);
            return told;
        }
        int name = verb + 1;
        while (name + 2 < spoken.size() && spoken.get(name + 1).getType() == FrostlakeLexer.EQ) {
            final Token value = spoken.get(name + 2);
            told.add(SyntaxErrorListener.sentence(value));
            if (ends(value)) {
                return told;
            }
            final int comma = value.getType() == FrostlakeLexer.COMMA ? name + 2 : name + 3;
            if (comma >= spoken.size() || spoken.get(comma).getType() != FrostlakeLexer.COMMA) {
                break;
            }
            if (comma + 1 >= spoken.size()) {
                return told;
            }
            final Token next = spoken.get(comma + 1);
            if (!isName(next)) {
                told.add(SyntaxErrorListener.sentence(next));
                return told;
            }
            name = comma + 1;
        }
        // The list ended at a property no comma follows: a comma that ends the statement tells what follows it.
        int end = name;
        while (end < spoken.size() && !ends(spoken.get(end))) {
            end++;
        }
        if (end < spoken.size() && spoken.get(end - 1).getType() == FrostlakeLexer.COMMA) {
            told.add(SyntaxErrorListener.sentence(spoken.get(end)));
        }
        return told;
    }

    private static boolean ends(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }

    /** Whether the token is a word: a plain or a quoted identifier, or a keyword. */
    private static boolean isName(final Token token) {
        if (SyntaxErrorListener.isIdentifierToken(token)) {
            return true;
        }
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }
}
