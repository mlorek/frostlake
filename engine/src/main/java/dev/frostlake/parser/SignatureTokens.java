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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * The tokens of a text from one character on, as the account's lexer reads them where this lexer reads them
 * otherwise (all live-verified):
 * <ul>
 *   <li>A stage reference is ONE token: the '@' and every character after it up to a delimiter — white space, '(',
 *       ')', ',', ';', a single quote, a backtick, '&lt;', '&gt;', '|', a character outside ASCII, and a '~' past the
 *       first character. A double quote, a comment's opening and an operator's characters are no delimiters:
 *       {@code @"s"}, {@code @s--x}, {@code @s/*x*}{@code /}, {@code @s!=x} and {@code @S3://b/k} are each one token,
 *       {@code @s<>x} is {@code @s} and more, {@code @s=>x} is {@code @s=} and more.</li>
 *   <li>'|', '^', '&amp;' and a '$' that opens nothing are tokens of one character.</li>
 *   <li>A character the account has no token for — one outside ASCII, a '#', a backslash — makes a token of two
 *       characters with the one after it, whatever that is: {@code é)}, {@code é }, {@code #a}. A quote that is the
 *       text's last character is such a token of one.</li>
 *   <li>A name between backticks is a quoted name.</li>
 *   <li>Three runs of digits with a point between them are one token, {@code 1.5.6}, and so is {@code 0x1F}.</li>
 *   <li>A text the account's lexer refuses is reported by a line of its own and read on: an unclosed string
 *       ({@code 'abc}) or dollar-quoted string loses its first character, an unclosed quoted name, backticked name,
 *       binary string or comment everything to the end, and a number that stops after its exponent's 'e' or after a
 *       second point ({@code 1e}, {@code 1.5.}) goes with the character after it, which the line names by its
 *       code.</li>
 * </ul>
 */
final class SignatureTokens {

    /** The type of a token made of a character the account has no token for, and the character after it. */
    static final int UNKNOWN_CHARACTER = -10;

    /** The type of a token the account has and this lexer lacks, which no rule of a signature reads. */
    static final int SYMBOL = -11;

    /** The type of a name written between backticks. */
    static final int BACKTICKED_NAME = -12;

    private static final int DONE = -1;
    private static final int DECLINED = -2;
    private static final int CONTINUE = -3;

    private final String sql;
    private final List<Token> tokens = new ArrayList<>();
    private int lexerLineAt = -1;
    private int lexerLine;
    private int lexerPosition;
    private String lexerNear;

    private SignatureTokens(final String sql) {
        this.sql = sql;
    }

    /**
     * The tokens of a text from a character on, the end of the input last.
     *
     * @param sql  the text
     * @param from the index of the first character read
     * @return the tokens, or null when the text holds a spelling this reading does not settle
     */
    static SignatureTokens read(final String sql, final int from) {
        final SignatureTokens read = new SignatureTokens(sql);
        return read.readFrom(from) ? read : null;
    }

    /**
     * The tokens, in order.
     *
     * @return the tokens, ending with the end of the input
     */
    List<Token> tokens() {
        return tokens;
    }

    /**
     * The index of the token before which the account's lexer refuses the text, or -1 when it reads it whole.
     *
     * @return the index, or -1
     */
    int lexerLineAt() {
        return lexerLineAt;
    }

    /**
     * The line the account's lexer reports, placed as the text places it.
     *
     * @return the line and position, as {line, position}
     */
    int[] lexerLinePlace() {
        return new int[] {lexerLine, lexerPosition};
    }

    /**
     * What the lexer's line names: the code of the character that ends a malformed number, or the end of the input.
     *
     * @return the name
     */
    String lexerLineNear() {
        return lexerNear;
    }

    private boolean readFrom(final int from) {
        int position = from;
        while (position >= 0) {
            position = readStretch(position);
        }
        return position == DONE;
    }

    /**
     * Read with this lexer from a character until the account's reading parts from it.
     *
     * @return where to read on from, {@link #DONE} at the end of the input, or {@link #DECLINED}
     */
    private int readStretch(final int from) {
        final int[] firstError = {-1};
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (firstError[0] == -1) {
                    firstError[0] = e instanceof LexerNoViableAltException
                        ? ((LexerNoViableAltException) e).getStartIndex() : DECLINED;
                }
            }
        });
        lexer.getInputStream().seek(from);
        final int[] place = place(from);
        lexer.setLine(place[0]);
        lexer.setCharPositionInLine(place[1]);
        while (true) {
            final Token token = lexer.nextToken();
            if (firstError[0] == DECLINED) {
                return DECLINED;
            }
            if (firstError[0] >= 0 && firstError[0] < token.getStartIndex()) {
                return unlexed(firstError[0]);
            }
            final int type = token.getType();
            if (type == Token.EOF) {
                tokens.add(token);
                return DONE;
            }
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            final int start = token.getStartIndex();
            if (type == FrostlakeLexer.AT) {
                final int end = stageReferenceEnd(start);
                tokens.add(made(FrostlakeLexer.AT, start, end - 1));
                return end;
            }
            if (type == FrostlakeLexer.MALFORMED_EXPONENT) {
                return malformedNumber(token.getStopIndex() + 1);
            }
            if (type == FrostlakeLexer.HEX_NUMBER || type == FrostlakeLexer.MULTI_POINT_NUMBER
                    || type == FrostlakeLexer.CARET || type == FrostlakeParser.DOLLAR || type == FrostlakeParser.BAR
                    || type == FrostlakeParser.AMP) {
                // 0x with its hexadecimal digits is one token to the account, and a symbol to this reading; so is
                // the caret, a token of one character as the bar and the ampersand are.
                tokens.add(made(SYMBOL, start, token.getStopIndex()));
                continue;
            }
            if (type == FrostlakeLexer.SLASH && start + 1 < sql.length() && sql.charAt(start + 1) == '*') {
                // A comment that closes is skipped, so this one does not.
                return refusedToTheEnd();
            }
            if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL) {
                final int next = numberEnd(token);
                if (next != CONTINUE) {
                    return next;
                }
            }
            tokens.add(token);
        }
    }

    /**
     * A number the account reads otherwise than this lexer: three runs of digits as one token, and a run that stops
     * after its second point as a malformed number.
     *
     * @return where to read on from, or {@link #CONTINUE} for a number both read alike
     */
    private int numberEnd(final Token number) {
        final int start = number.getStartIndex();
        final int at = digits(start);
        if (at > start && at < sql.length() && sql.charAt(at) == '.') {
            final int second = digits(at + 1);
            if (second > at + 1 && second < sql.length() && sql.charAt(second) == '.') {
                final int third = digits(second + 1);
                if (third == second + 1) {
                    return malformedNumber(third);
                }
                tokens.add(made(SYMBOL, start, third - 1));
                return third;
            }
        }
        return CONTINUE;
    }

    private int digits(final int from) {
        int at = from;
        while (at < sql.length() && sql.charAt(at) >= '0' && sql.charAt(at) <= '9') {
            at++;
        }
        return at;
    }

    /** A malformed number: reported by the lexer at the character after it, which goes with it. */
    private int malformedNumber(final int after) {
        if (after >= sql.length()) {
            return refusedToTheEnd();
        }
        final int[] place = place(after);
        if (!lexerLine(place[0], place[1], String.valueOf((int) sql.charAt(after)))) {
            return DECLINED;
        }
        return after + 1;
    }

    /** A text the account's lexer refuses and reads no further: the line names the end of the input. */
    private int refusedToTheEnd() {
        if (!lexerLine(EndOfInput.line(sql), EndOfInput.position(sql), "<EOF>")) {
            return DECLINED;
        }
        return end();
    }

    private int end() {
        final CommonToken end = new CommonToken(Token.EOF, "<EOF>");
        end.setStartIndex(sql.length());
        end.setStopIndex(sql.length() - 1);
        end.setLine(EndOfInput.line(sql));
        end.setCharPositionInLine(EndOfInput.position(sql));
        tokens.add(end);
        return DONE;
    }

    /** One lexer line a text: a second one is a text this reading does not settle. */
    private boolean lexerLine(final int line, final int position, final String near) {
        if (lexerLineAt >= 0) {
            return false;
        }
        lexerLineAt = tokens.size();
        lexerLine = line;
        lexerPosition = position;
        lexerNear = near;
        return true;
    }

    /**
     * A character this lexer reads no token from.
     *
     * @return where to read on from, {@link #DONE} or {@link #DECLINED}
     */
    private int unlexed(final int at) {
        final char character = sql.charAt(at);
        final boolean last = at == sql.length() - 1;
        if (character == '\'' || character == '"') {
            if (last) {
                tokens.add(made(UNKNOWN_CHARACTER, at, at));
                return at + 1;
            }
            // This lexer reads a literal that closes before the unclosed one written straight after it, 'a' in
            // 'a''b; the account reads one literal from the first quote on, and that one never closes.
            int opens = at;
            Token before = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
            if (before != null && before.getStopIndex() == at - 1
                    && before.getType() == (character == '"' ? FrostlakeLexer.QUOTED_IDENTIFIER
                        : FrostlakeLexer.STRING_LITERAL)) {
                tokens.remove(tokens.size() - 1);
                opens = before.getStartIndex();
                before = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
            }
            if (character == '"') {
                // A quoted name this lexer refuses on one line is one that never closes.
                return sql.indexOf('\n', opens) >= 0 || sql.indexOf('\r', opens) >= 0 ? DECLINED : refusedToTheEnd();
            }
            if (sql.indexOf('\\', opens) >= 0) {
                return DECLINED;
            }
            if (before != null && before.getType() == FrostlakeLexer.IDENTIFIER && before.getStopIndex() == opens - 1
                    && "x".equalsIgnoreCase(before.getText())) {
                // An unclosed binary string, its X with it.
                tokens.remove(tokens.size() - 1);
                return refusedToTheEnd();
            }
            return lexerLine(EndOfInput.line(sql), EndOfInput.position(sql), "<EOF>") ? opens + 1 : DECLINED;
        }
        if (character == '`') {
            if (!last && sql.charAt(at + 1) <= ' ') {
                tokens.add(made(UNKNOWN_CHARACTER, at, at + 1));
                return at + 2;
            }
            final int close = sql.indexOf('`', at + 1);
            final int lineEnd = sql.indexOf('\n', at);
            if (close > at && (lineEnd < 0 || close < lineEnd) && sql.substring(at, close).indexOf(' ') < 0) {
                tokens.add(made(BACKTICKED_NAME, at, close));
                return close + 1;
            }
            return close >= 0 || lineEnd >= 0 || last ? DECLINED : refusedToTheEnd();
        }
        if (character == '$') {
            if (!last && sql.charAt(at + 1) == '$') {
                // An unclosed dollar-quoted string: read on from its second '$'.
                return lexerLine(EndOfInput.line(sql), EndOfInput.position(sql), "<EOF>") ? at + 1 : DECLINED;
            }
            tokens.add(made(SYMBOL, at, at));
            return at + 1;
        }
        if (character == '|' || character == '^' || character == '&') {
            tokens.add(made(SYMBOL, at, at));
            return at + 1;
        }
        if (character == '#' || character == '\\' || character >= 128) {
            final int stop = last ? at : at + 1;
            tokens.add(made(UNKNOWN_CHARACTER, at, stop));
            return stop + 1;
        }
        return DECLINED;
    }

    /** Where the stage reference that opens at an '@' ends: the index of its delimiter. */
    private int stageReferenceEnd(final int at) {
        int end = at + 1;
        while (end < sql.length()) {
            final char character = sql.charAt(end);
            if (character <= ' ' || character == '(' || character == ')' || character == ',' || character == ';'
                    || character == '\'' || character == '`' || character == '<' || character == '>'
                    || character == '|' || character >= 128 || character == '~' && end > at + 1) {
                break;
            }
            end++;
        }
        return end;
    }

    private Token made(final int type, final int start, final int stop) {
        final CommonToken token = new CommonToken(type, sql.substring(start, stop + 1));
        final int[] place = place(start);
        token.setStartIndex(start);
        token.setStopIndex(stop);
        token.setLine(place[0]);
        token.setCharPositionInLine(place[1]);
        return token;
    }

    /** The line and the position in it of a character, as the lexer counts them. */
    private int[] place(final int index) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < index && i < sql.length(); i++) {
            if (sql.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[] {line, index - lineStart};
    }
}
