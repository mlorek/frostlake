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
import java.util.Deque;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
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
 * WHERE, GROUP BY, HAVING, QUALIFY or ORDER BY, reads on differently: see {@link StarModifierResync}. The rule applies
 * only where the keyword is the statement's first fault: the text before it must parse cleanly once its open brackets
 * are closed.
 */
final class StarModifierLines {

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
        if (sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int keyword = -1;
        int star = -1;
        for (int i = 1; i < spoken.size() && keyword < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.REPLACE || type == FrostlakeLexer.RENAME) {
                star = starBefore(spoken, i);
                keyword = star < 0 ? -1 : i;
            }
        }
        if (keyword < 0) {
            return null;
        }
        final int opener = argumentOpener(spoken, star);
        if (opener < 0) {
            return null;
        }
        final FrostlakeParser.SqlScriptContext tree = treeBefore(sql, spoken, keyword);
        final StarModifierPlace place = tree == null ? null : StarModifierPlace.of(tree, spoken.get(star));
        if (place == null) {
            return null;
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
            if (type == FrostlakeLexer.STRING_LITERAL && before == FrostlakeLexer.ILIKE
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
     * after the WITH.
     */
    static FrostlakeParser.SqlScriptContext treeBefore(final String sql, final List<Token> spoken, final int at) {
        String prefix = repairedPrefix(sql, spoken, at);
        FrostlakeParser.SqlScriptContext tree = prefix == null ? null : SyntaxErrorListener.cleanParse(prefix);
        if (tree == null && prefix != null && spoken.get(0).getType() == FrostlakeLexer.WITH) {
            prefix = prefix + " SELECT 1";
            tree = SyntaxErrorListener.cleanParse(prefix);
        }
        return tree == null || !separated(tree, prefix) ? null : tree;
    }

    /** The text before the token at {@code keyword}, every bracket it leaves open closed in order; null when empty. */
    private static String repairedPrefix(final String sql, final List<Token> spoken, final int keyword) {
        final Deque<Integer> open = new ArrayDeque<>();
        for (int i = 0; i < keyword; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE || type == FrostlakeLexer.LBRACKET) {
                open.push(Integer.valueOf(type));
            } else if ((type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE
                    || type == FrostlakeLexer.RBRACKET) && !open.isEmpty()) {
                open.pop();
            }
        }
        final StringBuilder repaired = new StringBuilder(sql.substring(0, spoken.get(keyword).getStartIndex()));
        while (!open.isEmpty()) {
            final int type = open.pop().intValue();
            repaired.append(type == FrostlakeLexer.LPAREN ? ')' : type == FrostlakeLexer.LBRACE ? '}' : ']');
        }
        return repaired.toString().trim().isEmpty() ? null : repaired.toString();
    }

    /** Whether the repaired text before the keyword holds no statement run into another without its semicolon. */
    private static boolean separated(final FrostlakeParser.SqlScriptContext tree, final String prefix) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(prefix));
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
