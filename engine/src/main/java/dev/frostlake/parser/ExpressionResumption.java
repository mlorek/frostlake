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
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The lines live stacks when a LET or a RETURN directly inside a BEGIN … END block or an exception handler runs into the
 * next word without its semicolon. Past the refused word live looks for the first token the value can go on with,
 * passing over the reserved words it cannot take: met before any name, the statement resumes there, the fault it then
 * meets is the second line, and the recovery runs once more from that fault the way
 * {@code SyntaxErrorListener.afterFirstName} describes; a name met first ends the search, and the second line is that
 * name's own (all live-verified):
 *
 * <pre>
 *   LET a := 1  then  CREATE OR REPLACE TABLE u (a INT);   'CREATE', 'TABLE', '('   OR goes on with the value
 *   LET a := 1  then  CREATE OR REPLACE VIEW v AS …;       'CREATE', 'VIEW', 'v'
 *   RETURN 'x'  then  CREATE OR REPLACE VIEW v AS …;       'CREATE', 'VIEW', 'v'
 *   LET a := 1  then  CREATE TABLE OR x y;                 'CREATE', 'y', 'END'     past TABLE, OR goes on
 *   LET a := 1  then  CREATE + 2 x;                        'CREATE', 'x', 'END'
 *   LET a := 1  then  CREATE OR;                           'CREATE', ';'
 *   LET a := 1  then  CREATE OR 2;                         'CREATE' alone           the value reads on whole
 *   LET a := 1  then  UPDATE t OR x y;                     'UPDATE', 'OR'           the name t comes first
 *   LET a := b  then  CREATE OR REPLACE TABLE u (a INT);   'CREATE', 'TABLE'        a value ending in a word
 * </pre>
 *
 * <p>A value that ends in a word — a name, TRUE, FALSE, CURRENT_DATE — is not resumed; one that ends in a number, a
 * string, NULL, a closing bracket, a bind variable, a cast's type or the END of a CASE expression is.
 */
final class ExpressionResumption {

    /**
     * How many tokens past the refused word the search tries. A value resumes within a few tokens of the refused word;
     * a longer run of tokens it cannot take is given up rather than re-read token by token.
     */
    private static final int MAX_ATTEMPTS = 16;

    private ExpressionResumption() {
    }

    /**
     * The tokens live refuses after {@code word} when it interrupts such a LET or RETURN: the resumed statement's fault
     * and the one after it, none when the statement resumes and reads on without a fault, or null when this is not that
     * case or no token before the statement's semicolon resumes it.
     *
     * @param recognizer the parser, its context the statement list the word stands in
     * @param sql        the text being parsed
     * @param word       the refused word
     * @return the stacked tokens, none, or null
     */
    static List<Token> lines(final Recognizer<?, ?> recognizer, final String sql, final Token word) {
        if (!(recognizer instanceof Parser) || sql == null || word.getType() == Token.EOF) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        if (!(parser.getContext() instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        final FrostlakeParser.StatementListContext list = (FrostlakeParser.StatementListContext) parser.getContext();
        if (!(list.getParent() instanceof FrostlakeParser.BeginEndBlockContext)
                && !(list.getParent() instanceof FrostlakeParser.ExceptionHandlerContext)) {
            return null;
        }
        FrostlakeParser.StatementContext finished = null;
        for (int i = list.getChildCount() - 1; i >= 0 && finished == null; i--) {
            if (list.getChild(i) instanceof FrostlakeParser.StatementContext) {
                finished = (FrostlakeParser.StatementContext) list.getChild(i);
            }
        }
        if (finished == null || finished.getStart() == null || finished.getStop() == null
                || !endsInValue(finished.proceduralStatement())) {
            return null;
        }
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        if (endsInWord(parser, stream, finished.getStop())) {
            return null;
        }
        // The statement is read on its own, at its own place: everything before it is blanked, line breaks kept.
        final char[] text = sql.toCharArray();
        for (int i = 0; i < finished.getStart().getStartIndex() && i < text.length; i++) {
            if (text[i] != '\n' && text[i] != '\r') {
                text[i] = ' ';
            }
        }
        Token end = nextSpoken(stream, word.getTokenIndex());
        while (end != null && end.getType() != FrostlakeLexer.SEMI && end.getType() != Token.EOF) {
            end = nextSpoken(stream, end.getTokenIndex());
        }
        final int keep = end == null || end.getType() == Token.EOF ? text.length : end.getStopIndex() + 1;
        int attempts = 0;
        for (Token resume = nextSpoken(stream, word.getTokenIndex()); resume != null && resume != end
                && resume.getType() != FrostlakeLexer.SEMI && resume.getType() != Token.EOF;
                resume = nextSpoken(stream, resume.getTokenIndex())) {
            if (SyntaxErrorListener.isNameLike(parser, resume) || ++attempts > MAX_ATTEMPTS) {
                return null;
            }
            final StringBuilder candidate = new StringBuilder(new String(text, 0, Math.min(keep, text.length)));
            for (int i = word.getStartIndex(); i < resume.getStartIndex() && i < candidate.length(); i++) {
                if (candidate.charAt(i) != '\n' && candidate.charAt(i) != '\r') {
                    candidate.setCharAt(i, ' ');
                }
            }
            final String first = StatementSeparation.firstFaultOf(candidate.toString(), resume.getStartIndex());
            if (first == null) {
                continue;
            }
            final List<Token> lines = new ArrayList<>();
            if (first.isEmpty()) {
                return lines;
            }
            final Token fault = tokenAt(stream, resume, SyntaxErrorListener.sentenceCoordinates(first));
            if (fault == null || fault == resume) {
                continue;
            }
            lines.add(fault);
            final Token again = SyntaxErrorListener.afterFirstName(parser, fault);
            if (again != null) {
                lines.add(again);
            }
            return lines;
        }
        return null;
    }

    /** Whether a statement is a LET or a RETURN whose last part is an expression value. */
    private static boolean endsInValue(final FrostlakeParser.ProceduralStatementContext statement) {
        if (statement == null) {
            return false;
        }
        if (statement.letStatement() != null) {
            return statement.letStatement().booleanExpr() != null;
        }
        return statement.returnStatement() != null && statement.returnStatement().booleanExpr() != null
            && statement.returnStatement().TABLE() == null;
    }

    /** Whether the value's last token is a word, not a bind variable's name, a cast's type or a CASE's END. */
    private static boolean endsInWord(final Parser parser, final TokenStream stream, final Token last) {
        Token word = last;
        if (word.getType() == FrostlakeLexer.SEMI) {
            word = previousSpoken(stream, word.getTokenIndex());
        }
        if (word == null || word.getType() == FrostlakeLexer.END || !SyntaxErrorListener.isNameLike(parser, word)) {
            return false;
        }
        final Token before = previousSpoken(stream, word.getTokenIndex());
        return before == null
            || before.getType() != FrostlakeLexer.COLON && before.getType() != FrostlakeLexer.DOUBLE_COLON;
    }

    /** The token at or after {@code from} standing at the given line and position, or null. */
    private static Token tokenAt(final TokenStream stream, final Token from, final int[] at) {
        if (at == null) {
            return null;
        }
        for (Token token = from; token != null && token.getType() != Token.EOF;
                token = nextSpoken(stream, token.getTokenIndex())) {
            final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (shown[0] == at[0] && shown[1] == at[1]) {
                return token;
            }
        }
        return null;
    }

    private static Token nextSpoken(final TokenStream stream, final int after) {
        for (int i = after + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    private static Token previousSpoken(final TokenStream stream, final int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
