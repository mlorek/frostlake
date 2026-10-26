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
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * How live reads on past the first fault of a MATCH_RECOGNIZE clause that stands where one of its sub-clauses is due,
 * before any DEFINE — the lines after the fault's own (all live-verified):
 *
 * <p>★ A WORD THAT CAN BE A NAME — a misplaced MEASURES, PARTITION, ONE, AFTER or PATTERN among them — is read as a
 * definition's symbol. The token after it is reported unless it is AS; when the token after that is the AS, it is
 * dropped; when it can begin a value, the value is read from it; a ROW wants its bracket, so the token after it is
 * reported and read as its argument, then the token after that. Past the value, the first token that cannot go on
 * with it is reported, and nothing after: a comma reads another definition, whose symbol is not reported.
 *
 * <pre>
 *   … ONE ROW PER MATCH MEASURES MATCH_NUMBER() AS mn PATTERN …   'MEASURES', 'MATCH_NUMBER', 'AS'
 *   … ONE ROW PER MATCH MEASURES x AS y PATTERN …                 'MEASURES', 'x', 'PATTERN'
 *   … ORDER BY id PARTITION BY v MEASURES …                        'PARTITION', 'BY'
 *   … PATTERN (a) PATTERN (a) DEFINE …                             'PATTERN', '(', 'DEFINE'
 *   … AFTER MATCH SKIP TO NEXT ROW ONE ROW PER MATCH PATTERN …     'ONE', 'ROW', 'PER', 'MATCH'
 *   … ONE ROW PER MATCH SHOW EMPTY MATCHES PATTERN …               'SHOW', 'EMPTY', 'MATCHES'
 * </pre>
 *
 * <p>A word followed by the clause's PATTERN is reported alone. A word standing inside a sub-clause's own words
 * ({@code ALL zz yy}) is reported, and the word after it is read as a definition's symbol. A token that cannot go on
 * with a value and that the query can go on with ({@code ORDER}) is followed by the query's own reading from it.
 *
 * <p>★ A RESERVED WORD — {@code ALL ROWS PER MATCH} after AFTER MATCH SKIP, {@code WITH}, {@code ROW}, {@code FROM},
 * or an ORDER past the PATTERN — is reported, and the first word after it that can be a name is read as a definition's
 * symbol, reported no more than the reading above reports it: {@code … ALL ROWS PER MATCH PATTERN …} is 'ALL',
 * 'MATCH', 'PATTERN'.
 *
 * <p>★ AN AS, A BRACKET, OR AN ORDER BEFORE THE PATTERN is reported, and the query reads on at the first comma or
 * clause word after it that a relation can be followed by — {@code PARTITION BY id % 2 AS odd ORDER BY id MEASURES …}
 * is 'AS', then the query's ORDER BY and 'MEASURES' — and nothing more when there is none.
 *
 * <p>★ A MEASURE WITHOUT ITS ALIAS before a comma is refused at the comma; the measure after it takes the word after
 * its value as its alias, a reserved word there is reported, and what follows reads on as above: {@code MATCH_NUMBER(),
 * COUNT(*) ONE ROW PER MATCH PATTERN …} is the comma, 'ROW', 'MATCH', 'PATTERN'.
 *
 * <p>★ A PATTERN WITHOUT ITS BRACKET is refused at the token after it; the pattern reads on from there, DEFINE one of
 * its symbols, and the first token it cannot take is reported — an AS, after which the query reads on as above.
 */
final class MatchRecognizeReadOn {

    private static final IntervalSet NAMES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);
    private static final IntervalSet VALUES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_expression]);
    private static final int MEASURES_RANK = 2;
    private static final int DEFINE_RANK = 6;

    private final String sql;
    private final List<Token> spoken;
    private final int opener;
    private final int close;
    private final List<String> lines = new ArrayList<>();

    private MatchRecognizeReadOn(final String sql, final List<Token> spoken, final int opener, final int close) {
        this.sql = sql;
        this.spoken = spoken;
        this.opener = opener;
        this.close = close;
    }

    /**
     * The lines for a fault at the clause's own depth, before any DEFINE, or null when live's reading of it is not one
     * of the above.
     *
     * @param sql    the text
     * @param spoken the text's default-channel tokens, end of input included
     * @param opener the index of the MATCH_RECOGNIZE word
     * @param close  the index of the clause's closing parenthesis
     * @param fault  the index of the fault, which stands after the clause's opening parenthesis
     * @param ranks  the rank of each sub-clause opened before the fault, in order
     * @return the lines, the fault's own first, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int opener, final int close,
                              final int fault, final List<Integer> ranks) {
        if (sql == null || ranks.isEmpty()) {
            return null;
        }
        boolean pattern = false;
        int latest = -1;
        for (final Integer rank : ranks) {
            if (rank.intValue() == DEFINE_RANK) {
                return null;
            }
            pattern = pattern || rank.intValue() == DEFINE_RANK - 1;
            latest = rank.intValue();
        }
        final MatchRecognizeReadOn reading = new MatchRecognizeReadOn(sql, spoken, opener, close);
        final Token token = spoken.get(fault);
        if ("DEFINE".equalsIgnoreCase(token.getText())) {
            return null;
        }
        if (token.getType() == FrostlakeLexer.PATTERN && !pattern) {
            // The clause's own PATTERN, refused at the token after it when no bracket follows.
            if (spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN) {
                return null;
            }
            reading.add(fault + 1);
            reading.unbracketedPattern(fault + 1);
            return reading.lines;
        }
        reading.add(fault);
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.PATTERN && latest == DEFINE_RANK - 1) {
            reading.unbracketedPattern(fault);
        } else if (token.getType() == FrostlakeLexer.COMMA && latest == MEASURES_RANK) {
            reading.measureWithoutAlias(fault);
        } else if (!reading.after(fault, pattern)) {
            return null;
        }
        return reading.lines;
    }

    /** Read on past a reported fault; false when live's reading of it is not modelled. */
    private boolean after(final int fault, final boolean pattern) {
        final Token token = spoken.get(fault);
        final int type = token.getType();
        if (type == FrostlakeLexer.AS || type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.ORDER && !pattern) {
            readOnAfter(fault);
            return true;
        }
        if (reserved(token)) {
            restart(fault);
            return true;
        }
        if (!NAMES.contains(type)) {
            return false;
        }
        final Token next = spoken.get(fault + 1);
        if (!pattern && next.getType() == FrostlakeLexer.PATTERN
                && spoken.get(fault + 2).getType() == FrostlakeLexer.LPAREN) {
            return true;
        }
        if (insidePhrase(fault)) {
            if (NAMES.contains(next.getType()) && !"DEFINE".equalsIgnoreCase(next.getText())) {
                definition(fault + 1);
            }
            return true;
        }
        definition(fault);
        return true;
    }

    /** The word at {@code symbol} is read as a definition's symbol, which is not reported here. */
    private void definition(final int symbol) {
        final int next = symbol + 1;
        final Token token = spoken.get(next);
        if (token.getType() == Token.EOF) {
            return;
        }
        final int value;
        if (token.getType() == FrostlakeLexer.AS) {
            value = next + 1;
        } else if (spoken.get(next + 1).getType() == FrostlakeLexer.AS) {
            add(next);
            value = next + 2;
        } else if (token.getType() == FrostlakeLexer.ROW) {
            add(next);
            final int argument = next + 1;
            final int opening = spoken.get(argument).getType();
            if (opening != FrostlakeLexer.LPAREN && opening != Token.EOF) {
                add(argument);
                final int type = spoken.get(argument + 1).getType();
                if (type != FrostlakeLexer.COMMA && type != FrostlakeLexer.RPAREN && type != Token.EOF) {
                    add(argument + 1);
                }
            }
            return;
        } else if (beginsValue(token)) {
            add(next);
            value = next;
        } else {
            add(next);
            if (continuesQuery(token)) {
                readOn(next);
            }
            return;
        }
        final int end = valueEnd(value);
        if (end < 0) {
            add(value);
            return;
        }
        if (spoken.get(end).getType() == FrostlakeLexer.COMMA) {
            definition(end + 1);
            return;
        }
        if (end != close && spoken.get(end).getType() != Token.EOF) {
            add(end);
        }
    }

    /** Past a reserved word: the first word after it that can be a name is read as a definition's symbol. */
    private void restart(final int fault) {
        int at = fault + 1;
        while (reserved(spoken.get(at))) {
            at++;
        }
        final Token word = spoken.get(at);
        if (NAMES.contains(word.getType()) && !"DEFINE".equalsIgnoreCase(word.getText())) {
            definition(at);
        }
    }

    /** A measure without its alias: the measures after the comma, their aliases taken without an AS. */
    private void measureWithoutAlias(final int comma) {
        int item = comma + 1;
        int end;
        while (true) {
            end = valueEnd(item);
            if (end < 0) {
                return;
            }
            final Token after = spoken.get(end);
            if (after.getType() == FrostlakeLexer.AS) {
                end += 2;
            } else if (NAMES.contains(after.getType())) {
                end++;
            } else if (reserved(after)) {
                add(end);
                return;
            }
            if (spoken.get(end).getType() != FrostlakeLexer.COMMA) {
                break;
            }
            item = end + 1;
        }
        final int rank = MatchRecognizeClauseLines.rankAt(spoken, end);
        if (rank > MEASURES_RANK && rank < DEFINE_RANK || spoken.get(end).getType() == Token.EOF) {
            return;
        }
        add(end);
        if (rank < 0) {
            after(end, false);
        }
    }

    /** A PATTERN written without its bracket: the pattern read from the fault, DEFINE among its symbols. */
    private void unbracketedPattern(final int fault) {
        int at = fault;
        while (true) {
            final int type = spoken.get(at).getType();
            if (type == FrostlakeLexer.LPAREN) {
                at = closing(at) + 1;
            } else if (NAMES.contains(type) || type == FrostlakeLexer.CARET || type == FrostlakeParser.DOLLAR
                    || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.STAR || type == FrostlakeLexer.QUESTION
                    || type == FrostlakeParser.BAR) {
                at++;
            } else {
                break;
            }
        }
        if (spoken.get(at).getType() == Token.EOF) {
            return;
        }
        add(at);
        if (spoken.get(at).getType() == FrostlakeLexer.AS) {
            readOnAfter(at);
        }
    }

    /** The query reads on at the first comma or clause word after {@code fault} a relation can be followed by. */
    private void readOnAfter(final int fault) {
        for (int i = fault + 1; i < spoken.size(); i++) {
            if (continuesQuery(spoken.get(i))) {
                readOn(i);
                return;
            }
        }
    }

    /** The query's own first fault, read with the clause up to {@code from} left out. */
    private void readOn(final int from) {
        final StringBuilder text = new StringBuilder(sql);
        final int end = Math.min(spoken.get(from).getStartIndex(), text.length());
        for (int i = spoken.get(opener).getStartIndex(); i < end; i++) {
            if (text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                text.setCharAt(i, ' ');
            }
        }
        final String line = StatementSeparation.firstFaultOf(text.toString(), -1);
        final int[] at = line == null ? null : SyntaxErrorListener.sentenceCoordinates(line);
        final Token resumed = spoken.get(from);
        if (at != null && (at[0] > resumed.getLine()
                || at[0] == resumed.getLine() && at[1] >= resumed.getCharPositionInLine())) {
            lines.add(line);
        }
    }

    /** The index past the value that begins at {@code at}, or -1 when none begins there. */
    private int valueEnd(final int at) {
        final Token token = spoken.get(at);
        final int type = token.getType();
        if (type == FrostlakeLexer.MINUS || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.NOT) {
            return valueEnd(at + 1);
        }
        int end;
        if (type == FrostlakeLexer.LPAREN) {
            end = closing(at) + 1;
        } else if (type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
                || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE
                || type == FrostlakeLexer.NULL) {
            end = at + 1;
        } else if (VALUES.contains(type) && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN) {
            end = closing(at + 1) + 1;
        } else if (NAMES.contains(type)) {
            end = at + 1;
        } else {
            return -1;
        }
        while (operator(spoken.get(end).getType())) {
            final int next = valueEnd(end + 1);
            if (next < 0) {
                return end + 1;
            }
            end = next;
        }
        return end;
    }

    /** The index of the parenthesis that closes the one at {@code open}, or the end of input. */
    private int closing(final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return spoken.size() - 1;
    }

    /** Whether the word at {@code at} stands inside a sub-clause's own words, where another of them is due. */
    private boolean insidePhrase(final int at) {
        final Token before = spoken.get(at - 1);
        final String text = before.getText();
        final int type = before.getType();
        final String second = at >= 2 ? spoken.get(at - 2).getText() : "";
        return type == FrostlakeLexer.ALL || type == FrostlakeLexer.WITH || type == FrostlakeLexer.AFTER
            || type == FrostlakeLexer.SHOW || type == FrostlakeLexer.TO || type == FrostlakeLexer.NEXT
            || "ONE".equalsIgnoreCase(text) || "PER".equalsIgnoreCase(text) || "OMIT".equalsIgnoreCase(text)
            || "EMPTY".equalsIgnoreCase(text) || "UNMATCHED".equalsIgnoreCase(text) || "SKIP".equalsIgnoreCase(text)
            || "PAST".equalsIgnoreCase(text)
            || type == FrostlakeLexer.ROWS && "ALL".equalsIgnoreCase(second)
            || type == FrostlakeLexer.ROW && "ONE".equalsIgnoreCase(second)
            || "MATCH".equalsIgnoreCase(text) && "AFTER".equalsIgnoreCase(second)
            || type == FrostlakeLexer.LAST && "PAST".equalsIgnoreCase(second);
    }

    /** Whether a token is a word that cannot be a name. */
    private static boolean reserved(final Token token) {
        final String text = token.getText();
        return token.getType() != Token.EOF && !NAMES.contains(token.getType()) && !text.isEmpty()
            && Character.isLetter(text.charAt(0));
    }

    /** Whether a token can begin a definition's value. */
    private static boolean beginsValue(final Token token) {
        final int type = token.getType();
        return !"DEFINE".equalsIgnoreCase(token.getText()) && (VALUES.contains(type) || type == FrostlakeLexer.NOT
            || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.PLUS);
    }

    /** Whether the query can go on with a token after a relation: a comma, a clause word, a set operator, a join. */
    private static boolean continuesQuery(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.COMMA || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.FETCH || type == FrostlakeLexer.OFFSET
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT || type == FrostlakeLexer.JOIN || type == FrostlakeLexer.INNER
            || type == FrostlakeLexer.LEFT || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL
            || type == FrostlakeLexer.CROSS || type == FrostlakeLexer.NATURAL;
    }

    private static boolean operator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE
            || type == FrostlakeLexer.AND || type == FrostlakeLexer.OR || type == FrostlakeLexer.LIKE
            || type == FrostlakeLexer.ILIKE;
    }

    private void add(final int at) {
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
    }
}
