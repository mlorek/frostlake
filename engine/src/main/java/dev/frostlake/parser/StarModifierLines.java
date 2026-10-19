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
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a star's RENAME or REPLACE where its grammar has no place for one: after a star that is a
 * call's argument, and inside the braces of an object star. Live refuses the keyword, then stacks exactly the token
 * after it and reports nothing more of the statement — unless that token closes the star's own call or braces, which
 * reads as if the keyword were not written, so the rest of the statement is judged on; a comma there carries the
 * call's argument list on, and its closing parenthesis or brace is refused instead. When the closing token is followed
 * at once by a fault, that fault is silent, and so is the rest of the statement (all live-verified):
 *
 * <pre>
 *   SELECT HASH(* REPLACE (1 AS id)) FROM fz         'REPLACE' at 14, '(' at 22
 *   SELECT OBJECT_CONSTRUCT(* RENAME id AS k) FROM fz 'RENAME' at 26, 'id' at 33
 *   SELECT HASH(* REPLACE, 1) x y FROM fz            'REPLACE' at 14, ')' at 24
 *   SELECT HASH(* REPLACE) x y FROM fz               'REPLACE' at 14, 'y' at 25
 *   SELECT {* REPLACE x} x y FROM fz                 'REPLACE' at 10, 'x' at 18
 *   SELECT {* REPLACE} FROM fz WHERE x y             'REPLACE' at 10, 'y' at 35
 *   SELECT {* REPLACE, 1} FROM fz                    'REPLACE' at 10, '}' at 20
 *   SELECT {* REPLACE}} FROM fz WHERE x y            'REPLACE' at 10
 * </pre>
 *
 * <p>That reading holds for a select item of a query that is a statement of its own — an INSERT's query and a set
 * operation's operand included — where the star's call carries the item's value, in parentheses or under an operator.
 * In the item of a derived table's or a CTE's query live reads the keyword as the item's alias, the star's call closed
 * before it, and reads on from there: a comma carries the list on, a name after a derived table's item is read as
 * {@link SelectListResync#derivedNameLines} reads it, and a '(' after a CTE's item as
 * {@link SelectListResync#cteBracketLines} does; after the '(' of a derived table's item the lines live stacks depend on
 * what the bracket holds, and none is reported. A star call under another call of a select item, or standing in a
 * WHERE, GROUP BY, HAVING, QUALIFY or ORDER BY, reads on differently: see {@link StarModifierResync}. In a clause of
 * a derived table's query nothing after the keyword is reported, but for a lone name in parentheses, after which the
 * call's own ')' is refused. The rule applies
 * only where the keyword is the statement's first fault: the text before it must parse cleanly once its open brackets
 * are closed.
 */
final class StarModifierLines {

    /** What stands for a CAST's or a TRY_CAST's own parenthesis among the brackets a text leaves open. */
    private static final int CAST_OPENER = -2;

    /** What stands for a CASE left open at its operand, and, below it, at a WHEN's condition and at a result. */
    private static final int CASE_OPERAND = -3;
    private static final int CASE_CONDITION = -4;
    private static final int CASE_RESULT = -5;

    private StarModifierLines() {
    }

    /**
     * The lines live reports for the first RENAME or REPLACE that follows a star argument or an object star's star, or
     * null when the text holds none, has an earlier fault, or holds it where this reading does not apply.
     *
     * @param sql       the text parsed
     * @param spoken    its default-channel tokens, end of input included
     * @param firstOnly whether only the first line is read, so the lines after it may be left out
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final boolean firstOnly) {
        int keyword = -1;
        int star = -1;
        for (int i = 1; i < spoken.size() && keyword < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.REPLACE || type == FrostlakeLexer.RENAME) {
                star = starBefore(spoken, i);
                keyword = star < 0 ? -1 : i;
            } else if (isPlainWord(spoken.get(i)) && starBefore(spoken, i) >= 0) {
                // A word that is no modifier at all stands before any keyword: it is read by its own rule.
                return wordLines(sql, spoken, i, starBefore(spoken, i), firstOnly);
            }
        }
        if (keyword < 0) {
            return null;
        }
        final int opener = argumentOpener(spoken, star);
        if (opener < 0) {
            return null;
        }
        final FrostlakeParser.SqlScriptContext tree = treeBefore(sql, spoken, keyword, true);
        final StarModifierPlace place = tree == null ? null : StarModifierPlace.of(tree, spoken.get(star));
        if (place == null) {
            return tree == null ? null : elsewhereLines(tree, spoken.get(star), firstOnly);
        }
        final Token refused = spoken.get(keyword);
        final Token next = spoken.get(keyword + 1);
        final int closer = matchingCloser(spoken, opener);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        if (next.getType() == Token.EOF || firstOnly) {
            return lines;
        }
        if (closer == keyword + 1) {
            // Read on as if the keyword were not written — but a fault right after the closing token, before any
            // other token is read, is silent, and so is the rest of the statement.
            lines.addAll(StarModifierResync.unlessRightAfter(linesAfterDeleting(sql, refused),
                spoken.get(closer + 1)));
        } else if (place.kind() == StarModifierPlaceKind.CLAUSE) {
            lines.addAll(StarModifierResync.clause(sql, spoken, keyword,
                spoken.get(opener).getType() == FrostlakeLexer.LBRACE ? "}" : ")"));
        } else if (place.kind() == StarModifierPlaceKind.DERIVED_CLAUSE) {
            // A lone name in parentheses is taken, and the call's own ')' refused; nothing else is reported.
            if (next.getType() == FrostlakeLexer.LPAREN && closer == keyword + 4
                    && StarModifierResync.isName(spoken.get(keyword + 2))
                    && spoken.get(keyword + 3).getType() == FrostlakeLexer.RPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(closer)));
            }
        } else if (place.kind() == StarModifierPlaceKind.NESTED) {
            lines.addAll(StarModifierResync.nested(sql, spoken, keyword, closer, place));
        } else if (place.queryOpener() != null) {
            lines.addAll(enclosedItemLines(sql, spoken, keyword, closer,
                spoken.get(opener).getType() == FrostlakeLexer.LBRACE, place));
        } else if (next.getType() == FrostlakeLexer.COMMA) {
            if (closer >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(closer)));
            }
        } else {
            lines.add(SyntaxErrorListener.sentence(next));
        }
        return lines;
    }

    /**
     * The lines for a star's misplaced keyword where no select item or clause of the statement holds it but live's
     * reading is known all the same (live-verified):
     *
     * <pre>
     *   SELECT 1 FROM fz a JOIN fz b ON HASH(* REPLACE (1 AS id)) = 1    'ON' at 29, and nothing else
     *   SELECT (SELECT HASH(* REPLACE x) FROM fz)                        'SELECT' at 8, '(' at 19
     * </pre>
     *
     * A join's condition is refused at its ON whatever follows; a subquery that is a select item is refused at its
     * SELECT, and the star's call at its opening parenthesis.
     */
    private static List<String> elsewhereLines(final FrostlakeParser.SqlScriptContext tree, final Token star,
                                               final boolean firstOnly) {
        final Token on = StarModifierPlace.joinOn(tree, star);
        if (on != null) {
            return new ArrayList<>(Collections.singletonList(SyntaxErrorListener.sentence(on)));
        }
        final Token[] subquery = StarModifierPlace.scalarSubqueryItem(tree, star);
        if (subquery == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(subquery[0]));
        if (!firstOnly) {
            lines.add(SyntaxErrorListener.sentence(subquery[1]));
        }
        return lines;
    }

    /**
     * The lines for a word or a number written after a call's star argument, where only a modifier may stand: the
     * word is refused, and the statement is read on as if it were not written — a fault right after it is silent
     * (live-verified):
     *
     * <pre>
     *   SELECT 1 FROM fz WHERE HASH(* x) = 1             'x' at 30
     *   SELECT 1 FROM fz WHERE HASH(* x y) = 1           'x' at 30
     *   SELECT HASH(* ILIKE 'i%' x y) FROM fz            'x' at 25, 'y' at 27
     *   SELECT 1 FROM fz WHERE HASH(* x) = 1 AND a b     'x' at 30, 'b' at 43
     *   SELECT 1 FROM fz a JOIN fz b ON HASH(* x) = 1    'ON' at 29
     * </pre>
     *
     * The rule holds where the word is the statement's first fault and the star is a call's argument.
     */
    private static List<String> wordLines(final String sql, final List<Token> spoken, final int word, final int star,
                                          final boolean firstOnly) {
        final int opener = argumentOpener(spoken, star);
        if (opener < 0 || spoken.get(opener).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final FrostlakeParser.SqlScriptContext tree = treeBefore(sql, spoken, word);
        if (tree == null) {
            return null;
        }
        final Token on = StarModifierPlace.joinOn(tree, spoken.get(star));
        final List<String> lines = new ArrayList<>();
        if (on != null) {
            lines.add(SyntaxErrorListener.sentence(on));
            return lines;
        }
        final Token refused = spoken.get(word);
        lines.add(SyntaxErrorListener.sentence(refused));
        if (firstOnly || spoken.get(word + 1).getType() == Token.EOF) {
            return lines;
        }
        final StarModifierPlace place = StarModifierPlace.of(tree, spoken.get(star));
        if (place != null && place.kind() == StarModifierPlaceKind.ITEM && place.queryOpener() == null
                && StarModifierResync.isName(spoken.get(word + 1))) {
            // In a statement's own select list a second word is refused after the first, and nothing more is told.
            lines.add(SyntaxErrorListener.sentence(spoken.get(word + 1)));
            return lines;
        }
        lines.addAll(StarModifierResync.unlessRightAfter(linesAfterDeleting(sql, refused), spoken.get(word + 1)));
        return lines;
    }

    /** Whether a token is a name or a number, and none of the words that may follow a star in a call. */
    private static boolean isPlainWord(final Token token) {
        final int type = token.getType();
        if (type == FrostlakeLexer.EXCLUDE || type == FrostlakeLexer.ILIKE || type == FrostlakeLexer.REPLACE
                || type == FrostlakeLexer.RENAME) {
            return false;
        }
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || StarModifierResync.isName(token);
    }

    /**
     * The lines after the keyword that follows a star argument in the item of a derived table's or a CTE's query, which
     * live reads as the item's alias, the star's call closed before it: a comma carries the select list on, a '(' after
     * a CTE's item and a name after a derived table's are read as after any alias there, and any other token is refused
     * alone (live-verified):
     *
     * <pre>
     *   SELECT a FROM (SELECT HASH(* REPLACE, 1) a FROM fz)                   'REPLACE', 'FROM' at 43
     *   WITH c AS (SELECT HASH(* REPLACE, 1) h FROM fz) SELECT * FROM c       'REPLACE', 'h' at 37
     *   SELECT a FROM (SELECT HASH(* REPLACE x) a FROM fz) WHERE x y          'REPLACE', 'x', 'y' at 59
     *   WITH c AS (SELECT HASH(* REPLACE (1 AS id)) h FROM fz) SELECT * FROM c 'REPLACE', '(', '1', ')' at 42
     *   SELECT a FROM (SELECT HASH(* REPLACE (id AS k)) a FROM fz)            'REPLACE', '(' at 37
     * </pre>
     *
     * An object star's braces are not read on after a comma: live refuses their '}' as it does for a statement's item.
     */
    private static List<String> enclosedItemLines(final String sql, final List<Token> spoken, final int keyword,
                                                  final int closer, final boolean braces,
                                                  final StarModifierPlace place) {
        final Token refused = spoken.get(keyword);
        final Token next = spoken.get(keyword + 1);
        final List<String> lines = new ArrayList<>();
        if (next.getType() == FrostlakeLexer.COMMA) {
            if (!braces) {
                lines.addAll(StarModifierResync.linesAfter(
                    StarModifierResync.closedAt(sql, refused, ")", refused.getStopIndex() + 1), refused));
            } else if (closer >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(closer)));
            }
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(next));
        final int opener = StarModifierResync.indexOf(spoken, place.queryOpener());
        if (place.inCte() && next.getType() == FrostlakeLexer.LPAREN) {
            lines.addAll(SelectListResync.cteBracketLines(spoken, keyword + 1));
        } else if (!place.inCte() && !braces && opener >= 0 && StarModifierResync.isName(next)) {
            lines.addAll(SelectListResync.derivedNameLines(sql, spoken, keyword, keyword + 1, opener));
        }
        return lines;
    }

    /**
     * The index of the star a RENAME or REPLACE at {@code keyword} follows — directly, or after the star's own EXCLUDE
     * and ILIKE filters — or -1.
     */
    private static int starBefore(final List<Token> spoken, final int keyword) {
        int at = keyword - 1;
        while (at > 0) {
            final int type = spoken.get(at).getType();
            final int before = spoken.get(at - 1).getType();
            if ((type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING)
                    && before == FrostlakeLexer.ILIKE
                    || StarModifierResync.isName(spoken.get(at)) && before == FrostlakeLexer.EXCLUDE) {
                at -= 2;
            } else if (type == FrostlakeLexer.RPAREN) {
                final int open = openingParen(spoken, at);
                if (open < 1 || spoken.get(open - 1).getType() != FrostlakeLexer.EXCLUDE) {
                    return -1;
                }
                at = open - 2;
            } else {
                break;
            }
        }
        return at >= 0 && spoken.get(at).getType() == FrostlakeLexer.STAR ? at : -1;
    }

    /**
     * The index of the '(' or '{' a star stands directly in as an argument or an object star — after the bracket, a
     * comma, a DISTINCT or a qualifier — or -1 when the star is an operator or stands anywhere else.
     */
    private static int argumentOpener(final List<Token> spoken, final int star) {
        int at = star - 1;
        while (at > 1 && spoken.get(at).getType() == FrostlakeLexer.DOT
                && StarModifierResync.isName(spoken.get(at - 1))) {
            at -= 2;
        }
        if (at >= 1 && spoken.get(at).getType() == FrostlakeLexer.DISTINCT) {
            at--;
        }
        if (at < 0) {
            return -1;
        }
        final int type = spoken.get(at).getType();
        if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE) {
            return at;
        }
        if (type != FrostlakeLexer.COMMA) {
            return -1;
        }
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int before = spoken.get(i).getType();
            if (before == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (before == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the ')' or '}' closing the bracket opened at {@code opener}, or -1 when the text leaves it open. */
    private static int matchingCloser(final List<Token> spoken, final int opener) {
        final int open = spoken.get(opener).getType();
        final int close = open == FrostlakeLexer.LBRACE ? FrostlakeLexer.RBRACE : FrostlakeLexer.RPAREN;
        int depth = 0;
        for (int i = opener; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == open) {
                depth++;
            } else if (type == close && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the '(' matching the ')' at {@code close}, or -1. */
    private static int openingParen(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The tree of the text before the token at {@code at}, every bracket it leaves open closed, when that text parses
     * cleanly and holds no statement run into another; else null. A text that ends in a CTE's body is given a query
     * after the WITH, one that ends in a MERGE's condition a WHEN clause, and a CAST left open its type.
     */
    static FrostlakeParser.SqlScriptContext treeBefore(final String sql, final List<Token> spoken, final int at) {
        return treeBefore(sql, spoken, at, false);
    }

    /**
     * {@link #treeBefore(String, List, int)}, with every CASE the text leaves open finished as well when
     * {@code cases} asks for it: its WHEN given a THEN, and the CASE its END.
     */
    private static FrostlakeParser.SqlScriptContext treeBefore(final String sql, final List<Token> spoken,
                                                               final int at, final boolean cases) {
        String prefix = repairedPrefix(sql, spoken, at, cases);
        FrostlakeParser.SqlScriptContext tree = prefix == null ? null : SyntaxErrorListener.cleanParse(prefix);
        if (tree == null && prefix != null && spoken.get(0).getType() == FrostlakeLexer.WITH) {
            prefix = prefix + " SELECT 1";
            tree = SyntaxErrorListener.cleanParse(prefix);
        }
        if (tree == null && prefix != null && spoken.get(0).getType() == FrostlakeLexer.MERGE) {
            // A MERGE that ends in its condition is given the one clause it cannot do without.
            prefix = prefix + " WHEN MATCHED THEN DELETE";
            tree = SyntaxErrorListener.cleanParse(prefix);
        }
        return tree == null || !separated(tree, prefix) ? null : tree;
    }

    /** The text before the token at {@code keyword}, every bracket it leaves open closed in order; null when empty. */
    private static String repairedPrefix(final String sql, final List<Token> spoken, final int keyword,
                                         final boolean cases) {
        final Deque<Integer> open = new ArrayDeque<>();
        for (int i = 0; i < keyword; i++) {
            final int type = spoken.get(i).getType();
            final boolean inCase = cases && !open.isEmpty() && open.peek().intValue() <= CASE_OPERAND;
            if (type == FrostlakeLexer.LPAREN && i > 0 && (spoken.get(i - 1).getType() == FrostlakeLexer.CAST
                    || spoken.get(i - 1).getType() == FrostlakeLexer.TRY_CAST)) {
                // A cast left open is closed with a type, as it cannot be without one.
                open.push(Integer.valueOf(CAST_OPENER));
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE
                    || type == FrostlakeLexer.LBRACKET) {
                open.push(Integer.valueOf(type));
            } else if ((type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) && !open.isEmpty()) {
                open.pop();
            } else if (cases && type == FrostlakeLexer.CASE) {
                open.push(Integer.valueOf(CASE_OPERAND));
            } else if (inCase && type == FrostlakeLexer.END) {
                open.pop();
            } else if (inCase && (type == FrostlakeLexer.WHEN || type == FrostlakeLexer.THEN
                    || type == FrostlakeLexer.ELSE)) {
                open.pop();
                open.push(Integer.valueOf(type == FrostlakeLexer.WHEN ? CASE_CONDITION : CASE_RESULT));
            }
        }
        final StringBuilder repaired = new StringBuilder(sql.substring(0, spoken.get(keyword).getStartIndex()));
        while (!open.isEmpty()) {
            final int type = open.pop().intValue();
            repaired.append(type == CAST_OPENER ? " AS INT)" : type == CASE_OPERAND ? " WHEN 1 THEN 1 END"
                : type == CASE_CONDITION ? " THEN 1 END" : type == CASE_RESULT ? " END"
                : type == FrostlakeLexer.LPAREN ? ")" : type == FrostlakeLexer.LBRACE ? "}" : "]");
        }
        return repaired.toString().trim().isEmpty() ? null : repaired.toString();
    }

    /** Whether the repaired text before the keyword holds no statement run into another without its semicolon. */
    private static boolean separated(final FrostlakeParser.SqlScriptContext tree, final String prefix) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(prefix));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        try {
            StatementSeparation.requireSeparators(tree, tokens, prefix);
        } catch (final RuntimeException unseparated) {
            return false;
        }
        return true;
    }

    /** The lines a parse of the text reports after the keyword's place once the keyword is not written there. */
    private static List<String> linesAfterDeleting(final String sql, final Token keyword) {
        final char[] text = sql.toCharArray();
        for (int c = keyword.getStartIndex(); c <= keyword.getStopIndex(); c++) {
            text[c] = ' ';
        }
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(keyword));
        final List<String> after = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && at != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                after.add(line);
            }
        }
        return after;
    }
}
