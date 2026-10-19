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
import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports around a character no token opens with. The character and the one after it are a
 * token the parser refuses where it stands; what it reads after that depends on where the token stood
 * (live-verified):
 *
 * <pre>
 *   SELECT 1 # 2 x y              '# ', then 'y': after a select list's value the reading goes on at the next
 *   SELECT 1 # 2, 3 x y           alias, comma or clause — '# ', then 'y'
 *   SELECT 1 # 2 3                nothing to go on at: '# ' alone
 *   SELECT # 1 x y                straight after the SELECT the statement is given up: '# ' alone
 *   SELECT 1 FROM t1 # x y z      and so it is in a FROM or a WHERE clause: '# ' alone
 *   … ORDER BY a # 2, 3 x y       a list of keys goes on at its comma and takes no alias: '# ', then 'x'
 *   SELECT 1 # 2; SELECT 3 x y    the next statement is read: '# ', then 'y'
 *   SELECT 1 x y # 2              a fault before the character ends a script's report: 'y' alone
 *   BEGIN SELECT 1 x y # 2; END;  a block reads on: 'y', then '# '
 * </pre>
 *
 * <p>The first thing refused straight after the token, with nothing read in between, is not told: {@code SELECT #}
 * is the '#' alone and no end of input after it. The lines around the token's own are the ones the text reports
 * with the token and what was skipped after it blanked, which {@link SyntaxErrorListener#linesReportedFor} reads.
 */
public final class UnknownCharacterLines {

    private static final int GIVEN_UP = 0;
    private static final int SELECT_LIST = 1;
    private static final int BRACKETS = 2;
    private static final int KEY_LIST = 3;

    private UnknownCharacterLines() {
    }

    /**
     * Refuse a text whose first lexical fault is a character no token opens with, with every line the account
     * reports for it.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors()) {
            return;
        }
        final List<String> lines = linesOf(sql);
        if (lines != null) {
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
    }

    private static List<String> linesOf(final String sql) {
        final List<Integer> unread = new ArrayList<>();
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (e instanceof LexerNoViableAltException) {
                    unread.add(Integer.valueOf(((LexerNoViableAltException) e).getStartIndex()));
                }
            }
        });
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final int at = unread.isEmpty() ? -1 : unread.get(0).intValue();
        if (at < 0 || at >= sql.length() || !opensNoToken(sql.charAt(at))) {
            return null;
        }
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : stream.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                spoken.add(token);
            }
        }
        int before = -1;
        int next = spoken.size() - 1;
        for (int i = 0; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getType() != Token.EOF && token.getStopIndex() < at) {
                before = i;
            } else if (token.getType() == Token.EOF || token.getStartIndex() >= at + 2) {
                next = i;
                break;
            }
        }
        final int resumes = resumesAt(spoken, before, next);
        int goesOn = spoken.get(resumes).getType() == Token.EOF ? sql.length()
            : spoken.get(resumes).getStartIndex();
        for (final Integer fault : unread) {
            // A string or a quoted name left unclosed after the token is the lexer's own fault, told with the rest.
            final int index = fault.intValue();
            if (index >= at + 2 && index < goesOn && (sql.charAt(index) == '\'' || sql.charAt(index) == '"')) {
                goesOn = index;
                break;
            }
        }
        final char[] text = sql.toCharArray();
        // An operator the token stood after has lost its operand, and is not read either: SELECT 1 + # 2 x y is
        // '# ', then 'y', the x an alias.
        final boolean operandLost = before >= 0 && opensAnOperand(spoken.get(before).getType())
            && SyntaxErrorListener.isNameLike(spoken.get(resumes));
        final int from = operandLost ? spoken.get(before).getStartIndex() : at;
        for (int i = from; i < goesOn && i < text.length; i++) {
            if (text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        final int[] place = UnclosedLiteralLines.placeOf(sql, at);
        final List<String> rest = SyntaxErrorListener.linesReportedFor(new String(text), true);
        final List<String> lines = new ArrayList<>();
        int taken = 0;
        while (taken < rest.size() && UnclosedLiteralLines.standsBefore(rest.get(taken), place)) {
            lines.add(rest.get(taken++));
        }
        final boolean block = !spoken.isEmpty() && (spoken.get(0).getType() == FrostlakeLexer.BEGIN
            || spoken.get(0).getType() == FrostlakeLexer.DECLARE);
        if (!lines.isEmpty() && !block) {
            // A fault before the character ends a script's report.
            return lines;
        }
        lines.add("syntax error line " + place[0] + " at position " + place[1] + " unexpected '"
            + sql.substring(at, Math.min(at + 2, sql.length())) + "'.");
        if (taken < rest.size() && readsNothingBefore(rest.get(taken), spoken, resumes)) {
            // What is refused straight after the token, nothing read in between, is not told.
            taken++;
        }
        lines.addAll(rest.subList(taken, rest.size()));
        return lines;
    }

    /**
     * The index of the token the reading goes on at after the refused one: an alias, a comma or a clause after a
     * select list's value, the bracket that closes the list the token stood in, or the statement's end.
     */
    private static int resumesAt(final List<Token> spoken, final int before, final int next) {
        final int stood = stoodIn(spoken, before);
        int depth = 0;
        for (int i = next; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return i;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0 && stood != GIVEN_UP) {
                    return i;
                }
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && type == FrostlakeLexer.SEMI) {
                return i;
            } else if (depth == 0 && stood == SELECT_LIST && (type == FrostlakeLexer.COMMA || opensClause(type)
                    || SyntaxErrorListener.isNameLike(token))) {
                return i;
            } else if (depth == 0 && stood == KEY_LIST && (type == FrostlakeLexer.COMMA || opensClause(type))) {
                return i;
            }
        }
        return spoken.size() - 1;
    }

    /** Where the refused token stood, by the tokens before it: after a select list's value, in brackets, or not. */
    private static int stoodIn(final List<Token> spoken, final int before) {
        int depth = 0;
        for (int i = before; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return BRACKETS;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                // Straight after the SELECT, or after a star, the statement is given up.
                return i == before || spoken.get(before).getType() == FrostlakeLexer.STAR ? GIVEN_UP : SELECT_LIST;
            } else if (depth == 0 && type == FrostlakeLexer.BY && i < before) {
                // A list of keys reads on at its next comma, and takes no alias.
                return KEY_LIST;
            } else if (depth == 0 && (type == FrostlakeLexer.SEMI || opensClause(type))) {
                return GIVEN_UP;
            }
        }
        return GIVEN_UP;
    }

    private static boolean opensAnOperand(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.SLASH
            || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    private static boolean opensClause(final int type) {
        return type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.UNION || type == FrostlakeLexer.INTERSECT
            || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW || type == FrostlakeLexer.INTO
            || type == FrostlakeLexer.VALUES;
    }

    /** Whether nothing but semicolons stands between the token the reading went on at and the one a line names. */
    private static boolean readsNothingBefore(final String line, final List<Token> spoken, final int resumes) {
        final int[] named = SyntaxErrorListener.sentenceCoordinates(line);
        if (named == null || !line.startsWith("syntax error")) {
            // The lexer's own line names the end of the text and is always told.
            return false;
        }
        for (int i = resumes; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int[] stands = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (stands[0] == named[0] && stands[1] == named[1]) {
                return true;
            }
            if (token.getType() != FrostlakeLexer.SEMI) {
                return false;
            }
        }
        return false;
    }

    /** A quote, a dollar sign, a slash and a backtick open a literal, a name or a comment: no such character. */
    private static boolean opensNoToken(final char character) {
        return character != '\'' && character != '"' && character != '$' && character != '/' && character != '`';
    }
}
