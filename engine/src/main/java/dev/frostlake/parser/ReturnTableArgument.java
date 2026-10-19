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
import org.antlr.v4.runtime.TokenStream;

/**
 * What stands inside {@code RETURN TABLE(…)}. It takes a RESULTSET by its name, or a call over a name that
 * yields one ({@code RESULTSET_FROM_CURSOR(c)}), and nothing else: a query, a parenthesized expression, a literal,
 * an operator and an empty pair of parentheses are syntax errors, whether the block runs on its own or is a
 * procedure's body compiled at CREATE (live-verified).
 *
 * <p>A fault at the FIRST token inside is followed by one more line where a name stands further on in the
 * statement: the token after that name, or the token after the semicolon that follows it.
 *
 * <pre>
 *   RETURN TABLE(SELECT 1 AS a);          'SELECT', then the ')' after a
 *   RETURN TABLE(SELECT COUNT(*) FROM t); 'SELECT', then the '(' after COUNT
 *   RETURN TABLE((SELECT 1 AS a));        '(', then the ')' after a
 *   RETURN TABLE((SELECT (1)));           '(' alone: no name follows
 *   RETURN TABLE(SELECT 1 AS a; END;      'SELECT', then 'END'
 *   RETURN TABLE(5);  RETURN TABLE();     '5' alone, ')' alone
 * </pre>
 *
 * <p>A fault at the token AFTER a name is reported twice, and one inside a call's parentheses once:
 *
 * <pre>
 *   RETURN TABLE(res + 1);                '+', then '+' again
 *   RETURN TABLE(x.y);                    '.', then '.' again
 *   RETURN TABLE(ABS(1));                 '1' alone
 * </pre>
 *
 * <p>Nothing more of the text is reported after these lines.
 */
final class ReturnTableArgument {

    private ReturnTableArgument() {
    }

    /**
     * Whether a token cannot open the argument although an expression may open with it: a parenthesis, a
     * literal, NULL, a sign, a bind's colon, a bracket, a brace, NOT, TABLE or VALUES. TRUE, a positional
     * {@code $1} and a word such as CURRENT_DATE read as names there.
     *
     * @param type the token's type
     * @return whether the argument is refused at it
     */
    static boolean refusedOpening(final int type) {
        return type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.STRING_LITERAL
            || type == FrostlakeLexer.DOLLAR_QUOTED_STRING || type == FrostlakeLexer.NULL
            || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.COLON
            || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.NOT
            || type == FrostlakeLexer.TABLE || type == FrostlakeLexer.VALUES;
    }

    /**
     * Where a token stands in a RETURN TABLE's argument.
     *
     * @param stream the statement's tokens
     * @param token  the token asked about
     * @return 1 for the first token inside, 2 for the token after a name, 3 for the first token inside that
     *         name's own parentheses, 0 anywhere else
     */
    static int place(final TokenStream stream, final Token token) {
        final List<Token> before = new ArrayList<>();
        for (int i = token.getTokenIndex() - 1; i >= 0 && before.size() < 5; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                before.add(stream.get(i));
            }
        }
        return place(before);
    }

    /** As {@link #place(TokenStream, Token)}, over the tokens before the one asked about, nearest first. */
    private static int place(final List<Token> before) {
        if (opened(before, 0)) {
            return 1;
        }
        if (opened(before, 1)) {
            return 2;
        }
        return before.size() > 1 && before.get(0).getType() == FrostlakeLexer.LPAREN && opened(before, 2) ? 3 : 0;
    }

    /** Whether RETURN TABLE and an opening parenthesis stand right before the {@code skipped} nearest tokens. */
    private static boolean opened(final List<Token> before, final int skipped) {
        return before.size() >= skipped + 3 && before.get(skipped).getType() == FrostlakeLexer.LPAREN
            && before.get(skipped + 1).getType() == FrostlakeLexer.TABLE
            && before.get(skipped + 2).getType() == FrostlakeLexer.RETURN;
    }

    /**
     * The lines reported when the parse's first line names a token of a RETURN TABLE's argument.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the first line names
     * @return the lines, or null when the first line names a token elsewhere
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 3; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        final List<Token> before = new ArrayList<>();
        for (int i = fault - 1; i >= 0 && before.size() < 5; i--) {
            before.add(spoken.get(i));
        }
        final int place = place(before);
        if (place == 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (place == 2) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        }
        if (place != 1) {
            return lines;
        }
        for (int i = fault + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return lines;
            }
            if (!SyntaxErrorListener.isNameLike(spoken.get(i))) {
                continue;
            }
            Token after = spoken.get(i + 1);
            if (after.getType() == FrostlakeLexer.SEMI && i + 2 < spoken.size()) {
                after = spoken.get(i + 2);
            }
            if (after.getType() != Token.EOF && after.getType() != FrostlakeLexer.SEMI) {
                lines.add(SyntaxErrorListener.sentence(after));
            }
            return lines;
        }
        return lines;
    }
}
