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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The lines live reports after a first fault in a query's select list that stands where an item's alias belongs, or
 * right after an item's alias (all live-verified over {@code fz (id INT, b BOOLEAN)}):
 *
 * <pre>
 *   SELECT 1 AS 1, 2 AS 2                          '1' at 12, then '2' at 20: the list goes on after the next comma
 *   SELECT 1 AS 1 x, 2 AS 2                        '1', then '2' at 22
 *   SELECT 1 AS, 2 AS 2                            ',' at 11, then '2' at 18
 *   SELECT 1 'x' FROM fz WHERE a b                 ''x'' at 9, then 'b' at 29: so does a clause
 *   SELECT 1 'x' FROM fz; SELECT 1 x y             ''x'', then the next statement's 'y'
 *   SELECT 1 AS 'x' (1, 2), 3 AS 3                 ''x'', then ')' at 21: the first comma, in brackets or not
 *   SELECT 1 x y, 2 AS 2; SELECT 1 x y             'y' alone: after an alias nothing more is reported
 *   SELECT 1 id AS k FROM fz                       'AS' alone
 *   SELECT a FROM (SELECT 1 x y FROM fz) WHERE a b                     'y', then 'b' at 45
 *   SELECT a FROM (SELECT 1 x y FROM fz WHERE id = 1) WHERE a b        'y', then ')' at 48
 *   WITH c AS (SELECT 1 x (1 AS id) FROM fz) SELECT * FROM c           '(' at 22, '1' at 23, 'FROM' at 32
 * </pre>
 *
 * <p>Where an alias belongs — after AS, or after an item's value when the token there cannot be a name — live skips to
 * the next comma, FROM, clause word, semicolon or end of input and reads on from there. A name after a value is the
 * alias, a string after a name is read as a typed literal, and a keyword there may be one live reads as an alias
 * (JOIN): those are left to the parse. Right after an alias live reports nothing more of the input — except in two
 * places. In a derived table's query a name there leaves that query: the derived table reads as closed before the
 * name, and the reading resumes at the first comma, join word, clause word, semicolon or end of input after it. In a
 * CTE's query a '(' there opens the statement's main query in brackets: see {@link #cteBracketLines}. Live reads other
 * tokens after an alias in those two places in ways that depend on what follows them, which are left to the parse.
 *
 * <p>The readings are rebuilt on the text: the skipped tokens are blanked, the brackets live takes as closed are
 * written where the first of them stood, every position staying where it was, and the lines after the fault are what
 * a parse of that text reports.
 */
final class SelectListResync {

    private SelectListResync() {
    }

    /**
     * The lines live reports when the parse's first line names a token at or right after a select item's alias place,
     * or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @param firstOnly      whether only the first line is read, so the lines after it may be left out
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse, final boolean firstOnly) {
        if (sql == null || !statementParse || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2 || isEnd(spoken.get(fault).getType())) {
            return null;
        }
        final boolean afterAs = spoken.get(fault - 1).getType() == FrostlakeLexer.AS;
        final int itemEnd = afterAs ? fault - 1 : fault;
        final FrostlakeParser.SqlScriptContext tree = StarModifierLines.treeBefore(sql, spoken, itemEnd);
        if (tree == null) {
            return null;
        }
        final FrostlakeParser.ExprItemContext item = itemEndingAt(tree, spoken.get(itemEnd - 1).getStartIndex());
        final FrostlakeParser.SelectStatementContext query = item == null ? null : StarModifierPlace.queryOf(item);
        if (query == null) {
            return null;
        }
        final Token refused = spoken.get(fault);
        final boolean aliased = !afterAs && (item.AS() != null || item.identifier() != null);
        if (!aliased && (StarModifierResync.isName(refused)
                || !afterAs && (refused.getType() == FrostlakeLexer.RPAREN
                    || refused.getType() == FrostlakeLexer.COMMA))) {
            // A name there would be the alias, a ')' after a value closes no group (see UnmatchedCloseParen), and a ','
            // after a value goes on with the list: a fault raised at it lies past it, where no alias belongs.
            return null;
        }
        if (!aliased && !afterAs && (isWord(refused) || refused.getType() == FrostlakeLexer.STRING_LITERAL
                && StarModifierResync.isName(spoken.get(fault - 1)))) {
            // A keyword there may be an alias live allows (JOIN), and a name followed by a string a typed literal.
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        if (aliased) {
            return afterAlias(sql, spoken, fault, query, lines, firstOnly);
        }
        if (!StarModifierPlace.statementQuery(query)) {
            return null;
        }
        if (firstOnly) {
            return lines;
        }
        int resume = fault;
        while (!isEnd(spoken.get(resume).getType()) && !opensTheRest(spoken.get(resume).getType())) {
            resume++;
        }
        final Token at = spoken.get(resume);
        if (at.getType() != Token.EOF) {
            final int from = spoken.get(itemEnd).getStartIndex();
            lines.addAll(StarModifierResync.linesAfter(StarModifierResync.blanked(sql, from, at.getStartIndex()),
                refused));
        }
        return lines;
    }

    /** The lines, the fault's own first, for a fault right after an alias; null where no reading applies. */
    private static List<String> afterAlias(final String sql, final List<Token> spoken, final int fault,
                                           final FrostlakeParser.SelectStatementContext query,
                                           final List<String> lines, final boolean firstOnly) {
        if (StarModifierPlace.statementQuery(query)) {
            return lines;
        }
        final int opener = StarModifierResync.indexOf(spoken, StarModifierPlace.enclosedQueryOpener(query));
        if (opener < 0) {
            return null;
        }
        final Token refused = spoken.get(fault);
        if (!StarModifierPlace.cteQuery(query) && StarModifierResync.isName(refused)) {
            if (!firstOnly) {
                lines.addAll(derivedNameLines(sql, spoken, fault, fault, opener));
            }
            return lines;
        }
        if (StarModifierPlace.cteQuery(query) && refused.getType() == FrostlakeLexer.LPAREN) {
            if (!firstOnly) {
                lines.addAll(cteBracketLines(spoken, fault));
            }
            return lines;
        }
        return null;
    }

    /**
     * The lines after a name right after an item's alias in a derived table's query: the derived table reads as closed
     * before the name — every bracket opened from the query's '(' on is closed where the token at {@code closeAt}
     * stands — and the reading resumes at the first comma, join word, clause word, semicolon or end of input after the
     * name (live-verified):
     *
     * <pre>
     *   SELECT a FROM (SELECT 1 x y FROM fz) d, fy WHERE a b             'y', then 'b'
     *   SELECT a FROM (SELECT 1 x y FROM fz, fy) WHERE a b                'y', then ')' at 39
     *   SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz) WHERE x y      'REPLACE', 'x', then 'y' at 59
     * </pre>
     *
     * @param sql     the text parsed
     * @param spoken  its default-channel tokens, end of input included
     * @param closeAt the index of the token whose place takes the closing brackets
     * @param name    the index of the refused name
     * @param opener  the index of the '(' that opens the derived table's query
     * @return the lines after the name's own
     */
    static List<String> derivedNameLines(final String sql, final List<Token> spoken, final int closeAt, final int name,
                                         final int opener) {
        final String closers = StarModifierResync.closersFrom(spoken, opener, closeAt);
        if (closers == null) {
            return Collections.emptyList();
        }
        int resume = name + 1;
        while (!isEnd(spoken.get(resume).getType()) && !continuesTheFromList(spoken.get(resume).getType())) {
            resume++;
        }
        final String text = StarModifierResync.closedAt(sql, spoken.get(closeAt), closers,
            spoken.get(resume).getStartIndex());
        return StarModifierResync.linesAfter(text, spoken.get(name));
    }

    /**
     * The lines after a '(' right after an item's alias in a CTE's query, which live reads as the statement's main query
     * in brackets, the CTE's query closed before it: the first token inside that cannot open a query is refused, and so
     * is the token after the bracket's ')'; nothing more is reported (live-verified):
     *
     * <pre>
     *   WITH c AS (SELECT HASH(* REPLACE (1 AS id)) h FROM fz) SELECT * FROM c     '(', '1', then ')' at 42
     *   WITH c AS (SELECT HASH(* REPLACE ((1))) h FROM fz) SELECT * FROM c         '(', '1' at 35, then ')' at 38
     *   WITH c AS (SELECT HASH(* REPLACE (1 AS id), 2) h FROM fz) SELECT * FROM c  '(', '1', then ',' at 42
     *   WITH c AS (SELECT HASH(* REPLACE ()) h FROM fz) SELECT * FROM c            '(' alone
     * </pre>
     *
     * A bracket that holds a query, or that the input leaves open, is not read on.
     *
     * @param spoken the default-channel tokens, end of input included
     * @param open   the index of the refused '('
     * @return the lines after the '(' already refused
     */
    static List<String> cteBracketLines(final List<Token> spoken, final int open) {
        int inner = open + 1;
        while (spoken.get(inner).getType() == FrostlakeLexer.LPAREN) {
            inner++;
        }
        final int type = spoken.get(inner).getType();
        final int close = closingParen(spoken, open);
        if (close < 0 || isEnd(type) || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.SELECT
                || type == FrostlakeLexer.WITH) {
            return Collections.emptyList();
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(inner)));
        if (!isEnd(spoken.get(close + 1).getType())) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
        }
        return lines;
    }

    /** The index of the ')' matching the '(' at {@code open}, or -1. */
    private static int closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The select item of the tree whose last token starts at character {@code start}, or null. */
    private static FrostlakeParser.ExprItemContext itemEndingAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.ExprItemContext) {
                final Token stop = ((FrostlakeParser.ExprItemContext) node).getStop();
                if (stop != null && stop.getStartIndex() == start) {
                    return (FrostlakeParser.ExprItemContext) node;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return null;
    }

    /** Whether a token is one the list's recovery reads on from: a comma, FROM or a clause word. */
    private static boolean opensTheRest(final int type) {
        return type == FrostlakeLexer.COMMA || type == FrostlakeLexer.FROM || isClauseWord(type);
    }

    /** Whether a token may follow a derived table that already has its alias: a comma, a join word or a clause word. */
    private static boolean continuesTheFromList(final int type) {
        return type == FrostlakeLexer.COMMA || type == FrostlakeLexer.JOIN || type == FrostlakeLexer.INNER
            || type == FrostlakeLexer.LEFT || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL
            || type == FrostlakeLexer.CROSS || type == FrostlakeLexer.NATURAL || isClauseWord(type);
    }

    private static boolean isClauseWord(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT;
    }

    private static boolean isEnd(final int type) {
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    /** Whether a token is a word: a keyword, as no name reaches here. */
    private static boolean isWord(final Token token) {
        final String text = token.getText();
        return !text.isEmpty() && Character.isLetter(text.charAt(0));
    }
}
