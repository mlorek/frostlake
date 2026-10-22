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
 * The lines the account reports for a text that leaves a string, a quoted name, a comment or a binary literal
 * unclosed. Its lexer tells the fault at the end of the text — {@code parse error line 1 at position 11 near
 * '<EOF>'.} — and the text is read all the same, less what the lexer gave up on (live-verified):
 *
 * <pre>
 *   SELECT 'abc def            the string loses its quote and its words are read on: abc is a column, def its
 *                              alias, and the lexer's line stands alone
 *   SELECT 'abc def ghi        the lexer's line, then 'ghi'
 *   SHOW TABLES LIKE 'abc      the lexer's line, then 'abc'
 *   SELECT 'a''b c             'a' is a string and the quote after it the unclosed one: the lexer's line, then 'c'
 *   SELECT "abc                a quoted name takes everything to the end: the lexer's line, then '&lt;EOF&gt;'
 *   SELECT 1 "abc              nothing is missing before it: the lexer's line alone
 *   SELECT /* abc              a comment and a binary literal, X'AB, take everything to the end as well
 *   SELECT 1 x y; SELECT 'abc  'y', then the lexer's line: a fault that stands before the lexer's is told first
 *   SELECT 'abc'def'           a quote that is the text's last character is a token no rule reads, refused where
 *                              it stands and alone
 *   SELECT `a b` FROM t1       a name between backticks takes the printable ASCII characters but the quotes and
 *                              the backtick: the lexer tells the first other one, here near '32' at 9, and drops
 *                              it with the name; b is read on, and the backtick and the blank after it are a
 *                              token no rule reads, '` ' at 11
 * </pre>
 *
 * <p>The lines after the lexer's are the ones the text reports once it is repaired the way the account's lexer
 * leaves it, which {@link SyntaxErrorListener#linesReportedFor} reads; a text with a second fault of this kind is
 * left to the parse's own lines.
 */
public final class UnclosedLiteralLines {

    private static final int NONE = 0;
    private static final int STRING = 1;
    private static final int TO_END = 2;
    private static final int LAST_QUOTE = 3;
    private static final int BACKTICK = 4;

    private UnclosedLiteralLines() {
    }

    /**
     * Refuse a text its lexer gives up on with every line the account reports for it.
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

    /** The lines of a text with one unclosed literal, or null for any other text. */
    private static List<String> linesOf(final String sql) {
        final int[] fault = faultOf(sql);
        if (fault[0] == NONE) {
            return null;
        }
        final char[] text = sql.toCharArray();
        final int to = fault[0] == TO_END ? text.length - 1
            : fault[0] == BACKTICK ? Math.min(fault[2], text.length - 1) : fault[1];
        for (int i = fault[1]; i <= to; i++) {
            if (text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        final String repaired = new String(text);
        if (faultOf(repaired)[0] != NONE) {
            return null;
        }
        final int[] at = placeOf(sql, fault[1]);
        // A text that is nothing but the unclosed part reports the lexer's line alone: nothing is left to read.
        final List<String> rest = repaired.trim().isEmpty() ? new ArrayList<String>()
            : SyntaxErrorListener.linesReportedFor(repaired, true);
        final List<String> lines = new ArrayList<>();
        int taken = 0;
        while (taken < rest.size() && standsBefore(rest.get(taken), at)) {
            lines.add(rest.get(taken++));
        }
        if (fault[0] == LAST_QUOTE) {
            // The quote is the last thing read, and it is told only when nothing before it was refused.
            if (lines.isEmpty()) {
                lines.add("syntax error line " + at[0] + " at position " + at[1] + " unexpected '''.");
            }
            return lines;
        }
        final int[] end = LeadingCommentOffset.rebase(EndOfInput.line(sql), EndOfInput.position(sql));
        if (fault[0] == BACKTICK && fault[2] < sql.length()) {
            final int[] stops = placeOf(sql, fault[2]);
            lines.add("parse error line " + stops[0] + " at position " + stops[1] + " near '"
                + (int) sql.charAt(fault[2]) + "'.");
            lines.addAll(rest.subList(taken, rest.size()));
            return lines;
        }
        final boolean escapes = fault[0] == STRING && sql.indexOf('\\', fault[1]) >= 0;
        // Inside brackets, after a string, the account's parser reads a token past the one it refuses before it tells
        // it, and when that token is the string left open, its lexer's line is told first: SELECT ABS('x, 'y') is the
        // end, then 'y', where SELECT 1 FROM t1 x y 'abc is 'y', then the end (live-verified).
        final String lookedPast = fault[0] == STRING && !escapes && !lines.isEmpty()
            && namesTokenBefore(sql, fault[1], lines.get(lines.size() - 1)) ? lines.remove(lines.size() - 1) : null;
        lines.add("parse error line " + end[0] + " at position " + end[1] + " near '<EOF>'.");
        if (escapes) {
            lines.addAll(escapeLines(sql, fault[1], end));
            return lines;
        }
        if (lookedPast != null) {
            lines.add(lookedPast);
        }
        lines.addAll(rest.subList(taken, rest.size()));
        return lines;
    }

    /**
     * Whether a reported line names the token the text holds last before the character at {@code index}, when that
     * token stands inside brackets straight after a string.
     */
    private static boolean namesTokenBefore(final String sql, final int index, final String line) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        Token before = null;
        Token earlier = null;
        int depth = 0;
        for (final Token token : stream.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF
                    || token.getStopIndex() >= index) {
                continue;
            }
            earlier = before;
            before = token;
            depth += token.getType() == FrostlakeLexer.LPAREN ? 1 : token.getType() == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        final int[] named = SyntaxErrorListener.sentenceCoordinates(line);
        if (before == null || named == null || depth < 1 || earlier == null
                || earlier.getType() != FrostlakeLexer.STRING_LITERAL) {
            return false;
        }
        final int[] stands = LeadingCommentOffset.rebase(before.getLine(), before.getCharPositionInLine());
        return named[0] == stands[0] && named[1] == stands[1];
    }

    /**
     * The lines the account's lexer adds for the escapes of a string it never closes, after its own line (all
     * live-verified): each backslash is told at the character after it, by that character's code — or at the end of the
     * input when nothing follows — but for a backslash before a blank, which escapes nothing told; and when the text
     * ends in a told escape, the end of the input is refused as well, unless the string follows a comma.
     *
     * <pre>
     *   SELECT 'a\bc          the lexer's line, then near '98' at 10
     *   SELECT 'abc\n         the lexer's line, near '110' at 12, then '&lt;EOF&gt;' at 13
     *   SELECT 'abc\          the lexer's line twice, then '&lt;EOF&gt;' at 12
     *   SELECT 1, 'abc\       the lexer's line twice: after a comma the end is not refused
     *   SELECT 'abc\ x        the lexer's line alone
     * </pre>
     */
    private static List<String> escapeLines(final String sql, final int quote, final int[] end) {
        final List<String> lines = new ArrayList<>();
        boolean endsInEscape = false;
        for (int i = quote + 1; i < sql.length(); i++) {
            endsInEscape = false;
            if (sql.charAt(i) != '\\') {
                continue;
            }
            if (i + 1 >= sql.length()) {
                lines.add("parse error line " + end[0] + " at position " + end[1] + " near '<EOF>'.");
                endsInEscape = true;
                break;
            }
            final char escaped = sql.charAt(i + 1);
            if (!Character.isWhitespace(escaped)) {
                final int[] at = placeOf(sql, i + 1);
                lines.add("parse error line " + at[0] + " at position " + at[1] + " near '" + (int) escaped + "'.");
                endsInEscape = i + 2 >= sql.length();
            }
            i++;
        }
        int before = quote - 1;
        while (before >= 0 && Character.isWhitespace(sql.charAt(before))) {
            before--;
        }
        if (endsInEscape && (before < 0 || sql.charAt(before) != ',')) {
            lines.add("syntax error line " + end[0] + " at position " + end[1] + " unexpected '<EOF>'.");
        }
        return lines;
    }

    /** Whether a reported line names a place before {@code at}; a line that names no place does not. */
    static boolean standsBefore(final String line, final int[] at) {
        final int[] named = SyntaxErrorListener.sentenceCoordinates(line);
        return named != null && (named[0] < at[0] || named[0] == at[0] && named[1] < at[1]);
    }

    /** The line and position of a character of the text, as a refusal names them. */
    static int[] placeOf(final String sql, final int index) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < index; i++) {
            if (sql.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return LeadingCommentOffset.rebase(line, index - lineStart);
    }

    /**
     * Whether the backtick at {@code at} opens a name: a character a name between backticks holds follows it.
     *
     * @param sql the text
     * @param at  the backtick's index
     * @return whether it does
     */
    static boolean opensBacktickName(final String sql, final int at) {
        return at + 1 < sql.length() && isBacktickNameCharacter(sql.charAt(at + 1));
    }

    /** A printable ASCII character other than a quote or a backtick, as a name between backticks holds them. */
    private static boolean isBacktickNameCharacter(final char character) {
        return character > ' ' && character < 127 && character != '"' && character != '\'' && character != '`';
    }

    /**
     * What the lexer gives up on first, and where: an unclosed string at its quote, a quote that ends the text, a
     * quoted name, a comment or a binary literal from its first character, or a name between backticks the text never
     * closes from its backtick, with the character it stops at (the length of the text when it reaches the end).
     *
     * @return the kind and the index, the kind NONE when the text holds no such fault
     */
    private static int[] faultOf(final String sql) {
        final int[] unread = {-1};
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (unread[0] < 0 && e instanceof LexerNoViableAltException) {
                    unread[0] = ((LexerNoViableAltException) e).getStartIndex();
                }
            }
        });
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final int comment = unclosedComment(stream.getTokens());
        if (comment >= 0 && (unread[0] < 0 || comment < unread[0])) {
            return new int[] {TO_END, comment};
        }
        if (unread[0] < 0 || unread[0] >= sql.length()) {
            return new int[] {NONE, -1};
        }
        final int at = unread[0];
        final char opens = sql.charAt(at);
        if (opens == '"') {
            return new int[] {TO_END, at};
        }
        if (opens == '`') {
            if (!opensBacktickName(sql, at)) {
                return new int[] {NONE, -1};
            }
            int stops = at + 1;
            while (stops < sql.length() && isBacktickNameCharacter(sql.charAt(stops))) {
                stops++;
            }
            return new int[] {BACKTICK, at, stops};
        }
        if (opens != '\'') {
            return new int[] {NONE, -1};
        }
        if (opensBinary(sql, at)) {
            return new int[] {TO_END, at - 1};
        }
        // A binary literal whose quotes are doubled after its opening one is left open by them: x''' takes everything
        // to the end, its doubled quote being part of it (live-verified).
        int run = at;
        while (run > 0 && sql.charAt(run - 1) == '\'') {
            run--;
        }
        if (at - run >= 2 && (at - run) % 2 == 0 && opensBinary(sql, run)) {
            return new int[] {TO_END, run - 1};
        }
        // A quote that ends the text is a token of its own unless a string closes straight before it: 'a'' is one
        // string left unclosed.
        final boolean alone = sql.substring(at + 1).trim().isEmpty() && (at == 0 || sql.charAt(at - 1) != '\'');
        return new int[] {alone ? LAST_QUOTE : STRING, at};
    }

    /** Where a comment the text never closes opens — a '/' and a '*' written together, which a closed one is not. */
    private static int unclosedComment(final List<Token> tokens) {
        Token before = null;
        for (final Token token : tokens) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (before != null && before.getType() == FrostlakeLexer.SLASH && token.getType() == FrostlakeLexer.STAR
                    && token.getStartIndex() == before.getStopIndex() + 1) {
                return before.getStartIndex();
            }
            before = token;
        }
        return -1;
    }

    /** Whether the quote at {@code at} opens a binary literal: an X stands straight before it, a word of its own. */
    private static boolean opensBinary(final String sql, final int at) {
        if (at < 1 || sql.charAt(at - 1) != 'X' && sql.charAt(at - 1) != 'x') {
            return false;
        }
        if (at == 1) {
            return true;
        }
        final char before = sql.charAt(at - 2);
        return !Character.isLetterOrDigit(before) && before != '_' && before != '$' && before != '"';
    }
}
