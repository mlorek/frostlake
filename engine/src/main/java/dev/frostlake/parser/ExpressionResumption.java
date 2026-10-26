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
import org.antlr.v4.runtime.ParserRuleContext;
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
 *
 * <p>In the branch of an IF or a CASE statement, and in a REPEAT's body, the lines are read as in the block's own
 * list - where a name comes first, the line is the token after that name - and then the last of them moves on to
 * the token after the first name past it, a semicolon's following END passed, an IF's or a CASE's search going on past
 * the statement's semicolon and a REPEAT's ending there (all live-verified):
 *
 * <pre>
 *   IF … THEN LET a := 1  then  CREATE OR REPLACE TABLE u (a INT);   'CREATE', 'TABLE', 'INT'
 *   IF … THEN LET a := 1  then  CREATE TABLE u (a INT);              'CREATE', 'INT'
 *   IF … THEN LET a := 1  then  UPDATE u SET a = 1;                  'UPDATE', '='
 *   IF … THEN LET a := 1  then  INSERT INTO u VALUES (1); END IF;    'INSERT', then the END after END IF;
 *   REPEAT LET a := 1  then  INSERT INTO u VALUES (1); UNTIL …        'INSERT' alone
 * </pre>
 *
 * <p>In a nested block's list and a FOR's body the block's own lines are read and one more follows them: the token
 * after the first name past the last of them, the statement's semicolon passed. In a WHILE's body and a LOOP's the
 * lines go on from the value's own fault, each the token after the first name past the one before; a statement
 * read on at a name passes that name over, and its second line is the token after the first name at or after the
 * one it names, a name standing for itself - but for the table INTO names, read as the fault it is (all
 * live-verified):
 *
 * <pre>
 *   BEGIN … LET a := 1  then  CREATE TABLE u (a INT);                 'CREATE', '(', 'INT'
 *   FOR … DO LET a := 1  then  CREATE OR REPLACE TABLE u (a INT, …);   'CREATE', 'TABLE', '(', 'INT'
 *   LOOP LET a := 1  then  CREATE OR REPLACE VIEW v AS SELECT 1;       'CREATE', 'VIEW', 'AS', the END after END LOOP;
 *   LOOP LET a := 1  then  CREATE TABLE u (a INT, b INT);              'CREATE', 'INT', ','
 *   LOOP LET a := 1  then  UPDATE u SET a = 1; END LOOP;               'UPDATE', '=', the END after END LOOP;
 *   LOOP LET a := 1  then  INSERT INTO u (a) VALUES (1, 2);            'INSERT', '(', ')'
 *   LOOP LET a := 1  then  DELETE FROM u; END LOOP;                    'DELETE', the END after END LOOP;
 * </pre>
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
        final boolean branch = inBranchBody(recognizer);
        final boolean loop = inLoopBody(list);
        final boolean inner = inInnerBody(list);
        if (!(list.getParent() instanceof FrostlakeParser.BeginEndBlockContext)
                && !(list.getParent() instanceof FrostlakeParser.ExceptionHandlerContext) && !branch && !loop
                && !inner) {
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
        final Token[] nameFirst = new Token[1];
        final List<Token> read = read(parser, stream, text, keep, word, end, nameFirst);
        if (loop) {
            return loopLines(stream, read, nameFirst[0]);
        }
        if (inner) {
            return innerLines(stream, word, read, nameFirst[0]);
        }
        if (!branch) {
            return read;
        }
        // A branch's lines: as the block's own, the token after a name that comes first standing for them, the last
        // moved on past the first name after it.
        List<Token> outer = read;
        if (outer == null) {
            final Token afterName = nameFirst[0] != null ? SyntaxErrorListener.afterFirstName(stream, word) : null;
            if (afterName == null) {
                return null;
            }
            outer = new ArrayList<>();
            outer.add(afterName);
        }
        if (outer.isEmpty()) {
            return outer;
        }
        final List<Token> lines = new ArrayList<>(outer.subList(0, outer.size() - 1));
        final Token next = pastNextName(stream, outer.get(outer.size() - 1),
            !(list.getParent() instanceof FrostlakeParser.RepeatStatementContext));
        if (next != null) {
            lines.add(next);
        }
        return lines;
    }

    /**
     * Whether the parser stands in the statement list of an IF's or a CASE statement's branch, or of a REPEAT's body.
     *
     * @param recognizer the parser
     * @return whether a branch's reading applies
     */
    static boolean inBranchBody(final Recognizer<?, ?> recognizer) {
        if (!(recognizer instanceof Parser)
                || !(((Parser) recognizer).getContext() instanceof FrostlakeParser.StatementListContext)) {
            return false;
        }
        final ParserRuleContext construct = ((Parser) recognizer).getContext().getParent();
        return construct instanceof FrostlakeParser.IfStatementContext
            || construct instanceof FrostlakeParser.CaseStatementContext
            || construct instanceof FrostlakeParser.RepeatStatementContext;
    }

    /**
     * Whether the report ends with the lines this reading stacks: in a branch's, a loop's or an inner body's list.
     *
     * @param recognizer the parser
     * @return whether nothing after the stacked lines is reported
     */
    static boolean endsTheReport(final Recognizer<?, ?> recognizer) {
        if (inBranchBody(recognizer)) {
            return true;
        }
        if (!(recognizer instanceof Parser)
                || !(((Parser) recognizer).getContext() instanceof FrostlakeParser.StatementListContext)) {
            return false;
        }
        final FrostlakeParser.StatementListContext list =
            (FrostlakeParser.StatementListContext) ((Parser) recognizer).getContext();
        return inLoopBody(list) || inInnerBody(list);
    }

    /** Whether a statement list is a WHILE's body or a LOOP's. */
    private static boolean inLoopBody(final FrostlakeParser.StatementListContext list) {
        return list.getParent() instanceof FrostlakeParser.WhileStatementContext
            || list.getParent() instanceof FrostlakeParser.LoopStatementContext;
    }

    /** Whether a statement list is a FOR's body or the list of a block nested in another block's statements. */
    private static boolean inInnerBody(final FrostlakeParser.StatementListContext list) {
        if (list.getParent() instanceof FrostlakeParser.ForStatementContext) {
            return true;
        }
        if (!(list.getParent() instanceof FrostlakeParser.BeginEndBlockContext)) {
            return false;
        }
        for (ParserRuleContext up = list.getParent().getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return true;
            }
        }
        return false;
    }

    /**
     * A WHILE's or a LOOP's lines: past a value that resumed, its fault and then twice the token after the first name
     * past the line before; past a name met first, the token after the first name past it, and the token after the
     * first name at or after that one - the table INTO names giving the token after it and one line more.
     *
     * @param stream the parse's token stream
     * @param read   the block's own reading, or null when no token resumed the value
     * @param name   the name met first, or null
     * @return the lines, or null when this reading names none
     */
    private static List<Token> loopLines(final TokenStream stream, final List<Token> read, final Token name) {
        final List<Token> lines = new ArrayList<>();
        if (read != null) {
            if (read.isEmpty()) {
                return read;
            }
            Token at = read.get(0);
            lines.add(at);
            for (int i = 0; i < 2 && at != null; i++) {
                at = pastNextName(stream, at, true);
                if (at != null) {
                    lines.add(at);
                }
            }
            return lines;
        }
        if (name == null) {
            return null;
        }
        final Token before = previousSpoken(stream, name.getTokenIndex());
        if (before != null && before.getType() == FrostlakeLexer.INTO) {
            final Token own = nextSpoken(stream, name.getTokenIndex());
            if (own == null || own.getType() == Token.EOF) {
                return null;
            }
            lines.add(own);
            final Token next = pastNextName(stream, own, true);
            if (next != null) {
                lines.add(next);
            }
            return lines;
        }
        final Token second = pastNextName(stream, name, true);
        if (second == null) {
            return lines;
        }
        lines.add(second);
        final Token third = atOrPastName(stream, second);
        if (third != null) {
            lines.add(third);
        }
        return lines;
    }

    /**
     * A FOR's body's or a nested block's lines: the block's own reading, then the token after the first name past the
     * last of them, the statement's semicolon passed.
     *
     * @param stream the parse's token stream
     * @param word   the refused word
     * @param read   the block's own reading, or null when no token resumed the value
     * @param name   the name met first, or null
     * @return the lines, or null when this reading names none
     */
    private static List<Token> innerLines(final TokenStream stream, final Token word, final List<Token> read,
                                          final Token name) {
        final List<Token> lines = new ArrayList<>();
        if (read != null) {
            lines.addAll(read);
        } else {
            final Token afterName = name != null ? SyntaxErrorListener.afterFirstName(stream, word) : null;
            if (afterName == null) {
                return null;
            }
            lines.add(afterName);
        }
        if (lines.isEmpty()) {
            return lines;
        }
        final Token next = pastNextName(stream, lines.get(lines.size() - 1), true);
        if (next != null) {
            lines.add(next);
        }
        return lines;
    }

    /** As {@link #pastNextName}, crossing semicolons, with {@code from} itself the name when it is one. */
    private static Token atOrPastName(final TokenStream stream, final Token from) {
        if (from.getType() == FrostlakeLexer.END || !SyntaxErrorListener.isNameLike(from)) {
            return pastNextName(stream, from, true);
        }
        Token next = nextSpoken(stream, from.getTokenIndex());
        while (next != null && next.getType() == FrostlakeLexer.SEMI) {
            next = nextSpoken(stream, next.getTokenIndex());
        }
        return next == null || next.getType() == Token.EOF ? null : next;
    }

    /**
     * The token after the first name past {@code from}, the semicolons after the name passed; an END is no such name,
     * and the search ends at a semicolon unless it {@code crosses} one. Null when it finds none.
     */
    private static Token pastNextName(final TokenStream stream, final Token from, final boolean crosses) {
        for (Token at = nextSpoken(stream, from.getTokenIndex()); at != null && at.getType() != Token.EOF;
                at = nextSpoken(stream, at.getTokenIndex())) {
            if (at.getType() == FrostlakeLexer.SEMI) {
                if (!crosses) {
                    return null;
                }
                continue;
            }
            if (at.getType() == FrostlakeLexer.END || !SyntaxErrorListener.isNameLike(at)) {
                continue;
            }
            Token next = nextSpoken(stream, at.getTokenIndex());
            while (next != null && next.getType() == FrostlakeLexer.SEMI) {
                next = nextSpoken(stream, next.getTokenIndex());
            }
            return next == null || next.getType() == Token.EOF ? null : next;
        }
        return null;
    }

    /**
     * The block's own reading: the resumed statement's fault and the one after it, none when it reads on without
     * one, or null when no token before the statement's semicolon resumes it - a name met first kept in
     * {@code nameFirst}.
     */
    private static List<Token> read(final Parser parser, final TokenStream stream, final char[] text, final int keep,
                                    final Token word, final Token end, final Token[] nameFirst) {
        int attempts = 0;
        for (Token resume = nextSpoken(stream, word.getTokenIndex()); resume != null && resume != end
                && resume.getType() != FrostlakeLexer.SEMI && resume.getType() != Token.EOF;
                resume = nextSpoken(stream, resume.getTokenIndex())) {
            if (SyntaxErrorListener.isNameLike(parser, resume) || ++attempts > MAX_ATTEMPTS) {
                nameFirst[0] = SyntaxErrorListener.isNameLike(parser, resume) ? resume : null;
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
