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
 * The lines the account reports for a word of a FROM list that it reads in a way of its own (all live-verified,
 * each for a statement that is one SELECT):
 *
 * <pre>
 *   … JOIN USING                    the token after USING: '&lt;EOF&gt;', a name, or its '(' and then the first ')'
 *   … JOIN OFFSET 1 [ROWS]          the number, then the token after it
 *   … JOIN WINDOW w AS (ORDER BY a) 'AS', and the ')' of a bracket that opens with ORDER
 *   … JOIN INNER n                  'n' twice: after INNER, CROSS, NATURAL or ASOF its JOIN is due
 *   … JOIN ASOF n                   'n' twice, then '&lt;EOF&gt;'
 *   … JOIN LEFT OUTER               'OUTER', then '&lt;EOF&gt;'
 *   … JOIN LATERAL x y              'y' twice: LATERAL takes a query or a call, never a name
 *   … JOIN LATERAL x y z            'y' twice, then 'z'
 *   … JOIN LATERAL x, n             ',', then '&lt;EOF&gt;'
 *   … FROM t FOR SHARE              'SHARE': after FOR only UPDATE is read
 *   … FROM t FOR UPDATE WAIT x      'x': WAIT wants its seconds, and NOWAIT nothing after it
 * </pre>
 */
final class FromListWordLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private FromListWordLines() {
    }

    /**
     * The lines of a text that is one SELECT holding such a word at or before its first fault, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        if (spoken.size() < 5 || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final int end = spoken.size() - 1;
        int depth = 0;
        boolean from = false;
        boolean anyFrom = false;
        for (int i = 1; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (depth != 0) {
                continue;
            }
            if (faultIndex >= 0 && spoken.get(i).getTokenIndex() > faultIndex) {
                return null;
            }
            if (type == FrostlakeLexer.FROM) {
                from = true;
                anyFrom = true;
            } else if (anyFrom && type == FrostlakeLexer.FOR && spoken.get(i - 1).getType() != FrostlakeLexer.JOIN) {
                return spoken.get(i + 1).getType() == FrostlakeLexer.UPDATE ? afterForUpdate(spoken, i + 2, end)
                    : told(spoken.get(i + 1));
            } else if (from && type == FrostlakeLexer.LATERAL) {
                final int before = spoken.get(i - 1).getType();
                if (before == FrostlakeLexer.JOIN || before == FrostlakeLexer.COMMA || before == FrostlakeLexer.FROM) {
                    return afterLateral(spoken, i, end);
                }
            } else if (from && type == FrostlakeLexer.JOIN) {
                final List<String> lines = afterJoin(spoken, i, end);
                if (lines != null) {
                    return lines;
                }
            } else if (endsFromList(type)) {
                from = false;
            }
        }
        return null;
    }

    /** The word after FOR UPDATE: WAIT wants its seconds, and NOWAIT nothing after it. */
    private static List<String> afterForUpdate(final List<Token> spoken, final int at, final int end) {
        if (at >= end) {
            return null;
        }
        final String word = spoken.get(at).getText();
        if ("WAIT".equalsIgnoreCase(word)) {
            return spoken.get(at + 1).getType() == FrostlakeLexer.INTEGER_LITERAL ? null : told(spoken.get(at + 1));
        }
        return "NOWAIT".equalsIgnoreCase(word) && at + 1 < end ? told(spoken.get(at + 1)) : null;
    }

    private static List<String> afterJoin(final List<Token> spoken, final int join, final int end) {
        final Token word = spoken.get(join + 1);
        final Token next = join + 2 <= end ? spoken.get(join + 2) : null;
        final int type = word.getType();
        if (next == null) {
            return null;
        }
        if (type == FrostlakeLexer.USING) {
            final List<String> lines = told(next);
            if (next.getType() == FrostlakeLexer.LPAREN && spoken.get(join + 3).getType() != FrostlakeLexer.RPAREN) {
                for (int i = join + 3; i < end; i++) {
                    if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                        lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                        break;
                    }
                }
            }
            return lines;
        }
        if (type == FrostlakeLexer.OFFSET && next.getType() == FrostlakeLexer.INTEGER_LITERAL) {
            final List<String> lines = told(next);
            int after = join + 3;
            if (after < end && (spoken.get(after).getType() == FrostlakeLexer.ROWS
                    || spoken.get(after).getType() == FrostlakeLexer.ROW)) {
                after++;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(after)));
            return lines;
        }
        if ("WINDOW".equalsIgnoreCase(word.getText()) && isName(next.getType()) && join + 3 <= end
                && spoken.get(join + 3).getType() == FrostlakeLexer.AS) {
            final List<String> lines = told(spoken.get(join + 3));
            if (join + 5 < end && spoken.get(join + 4).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(join + 5).getType() == FrostlakeLexer.ORDER) {
                for (int i = join + 5; i < end; i++) {
                    if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                        lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                        break;
                    }
                }
            }
            return lines;
        }
        final boolean asof = type == FrostlakeLexer.ASOF;
        if (asof || type == FrostlakeLexer.INNER || type == FrostlakeLexer.CROSS || type == FrostlakeLexer.NATURAL) {
            if (next.getType() == FrostlakeLexer.JOIN) {
                // ASOF JOIN is read, and its MATCH_CONDITION is missing where the input ends.
                if (!asof || join + 4 != end || !isName(spoken.get(join + 3).getType())) {
                    return null;
                }
                final List<String> lines = told(next);
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
                return lines;
            }
            final List<String> lines = told(next);
            lines.add(lines.get(0));
            if (asof && isName(next.getType())) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            }
            return lines;
        }
        if ((type == FrostlakeLexer.LEFT || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL)
                && next.getType() == FrostlakeLexer.OUTER && join + 3 == end) {
            final List<String> lines = told(next);
            lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            return lines;
        }
        return null;
    }

    /** LATERAL before a name that no call's parenthesis follows. */
    private static List<String> afterLateral(final List<Token> spoken, final int lateral, final int end) {
        int at = lateral + 1;
        if (at > end || !isName(spoken.get(at).getType())) {
            return null;
        }
        at++;
        while (at + 1 <= end && spoken.get(at).getType() == FrostlakeLexer.DOT && isName(spoken.get(at + 1).getType())) {
            at += 2;
        }
        final Token after = spoken.get(at);
        if (after.getType() == FrostlakeLexer.LPAREN || after.getType() == FrostlakeLexer.DOT) {
            return null;
        }
        final List<String> lines = told(after);
        if (after.getType() == FrostlakeLexer.COMMA) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            return lines;
        }
        lines.add(lines.get(0));
        if (isName(after.getType()) && at + 1 < end) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
        }
        return lines;
    }

    private static List<String> told(final Token token) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    /** Whether a token at the query's own level ends its FROM list. */
    private static boolean endsFromList(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.INTERSECT
            || type == FrostlakeLexer.MINUS;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
