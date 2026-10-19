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
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a scope SHOW LOCKS and SHOW TRANSACTIONS do not take (all live-verified). Both read
 * {@code IN ACCOUNT} alone, so whatever else follows the IN is refused where it stands, and the token after it is
 * named too when the two could be a kind and its name — or two names:
 *
 * <pre>
 *   SHOW LOCKS IN DATABASE abc          'DATABASE' then 'abc'      SCHEMA, VIEW, WAREHOUSE, USER, STAGE … alike
 *   SHOW LOCKS IN abc def               'abc' then 'def'
 *   SHOW LOCKS IN DATABASE 'abc'        'DATABASE' then ''abc''    a text, a quoted name and a '(' are named
 *   SHOW LOCKS IN DATABASE 1            'DATABASE' alone           a number and a $variable are not
 *   SHOW LOCKS IN abc.def               'abc' alone
 *   SHOW LOCKS IN TABLE abc             'TABLE' alone              the one kind that takes its name with it
 *   SHOW LOCKS IN 1 abc                 '1' alone                  nor does anything follow a number or a text
 * </pre>
 */
final class ShowLockScopeLines {

    private ShowLockScopeLines() {
    }

    /**
     * The lines for a SHOW LOCKS or SHOW TRANSACTIONS given such a scope, or null for any other statement, for a
     * scope of ACCOUNT, and when another statement follows.
     *
     * @param spoken the default-channel tokens of the text parsed, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken) {
        int at = 0;
        if (spoken.get(at++).getType() != FrostlakeLexer.SHOW) {
            return null;
        }
        if (spoken.get(at).getType() == FrostlakeLexer.TERSE) {
            at++;
        }
        final int listed = spoken.get(at++).getType();
        if (listed != FrostlakeLexer.LOCKS && listed != FrostlakeLexer.TRANSACTIONS
                || spoken.get(at++).getType() != FrostlakeLexer.IN) {
            return null;
        }
        final Token scope = spoken.get(at);
        if (scope.getType() == Token.EOF || scope.getType() == FrostlakeLexer.SEMI
                || scope.getType() == FrostlakeLexer.ACCOUNT) {
            return null;
        }
        for (int i = at; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI && spoken.get(i + 1).getType() != Token.EOF) {
                // Another statement follows, and is read on its own.
                return null;
            }
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(scope));
        final Token after = spoken.get(at + 1);
        if (scope.getType() != FrostlakeLexer.TABLE && isName(scope)
                && (isName(after) || after.getType() == FrostlakeLexer.STRING_LITERAL
                    || after.getType() == FrostlakeLexer.LPAREN)) {
            lines.add(SyntaxErrorListener.sentence(after));
        }
        return lines;
    }

    /** Whether a token is a word or a quoted name: what a kind or a name is written as. */
    private static boolean isName(final Token token) {
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return true;
        }
        final String text = token.getText();
        return token.getType() != Token.EOF && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }
}
