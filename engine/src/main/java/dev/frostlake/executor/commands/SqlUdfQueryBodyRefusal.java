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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.parser.ColdPrediction;
import dev.frostlake.parser.EndOfInput;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The refusal of a SQL UDF body holding a query that does not read. The account reads the body inside a frame of
 * parentheses and cannot tell a parenthesised query from a parenthesised expression there, so it refuses the query
 * at its keyword, in the frame's positions:
 * <ul>
 *   <li>a query body that does not read is refused at its first word — {@code SELECT 1 +}, {@code SELECT a FROM},
 *       {@code SELECT CASE} and a bare {@code SELECT} at 'SELECT', position 1, a CTE at 'WITH';</li>
 *   <li>the first query written in parentheses inside it is named too, at its own keyword, and when that query stops
 *       at a closing parenthesis before its expression does, so is that parenthesis: {@code SELECT 1 FROM (SELECT 1 +}
 *       names 'SELECT' 1, 'SELECT' 16 and ')' 26;</li>
 *   <li>a body opening with a whole parenthesised query reads it as an expression, so a set operator after it is
 *       refused: {@code (SELECT 1) UNION SELECT} at 'UNION', position 12;</li>
 *   <li>an expression body holding such a query names the outermost parenthesised query the parse broke in, the
 *       same way, and the end of input after it while that query is a bare keyword left open by the frame:
 *       {@code x IN (SELECT} names 'SELECT' 7 and '&lt;EOF&gt;' 14. A query EXISTS reads is a query already, so
 *       {@code EXISTS (SELECT 1 WHERE)} is refused where it breaks.</li>
 * </ul>
 * A body the account reads with a bare select-list alias this grammar lacks — {@code SELECT CASE … END CASE} —
 * stays fail-open, since the account creates and calls it. So does a query this grammar stops reading on a token
 * before the query's end, which the caller leaves alone: the account's grammar reads more than this one, and
 * creates {@code SELECT MAX(n) FROM t ORDER BY ALL}.
 */
final class SqlUdfQueryBodyRefusal {

    /** The words the account reads as a bare select-list alias, where this grammar does not. */
    private static final int[] BARE_ALIAS_WORDS = {
        FrostlakeLexer.CASE, FrostlakeLexer.WHEN, FrostlakeLexer.JOIN, FrostlakeLexer.INNER, FrostlakeLexer.CROSS,
        FrostlakeLexer.CAST, FrostlakeLexer.CONSTRAINT, FrostlakeLexer.DEFAULT
    };

    /**
     * The words that open what a query must go on to supply — a clause, a join, a predicate's other side, a CASE's
     * branches, an alias — so a query that stops after one ran out of text.
     */
    private static final int[] OPENING_WORDS = {
        FrostlakeLexer.SELECT, FrostlakeLexer.WITH, FrostlakeLexer.FROM, FrostlakeLexer.WHERE, FrostlakeLexer.GROUP,
        FrostlakeLexer.ORDER, FrostlakeLexer.BY, FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY, FrostlakeLexer.LIMIT,
        FrostlakeLexer.OFFSET, FrostlakeLexer.FETCH, FrostlakeLexer.UNION, FrostlakeLexer.EXCEPT, FrostlakeLexer.MINUS_KW,
        FrostlakeLexer.INTERSECT, FrostlakeLexer.JOIN, FrostlakeLexer.INNER, FrostlakeLexer.LEFT, FrostlakeLexer.RIGHT,
        FrostlakeLexer.FULL, FrostlakeLexer.OUTER, FrostlakeLexer.CROSS, FrostlakeLexer.NATURAL, FrostlakeLexer.ON,
        FrostlakeLexer.USING, FrostlakeLexer.AND, FrostlakeLexer.OR, FrostlakeLexer.NOT, FrostlakeLexer.IS,
        FrostlakeLexer.IN, FrostlakeLexer.BETWEEN, FrostlakeLexer.LIKE, FrostlakeLexer.ILIKE, FrostlakeLexer.RLIKE,
        FrostlakeLexer.REGEXP, FrostlakeLexer.ESCAPE, FrostlakeLexer.COLLATE, FrostlakeLexer.CASE, FrostlakeLexer.WHEN,
        FrostlakeLexer.THEN, FrostlakeLexer.ELSE, FrostlakeLexer.AS, FrostlakeLexer.CAST, FrostlakeLexer.TRY_CAST,
        FrostlakeLexer.EXISTS, FrostlakeLexer.DISTINCT, FrostlakeLexer.NULLS, FrostlakeLexer.OVER, FrostlakeLexer.PARTITION
    };

    private SqlUdfQueryBodyRefusal() {
    }

    /**
     * Whether the framed body's parse ran out of text rather than stopping on a token it could not place. A query
     * this grammar stops reading before its end may hold a construct only the account's grammar has, and is left
     * alone; one that runs out is refused. The parse runs out when it breaks at the end of the statement or at a
     * closing parenthesis — {@code SELECT 1 +} at the frame's — or when it breaks on a word or an operator that
     * opens what must follow and reads on to a closing parenthesis or the end before giving up: {@code SELECT CASE},
     * {@code SELECT 1 FROM t JOIN}, {@code … WHERE a IN (SELECT a FROM t WHERE)}. The account's {@code ORDER BY ALL}
     * breaks this grammar on a word that can end a query, and {@code MATCH_RECOGNIZE (…)} where the parse gives up
     * mid-way. The parse is a cold one, so the answer is the body's own and not the prediction cache's.
     *
     * @param framed the body inside its frame
     * @return whether the parse ran out of text
     */
    static boolean runsOut(final String framed) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(framed));
        lexer.removeErrorListeners();
        final int[] furthest = {0};
        final CommonTokenStream tokens = new CommonTokenStream(lexer) {
            @Override
            public Token LT(final int k) {
                final Token token = super.LT(k);
                if (token != null && token.getTokenIndex() > furthest[0]) {
                    furthest[0] = token.getTokenIndex();
                }
                return token;
            }
        };
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        ColdPrediction.arm(parser);
        final Token offending;
        try {
            parser.booleanExpr();
            return true;
        } catch (final ParseCancellationException bailed) {
            offending = bailed.getCause() instanceof RecognitionException
                ? ((RecognitionException) bailed.getCause()).getOffendingToken() : parser.getCurrentToken();
        }
        final int type = offending.getType();
        if (type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.RPAREN) {
            return true;
        }
        if (!opensWhatFollows(offending)) {
            return false;
        }
        // It ran out when it read on to a closing parenthesis, or to the end, before giving up.
        final Token reached = tokens.get(furthest[0]);
        final List<Token> spoken = spokenTokens(framed);
        return reached.getType() == Token.EOF || reached.getType() == FrostlakeLexer.RPAREN
            || !spoken.isEmpty() && reached.getStartIndex() >= spoken.get(spoken.size() - 1).getStartIndex();
    }

    /** Whether a token opens what must follow it: an operator or punctuation, or one of {@link #OPENING_WORDS}. */
    private static boolean opensWhatFollows(final Token token) {
        for (final int word : OPENING_WORDS) {
            if (token.getType() == word) {
                return true;
            }
        }
        final String text = token.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        final char first = text.charAt(0);
        return !Character.isLetterOrDigit(first) && first != '_' && first != '$' && first != '"' && first != '\''
            && first != ']' && first != '}';
    }

    /**
     * Whether the body reads once one of the words the account takes as a bare select-list alias is read as that
     * alias: {@code SELECT CASE WHEN TRUE THEN 1 END CASE} is created and answers 1 on the account, where
     * {@code SELECT CASE} and {@code SELECT 1 FROM t JOIN} are refused — the word is no alias there.
     *
     * @param queryExecutor the executor whose parser reads the body
     * @param body          the body as written
     * @return whether one such word, standing as a select item's alias, is all that keeps the body from reading
     */
    static boolean readsWithBareAlias(final QueryExecutor queryExecutor, final String body) {
        for (final Token token : spokenTokens(body)) {
            if (!isBareAliasWord(token)) {
                continue;
            }
            final String named = body.substring(0, token.getStartIndex()) + "\""
                + token.getText().toUpperCase(Locale.ROOT) + "\"" + body.substring(token.getStopIndex() + 1);
            ParseTree tree = queryExecutor.queryStatementOf(named);
            if (tree == null) {
                try {
                    tree = AntlrExpressionParser.parseTree(named);
                } catch (final RuntimeException stillUnreadable) {
                    continue;
                }
            }
            if (isSelectAlias(tree, token.getStartIndex())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the select item alias written without AS at {@code start} is found in {@code tree}. */
    private static boolean isSelectAlias(final ParseTree tree, final int start) {
        if (tree instanceof FrostlakeParser.ExprItemContext) {
            final FrostlakeParser.IdentifierContext alias = ((FrostlakeParser.ExprItemContext) tree).identifier();
            if (alias != null && alias.getStart().getStartIndex() == start) {
                return true;
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (isSelectAlias(tree.getChild(i), start)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The refusal of a body holding a query, one line per error, or null when the refusal is the expression's own:
     * no parenthesised query holds the point where an expression body broke.
     *
     * @param framed    the body inside its frame
     * @param tokens    the framed body's spoken tokens
     * @param offending where the framed body's parse broke, or null
     * @param unclosed  how many of its own parentheses the body leaves open
     * @return the refusal's lines, or null
     */
    static String describe(final String framed, final List<Token> tokens, final Token offending,
                           final int unclosed) {
        if (tokens.size() > 1 && isQueryKeyword(tokens.get(1))) {
            return render(queryLines(framed, tokens, 1, tokens.size()));
        }
        // A body that opens with a whole parenthesised query reads it as an expression, which no set operator
        // continues: (SELECT 1) UNION SELECT is refused at the UNION, position 12.
        final int group = opensQuery(tokens, 1) ? matchingClose(tokens, 1) : -1;
        if (group > 0 && group + 1 < tokens.size() && isSetOperator(tokens.get(group + 1))) {
            return render(queryLines(framed, tokens, group + 1, tokens.size()));
        }
        if (offending == null) {
            return null;
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (!opensQuery(tokens, i) || i > 0 && tokens.get(i - 1).getType() == FrostlakeLexer.EXISTS) {
                continue;
            }
            final int close = matchingClose(tokens, i);
            final boolean holds = offending.getStartIndex() > tokens.get(i).getStartIndex()
                && (close < 0 || offending.getStartIndex() <= tokens.get(close).getStartIndex());
            if (!holds) {
                continue;
            }
            final String lines = render(queryLines(framed, tokens, i + 1, close < 0 ? tokens.size() : close));
            final Token last = tokens.get(tokens.size() - 1);
            if (offending.getStartIndex() == last.getStartIndex() && unclosed > 0 && i + 2 == tokens.size() - 1) {
                // A bare keyword left open by the frame: the frame's own parenthesis is still unclosed.
                return lines + "\nsyntax error line " + EndOfInput.line(framed) + " at position "
                    + EndOfInput.position(framed) + " unexpected '<EOF>'.";
            }
            return lines;
        }
        return null;
    }

    /** One syntax-error line per token, in the frame's positions. */
    private static String render(final List<Token> tokens) {
        final StringBuilder lines = new StringBuilder();
        for (final Token token : tokens) {
            lines.append(lines.length() == 0 ? "" : "\n").append("syntax error line ").append(token.getLine())
                .append(" at position ").append(token.getCharPositionInLine()).append(" unexpected '")
                .append(token.getText()).append("'.");
        }
        return lines.toString();
    }

    /**
     * The query at {@code keyword}: its keyword, the first query written in parentheses inside it (before
     * {@code end}), and that query's closing parenthesis when the query stops there before its expression does.
     */
    private static List<Token> queryLines(final String framed, final List<Token> tokens, final int keyword,
                                          final int end) {
        final List<Token> lines = new ArrayList<>();
        lines.add(tokens.get(keyword));
        for (int i = keyword + 1; i + 1 < end; i++) {
            if (opensQuery(tokens, i)) {
                lines.add(tokens.get(i + 1));
                final Token unfinished = unfinishedClose(framed, tokens, i);
                if (unfinished != null) {
                    lines.add(unfinished);
                }
                break;
            }
        }
        return lines;
    }

    /**
     * The closing parenthesis the parenthesised query opened at {@code open} stops at before its expression does,
     * or null when it reads, breaks elsewhere, or is nothing but its keyword.
     */
    private static Token unfinishedClose(final String framed, final List<Token> tokens, final int open) {
        final int close = matchingClose(tokens, open);
        final int stop = close >= 0 ? close : tokens.size() - 1;
        if (stop <= open + 2) {
            return null;
        }
        final Token keyword = tokens.get(open + 1);
        final int textEnd = close >= 0 ? tokens.get(close).getStartIndex() : framed.length() - 1;
        final Token failed = AntlrExpressionParser.offendingToken("(" + framed.substring(keyword.getStartIndex(),
            textEnd) + ")");
        if (failed == null || failed.getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final int at = keyword.getStartIndex() + failed.getStartIndex() - 1;
        for (int i = open + 2; i < tokens.size(); i++) {
            if (tokens.get(i).getStartIndex() == at) {
                final Token before = tokens.get(i - 1);
                return isQueryKeyword(before) || before.getType() == FrostlakeLexer.LPAREN ? null : tokens.get(i);
            }
        }
        return null;
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 when none does. */
    private static int matchingClose(final List<Token> tokens, final int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            if (tokens.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (tokens.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Whether a query opens in parentheses at {@code i}. */
    private static boolean opensQuery(final List<Token> tokens, final int i) {
        return tokens.get(i).getType() == FrostlakeLexer.LPAREN && i + 1 < tokens.size()
            && isQueryKeyword(tokens.get(i + 1));
    }

    private static boolean isSetOperator(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT;
    }

    private static boolean isQueryKeyword(final Token token) {
        return token.getType() == FrostlakeLexer.SELECT || token.getType() == FrostlakeLexer.WITH;
    }

    private static boolean isBareAliasWord(final Token token) {
        for (final int word : BARE_ALIAS_WORDS) {
            if (token.getType() == word) {
                return true;
            }
        }
        return false;
    }

    /** The text's spoken tokens: comments and whitespace dropped, end of input excluded. */
    private static List<Token> spokenTokens(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> spoken = new ArrayList<>();
        for (final Token token : stream.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                spoken.add(token);
            }
        }
        return spoken;
    }
}
