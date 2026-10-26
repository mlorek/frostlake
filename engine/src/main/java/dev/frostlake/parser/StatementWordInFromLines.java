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
     * The lines for a text whose first fault stands in the parentheses that follow FROM, behind a statement's
     * first word or on an UPDATE, or null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault >= spoken.size()) {
            return null;
        }
        int depth = 0;
        int open = -1;
        for (int i = fault - 1; i >= 0 && open < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open = i;
                } else {
                    depth--;
                }
            }
        }
        if (open < 1 || open + 1 > fault || !OPENERS.contains(Integer.valueOf(spoken.get(open + 1).getType()))) {
            return null;
        }
        final int word = spoken.get(open + 1).getType();
        if (open + 1 == fault && word != FrostlakeLexer.UPDATE) {
            return null;
        }
        int before = open - 1;
        while (before > 0 && spoken.get(before).getType() == FrostlakeLexer.LPAREN) {
            before--;
        }
        final int reads = spoken.get(before).getType();
        if (reads != FrostlakeLexer.FROM && (reads != FrostlakeLexer.JOIN || before != open - 1)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
        final Token later = word == FrostlakeLexer.EXPLAIN ? usingAfter(spoken, open + 2)
            : word == FrostlakeLexer.UPDATE ? setAfterName(spoken, open + 2)
            : word == FrostlakeLexer.INSERT ? closeAfterComma(spoken, open + 2) : null;
        if (later != null) {
            lines.add(SyntaxErrorListener.sentence(later));
        }
        final Token exists = existsClose(spoken, before);
        if (exists != null) {
            lines.add(SyntaxErrorListener.sentence(exists));
        }
        return lines;
    }

    /** The ')' of the EXISTS whose query holds the FROM at {@code from}, or null. */
    private static Token existsClose(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (spoken.get(i + 1).getType() != FrostlakeLexer.SELECT
                        || spoken.get(i - 1).getType() != FrostlakeLexer.EXISTS) {
                    return null;
                }
                int inner = 0;
                for (int j = i; j < spoken.size(); j++) {
                    final int at = spoken.get(j).getType();
                    if (at == FrostlakeLexer.LPAREN) {
                        inner++;
                    } else if (at == FrostlakeLexer.RPAREN && --inner == 0) {
                        return spoken.get(j);
                    }
                }
                return null;
            }
        }
        return null;
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
