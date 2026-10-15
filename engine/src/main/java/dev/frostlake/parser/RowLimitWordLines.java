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
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The lines the account reports for the token after LIMIT, OFFSET or FETCH when the word stands where a table's
 * alias may: it is read both ways — as the alias, which the token cannot follow, and as the clause, whose value the
 * token cannot be — and the token is reported once for each reading (all live-verified):
 *
 * <pre>
 *   SELECT a FROM t LIMIT -1                  '-' twice
 *   SELECT a FROM t LIMIT x y                 'x' twice
 *   SELECT a FROM (SELECT a FROM t) LIMIT -1  '-' twice
 *   SELECT a FROM t OFFSET x                  'x' twice
 *   SELECT a FROM t LIMIT (1)                 '1', then the '(': an alias takes a list of column names
 *   SELECT a FROM t LIMIT ((1))               the second '(', then the first
 *   SELECT a FROM t t2 LIMIT -1               '-' once: the table has its alias
 *   SELECT a FROM t ORDER BY a LIMIT -1       '-' once: no alias stands after an ORDER BY
 * </pre>
 *
 * <p>Whether the word may be an alias is asked of the grammar: the statement cut after the word, its open brackets
 * closed, parses when it may. A statement that ends at the word where it is the clause alone lacks the clause's
 * value at the end of the input: {@code SELECT a FROM t ORDER BY a LIMIT} names {@code <EOF>}.
 */
final class RowLimitWordLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private RowLimitWordLines() {
    }

    /**
     * The lines of a text whose first fault stands straight after such a word, the token told for both readings.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @param lines      the lines worked out for the text
     * @return {@code lines}, or the lines that tell both readings
     */
    static List<String> withBothReadings(final String sql, final List<Token> spoken, final int faultIndex,
                                         final List<String> lines) {
        if (spoken.size() < 4) {
            return lines;
        }
        int at = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
                break;
            }
        }
        if (at < 1) {
            return lines;
        }
        final int end = spoken.size() - 1;
        if (isRowLimitWord(spoken.get(at)) && at + 1 == end) {
            // The clause's value is missing where the input ends.
            return lines.size() != 1 || mayBeAlias(sql, spoken, at) ? lines : told(spoken.get(end));
        }
        // The word, and the first '(' after it when the fault stands in the brackets the value opens with.
        int word = at - 1;
        while (word > 0 && spoken.get(word).getType() == FrostlakeLexer.LPAREN && at - word <= 2) {
            word--;
        }
        if (spoken.get(at).getType() == FrostlakeLexer.LPAREN && isRowLimitWord(spoken.get(at - 1))) {
            word = at - 1;
        }
        if (!isRowLimitWord(spoken.get(word)) || !mayBeAlias(sql, spoken, word)) {
            return lines;
        }
        final Token after = spoken.get(word + 1);
        final String first;
        final String second;
        if (after.getType() != FrostlakeLexer.LPAREN) {
            if (at != word + 1 || after.getType() == FrostlakeLexer.QUESTION || after.getType() == FrostlakeLexer.COLON
                    || after.getType() == Token.EOF || after.getType() == FrostlakeLexer.SEMI) {
                return lines;
            }
            first = SyntaxErrorListener.sentence(after);
            second = first;
        } else {
            if (word + 2 > end) {
                return lines;
            }
            final Token inside = spoken.get(word + 2);
            if (WORDS.contains(inside.getType()) || inside.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
                // A name there opens the alias's list of column names.
                return lines;
            }
            first = SyntaxErrorListener.sentence(inside);
            second = SyntaxErrorListener.sentence(after);
        }
        final int told = lines.indexOf(first);
        if (told < 0) {
            // The parse names another token of the brackets: the two readings' lines stand for it.
            if (lines.size() != 1 || first.equals(second)) {
                return lines;
            }
            final List<String> both = told(first);
            both.add(second);
            return both;
        }
        if (told + 1 < lines.size() && lines.get(told + 1).equals(second) || lines.lastIndexOf(second) > told) {
            return lines;
        }
        final List<String> both = new ArrayList<>(lines);
        both.add(told + 1, second);
        return both;
    }

    private static List<String> told(final String line) {
        final List<String> lines = new ArrayList<>();
        lines.add(line);
        return lines;
    }

    private static boolean isRowLimitWord(final Token token) {
        return token.getType() == FrostlakeLexer.LIMIT || token.getType() == FrostlakeLexer.OFFSET
            || token.getType() == FrostlakeLexer.FETCH;
    }

    /** Whether the statement reads whole when it ends at the word: the word is then the alias of what stands before it. */
    private static boolean mayBeAlias(final String sql, final List<Token> spoken, final int word) {
        int from = 0;
        int open = 0;
        for (int i = 0; i < word; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                from = spoken.get(i).getStopIndex() + 1;
                open = 0;
            } else if (type == FrostlakeLexer.LPAREN) {
                open++;
            } else if (type == FrostlakeLexer.RPAREN) {
                open--;
            }
        }
        if (open < 0) {
            return false;
        }
        final StringBuilder text = new StringBuilder(sql.substring(from, spoken.get(word).getStopIndex() + 1));
        for (int i = 0; i < open; i++) {
            text.append(')');
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text.toString()));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = parser.sqlScript();
        } catch (final ParseCancellationException doesNotParse) {
            return false;
        }
        // The word must be a TABLE's alias: a select item's is no second reading (SELECT 1 LIMIT -1 names the '-'
        // once).
        ParseTree last = script;
        while (last.getChildCount() > 0) {
            ParseTree child = null;
            for (int i = last.getChildCount() - 1; i >= 0 && child == null; i--) {
                final ParseTree candidate = last.getChild(i);
                final boolean ends = candidate instanceof TerminalNode
                    && (((TerminalNode) candidate).getSymbol().getType() == Token.EOF
                        || ((TerminalNode) candidate).getSymbol().getType() == FrostlakeLexer.RPAREN
                        || ((TerminalNode) candidate).getSymbol().getType() == FrostlakeLexer.SEMI);
                if (!ends) {
                    child = candidate;
                }
            }
            if (child == null) {
                return false;
            }
            last = child;
        }
        for (ParseTree up = last; up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.TableReferenceContext) {
                // An alias follows what it names: the word alone after FROM or JOIN is the table itself.
                return ((FrostlakeParser.TableReferenceContext) up).getStart() != ((TerminalNode) last).getSymbol();
            }
            if (up instanceof FrostlakeParser.SelectItemContext) {
                return false;
            }
        }
        return false;
    }

    private static List<String> told(final Token token) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }
}
