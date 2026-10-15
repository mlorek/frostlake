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

import dev.frostlake.executor.LeadingCommentOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a clause written out of place after a key constraint — a foreign key's MATCH or its
 * ON UPDATE / ON DELETE rule after a property, a second MATCH, a rule given twice, a MATCH or a rule after a
 * PRIMARY KEY or a UNIQUE, a VALIDATE with no ENABLE before it (all live-verified). A foreign key reads
 * {@code [MATCH kind] [ON UPDATE … | ON DELETE …]{0,2} property*}; the first word that breaks the order is where
 * the report starts. An ALTER TABLE names that word alone, and so does a rule of a kind already given, at its
 * DELETE or UPDATE. In a CREATE TABLE's column list the account reads on, and names more:
 *
 * <pre>
 *   … NOT ENFORCED MATCH SIMPLE)                 'MATCH' 'SIMPLE' ')'
 *   … NOT ENFORCED ON DELETE NO ACTION)          'ON' 'DELETE' 'NO' 'ACTION' ')'
 *   … NOT ENFORCED ON UPDATE CASCADE)            'ON' 'UPDATE' ')'             the word after UPDATE is taken with it
 *   … ON UPDATE CASCADE MATCH PARTIAL ON DELETE SET NULL)    'MATCH' 'PARTIAL' 'DELETE' 'SET'
 *   … NOT ENFORCED MATCH FULL, z INT)            'MATCH' 'FULL' 'INT' ')'      a comma and the name after it are read
 *   … VALIDATE MATCH SIMPLE)                     'VALIDATE' 'MATCH' ')'
 * </pre>
 *
 * <p>The word after the misplaced one is always named, unless it closes the list. After that a word named takes
 * the next token with it or leaves it to be named, by what it is: UPDATE takes its target, NOT the word it
 * negates, a MATCH read late its kind, a kind read after the misplaced MATCH one more word; DELETE, ON and NO
 * leave the next token to be named, as the words of a DELETE rule do. A parenthesis ends the report, and so does a
 * NULL.
 */
final class KeyConstraintTailLines {

    private static final String MATCH = "MATCH";
    private static final String ON = "ON";
    private static final String DELETE = "DELETE";
    private static final String UPDATE = "UPDATE";
    private static final String NOT = "NOT";
    private static final String SET = "SET";
    private static final String NULL = "NULL";
    private static final String VALIDATE = "VALIDATE";
    private static final String NOVALIDATE = "NOVALIDATE";

    /** How many words the walk reads at most: far more than a constraint's tail holds. */
    private static final int LONGEST_TAIL = 64;

    private KeyConstraintTailLines() {
    }

    /**
     * The lines for a CREATE TABLE or an ALTER TABLE whose first fault is such a clause, or null when it has none,
     * when the statement's first fault stands before the constraint, or when another statement follows.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        final int opener = spoken.get(0).getType();
        if (opener != FrostlakeLexer.CREATE && opener != FrostlakeLexer.ALTER) {
            return null;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI && spoken.get(i + 1).getType() != Token.EOF) {
                // Another statement follows, and is read on its own.
                return null;
            }
        }
        for (int i = 1; i < spoken.size(); i++) {
            final int tail = tailAfter(spoken, i);
            if (tail < 0) {
                continue;
            }
            final boolean foreign = "REFERENCES".equals(word(spoken, i));
            final int[] found = misplaced(spoken, tail, foreign);
            if (found == null) {
                continue;
            }
            if (faultIndex >= 0 && faultIndex < spoken.get(tail).getTokenIndex()) {
                return null;
            }
            final List<Integer> named = new ArrayList<Integer>();
            if (found[1] == 1 || opener == FrostlakeLexer.ALTER) {
                named.add(Integer.valueOf(found[0]));
            } else {
                walk(spoken, found[0], named);
            }
            final List<String> lines = new ArrayList<String>();
            for (final Integer at : named) {
                final Token token = spoken.get(at.intValue());
                if (token.getType() == Token.EOF) {
                    final int[] shown = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
                    lines.add("syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.");
                } else {
                    lines.add(SyntaxErrorListener.sentence(token));
                }
            }
            return lines;
        }
        return null;
    }

    /** A token's text as a word, upper case; a parenthesis, a comma and the end of input are their own. */
    private static String word(final List<Token> spoken, final int at) {
        if (at >= spoken.size()) {
            return "<EOF>";
        }
        final Token token = spoken.get(at);
        return token.getType() == Token.EOF ? "<EOF>" : token.getText().toUpperCase(Locale.ROOT);
    }

    private static boolean ends(final List<Token> spoken, final int at) {
        return at >= spoken.size() || spoken.get(at).getType() == Token.EOF
            || spoken.get(at).getType() == FrostlakeLexer.SEMI;
    }

    /**
     * Where the tail of the key constraint whose head word stands at {@code at} begins — after REFERENCES and its
     * target, after PRIMARY KEY or UNIQUE and their column list — or -1 when the word heads no constraint.
     */
    private static int tailAfter(final List<Token> spoken, final int at) {
        final String head = word(spoken, at);
        int tail;
        if ("REFERENCES".equals(head)) {
            tail = at + 2;
            while (spoken.get(Math.min(tail, spoken.size() - 1)).getType() == FrostlakeLexer.DOT) {
                tail += 2;
            }
        } else if ("KEY".equals(head) && "PRIMARY".equals(word(spoken, at - 1)) || "UNIQUE".equals(head)) {
            tail = at + 1;
        } else {
            return -1;
        }
        if (tail < spoken.size() && spoken.get(tail).getType() == FrostlakeLexer.LPAREN) {
            while (!ends(spoken, tail) && spoken.get(tail).getType() != FrostlakeLexer.RPAREN) {
                tail++;
            }
            tail++;
        }
        return tail < spoken.size() ? tail : -1;
    }

    /** How many words a rule's option written at {@code at} takes, or 0 when it is none. */
    private static int optionLength(final List<Token> spoken, final int at) {
        final String first = word(spoken, at);
        if ("CASCADE".equals(first) || "RESTRICT".equals(first)) {
            return 1;
        }
        final String second = word(spoken, at + 1);
        return "NO".equals(first) && "ACTION".equals(second)
            || SET.equals(first) && (NULL.equals(second) || "DEFAULT".equals(second)) ? 2 : 0;
    }

    /**
     * The first word of a tail that breaks the constraint's order: its position, and 1 when it is the DELETE or
     * UPDATE of a rule whose kind was given already. Null for a tail in order, and for one whose fault is a clause
     * left unfinished, which the parser reports as it stands.
     */
    private static int[] misplaced(final List<Token> spoken, final int tail, final boolean foreign) {
        int at = tail;
        if (foreign) {
            if (MATCH.equals(word(spoken, at))) {
                if (!isKind(word(spoken, at + 1))) {
                    return null;
                }
                at += 2;
            }
            final Set<String> given = new HashSet<String>();
            while (ON.equals(word(spoken, at))) {
                if (given.size() == 2) {
                    return new int[] {at, 0};
                }
                final String rule = word(spoken, at + 1);
                if (!DELETE.equals(rule) && !UPDATE.equals(rule)) {
                    return null;
                }
                if (given.contains(rule)) {
                    return new int[] {at + 1, 1};
                }
                final int option = optionLength(spoken, at + 2);
                if (option == 0) {
                    return null;
                }
                given.add(rule);
                at += 2 + option;
            }
        }
        while (true) {
            final String read = word(spoken, at);
            final String next = word(spoken, at + 1);
            if (MATCH.equals(read) || ON.equals(read) || VALIDATE.equals(read) || NOVALIDATE.equals(read)) {
                return new int[] {at, 0};
            }
            if (NOT.equals(read) && ("ENFORCED".equals(next) || "DEFERRABLE".equals(next))
                    || "INITIALLY".equals(read) && ("DEFERRED".equals(next) || "IMMEDIATE".equals(next))) {
                at += 2;
            } else if ("ENFORCED".equals(read) || "DEFERRABLE".equals(read) || "RELY".equals(read)
                    || "NORELY".equals(read)) {
                at += 1;
            } else if ("ENABLE".equals(read) || "DISABLE".equals(read)) {
                at += VALIDATE.equals(next) || NOVALIDATE.equals(next) ? 2 : 1;
            } else {
                return null;
            }
        }
    }

    private static boolean isKind(final String word) {
        return "FULL".equals(word) || "SIMPLE".equals(word) || "PARTIAL".equals(word);
    }

    /** Reads on from the misplaced word as the account does, collecting the positions it names. */
    private static void walk(final List<Token> spoken, final int misplaced, final List<Integer> named) {
        named.add(Integer.valueOf(misplaced));
        int at = misplaced + 1;
        if (ends(spoken, at) || spoken.get(at).getType() == FrostlakeLexer.RPAREN) {
            return;
        }
        named.add(Integer.valueOf(at));
        final String first = word(spoken, misplaced);
        // Whether the word last named takes the token after it along, or leaves it to be named.
        boolean takes = MATCH.equals(first)
            || (VALIDATE.equals(first) || NOVALIDATE.equals(first)) && MATCH.equals(word(spoken, at));
        for (int read = 0; read < LONGEST_TAIL; read++) {
            final String last = word(spoken, at);
            final int next = at + 1;
            final String after = word(spoken, next);
            final int type = spoken.get(at).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN) {
                return;
            }
            if (type == FrostlakeLexer.COMMA) {
                // The comma is read with the name after it.
                if (ends(spoken, next) || ends(spoken, next + 1)) {
                    return;
                }
                at = next + 1;
            } else if (UPDATE.equals(last)) {
                // UPDATE takes the word after it for its target; a SET there is the rest of the statement.
                if (SET.equals(after) || ends(spoken, next) || ends(spoken, next + 1)) {
                    return;
                }
                at = next + 1;
            } else if (DELETE.equals(last) || ON.equals(last) || "NO".equals(last)) {
                if (ends(spoken, next)) {
                    return;
                }
                at = next;
            } else if (SET.equals(last)) {
                // SET DEFAULT is read as a name set to nothing; SET NULL ends what is reported.
                if (!"DEFAULT".equals(after) || ends(spoken, next + 1)) {
                    return;
                }
                at = next + 1;
                named.add(Integer.valueOf(at));
                continue;
            } else if (NOT.equals(last)) {
                if (NULL.equals(after) || ends(spoken, next) || ends(spoken, next + 1)) {
                    return;
                }
                at = next + 1;
            } else if (ends(spoken, next)) {
                if (!takes && spoken.get(next).getType() == Token.EOF
                        && ("CASCADE".equals(last) || "RESTRICT".equals(last) || "ACTION".equals(last))) {
                    named.add(Integer.valueOf(next));
                }
                return;
            } else if (spoken.get(next).getType() == FrostlakeLexer.RPAREN
                    || spoken.get(next).getType() == FrostlakeLexer.LPAREN) {
                named.add(Integer.valueOf(next));
                return;
            } else if (spoken.get(next).getType() == FrostlakeLexer.COMMA) {
                if (ends(spoken, next + 1) || ends(spoken, next + 2)) {
                    return;
                }
                at = next + 2;
            } else if (NOT.equals(after)) {
                at = next;
                named.add(Integer.valueOf(at));
                continue;
            } else if (takes) {
                if (ends(spoken, next + 1)) {
                    return;
                }
                at = next + 1;
            } else {
                at = next;
            }
            named.add(Integer.valueOf(at));
            takes = false;
        }
    }
}
