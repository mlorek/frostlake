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
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The refusals judged once a statement has parsed — an operator after a finished predicate ({@link
 * FinishedPredicateSyntax}), a predicate in POSITION's needle ({@link PositionNeedleSyntax}), a call in a FROM form no
 * name accepts ({@link AnsiFromFormSyntax}) — made ahead of the parse's own fault when they stand before it in the
 * text, the order live reports them in:
 *
 * <pre>
 *   SELECT 1 IN (1, 2) + 1 x y                    '+' at 19, then 'y' at 25
 *   SELECT 1 IN (1, 2)::INT AS x + 1              '::' at 18, then 'AS' at 24
 *   SELECT 1 IN (1, 2)::"INT" + 1                 '::' at 18, then '+' at 26 — not the unsupported type
 *   SELECT EXTRACT('wks' FROM d, 1) FROM t        'FROM' at 21, then '1' at 29
 *   SELECT EXTRACT('wks' FROM d) x y FROM t       'FROM' at 21, then ')' at 27
 *   SELECT CAST(EXTRACT('wks' FROM d), 1) FROM t  'FROM' at 26, then ')' at 32 — ahead of the comma at 33
 * </pre>
 *
 * <p>Those checks need a parse tree, and a failed parse has none worth reading, so the text before the parse's first
 * fault is parsed on its own: every bracket it leaves open is closed after it, a CAST's left open before its AS is
 * given a type first, and a cast whose type the parse refused is completed with a type it reads. When that text parses
 * cleanly, the checks judge its tree; the lines they stack are still read from the whole text.
 */
public final class PostParseRefusals {

    private static final String SOME_TYPE = " NUMBER";
    private static final String SOME_CAST_TYPE = " AS NUMBER";
    private static final String SOME_OPERAND = " 1";

    private PostParseRefusals() {
    }

    /**
     * Refuse the earliest of those refusals when it stands before the parse's first fault.
     *
     * @param tokens   the token stream the script was parsed from
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final TokenStream tokens, final String sql, final SyntaxErrorListener listener) {
        if (tokens == null || sql == null || !listener.hasErrors()) {
            return;
        }
        final int faultStart = listener.firstFaultStartIndex();
        final int[] faultAt = listener.firstFaultCoordinates();
        if (faultStart < 0 || faultStart > sql.length() || faultAt == null) {
            return;
        }
        final String candidate = repairedPrefix(sql.substring(0, faultStart));
        final FrostlakeParser.SqlScriptContext tree = candidate == null ? null : SyntaxErrorListener.cleanParse(candidate);
        if (tree == null) {
            return;
        }
        final List<String> reported = listener.reportedSyntaxLines();
        final int[] reportedAt = reported.isEmpty() ? null : SyntaxErrorListener.sentenceCoordinates(reported.get(0));
        SqlSyntaxException earliest = null;
        int[] earliestAt = null;
        for (int check = 0; check < 3; check++) {
            final SqlSyntaxException refusal = refusalOf(check, tree, tokens, sql);
            final int[] at = refusal == null || refusal.getSyntaxErrors().isEmpty() ? null
                : SyntaxErrorListener.sentenceCoordinates(refusal.getSyntaxErrors().get(0));
            if (at != null && precedes(at, faultAt) && (reportedAt == null || precedes(at, reportedAt))
                    && (earliestAt == null || precedes(at, earliestAt))) {
                earliest = refusal;
                earliestAt = at;
            }
        }
        if (earliest != null) {
            throw earliest;
        }
    }

    /** The refusal the check numbered {@code check} makes over {@code tree}, or null. */
    private static SqlSyntaxException refusalOf(final int check, final FrostlakeParser.SqlScriptContext tree,
                                                final TokenStream tokens, final String sql) {
        try {
            if (check == 0) {
                FinishedPredicateSyntax.requireOpenOperands(tree, tokens, sql);
            } else if (check == 1) {
                PositionNeedleSyntax.requireValueNeedles(tree, tokens, sql);
            } else {
                AnsiFromFormSyntax.requireCallForms(tree, tokens, sql);
            }
        } catch (final SqlSyntaxException refused) {
            return refused;
        }
        return null;
    }

    /**
     * The text before a parse's first fault made whole: a trailing '::', or a trailing AS of a CAST or TRY_CAST, given
     * a type, a trailing FOR or FROM inside a bracket given an operand, then every bracket left open closed in order — a
     * CAST's or TRY_CAST's still waiting for its AS given a type first. Null when the prefix holds nothing to close and
     * ends nowhere a check could read.
     */
    private static String repairedPrefix(final String prefix) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(prefix));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Integer> open = new ArrayList<>();
        final List<Boolean> castAwaitingType = new ArrayList<>();
        boolean castAs = false;
        Token last = null;
        for (final Token token : stream.getTokens()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
                continue;
            }
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) {
                open.add(Integer.valueOf(type));
                castAwaitingType.add(Boolean.valueOf(type == FrostlakeLexer.LPAREN && last != null
                    && (last.getType() == FrostlakeLexer.CAST || last.getType() == FrostlakeLexer.TRY_CAST)));
            } else if ((type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) && !open.isEmpty()) {
                open.remove(open.size() - 1);
                castAwaitingType.remove(castAwaitingType.size() - 1);
            } else if (type == FrostlakeLexer.AS && !castAwaitingType.isEmpty()) {
                castAs = Boolean.TRUE.equals(castAwaitingType.get(castAwaitingType.size() - 1));
                castAwaitingType.set(castAwaitingType.size() - 1, Boolean.FALSE);
            }
            last = token;
        }
        if (last == null) {
            return null;
        }
        final StringBuilder repaired = new StringBuilder(prefix);
        if (last.getType() == FrostlakeLexer.DOUBLE_COLON || last.getType() == FrostlakeLexer.AS && castAs) {
            repaired.append(SOME_TYPE);
        } else if ((last.getType() == FrostlakeLexer.FOR || last.getType() == FrostlakeLexer.FROM) && !open.isEmpty()) {
            repaired.append(SOME_OPERAND);
        }
        for (int i = open.size() - 1; i >= 0; i--) {
            if (Boolean.TRUE.equals(castAwaitingType.get(i))) {
                repaired.append(SOME_CAST_TYPE);
            }
            repaired.append(open.get(i).intValue() == FrostlakeLexer.LPAREN ? ')' : ']');
        }
        return repaired.toString();
    }

    private static boolean precedes(final int[] candidate, final int[] reference) {
        return candidate[0] < reference[0] || candidate[0] == reference[0] && candidate[1] < reference[1];
    }
}
