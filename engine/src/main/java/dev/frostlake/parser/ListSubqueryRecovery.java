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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * A subquery written without parentheses of its own as a LATER element of a list — a call's second argument,
 * {@code CONCAT('a', SELECT 'b')}, or an IN list's, {@code a IN (1, SELECT 2)} — refused where live refuses it.
 * Only a call's first argument may be such a subquery, so live names the SELECT or WITH, and then recovers the way
 * its parser recovers from any refused token: it drops the keyword and every token after it that could not follow
 * the list closed right there, and resumes at the first one that could. A comma or the list's closing parenthesis
 * carries on the list; any other such token closes the list before itself and is read as what follows it, unless
 * the closing parenthesis comes right after it, which drops that token as well (all live-verified):
 *
 * <pre>
 *   SELECT CONCAT('a', SELECT 'b')             'SELECT'
 *   SELECT CONCAT('a', SELECT 'b', 'c')        'SELECT'                the comma carries on the list
 *   SELECT CONCAT('a', SELECT 1 + 2)           'SELECT', then ')'      read as CONCAT('a') + 2, then a stray ')'
 *   SELECT CONCAT('a', SELECT 'b' FROM t)      'SELECT', then ')'      FROM t becomes the query's own clause
 *   SELECT CONCAT('a', SELECT 'b' AS)          'SELECT'                AS stands right before the ')'
 *   SELECT CONCAT('a', WITH x AS (SELECT 1))   'WITH', then 'AS'       x reads as the call's alias, and nothing follows
 *   SELECT a FROM t WHERE a IN (1, SELECT 2)   'SELECT'
 * </pre>
 *
 * <p>A NOT is never where live resumes: {@code CONCAT('a', SELECT NOT 'b')} is the SELECT alone. This parser takes
 * other roads there — a statement's semicolon is optional in its grammar, so it may split the statement at the
 * SELECT — so the resumption is rebuilt on the text. The keyword, the comma before it and the dropped tokens are
 * blanked, a ')' stands in the keyword's place when the list closes early, and the lines after the keyword's are
 * what a parse of that text reports, with every position where it was written.
 */
final class ListSubqueryRecovery {

    private ListSubqueryRecovery() {
    }

    /**
     * The lines live reports for the first such subquery, or null when the text holds none before its first fault.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param firstFault     where the parse's first line stands: its line and position
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken, final int[] firstFault,
                                final boolean statementParse) {
        if (sql == null || firstFault == null || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        final int keyword = firstLaterElementSubquery(spoken);
        if (keyword < 0) {
            return null;
        }
        final Token opener = spoken.get(keyword);
        final int[] at = LeadingCommentOffset.rebase(opener.getLine(), opener.getCharPositionInLine());
        // This parser refuses an IN whose list it cannot read at the IN itself; any earlier line is a fault of its own.
        final Token listHead = spoken.get(enclosingOpen(spoken, keyword) - 1);
        final int[] earliest = listHead.getType() == FrostlakeLexer.IN
            ? LeadingCommentOffset.rebase(listHead.getLine(), listHead.getCharPositionInLine()) : at;
        if (firstFault[0] < earliest[0] || firstFault[0] == earliest[0] && firstFault[1] < earliest[1]) {
            return null;
        }
        String repaired = null;
        boolean aliased = false;
        for (int j = keyword + 1; j < spoken.size() && repaired == null; j++) {
            final int type = spoken.get(j).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.RPAREN) {
                repaired = blanked(sql, spoken, keyword, j - 1, false);
                continue;
            }
            final Boolean resumes = resumesAt(sql, spoken, keyword, j);
            if (resumes == null) {
                return null;
            }
            if (resumes.booleanValue()) {
                final boolean deleted = spoken.get(j + 1).getType() == FrostlakeLexer.RPAREN;
                repaired = deleted ? blanked(sql, spoken, keyword, j, false) : blanked(sql, spoken, keyword, j - 1, true);
                aliased = !deleted && isName(spoken.get(j));
            }
        }
        if (repaired == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(opener));
        for (final String line : SyntaxErrorListener.linesReportedFor(repaired, statementParse)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place == null || place[0] > at[0] || place[0] == at[0] && place[1] > at[1]) {
                lines.add(line);
                if (aliased) {
                    // Read as the call's alias, the name leaves nothing after the next fault to report.
                    break;
                }
            }
        }
        return lines;
    }

    /** Whether a token may be a name. */
    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    /**
     * The index in {@code spoken} of the first SELECT or WITH standing right after a comma inside a call's
     * parentheses or an IN list, or -1.
     */
    private static int firstLaterElementSubquery(final List<Token> spoken) {
        final ATN atn = FrostlakeParser._ATN;
        final IntervalSet names = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_functionName]);
        for (int i = 2; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.SELECT && type != FrostlakeLexer.WITH
                    || spoken.get(i - 1).getType() != FrostlakeLexer.COMMA) {
                continue;
            }
            final int open = enclosingOpen(spoken, i);
            if (open < 1) {
                continue;
            }
            final int before = spoken.get(open - 1).getType();
            if (before == FrostlakeLexer.IN || names.contains(before)) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the innermost '(' still open at {@code index}, or -1. */
    private static int enclosingOpen(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /**
     * Whether live's recovery resumes at the token at {@code index}: the text up to it, with the list closed right
     * before the dropped keyword, reads that token without refusing it. Null when that text is refused BEFORE the
     * keyword, which means the statement had an earlier fault of its own.
     */
    private static Boolean resumesAt(final String sql, final List<Token> spoken, final int keyword, final int index) {
        final Token token = spoken.get(index);
        final String trial = blanked(sql, spoken, keyword, index - 1, true).substring(0, token.getStopIndex() + 1);
        final int refused = firstRefusedStart(trial);
        if (refused >= 0 && refused < spoken.get(keyword - 1).getStartIndex()) {
            return null;
        }
        final int type = token.getType();
        if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH || type == FrostlakeLexer.NOT) {
            return Boolean.FALSE;
        }
        return Boolean.valueOf(refused != token.getStartIndex());
    }

    /** The character index of the token the first syntax error of a parse of {@code text} names, or -1 when none. */
    private static int firstRefusedStart(final String text) {
        final int[] first = {-1};
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (first[0] < 0 && offendingSymbol instanceof Token) {
                    final Token offending = (Token) offendingSymbol;
                    first[0] = offending.getType() == Token.EOF ? text.length() : offending.getStartIndex();
                }
            }
        });
        parser.sqlScript();
        return first[0];
    }

    /**
     * {@code sql} with the comma before the keyword at {@code keyword} and every token from the keyword through
     * {@code last} blanked, and a ')' in the keyword's first character when {@code closeList}.
     */
    private static String blanked(final String sql, final List<Token> spoken, final int keyword, final int last,
                                  final boolean closeList) {
        final char[] text = sql.toCharArray();
        blank(text, spoken.get(keyword - 1));
        for (int i = keyword; i <= last; i++) {
            blank(text, spoken.get(i));
        }
        if (closeList) {
            text[spoken.get(keyword).getStartIndex()] = ')';
        }
        return new String(text);
    }

    /** Replace every character of {@code token} but a line break with a blank. */
    private static void blank(final char[] text, final Token token) {
        for (int c = token.getStartIndex(); c <= token.getStopIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }
}
