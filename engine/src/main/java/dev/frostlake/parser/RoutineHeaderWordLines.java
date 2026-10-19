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
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a word out of place between a routine's parameters and its body — a second
 * {@code COPY GRANTS}, a clause written twice, a word that is no clause at all (all live-verified). Where the
 * clauses of a CREATE FUNCTION or PROCEDURE stop being ones it takes, the account reads what follows as
 * properties, a name, an {@code =} and a value each. A name with no {@code =} after it has the next token named,
 * which is then taken for the value when it could be one — a word, a text, a number:
 *
 * <pre>
 *   f(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS 'x'      'GRANTS' then 'NUMBER'
 *   f(x NUMBER) COPY GRANTS COPY RETURNS NUMBER AS 'x'             'RETURNS' then 'AS'
 *   f(x NUMBER) FOO = 1 RETURNS NUMBER AS 'x'                      'NUMBER'
 *   f(x NUMBER) RETURNS NUMBER LANGUAGE SQL LANGUAGE SQL AS 'x'    'SQL'
 *   p() COPY GRANTS COPY GRANTS RETURNS NUMBER LANGUAGE SQL AS …   'GRANTS' 'NUMBER' 'SQL'
 * </pre>
 *
 * <p>So a RETURNS after the first fault is a property's name like any other, and its type the token named. A
 * token that could be no value — a reserved word, a parenthesis — is named and the reading resumes at the next
 * word, as it does at a token standing where a name should: {@code RETURNS TABLE (a INT)} is {@code 'TABLE'},
 * {@code 'INT'} and the {@code ')'}, {@code RETURNS NUMBER NOT NULL} is {@code 'NUMBER'} and {@code 'NOT'},
 * {@code CALLED ON NULL INPUT AS 'x'} is {@code 'ON'} and {@code 'AS'}. The body, {@code AS} and its text, ends
 * the reading: a token after it is named, and an {@code AS} or a name the text ends on has the end of input
 * named. After a fault a word that opens a statement — COPY, SELECT, INSERT — is named where a name would stand,
 * and nothing after it is read: {@code COPY GRANTS COPY GRANTS COPY GRANTS} is {@code 'GRANTS'} and the third
 * {@code 'COPY'}.
 */
final class RoutineHeaderWordLines {

    private RoutineHeaderWordLines() {
    }

    /**
     * The lines for a CREATE FUNCTION or PROCEDURE whose first fault stands among the words after its parameters,
     * or null when the statement is no such one: the fault is elsewhere, the words before it are no clauses the
     * statement takes, or the routine is an external one.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        int opener = fault;
        while (opener > 0 && spoken.get(opener - 1).getType() != FrostlakeLexer.SEMI) {
            opener--;
        }
        final int closed = parametersEnd(spoken, opener);
        if (closed < 0 || fault <= closed) {
            return null;
        }
        final boolean procedure = isProcedure(spoken, opener, closed);
        // The reading starts at the first word the statement's own clauses do not take: the fault, or the word
        // before it that the fault could not follow.
        int start = -1;
        if (takesClauses(sql, spoken.get(fault), procedure)) {
            start = fault;
        } else if (fault - 1 > closed && takesClauses(sql, spoken.get(fault - 1), procedure)) {
            start = fault - 1;
        }
        if (start < 0 || !isWord(spoken.get(start)) || holdsBody(spoken, closed, start)) {
            // A token that is no word stands in no property's place: a type's parameters, a query's opener.
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        int at = start;
        while (true) {
            final Token token = spoken.get(at);
            if (endsStatement(token)) {
                return lines;
            }
            if (token.getType() == FrostlakeLexer.AS) {
                final Token after = spoken.get(at + 1);
                if (isText(after)) {
                    final Token trailing = spoken.get(at + 2);
                    if (!endsStatement(trailing)) {
                        lines.add(SyntaxErrorListener.sentence(trailing));
                    }
                    return lines;
                }
                lines.add(after.getType() == Token.EOF ? endOfInput(sql) : SyntaxErrorListener.sentence(after));
                return lines;
            }
            if (!isWord(token)) {
                lines.add(SyntaxErrorListener.sentence(token));
                if (opensStatement(token)) {
                    return lines;
                }
                at = nextWord(spoken, at + 1);
                continue;
            }
            if (!lines.isEmpty() && opensStatement(token)) {
                lines.add(SyntaxErrorListener.sentence(token));
                return lines;
            }
            final Token next = spoken.get(at + 1);
            if (next.getType() == FrostlakeLexer.EQ) {
                at = endsStatement(spoken.get(at + 2)) ? at + 2 : at + 3;
                continue;
            }
            if (next.getType() == FrostlakeLexer.SEMI) {
                return lines;
            }
            if (next.getType() == Token.EOF) {
                lines.add(endOfInput(sql));
                return lines;
            }
            lines.add(SyntaxErrorListener.sentence(next));
            at = isWord(next) || isLiteral(next) ? at + 2 : nextWord(spoken, at + 2);
        }
    }

    /**
     * The position of the parenthesis closing the parameters of the CREATE FUNCTION or PROCEDURE that opens at
     * {@code opener}, or -1 when the statement is no such one.
     */
    private static int parametersEnd(final List<Token> spoken, final int opener) {
        if (spoken.get(opener).getType() != FrostlakeLexer.CREATE) {
            return -1;
        }
        for (int i = opener + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.EXTERNAL || type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN) {
                return isRoutineKind(spoken, opener, i) ? CallTokenGroups.closeOf(spoken, i) : -1;
            }
        }
        return -1;
    }

    /** Whether FUNCTION or PROCEDURE stands between the statement's opener and its first parenthesis. */
    private static boolean isRoutineKind(final List<Token> spoken, final int opener, final int open) {
        for (int i = opener + 1; i < open; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.FUNCTION || type == FrostlakeLexer.PROCEDURE) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProcedure(final List<Token> spoken, final int opener, final int closed) {
        for (int i = opener + 1; i < closed; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.PROCEDURE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the statement's text before {@code token} is clauses it takes: completed with what a routine still
     * needs — a return type, a body — it parses cleanly.
     */
    private static boolean takesClauses(final String sql, final Token token, final boolean procedure) {
        final String before = sql.substring(0, Math.min(token.getStartIndex(), sql.length()));
        final String body = procedure ? " AS 'BEGIN RETURN 1; END'" : " AS '1'";
        return SyntaxErrorListener.cleanParse(before + " RETURNS NUMBER" + body) != null
            || SyntaxErrorListener.cleanParse(before + body) != null;
    }

    /** Whether a body, an AS and its text, stands between the parameters and the token at {@code before}. */
    private static boolean holdsBody(final List<Token> spoken, final int closed, final int before) {
        for (int i = closed + 1; i + 1 < before; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.AS && isText(spoken.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    /** The position of the next word, body or end of the statement at or after {@code from}. */
    private static int nextWord(final List<Token> spoken, final int from) {
        int at = from;
        while (at < spoken.size() - 1 && !endsStatement(spoken.get(at)) && !isWord(spoken.get(at))
                && !(spoken.get(at).getType() == FrostlakeLexer.AS && isText(spoken.get(at + 1)))) {
            at++;
        }
        return at;
    }

    private static boolean endsStatement(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }

    private static boolean isText(final Token token) {
        return token.getType() == FrostlakeLexer.STRING_LITERAL
            || token.getType() == FrostlakeLexer.DOLLAR_QUOTED_STRING;
    }

    private static boolean isLiteral(final Token token) {
        return isText(token) || token.getType() == FrostlakeLexer.INTEGER_LITERAL
            || token.getType() == FrostlakeLexer.FLOAT_LITERAL;
    }

    /** Whether a token may name a property: a word that is no reserved one. */
    private static boolean isWord(final Token token) {
        return token.getType() != FrostlakeLexer.AS && CallTokenGroups.isCallName(token);
    }

    /** Whether a word opens a statement of its own, which a reading already at fault does not go past. */
    private static boolean opensStatement(final Token token) {
        switch (token.getType()) {
            case FrostlakeLexer.SELECT:
            case FrostlakeLexer.INSERT:
            case FrostlakeLexer.UPDATE:
            case FrostlakeLexer.DELETE:
            case FrostlakeLexer.MERGE:
            case FrostlakeLexer.COPY:
            case FrostlakeLexer.CREATE:
            case FrostlakeLexer.ALTER:
            case FrostlakeLexer.DROP:
            case FrostlakeLexer.WITH:
                return true;
            default:
                return false;
        }
    }

    private static String endOfInput(final String sql) {
        final int[] shown = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '<EOF>'.";
    }
}
