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
 * </pre>
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
        for (final Token token : stream.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                spoken.add(token);
            }
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
        if (!SyntaxErrorListener.isNameLike(spoken.get(first - 3))) {
            return null;
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
        final int[] shown = LeadingCommentOffset.rebase(closing.getLine(), closing.getCharPositionInLine());
        final String ended = "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected ';'.";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(ended) || lines.get(i).endsWith(END_OF_INPUT)) {
                lines.set(i, "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected ')'.");
            }
        }
        return lines;
    }
}
