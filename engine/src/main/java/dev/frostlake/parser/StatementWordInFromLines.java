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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a statement that is no query written in the parentheses a FROM clause reads a
 * query in — after FROM or a JOIN, in one pair or more. The statement's first word is a name there, and the parse
 * stops at what follows it; the account names the word itself, and nothing after it (live-verified):
 *
 * <pre>
 *   SELECT * FROM (DESCRIBE TABLE t)          'DESCRIBE'
 *   SELECT * FROM (DESC TABLE t) d JOIN …     'DESC'
 *   SELECT * FROM (SHOW TABLES IN SCHEMA s)   'SHOW'
 *   SELECT * FROM (EXPLAIN SELECT 1)          'EXPLAIN'
 *   SELECT * FROM (CALL p())                  'CALL'
 *   SELECT * FROM (USE SCHEMA s)              'USE'
 *   SELECT * FROM (VALUES)                    'VALUES'
 *   SELECT * FROM (MERGE INTO t USING …)      'MERGE', as TRUNCATE TABLE t is 'TRUNCATE'
 *   SELECT * FROM ((DESCRIBE TABLE t))        'DESCRIBE'
 *   SELECT * FROM t JOIN (DESCRIBE TABLE t) d ON TRUE   'DESCRIBE'
 * </pre>
 *
 * A word that stands alone in the parentheses, or with one name after it, is the table it names:
 * {@code FROM (DESCRIBE)} and {@code FROM (describe t)} read a table called DESCRIBE. The FROM of an EXISTS's query
 * is followed by the EXISTS's ')': {@code SELECT 1 WHERE EXISTS (SELECT 1 FROM (DESCRIBE TABLE t))} is 'DESCRIBE',
 * then that ')' (live-verified).
 *
 * <p>Three words are followed by one more line (all live-verified):
 *
 * <pre>
 *   SELECT * FROM (EXPLAIN USING TEXT SELECT 1)          'EXPLAIN', then 'USING'
 *   SELECT * FROM (UPDATE t SET a = 1)                   'UPDATE', then 'SET'; with an alias after the name,
 *                                                        UPDATE t r SET a = 1, 'UPDATE' alone
 *   SELECT * FROM (INSERT INTO t VALUES (1, 'a'))        'INSERT', then the ')' after the value that follows the
 *                                                        first comma; with no comma, INSERT INTO t VALUES (1),
 *                                                        'INSERT' alone
 * </pre>
 */
final class StatementWordInFromLines {

    private static final Set<Integer> OPENERS = new HashSet<Integer>(Arrays.asList(
        Integer.valueOf(FrostlakeLexer.DESCRIBE), Integer.valueOf(FrostlakeLexer.DESC),
        Integer.valueOf(FrostlakeLexer.SHOW), Integer.valueOf(FrostlakeLexer.EXPLAIN),
        Integer.valueOf(FrostlakeLexer.CALL), Integer.valueOf(FrostlakeLexer.USE),
        Integer.valueOf(FrostlakeLexer.VALUES), Integer.valueOf(FrostlakeLexer.MERGE),
        Integer.valueOf(FrostlakeLexer.TRUNCATE), Integer.valueOf(FrostlakeLexer.INSERT),
        Integer.valueOf(FrostlakeLexer.UPDATE)));

    private StatementWordInFromLines() {
    }

    /**
     * The lines for a text whose first fault stands in the parentheses that follow FROM, a JOIN or a FROM list's
     * comma, behind a statement's first word or on an UPDATE, or that is that comma itself; null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault >= spoken.size()) {
            return null;
        }
        final boolean atComma = spoken.get(fault).getType() == FrostlakeLexer.COMMA && fault + 2 < spoken.size()
            && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN && opensStatement(spoken.get(fault + 2));
        final boolean atOpen = spoken.get(fault).getType() == FrostlakeLexer.LPAREN && fault + 1 < spoken.size()
            && opensStatement(spoken.get(fault + 1));
        final int open = atComma ? fault + 1 : atOpen ? fault : enclosingOpener(spoken, fault);
        if (open < 1) {
            return null;
        }
        final int word = spoken.get(open + 1).getType();
        int before = open - 1;
        while (before > 0 && spoken.get(before).getType() == FrostlakeLexer.LPAREN) {
            before--;
        }
        final int reads = spoken.get(before).getType();
        final boolean listed = reads == FrostlakeLexer.COMMA && before == open - 1 && listComma(spoken, before);
        if (reads != FrostlakeLexer.FROM && (reads != FrostlakeLexer.JOIN || before != open - 1) && !listed) {
            return null;
        }
        final int exists = existsOpen(spoken, before);
        if (open + 1 == fault && word != FrostlakeLexer.UPDATE && !listed && exists < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
        if (exists >= 0) {
            lines.addAll(existsLines(spoken, open, exists));
            return lines;
        }
        final Token later = word == FrostlakeLexer.EXPLAIN ? usingAfter(spoken, open + 2)
            : word == FrostlakeLexer.UPDATE ? setAfterName(spoken, open + 2)
            : listed && (word == FrostlakeLexer.CALL || word == FrostlakeLexer.INSERT) ? listedCommaTail(spoken, open)
            : word == FrostlakeLexer.INSERT ? closeAfterComma(spoken, open + 2) : null;
        if (later != null) {
            lines.add(SyntaxErrorListener.sentence(later));
        }
        return lines;
    }

    /**
     * The lines after the statement word of an EXISTS's query (all live-verified). In a select list, the word alone,
     * or a CALL's name and the '(' after it. In a condition, an UPDATE's SET after its name and an EXPLAIN's USING;
     * for any other word, after a CALL's or an INSERT's first comma the number it holds, and then the token after
     * the first ')' past that comma or past the word:
     *
     * <pre>
     *   … WHERE EXISTS (SELECT 1 FROM t, (DESCRIBE TABLE t))                'DESCRIBE', then the EXISTS's ')'
     *   … WHERE EXISTS (SELECT 1 FROM t, (DESCRIBE TABLE t) x)              'DESCRIBE', then 'x'
     *   … WHERE EXISTS (SELECT 1 FROM t, (CALL p(1, 2)))                    'CALL', '2', then the EXISTS's ')'
     *   … WHERE EXISTS (SELECT 1 FROM t, (INSERT INTO t VALUES (1, 'a')))   'INSERT', then the item's ')'
     *   SELECT EXISTS (SELECT 1 FROM t, (DESCRIBE TABLE t))                 'DESCRIBE' alone
     * </pre>
     */
    private static List<String> existsLines(final List<Token> spoken, final int open, final int exists) {
        final List<String> lines = new ArrayList<>();
        final int word = spoken.get(open + 1).getType();
        if (inSelectList(spoken, exists - 1)) {
            final Token set = word == FrostlakeLexer.UPDATE ? setAfterName(spoken, open + 2) : null;
            if (word == FrostlakeLexer.CALL && open + 3 < spoken.size()
                    && SyntaxErrorListener.isNameLike(spoken.get(open + 2))
                    && spoken.get(open + 3).getType() == FrostlakeLexer.LPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(open + 3)));
            } else if (set != null) {
                // An UPDATE reads its name and SET, the column after it, and is refused at the '=' then.
                lines.add(SyntaxErrorListener.sentence(set));
                final int column = spoken.indexOf(set) + 1;
                if (column + 1 < spoken.size()
                        && SyntaxErrorListener.isNameLike(spoken.get(column))
                        && spoken.get(column + 1).getType() == FrostlakeLexer.EQ) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(column + 1)));
                }
            }
            return lines;
        }
        final Token own = word == FrostlakeLexer.UPDATE ? setAfterName(spoken, open + 2)
            : word == FrostlakeLexer.EXPLAIN ? usingAfter(spoken, open + 2) : null;
        if (own != null) {
            lines.add(SyntaxErrorListener.sentence(own));
            return lines;
        }
        int from = open + 1;
        if (word == FrostlakeLexer.CALL || word == FrostlakeLexer.INSERT) {
            final int comma = firstCommaInItem(spoken, open);
            if (comma > 0) {
                from = comma + 1;
                if (isNumber(spoken.get(from))) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(from)));
                }
            }
        }
        final Token after = afterFirstClose(spoken, from);
        if (after != null) {
            lines.add(SyntaxErrorListener.sentence(after));
        }
        return lines;
    }

    /**
     * The '(' around the fault whose next token opens a statement, past a CALL's own argument list that holds the
     * fault; -1 when the innermost bracket around the fault is any other, or there is none.
     */
    private static int enclosingOpener(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (opensStatement(spoken.get(i + 1))) {
                    return i;
                }
                if (!calledArguments(spoken, i)) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** Whether the '(' at {@code open} opens the argument list of the procedure a CALL names. */
    private static boolean calledArguments(final List<Token> spoken, final int open) {
        int at = open - 1;
        if (at < 1 || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return false;
        }
        while (at > 2 && spoken.get(at - 1).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(at - 2))) {
            at -= 2;
        }
        return spoken.get(at - 1).getType() == FrostlakeLexer.CALL;
    }

    /** Whether the comma at {@code comma} separates the items of a FROM list. */
    private static boolean listComma(final List<Token> spoken, final int comma) {
        int depth = 0;
        for (int i = comma - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return true;
            } else if (depth == 0 && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.ON || type == FrostlakeLexer.USING || type == FrostlakeLexer.SEMI)) {
                return false;
            }
        }
        return false;
    }

    /** The '(' of the EXISTS whose query holds the FROM list item at {@code from}, or -1. */
    private static int existsOpen(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                return spoken.get(i + 1).getType() == FrostlakeLexer.SELECT
                    && spoken.get(i - 1).getType() == FrostlakeLexer.EXISTS ? i : -1;
            }
        }
        return -1;
    }

    /** Whether the EXISTS at {@code at} stands in a select list rather than in a condition. */
    private static boolean inSelectList(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth = depth > 0 ? depth - 1 : 0;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return true;
            } else if (depth == 0 && (type == FrostlakeLexer.WHERE || type == FrostlakeLexer.HAVING
                    || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ON || type == FrostlakeLexer.SEMI)) {
                return false;
            }
        }
        return false;
    }

    /** The first comma inside the item that opens at {@code open}, or -1. */
    private static int firstCommaInItem(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return -1;
                }
            } else if (type == FrostlakeLexer.COMMA) {
                return i;
            } else if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * The token after the first ')' past {@code from}, or null at the statement's end.
     *
     * @param spoken the default-channel tokens
     * @param from   the index to look past
     * @return the token, or null
     */
    static Token afterFirstClose(final List<Token> spoken, final int from) {
        for (int i = from + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.RPAREN) {
                final Token after = spoken.get(i + 1);
                return after.getType() == Token.EOF || after.getType() == FrostlakeLexer.SEMI ? null : after;
            }
        }
        return null;
    }

    /**
     * After a FROM list's comma, the token a CALL's or an INSERT's item is refused at past its first comma: a number
     * there, or the ')' after a text or a name (live-verified: CALL p(1, 2) and VALUES (1, 2) are '2', CALL p(1, 'a')
     * and VALUES (1, 'a') the ')'); null when the item holds no comma.
     */
    private static Token listedCommaTail(final List<Token> spoken, final int open) {
        final int comma = firstCommaInItem(spoken, open);
        if (comma < 0 || comma + 2 >= spoken.size()) {
            return null;
        }
        final Token value = spoken.get(comma + 1);
        if (isNumber(value)) {
            return value;
        }
        final boolean read = value.getType() == FrostlakeLexer.STRING_LITERAL || SyntaxErrorListener.isNameLike(value);
        return read && spoken.get(comma + 2).getType() == FrostlakeLexer.RPAREN ? spoken.get(comma + 2) : null;
    }

    /** Whether a token is a number. */
    private static boolean isNumber(final Token token) {
        return token.getType() == FrostlakeLexer.INTEGER_LITERAL || token.getType() == FrostlakeLexer.FLOAT_LITERAL;
    }

    /** Whether a token is the first word of a statement this reads in a FROM clause's parentheses. */
    static boolean opensStatement(final Token token) {
        return OPENERS.contains(Integer.valueOf(token.getType()));
    }

    /**
     * The token a query used as a call's argument is refused at after the statement word at {@code word} its FROM
     * reads (see ValueSubqueryLines): the SET after an UPDATE's name, the token after INSERT INTO and its name, the
     * token after any other word; null where the word stands alone in its parentheses.
     *
     * @param spoken the default-channel tokens
     * @param word   the word's index
     * @return the token, or null
     */
    static Token afterWord(final List<Token> spoken, final int word) {
        final int type = spoken.get(word).getType();
        if (type == FrostlakeLexer.UPDATE) {
            return setAfterName(spoken, word + 1);
        }
        int at = word + 1;
        if (type == FrostlakeLexer.INSERT && at + 2 < spoken.size() && spoken.get(at).getType() == FrostlakeLexer.INTO
                && SyntaxErrorListener.isNameLike(spoken.get(at + 1))) {
            at += 2;
        }
        return at < spoken.size() && spoken.get(at).getType() != FrostlakeLexer.RPAREN
            && spoken.get(at).getType() != Token.EOF ? spoken.get(at) : null;
    }

    /** The USING right after EXPLAIN, or null. */
    private static Token usingAfter(final List<Token> spoken, final int at) {
        return at < spoken.size() && spoken.get(at).getType() == FrostlakeLexer.USING ? spoken.get(at) : null;
    }

    /** The SET right after the name UPDATE is followed by, or null. */
    private static Token setAfterName(final List<Token> spoken, final int at) {
        return at + 1 < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(at))
            && spoken.get(at + 1).getType() == FrostlakeLexer.SET ? spoken.get(at + 1) : null;
    }

    /** The ')' after the string or the name that follows the statement's first comma, or null. */
    private static Token closeAfterComma(final List<Token> spoken, final int from) {
        for (int i = from; i + 2 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.COMMA) {
                final Token value = spoken.get(i + 1);
                final boolean read = value.getType() == FrostlakeLexer.STRING_LITERAL
                    || SyntaxErrorListener.isNameLike(value);
                return read && spoken.get(i + 2).getType() == FrostlakeLexer.RPAREN ? spoken.get(i + 2) : null;
            }
        }
        return null;
    }
}
