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

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * A query assigned in parentheses that does not parse, {@code res := (SELECT …)}. The statement's own parse gives
 * the assignment up at the query's first word; the account reads the query on and reports the faults the query has,
 * where they stand in the text, the end a clause still waits at being the parenthesis that closes the query
 * (live-verified):
 *
 * <pre>
 *   res := (SELECT v FROM t ORDER BY v OFFSET :s);         the ')': an OFFSET alone wants its ROWS
 *   res := (SELECT TOP :s v AS c FROM t ORDER BY v);       'AS', then 'FROM': TOP is a name, :s a path, v its alias
 *   res := (SELECT * FROM t UNPIVOT (x FOR y IN (:s)));    the ':': the list takes names
 *   LET r RESULTSET := (SELECT 1 AS 1, 2 AS 2);            '1', '2': a RESULTSET's query is read as a statement of
 *                                                          its own, and nothing after it
 *   DECLARE x INT; BEGIN x := (SELECT 1 x y); …            'y' alone: a target declared of another type than
 *                                                          RESULTSET reads its query so too
 * </pre>
 *
 * <p>A LET of any other type, or of none, reads its query as a value (see {@link BlockValueSubqueryLines}).
 */
public final class AssignedQueryLines {

    private static final String END_OF_INPUT = "unexpected '<EOF>'.";

    private AssignedQueryLines() {
    }

    /**
     * Refuse the text by its assigned query's own faults when the parse gave the assignment up at that query's
     * first word.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors()) {
            return;
        }
        final String first = listener.firstReportedLine();
        final List<String> lines = first == null ? null : linesOf(sql, first);
        if (lines != null) {
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
    }

    private static List<String> linesOf(final String sql, final String firstLine) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> spoken = new ArrayList<>();
        Token end = null;
        for (final Token token : stream.getTokens()) {
            if (token.getType() == Token.EOF) {
                end = token;
            } else if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                spoken.add(token);
            }
        }
        final List<String> tableNames = AssignedTableNameLines.linesOf(sql, spoken, end, firstLine);
        if (tableNames != null) {
            return tableNames;
        }
        // The first line names the word that opens the query, straight after the assignment's parenthesis.
        int first = -1;
        for (int i = 2; i < spoken.size() && first < 0; i++) {
            final Token token = spoken.get(i);
            if (token.getType() != FrostlakeLexer.SELECT && token.getType() != FrostlakeLexer.WITH
                    || spoken.get(i - 1).getType() != FrostlakeLexer.LPAREN
                    || spoken.get(i - 2).getType() != FrostlakeLexer.COLON_EQ) {
                continue;
            }
            final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (firstLine.equals("syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '"
                    + token.getText() + "'.")) {
                first = i;
            }
        }
        if (first < 0) {
            return null;
        }
        final int faultAt = spoken.get(first).getStartIndex();
        int close = -1;
        int depth = 0;
        for (int i = first; i < spoken.size() && close < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    close = i;
                }
                depth--;
            }
        }
        if (close < 0) {
            return null;
        }
        final Token closing = spoken.get(close);
        if (!SyntaxErrorListener.isNameLike(spoken.get(first - 3))
                || first >= 4 && spoken.get(first - 4).getType() == FrostlakeLexer.LET) {
            return null;
        }
        if (first >= 5 && spoken.get(first - 5).getType() == FrostlakeLexer.LET) {
            return spoken.get(first - 3).getType() == FrostlakeLexer.RESULTSET
                ? resultSetLines(sql, spoken.get(first), closing) : null;
        }
        if (declaredScalar(spoken, first - 3)) {
            return framed(spoken, end, resultSetLines(sql, spoken.get(first), closing));
        }
        // The query as a statement of its own, standing where it stood in its block: the assignment's opening is
        // read over, and the parenthesis that closes the query ends the statement.
        final char[] text = sql.toCharArray();
        for (int c = spoken.get(first - 3).getStartIndex(); c < faultAt; c++) {
            if (text[c] != '\n') {
                text[c] = ' ';
            }
        }
        text[closing.getStartIndex()] = ';';
        if (close + 1 < spoken.size() && spoken.get(close + 1).getType() == FrostlakeLexer.SEMI) {
            text[spoken.get(close + 1).getStartIndex()] = ' ';
        }
        final int opener = spoken.get(0).getType();
        final List<String> lines = new ArrayList<>(SyntaxErrorListener.linesReportedFor(new String(text),
            opener != FrostlakeLexer.BEGIN && opener != FrostlakeLexer.DECLARE));
        if (lines.isEmpty()) {
            return null;
        }
        final List<String> bound = frameOffsetLines(spoken, end, lines.get(0));
        if (bound != null) {
            return bound;
        }
        final int[] shown = LeadingCommentOffset.rebase(closing.getLine(), closing.getCharPositionInLine());
        final String ended = "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected ';'.";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(ended) || lines.get(i).endsWith(END_OF_INPUT)) {
                lines.set(i, "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected ')'.");
            }
        }
        return lines;
    }

    /** The lines read, or a window frame offset's lines when the first of them names one (see below). */
    private static List<String> framed(final List<Token> spoken, final Token end, final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        final List<String> bound = frameOffsetLines(spoken, end, lines.get(0));
        return bound != null ? bound : lines;
    }

    /**
     * The lines for a window frame's offset the query writes that is no literal, read over the whole text as a LET's
     * RESULTSET query is (see {@link FrameOffsetLines}): the offset's two lines and the token after the name the
     * query's FROM reads first; null when the first line names no such offset.
     */
    private static List<String> frameOffsetLines(final List<Token> spoken, final Token end, final String firstLine) {
        if (end == null) {
            return null;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (firstLine.equals(SyntaxErrorListener.sentence(spoken.get(i)))) {
                final List<Token> ended = new ArrayList<>(spoken);
                ended.add(end);
                return FrameOffsetLines.lines(ended, i);
            }
        }
        return null;
    }

    /**
     * Whether the name at {@code target} is declared, in a DECLARE section before it, of a type other than RESULTSET
     * or CURSOR.
     */
    private static boolean declaredScalar(final List<Token> spoken, final int target) {
        final String name = spoken.get(target).getText();
        boolean declaring = false;
        for (int i = 0; i + 1 < target; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.DECLARE) {
                declaring = true;
            } else if (type == FrostlakeLexer.BEGIN) {
                declaring = false;
            } else if (declaring && spoken.get(i).getText().equalsIgnoreCase(name) && i > 0
                    && (spoken.get(i - 1).getType() == FrostlakeLexer.DECLARE
                        || spoken.get(i - 1).getType() == FrostlakeLexer.SEMI)) {
                final int declared = spoken.get(i + 1).getType();
                return declared != FrostlakeLexer.RESULTSET && declared != FrostlakeLexer.CURSOR;
            }
        }
        return false;
    }

    /**
     * The lines of a RESULTSET's query read as a statement of its own, every place kept: a line for the end of the
     * input names the parenthesis that closes the query.
     */
    private static List<String> resultSetLines(final String sql, final Token first, final Token closing) {
        final char[] text = sql.toCharArray();
        for (int c = 0; c < text.length; c++) {
            if ((c < first.getStartIndex() || c >= closing.getStartIndex()) && text[c] != '\n') {
                text[c] = ' ';
            }
        }
        final List<String> lines = new ArrayList<>(SyntaxErrorListener.linesReportedFor(new String(text), true));
        if (lines.isEmpty()) {
            return null;
        }
        final int[] shown = LeadingCommentOffset.rebase(closing.getLine(), closing.getCharPositionInLine());
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).endsWith(END_OF_INPUT)) {
                lines.set(i, "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected ')'.");
            }
        }
        return lines;
    }
}
