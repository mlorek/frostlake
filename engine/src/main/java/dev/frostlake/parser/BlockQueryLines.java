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
 * The lines the account reports for a faulty query that is a statement of a BEGIN … END block. The query's own
 * lines are those it reports as a statement of its own; what follows them is the block's reading of the place the
 * query was given up at (all live-verified):
 *
 * <pre>
 *   BEGIN SELECT 1 AS 1, 2 AS 2; END;                    '1', '2': the select list is read on, and the block too
 *   BEGIN SELECT 1 AS 1, 2 AS 2; SELECT 3 AS 3; END;     '1', '2', then the next statement's '3'
 *   BEGIN SELECT UPPER(CAST(1, 2)) FROM t; END;          ',', ')', then the block's END: t is taken as a name, its
 *                                                        ';' gives the block up, and the END is left over
 *   BEGIN SELECT CAST(1, 2) x FROM t; END;               ',', ')', then 'FROM': x is the name, FROM cannot follow
 *   BEGIN SELECT CAST(1, 2); END;                        ',', ')': no name stands before the ';'
 *   BEGIN SELECT (1 x y); END;                           'x', 'y', then the ')': y is the name
 *   BEGIN SELECT 1 FROM t WHERE a IN (1 x y); END;       'x', then the ')': the name is the word after the refused
 *   BEGIN SELECT 1 FROM t WHERE HASH(* REPLACE (1 AS a)) = 1; END;   'REPLACE', then the ')' after the name a
 *   BEGIN SELECT CASE CAST(1, 2) WHEN 1 THEN 1 END; END; ',', '1', then the block's END: the CASE's END closed it
 *   BEGIN SELECT 1 FROM t JOIN USING; END;               the ';' where a statement of its own ends
 * </pre>
 *
 * <p>After the last of the query's own lines the account looks, from the token after it, for the first word it can
 * read as a name — or takes that line's token itself when it is a word the query could have ended before. The token
 * after the name is refused, and nothing after it is reported; when that token is the statement's semicolon the
 * statement is given up with its block and the rest read on (see {@link BlockReadOn}). An END met on the way closes
 * the block. A fault the query recovers from by itself — a literal after AS, a word before a closing parenthesis, a
 * word inside a star's REPLACE list, a closing parenthesis where its bracket wants an operand, the statement's
 * semicolon where a clause or an operator wants one — is followed by nothing, and the statements after are read.
 */
final class BlockQueryLines {

    /** A statement no parse refuses, put where a statement is passed over. */
    private static final String FILLER = "COMMIT";

    private static final String END_OF_INPUT = "unexpected '<EOF>'.";

    /** The keywords a statement opens with. */
    private static final int[] OPENERS = {
        FrostlakeLexer.SELECT, FrostlakeLexer.WITH, FrostlakeLexer.INSERT, FrostlakeLexer.UPDATE,
        FrostlakeLexer.DELETE, FrostlakeLexer.MERGE, FrostlakeLexer.COPY, FrostlakeLexer.TRUNCATE,
        FrostlakeLexer.CREATE, FrostlakeLexer.DROP, FrostlakeLexer.ALTER, FrostlakeLexer.UNDROP,
        FrostlakeLexer.SHOW, FrostlakeLexer.DESCRIBE, FrostlakeLexer.DESC, FrostlakeLexer.USE, FrostlakeLexer.SET,
        FrostlakeLexer.UNSET, FrostlakeLexer.GRANT, FrostlakeLexer.REVOKE, FrostlakeLexer.COMMIT,
        FrostlakeLexer.ROLLBACK, FrostlakeLexer.EXECUTE, FrostlakeLexer.CALL, FrostlakeLexer.EXPLAIN,
        FrostlakeLexer.COMMENT, FrostlakeLexer.LET, FrostlakeLexer.RETURN, FrostlakeLexer.IF, FrostlakeLexer.FOR,
        FrostlakeLexer.WHILE, FrostlakeLexer.LOOP, FrostlakeLexer.REPEAT, FrostlakeLexer.BREAK,
        FrostlakeLexer.CONTINUE, FrostlakeLexer.RAISE, FrostlakeLexer.OPEN, FrostlakeLexer.CLOSE,
        FrostlakeLexer.FETCH, FrostlakeLexer.BEGIN, FrostlakeLexer.DECLARE, FrostlakeLexer.CASE,
    };

    private BlockQueryLines() {
    }

    /**
     * The lines of a block whose first fault stands in a query statement of it, or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        return lines(sql, spoken, faultIndex, false);
    }

    /**
     * As {@link #lines(String, List, int)}; with {@code callForms} the query's own lines are also those of a call in
     * a FROM form no name accepts, which a parse does not refuse (see {@link AnsiFromFormSyntax}).
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean callForms) {
        if (sql == null || spoken.size() < 6) {
            return null;
        }
        final int opener = spoken.get(0).getType();
        if (opener != FrostlakeLexer.DECLARE
                && !(opener == FrostlakeLexer.BEGIN && SyntaxErrorListener.isBareBegin(spoken, 0))) {
            return null;
        }
        final int end = spoken.size() - 1;
        int fault = end;
        for (int i = 0; i < end; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        int boundary = fault == end ? end - 1 : fault;
        while (boundary > 0 && spoken.get(boundary - 1).getType() != FrostlakeLexer.SEMI) {
            boundary--;
        }
        int start = -1;
        for (int i = Math.max(boundary, 1); i <= Math.min(fault, end - 1) && start < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SELECT && opensStatement(spoken.get(i - 1).getType())) {
                start = i;
            }
        }
        if (start < 0) {
            return null;
        }
        int stop = start;
        int cases = 0;
        while (stop < end && spoken.get(stop).getType() != FrostlakeLexer.SEMI) {
            final int type = spoken.get(stop).getType();
            if (type == FrostlakeLexer.CASE) {
                cases++;
            } else if (type == FrostlakeLexer.END && cases-- == 0) {
                break;
            }
            stop++;
        }
        if (stop >= end || fault != end && fault > stop) {
            return null;
        }
        final String own = alone(sql, spoken.get(start).getStartIndex(), spoken.get(stop).getStartIndex());
        final List<String> top = callForms ? AnsiFromFormSyntax.linesOf(own)
            : SyntaxErrorListener.linesReportedAfresh(own);
        if (top.isEmpty()) {
            return null;
        }
        final List<Integer> at = new ArrayList<>();
        for (final String line : top) {
            final int index = line.endsWith(END_OF_INPUT) ? -1 : BlockReadOn.indexOfLine(spoken, line);
            if (index < 0 && !line.endsWith(END_OF_INPUT) || index > stop) {
                return null;
            }
            at.add(Integer.valueOf(index));
        }
        final boolean terminated = spoken.get(stop).getType() == FrostlakeLexer.SEMI;
        // A control construct's body has readings of its own; there only a fault the query recovers from is read.
        final boolean direct = BlockReadOn.standsDirectlyInBlock(spoken, stop);
        final int last = at.get(at.size() - 1).intValue();
        final List<String> lines = new ArrayList<>(top);
        if (last < 0) {
            // Where a statement of its own ends, the block's statement meets its semicolon.
            if (!terminated || !direct) {
                return null;
            }
            lines.set(lines.size() - 1, SyntaxErrorListener.sentence(spoken.get(stop)));
            // Where a clause or an operator wants an operand the semicolon ends the statement all the same, and the
            // statements after it are read; after a word the query takes as a name the report ends.
            return SyntaxErrorListener.isNameLike(spoken.get(stop - 1)) ? lines
                : readOn(sql, spoken, lines, start, stop, terminated);
        }
        final int head = at.get(0).intValue();
        final int leading = spoken.get(head).getType();
        if (opensAStatement(leading) && spoken.get(head - 1).getType() != FrostlakeLexer.LPAREN
                && depthAt(spoken, start, head) == 0) {
            // A statement's keyword run into the query: the query lacks its semicolon, which is read elsewhere.
            return null;
        }
        if (direct && (leading == FrostlakeLexer.REPLACE || leading == FrostlakeLexer.RENAME
                || leading == FrostlakeLexer.EXCLUDE || leading == FrostlakeLexer.VALUES)) {
            // A star's modifier where none is read, and a VALUES: the word, the token straight after it when the
            // query names it too, and then the block's reading from there.
            final int kept = leading != FrostlakeLexer.VALUES && at.size() > 1 && at.get(1).intValue() == head + 1
                ? 2 : 1;
            while (lines.size() > kept) {
                lines.remove(lines.size() - 1);
            }
            return afterScan(sql, spoken, lines, head + kept, stop, terminated);
        }
        final int around = openerOf(spoken, start, head);
        if (direct && around > start && spoken.get(around - 1).getType() == FrostlakeLexer.EXISTS
                && spoken.get(around + 1).getType() == FrostlakeLexer.SELECT) {
            // A fault in an EXISTS's query is passed over at the top level; a block takes the first name after it.
            while (lines.size() > 1) {
                lines.remove(lines.size() - 1);
            }
            return afterScan(sql, spoken, lines, head + 1, stop, terminated);
        }
        final int depth = depthAt(spoken, start, last);
        if (direct && at.size() > 1 && at.get(at.size() - 2).intValue() == last && depth == 0) {
            // A token refused twice where it stands ends the report.
            return lines;
        }
        final int type = spoken.get(last).getType();
        if (SyntaxErrorListener.isNameLike(spoken.get(last)) && type != FrostlakeLexer.CASE
                && type != FrostlakeLexer.WHEN) {
            if (recoversAtWord(spoken, start, last)) {
                return readOn(sql, spoken, lines, start, stop, terminated);
            }
            if (!direct) {
                return null;
            }
            if (depth > 0 && at.size() == 1) {
                // A word refused inside a bracket is passed over: a word after it is the name the block takes.
                return SyntaxErrorListener.isNameLike(spoken.get(last + 1))
                    ? afterName(sql, spoken, lines, last + 1, stop, terminated)
                    : readOn(sql, spoken, lines, start, stop, terminated);
            }
            return afterName(sql, spoken, lines, last, stop, terminated);
        }
        if (recoversAtToken(spoken, start, last, depth, at.size() == 1)) {
            return readOn(sql, spoken, lines, start, stop, terminated);
        }
        if (!direct) {
            return null;
        }
        if (type == FrostlakeLexer.SELECT && at.size() == 1 && spoken.get(last - 1).getType() == FrostlakeLexer.LPAREN) {
            // A query in parentheses is named by its SELECT; the word it could not take is the name.
            for (int i = last + 2; i < stop; i++) {
                if (SyntaxErrorListener.isNameLike(spoken.get(i)) && SyntaxErrorListener.isNameLike(spoken.get(i - 1))) {
                    return afterName(sql, spoken, lines, i, stop, terminated);
                }
            }
            return lines;
        }
        return afterScan(sql, spoken, lines, last + 1, stop, terminated);
    }

    /**
     * The block's reading from {@code from} on: the first name is taken, an END closes the block, and with no name
     * before the statement's end the report ends.
     */
    private static List<String> afterScan(final String sql, final List<Token> spoken, final List<String> lines,
                                          final int from, final int stop, final boolean terminated) {
        for (int i = from; i < stop; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == FrostlakeLexer.END) {
                // The END closes the block where it stands, and what follows its semicolon is read from outside.
                if (spoken.get(i + 1).getType() == FrostlakeLexer.SEMI) {
                    lines.addAll(givenUp(sql, spoken, i + 1));
                }
                return lines;
            }
            if (SyntaxErrorListener.isNameLike(token)) {
                return afterName(sql, spoken, lines, i, stop, terminated);
            }
        }
        return lines;
    }

    /** The token after the name the block takes: refused, or the semicolon that gives the block up. */
    private static List<String> afterName(final String sql, final List<Token> spoken, final List<String> lines,
                                          final int name, final int stop, final boolean terminated) {
        final Token next = spoken.get(name + 1);
        if (name + 1 == stop) {
            if (terminated) {
                lines.addAll(givenUp(sql, spoken, stop));
            }
            return lines;
        }
        if (next.getType() != Token.EOF && next.getType() != FrostlakeLexer.END) {
            lines.add(SyntaxErrorListener.sentence(next));
        }
        return lines;
    }

    /**
     * What the rest refuses once the statement that ends at a semicolon is given up with its block: a query read on
     * outside the block reports the lines of its own.
     */
    private static List<String> givenUp(final String sql, final List<Token> spoken, final int semicolon) {
        final List<String> after = BlockReadOn.linesAfter(spoken, semicolon);
        final int token = after.size() == 1 ? BlockReadOn.indexOfLine(spoken, after.get(0)) : -1;
        if (token < 0) {
            return after;
        }
        int from = token;
        while (from > semicolon + 1 && spoken.get(from - 1).getType() != FrostlakeLexer.SEMI) {
            from--;
        }
        if (token == from || spoken.get(from).getType() != FrostlakeLexer.SELECT) {
            return after;
        }
        int to = token;
        while (spoken.get(to).getType() != FrostlakeLexer.SEMI && spoken.get(to).getType() != Token.EOF) {
            to++;
        }
        final List<String> own = SyntaxErrorListener.linesReportedAfresh(
            alone(sql, spoken.get(from).getStartIndex(), spoken.get(to).getStartIndex()));
        return own.isEmpty() ? after : own;
    }

    /** The query recovered by itself: the statements after it are read. */
    private static List<String> readOn(final String sql, final List<Token> spoken, final List<String> lines,
                                       final int start, final int stop, final boolean terminated) {
        final int from = spoken.get(start).getStartIndex();
        final int to = spoken.get(stop).getStartIndex() - 1;
        if (!terminated || to - from + 1 < FILLER.length()) {
            return lines;
        }
        final char[] text = sql.toCharArray();
        blank(text, from, to);
        for (int c = 0; c < FILLER.length(); c++) {
            text[from + c] = FILLER.charAt(c);
        }
        lines.addAll(SyntaxErrorListener.linesReportedAfresh(new String(text)));
        return lines;
    }

    /**
     * Whether a word the query refused is one it recovers from where it stands: a word before the parenthesis that
     * closes its bracket, a word inside a star's REPLACE or RENAME list or after a RENAME's name, and the word
     * after FOR.
     */
    private static boolean recoversAtWord(final List<Token> spoken, final int start, final int word) {
        final int before = spoken.get(word - 1).getType();
        if (before == FrostlakeLexer.FOR) {
            return true;
        }
        if (word - 2 > start && spoken.get(word - 2).getType() == FrostlakeLexer.RENAME
                && SyntaxErrorListener.isNameLike(spoken.get(word - 1))) {
            return true;
        }
        final int open = openerOf(spoken, start, word);
        if (open < 0) {
            return false;
        }
        final int kind = spoken.get(open - 1).getType();
        return kind == FrostlakeLexer.REPLACE || kind == FrostlakeLexer.RENAME;
    }

    /**
     * Whether a token the query refused that is no word is one it recovers from: a literal after AS or inside a
     * bracket, a FROM, a colon in the list of names a FOR … IN takes, the parenthesis that closes a star's EXCLUDE
     * list, and a closing parenthesis that is the
     * query's one fault where its own bracket wants an operand — {@code OVER (ORDER BY)}, {@code ABS(1,)},
     * {@code IN ()} — or ends a list after its comma, however many lists do.
     *
     * @param alone whether the token is the only one the query refused
     */
    private static boolean recoversAtToken(final List<Token> spoken, final int start, final int token,
                                           final int depth, final boolean alone) {
        final int type = spoken.get(token).getType();
        if (type == FrostlakeLexer.FROM) {
            return true;
        }
        if (type == FrostlakeLexer.COLON && alone && depth > 0) {
            // A colon in the list of names an UNPIVOT's FOR … IN takes: the word after it is the list's name.
            final int around = openerOf(spoken, start, token);
            return around > start + 2 && spoken.get(around - 1).getType() == FrostlakeLexer.IN
                && spoken.get(around - 3).getType() == FrostlakeLexer.FOR
                && SyntaxErrorListener.isNameLike(spoken.get(token + 1));
        }
        if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
                || type == FrostlakeLexer.STRING_LITERAL) {
            return depth > 0 || spoken.get(token - 1).getType() == FrostlakeLexer.AS;
        }
        if (type == FrostlakeLexer.RPAREN) {
            if (alone && depth > 0) {
                return true;
            }
            if (spoken.get(token - 1).getType() == FrostlakeLexer.COMMA) {
                // A list that ends in a comma, each such list refused in turn: ABS(1,), ABS(2,) (live-verified).
                return true;
            }
            for (int i = token - 1, nested = 0; i > start; i--) {
                final int each = spoken.get(i).getType();
                if (each == FrostlakeLexer.RPAREN) {
                    nested++;
                } else if (each == FrostlakeLexer.LPAREN && nested-- == 0) {
                    return spoken.get(i - 1).getType() == FrostlakeLexer.EXCLUDE;
                }
            }
        }
        return false;
    }

    /** The '(' of the innermost bracket open at {@code index}, or -1 when none is. */
    private static int openerOf(final List<Token> spoken, final int start, final int index) {
        int nested = 0;
        for (int i = index - 1; i > start; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                nested++;
            } else if (type == FrostlakeLexer.LPAREN && nested-- == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int depthAt(final List<Token> spoken, final int start, final int index) {
        int depth = 0;
        for (int i = start; i < index; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        return depth;
    }

    private static boolean opensAStatement(final int type) {
        for (final int opener : OPENERS) {
            if (opener == type) {
                return true;
            }
        }
        return false;
    }

    /** Whether a query may open a block's statement after a token. */
    private static boolean opensStatement(final int before) {
        return before == FrostlakeLexer.SEMI || before == FrostlakeLexer.BEGIN || before == FrostlakeLexer.THEN
            || before == FrostlakeLexer.ELSE || before == FrostlakeLexer.DO || before == FrostlakeLexer.LOOP
            || before == FrostlakeLexer.REPEAT;
    }

    /** The text with everything outside a stretch blanked, so that the stretch is read as a statement of its own. */
    private static String alone(final String sql, final int from, final int to) {
        final char[] text = sql.toCharArray();
        blank(text, 0, from - 1);
        blank(text, to, text.length - 1);
        return new String(text);
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = Math.max(from, 0); c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }
}
