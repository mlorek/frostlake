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
 * The lines the account reports for the clauses of a task, a contact and a property list that it reads further than
 * Frostlake's grammar does before it gives up (all live-verified):
 *
 * <pre>
 *   … QUERY_TAG = (('a')) AS SELECT 1                   ')' — a list inside the value's list reads a string as a
 *   ALTER TASK t SET QUERY_TAG = (('a' 'b'))            ''b''   property's name and wants its '='; each such
 *   ALTER TASK t SET QUERY_TAG = (('a'), ('b'))         ')' and ')'   name is a line of its own
 *   ALTER TASK t SET QUERY_TAG = (('a', 'b'))           ',' and ')'
 *   CREATE TASK t EXECUTE AS USER CURRENT_USER() …      '(' alone
 *   CREATE TASK t EXECUTE AS USER IDENTIFIER(…) …       the '(' after IDENTIFIER, alone: the word is the user's name
 *   CREATE TASK t SCHEDULE = '1 MINUTE' WITH TAG (…)    'WITH' alone, and so after any other clause
 *   CREATE TASK t COMMENT = 'x' TAG (…) | AS …          'TAG', where the value of a property is read as complete
 *   CREATE TASK t COMMENT = 'x' CONTACT (…) …           'CONTACT'
 *   ALTER TABLE t SET CONTACT (SUPPORT = c) COMMENT …   'COMMENT' alone: the list wants its '=' and a contact,
 *   ALTER TABLE t SET CONTACT (…) = d COMMENT = 'x'     'COMMENT' alone: and nothing after the contact
 *   EXECUTE TASK r USING                                '&lt;EOF&gt;': the word after USING is the first property's
 *   EXECUTE TASK r RETRY                                '&lt;EOF&gt;': RETRY wants its LAST
 *   EXECUTE TASK r USING CONFIG = '{}' FOO | RETRY      '&lt;EOF&gt;': a word after a value is the next property's
 *   EXECUTE TASK r USING CONFIG = '{}' FOO BAR          'BAR'        name, and wants its '='
 * </pre>
 */
final class TaskClauseLines {

    private TaskClauseLines() {
    }

    /**
     * The lines for a statement whose first fault is one of the shapes above, or the lines Frostlake worked out for
     * any other.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty()) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        if (fault < 2) {
            return lines;
        }
        final List<String> inner = innerListNames(spoken, faults, lines);
        if (inner != null) {
            return inner;
        }
        final Token at = spoken.get(fault);
        final int type = at.getType();
        if (executesAsUser(spoken, fault)) {
            // The word after USER is the user's name, whatever it is, and the bracket after it is refused.
            final int bracket = "IDENTIFIER".equalsIgnoreCase(at.getText()) ? fault + 1 : fault;
            return spoken.get(bracket).getType() == FrostlakeLexer.LPAREN ? one(spoken.get(bracket)) : lines;
        }
        if (opensTask(spoken) && type == FrostlakeLexer.WITH && fault + 1 < spoken.size()
                && (spoken.get(fault + 1).getType() == FrostlakeLexer.TAG
                    || spoken.get(fault + 1).getType() == FrostlakeLexer.CONTACT)) {
            return one(at);
        }
        if (opensTask(spoken) && fault >= 3
                && (spoken.get(fault - 1).getType() == FrostlakeLexer.TAG
                    || spoken.get(fault - 1).getType() == FrostlakeLexer.CONTACT)
                && spoken.get(fault - 2).getType() == FrostlakeLexer.STRING_LITERAL
                && spoken.get(fault - 3).getType() == FrostlakeLexer.EQ) {
            return one(spoken.get(fault - 1));
        }
        final int afterContact = afterContactList(spoken);
        if (afterContact >= 0 && afterContact <= fault && spoken.get(afterContact).getType() != Token.EOF) {
            return one(spoken.get(afterContact));
        }
        if (unfinishedTaskRun(spoken, fault)) {
            return one(spoken.get(fault + 1));
        }
        return lines;
    }

    /**
     * The lines for faults at strings a list inside a property's list value reads as properties' names, each told at
     * the token after its string, or null when no fault is such a string or a line stands for no fault.
     */
    private static List<String> innerListNames(final List<Token> spoken, final List<Integer> faults,
                                               final List<String> lines) {
        if (lines.size() != faults.size()) {
            return null;
        }
        boolean named = false;
        final List<String> moved = new ArrayList<String>();
        for (final Integer fault : faults) {
            final int at = spokenIndex(spoken, fault.intValue());
            if (at < 0) {
                return null;
            }
            final boolean name = namesAnInnerProperty(spoken, at);
            named |= name;
            moved.add(SyntaxErrorListener.sentence(spoken.get(name ? at + 1 : at)));
        }
        return named ? moved : null;
    }

    /**
     * Whether the token at {@code at} is a string a list inside a property's parenthesized value reads as a property's
     * name: after the inner list's '(' or a comma of it, and followed by anything but its '='.
     */
    private static boolean namesAnInnerProperty(final List<Token> spoken, final int at) {
        if (at < 3 || at + 1 >= spoken.size() || spoken.get(at).getType() != FrostlakeLexer.STRING_LITERAL
                || spoken.get(at + 1).getType() == FrostlakeLexer.EQ
                || spoken.get(at - 1).getType() != FrostlakeLexer.LPAREN
                    && spoken.get(at - 1).getType() != FrostlakeLexer.COMMA) {
            return false;
        }
        final int inner = openBracketBefore(spoken, at);
        final int outer = inner < 1 ? -1 : openBracketBefore(spoken, inner);
        return outer >= 1 && spoken.get(outer - 1).getType() == FrostlakeLexer.EQ;
    }

    /** The nearest '(' before {@code at} that is left open there, or -1 for none. */
    private static int openBracketBefore(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** Whether the fault is the word after {@code EXECUTE AS USER}, or the bracket after that word. */
    private static boolean executesAsUser(final List<Token> spoken, final int fault) {
        final int user = spoken.get(fault).getType() == FrostlakeLexer.LPAREN ? fault - 2 : fault - 1;
        return user >= 2 && spoken.get(user).getType() == FrostlakeLexer.USER
            && spoken.get(user - 1).getType() == FrostlakeLexer.AS
            && spoken.get(user - 2).getType() == FrostlakeLexer.EXECUTE && opensTask(spoken);
    }

    /** Whether the statement is a CREATE TASK, OR REPLACE and IF NOT EXISTS aside. */
    private static boolean opensTask(final List<Token> spoken) {
        if (spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return false;
        }
        for (int i = 1; i < Math.min(spoken.size(), 4); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.TASK) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first token an ALTER's {@code SET CONTACT ( … )} cannot take after it: the one after the bracket that closes
     * the list, or, where an '=' and a contact follow the list, the one after the contact; -1 for another statement
     * or for an '=' followed by anything but a name.
     */
    private static int afterContactList(final List<Token> spoken) {
        if (spoken.get(0).getType() != FrostlakeLexer.ALTER) {
            return -1;
        }
        for (int i = 2; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i - 1).getType() == FrostlakeLexer.CONTACT
                    && spoken.get(i - 2).getType() == FrostlakeLexer.SET) {
                int depth = 0;
                for (int j = i; j < spoken.size(); j++) {
                    final int type = spoken.get(j).getType();
                    if (type == FrostlakeLexer.LPAREN) {
                        depth++;
                    } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                        if (j + 1 < spoken.size() && spoken.get(j + 1).getType() == FrostlakeLexer.EQ) {
                            // A contact is a name: what else stands after the '=' is refused where it stands.
                            return j + 2 < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(j + 2))
                                ? j + 3 : -1;
                        }
                        return j + 1;
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether the fault is a word an {@code EXECUTE TASK} reads on from: USING or RETRY after the task's name, or
     * after a property's value, with nothing after it, or a word after a property's value, the next property's name.
     */
    private static boolean unfinishedTaskRun(final List<Token> spoken, final int fault) {
        if (spoken.get(0).getType() != FrostlakeLexer.EXECUTE || spoken.get(1).getType() != FrostlakeLexer.TASK
                || fault + 1 >= spoken.size()) {
            return false;
        }
        final Token at = spoken.get(fault);
        if (at.getType() == FrostlakeLexer.USING || at.getType() == FrostlakeLexer.RETRY) {
            return spoken.get(fault + 1).getType() == Token.EOF && (fault == 3 || afterAValue(spoken, fault));
        }
        return SyntaxErrorListener.isNameLike(at) && afterAValue(spoken, fault)
            && spoken.get(fault + 1).getType() != FrostlakeLexer.EQ;
    }

    /** Whether the token before {@code at} ends a property's value: a value right after a USING list's '='. */
    private static boolean afterAValue(final List<Token> spoken, final int at) {
        return at >= 6 && spoken.get(at - 2).getType() == FrostlakeLexer.EQ
            && spoken.get(at - 1).getType() != FrostlakeLexer.LPAREN;
    }

    private static List<String> one(final Token token) {
        return Collections.singletonList(SyntaxErrorListener.sentence(token));
    }

    /** The index in {@code spoken} of the token with the stream index {@code tokenIndex}; -1 for the end of input. */
    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
