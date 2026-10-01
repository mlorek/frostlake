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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The line live's recovery stacks after refusing an operator whose left operand is a finished predicate — see
 * {@link FinishedPredicateSyntax} for the refusal and the measured shapes. The recovery is rebuilt on the text: the
 * part live's parser has already given up is replaced by a one-character operand, every position stays where it was
 * written, and the stacked line is the first one a parse of that text reports after the refused operator.
 */
final class FinishedPredicateRecovery {

    private static final int NOT_FOUND = -1;

    private FinishedPredicateRecovery() {
    }

    /**
     * The lines stacked after the refusal of {@code operator}, possibly none.
     *
     * @param operatorNode the parse-tree node the refused operator belongs to
     * @param operator     the refused operator
     * @param tokens       the token stream the script was parsed from
     * @param sql          the script's text
     * @return the stacked lines, in order
     */
    static List<String> stackedLines(final ParseTree operatorNode, final Token operator, final TokenStream tokens,
                                     final String sql) {
        final List<String> lines = new ArrayList<>();
        if (sql == null || sql.length() != sql.codePointCount(0, sql.length()) || opensWithExists(operatorNode)) {
            return lines;
        }
        ParserRuleContext construct = null;
        for (ParseTree up = operatorNode.getParent(); up != null && construct == null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ParenExprContext || up instanceof FrostlakeParser.FunctionArgContext
                    || up instanceof FrostlakeParser.WhenClauseContext || up instanceof FrostlakeParser.ExprItemContext) {
                construct = (ParserRuleContext) up;
            } else if (!(up instanceof FrostlakeParser.ExpressionContext)
                    && !(up instanceof FrostlakeParser.BooleanExprContext)) {
                return lines;
            }
        }
        if (construct == null) {
            return lines;
        }
        final int start;
        final int resumeAt;
        if (construct instanceof FrostlakeParser.ParenExprContext) {
            start = construct.getStart().getStartIndex();
            resumeAt = operator.getStartIndex();
        } else {
            final FrostlakeParser.ExprItemContext item = enclosingItem(construct);
            final int resume = resumeToken(tokens, operator.getTokenIndex());
            if (item == null || resume == NOT_FOUND) {
                return lines;
            }
            final Token resumed = tokens.get(resume);
            final int type = resumed.getType();
            if (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.SEMI
                    || type == Token.EOF) {
                return lines;
            }
            final int after = nextSpoken(tokens, resume);
            if (construct instanceof FrostlakeParser.FunctionArgContext && after != NOT_FOUND
                    && tokens.get(after).getType() == FrostlakeLexer.RPAREN) {
                return lines;
            }
            start = item.getStart().getStartIndex();
            resumeAt = resumed.getStartIndex();
        }
        final char[] text = sql.toCharArray();
        for (int c = start; c < resumeAt; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(operator));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                // One line: live's recovery reports nothing after the first fault it meets there.
                lines.add(line);
                return lines;
            }
        }
        return lines;
    }

    /** Whether the refused operator's left operand is an EXISTS, after which live stacks nothing. */
    private static boolean opensWithExists(final ParseTree operatorNode) {
        return operatorNode.getChildCount() > 0 && operatorNode.getChild(0) instanceof FrostlakeParser.ExistsExprContext;
    }

    /** The select item a call argument or a CASE branch stands in, through expressions only; or null. */
    private static FrostlakeParser.ExprItemContext enclosingItem(final ParserRuleContext construct) {
        for (ParserRuleContext up = construct; up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.ExprItemContext) {
                return (FrostlakeParser.ExprItemContext) up;
            }
            if (!(up instanceof FrostlakeParser.ExpressionContext) && !(up instanceof FrostlakeParser.BooleanExprContext)
                    && !(up instanceof FrostlakeParser.FunctionArgContext)
                    && !(up instanceof FrostlakeParser.FunctionArgListContext)
                    && !(up instanceof FrostlakeParser.WhenClauseContext)
                    && !(up instanceof FrostlakeParser.CaseExpressionContext)) {
                return null;
            }
        }
        return null;
    }

    /**
     * The index of the first token after the refused operator that live's recovery resumes at: a word that may be a
     * name, AS, or a token that may follow a select item — a comma, a closing parenthesis, a clause keyword, the end.
     */
    private static int resumeToken(final TokenStream tokens, final int operatorIndex) {
        final ATN atn = FrostlakeParser._ATN;
        final IntervalSet names = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]);
        for (int i = nextSpoken(tokens, operatorIndex); i != NOT_FOUND; i = nextSpoken(tokens, i)) {
            final int type = tokens.get(i).getType();
            if (names.contains(type) || isFollower(type)) {
                return i;
            }
        }
        return NOT_FOUND;
    }

    private static boolean isFollower(final int type) {
        return type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.COMMA
            || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.AS || type == FrostlakeLexer.FROM
            || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.FETCH || type == FrostlakeLexer.UNION
            || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW || type == FrostlakeLexer.INTERSECT;
    }

    /** The index of the next default-channel token after {@code index}, or {@link #NOT_FOUND}. */
    private static int nextSpoken(final TokenStream tokens, final int index) {
        for (int i = index + 1; i < tokens.size(); i++) {
            if (tokens.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return i;
            }
        }
        return NOT_FOUND;
    }
}
