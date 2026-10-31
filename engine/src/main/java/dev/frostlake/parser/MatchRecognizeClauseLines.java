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

/**
 * The lines live reports for a fault inside a MATCH_RECOGNIZE clause, whose sub-clauses it reads in their one order —
 * PARTITION BY, ORDER BY, MEASURES, ONE ROW or ALL ROWS PER MATCH, AFTER MATCH SKIP, PATTERN, DEFINE (all
 * live-verified). With every sub-clause in its place, the first fault is reported and the clause is read on to its
 * closing parenthesis, nothing inside a later sub-clause reported:
 *
 * <pre>
 *   … MEASURES FINAL v AS f ALL ROWS PER MATCH …            'AS' alone
 *   … PATTERN (^ any+ $) DEFINE …                            'any' alone
 *   … PATTERN (lo () hi) DEFINE …                            ')' alone
 * </pre>
 *
 * <p>A sub-clause written where it cannot stand — after a later one, or a DEFINE before any PATTERN — is refused at
 * its keyword, and each later one out of place again once; a PATTERN left last is followed by the clause's ')':
 *
 * <pre>
 *   … PATTERN (a) DEFINE a AS TRUE MEASURES …                'MEASURES'
 *   … MEASURES MATCH_NUMBER() AS mn ORDER BY id …            'ORDER'
 *   … MEASURES 1 AS m DEFINE a AS TRUE)                      'DEFINE'
 *   … MEASURES … DEFINE a AS TRUE PATTERN (a))               'DEFINE', 'PATTERN', then the ')'
 * </pre>
 *
 * <p>A comma in the pattern's bracket reads on as {@link MatchRecognizePatternLines} tells.
 */
final class MatchRecognizeClauseLines {

    private static final int PATTERN_RANK = 5;
    private static final int DEFINE_RANK = 6;

    private MatchRecognizeClauseLines() {
    }

    /**
     * The lines for a text whose first fault stands in such a clause, or null when it stands elsewhere or the
     * clause's shape is not one of the above.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @param firstOnly  whether only the first line is read
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean firstOnly) {
        int open = -1;
        int fault = -1;
        for (int i = 0; i + 1 < spoken.size(); i++) {
            if (open < 0 && "MATCH_RECOGNIZE".equalsIgnoreCase(spoken.get(i).getText())
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                open = i + 1;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (open < 0 || fault < open) {
            return null;
        }
        final List<Integer> keywords = new ArrayList<>();
        final List<Integer> ranks = new ArrayList<>();
        int close = -1;
        int depth = 0;
        for (int i = open + 1; i < spoken.size() && close < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    close = i;
                }
                depth--;
            } else if (depth == 0) {
                final int rank = rankAt(spoken, i);
                if (rank >= 0) {
                    keywords.add(Integer.valueOf(i));
                    ranks.add(Integer.valueOf(rank));
                }
            }
        }
        if (close < 0 || fault > close) {
            return null;
        }
        // The parse may stop at the clause's own parenthesis or the PATTERN, unable to predict a pattern that holds a
        // comma.
        final List<String> patternComma = MatchRecognizePatternLines.lines(spoken, open, close, fault, firstOnly);
        if (patternComma != null || fault == open) {
            return patternComma;
        }
        final List<String> readOn = firstOnly ? null
            : readOn(sql, spoken, open, close, firstMisplaced(fault, keywords, ranks), keywords, ranks);
        if (readOn != null) {
            return readOn;
        }
        final List<Integer> misplaced = new ArrayList<>();
        int highest = -1;
        boolean pattern = false;
        boolean define = false;
        for (int k = 0; k < keywords.size(); k++) {
            final int rank = ranks.get(k).intValue();
            if (rank <= highest || rank == DEFINE_RANK && !pattern) {
                misplaced.add(keywords.get(k));
            }
            highest = Math.max(highest, rank);
            pattern = pattern || rank == PATTERN_RANK;
            define = define || rank == DEFINE_RANK;
        }
        final List<String> lines = new ArrayList<>();
        if (misplaced.isEmpty()) {
            if (!pattern || !define || keywords.contains(Integer.valueOf(fault))) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            return lines;
        }
        if (fault < misplaced.get(0).intValue()) {
            return null;
        }
        for (final Integer at : misplaced) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(at.intValue())));
            if (firstOnly) {
                return lines;
            }
        }
        if (ranks.get(ranks.size() - 1).intValue() == PATTERN_RANK) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        }
        return lines;
    }

    /**
     * The lines live reports past a fault at the clause's own depth, before any DEFINE and no later than the first
     * sub-clause out of its place (see {@link MatchRecognizeReadOn}), or null.
     */
    private static List<String> readOn(final String sql, final List<Token> spoken, final int open, final int close,
                                       final int fault, final List<Integer> keywords, final List<Integer> ranks) {
        int depth = 0;
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        if (depth != 0) {
            return null;
        }
        final List<Integer> before = new ArrayList<>();
        int highest = -1;
        for (int k = 0; k < keywords.size() && keywords.get(k).intValue() < fault; k++) {
            final int rank = ranks.get(k).intValue();
            if (rank <= highest) {
                return null;
            }
            highest = rank;
            before.add(ranks.get(k));
        }
        return MatchRecognizeReadOn.lines(sql, spoken, open - 1, close, fault, before);
    }

    /** The first sub-clause keyword out of its place at or before {@code fault}, else {@code fault}. */
    private static int firstMisplaced(final int fault, final List<Integer> keywords, final List<Integer> ranks) {
        int highest = -1;
        for (int k = 0; k < keywords.size() && keywords.get(k).intValue() <= fault; k++) {
            final int rank = ranks.get(k).intValue();
            if (rank <= highest) {
                return keywords.get(k).intValue();
            }
            highest = rank;
        }
        return fault;
    }

    /** The rank of the sub-clause a token opens, in the clause's order, or -1 when it opens none. */
    static int rankAt(final List<Token> spoken, final int at) {
        final Token token = spoken.get(at);
        final int next = at + 1 < spoken.size() ? spoken.get(at + 1).getType() : Token.EOF;
        final String text = token.getText();
        if (token.getType() == FrostlakeLexer.PARTITION && next == FrostlakeLexer.BY) {
            return 0;
        }
        if (token.getType() == FrostlakeLexer.ORDER && next == FrostlakeLexer.BY) {
            return 1;
        }
        if ("MEASURES".equalsIgnoreCase(text)) {
            return 2;
        }
        if ("ONE".equalsIgnoreCase(text) && next == FrostlakeLexer.ROW
                || token.getType() == FrostlakeLexer.ALL && next == FrostlakeLexer.ROWS) {
            return 3;
        }
        if (token.getType() == FrostlakeLexer.AFTER && "MATCH".equalsIgnoreCase(spoken.get(at + 1).getText())) {
            return 4;
        }
        if (token.getType() == FrostlakeLexer.PATTERN) {
            return PATTERN_RANK;
        }
        return "DEFINE".equalsIgnoreCase(text) ? DEFINE_RANK : -1;
    }
}
