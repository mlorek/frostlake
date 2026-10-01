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
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;

/**
 * A ')' that closes no group at all ends live's report of the whole input, the statements after it included — outside
 * a scripting block, where live reads on (all live-verified):
 *
 * <pre>
 *   SELECT 1) x y FROM t                     unexpected ')' at 8, and nothing more
 *   SELECT 1) x; SELECT 1 x y                unexpected ')' at 8, and nothing more
 *   SELECT 1 FROM t WHERE (a = 1)) b c       unexpected ')' at 29, and nothing more
 * </pre>
 */
final class UnmatchedCloseParen {

    private UnmatchedCloseParen() {
    }

    /**
     * Whether the refused token is such a ')', so nothing after it is reported.
     *
     * @param recognizer the parser that raised the error
     * @param named      the token the line names
     * @return whether the report ends with it
     */
    static boolean endsTheReport(final Recognizer<?, ?> recognizer, final Token named) {
        return named.getType() == FrostlakeLexer.RPAREN && recognizer instanceof Parser
            && closesNoOpenGroup((Parser) recognizer, named) && !insideABlock((Parser) recognizer);
    }

    /**
     * The lines live reports when the first line names such a ')' in a scripting block, where live reads on: an alias
     * and a semicolon after it are read as a statement's end, the next statement is refused at its first word — a
     * query is read whole and the word after its semicolon refused instead — and nothing more is reported; a second
     * word after the alias is refused at once; a semicolon right after the ')' ends the report (all live-verified):
     *
     * <pre>
     *   BEGIN SELECT 1) x; RETURN 1 1; END       ')' at 14, then 'RETURN' at 19
     *   BEGIN SELECT 1) x; LET a := 1; END       ')' at 14, then 'LET' at 19
     *   BEGIN SELECT 1) x; SELECT 2; END         ')' at 14, then 'END' at 29
     *   BEGIN SELECT 1) x y; RETURN 1; END       ')' at 14, then 'y' at 18
     *   BEGIN SELECT 1); RETURN 1 1; END         ')' at 14 alone
     * </pre>
     *
     * @param spoken     the default-channel tokens of the input, end of input included
     * @param faultIndex the token index of the token the first line names
     * @return the lines, or null when the first line names no such ')' or the shape after it is another
     */
    static List<String> blockLines(final List<Token> spoken, final int faultIndex) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return null;
        }
        int close = -1;
        for (int i = 1; i < spoken.size() && close < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                close = i;
            }
        }
        if (close < 1 || close + 3 >= spoken.size() || spoken.get(close).getType() != FrostlakeLexer.RPAREN
                || !closesNoGroupOfItsStatement(spoken, close)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        final Token next = spoken.get(close + 1);
        if (next.getType() == FrostlakeLexer.SEMI) {
            return lines;
        }
        if (!isName(next)) {
            return null;
        }
        final Token after = spoken.get(close + 2);
        if (isName(after)) {
            lines.add(SyntaxErrorListener.sentence(after));
            return lines;
        }
        if (after.getType() != FrostlakeLexer.SEMI) {
            return null;
        }
        Token refused = spoken.get(close + 3);
        if (refused.getType() == FrostlakeLexer.SELECT || refused.getType() == FrostlakeLexer.WITH) {
            refused = null;
            int depth = 0;
            for (int i = close + 3; i + 1 < spoken.size() && refused == null; i++) {
                final int type = spoken.get(i).getType();
                if (type == FrostlakeLexer.LPAREN) {
                    depth++;
                } else if (type == FrostlakeLexer.RPAREN) {
                    depth--;
                } else if (depth == 0 && type == FrostlakeLexer.SEMI) {
                    refused = spoken.get(i + 1);
                }
            }
            if (refused == null) {
                return null;
            }
        }
        if (refused.getType() != Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(refused));
        }
        return lines;
    }

    /** Whether the ')' at {@code close} closes no group opened in its own statement. */
    private static boolean closesNoGroupOfItsStatement(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (i != close && (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN)) {
                break;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            }
        }
        return depth > 0;
    }

    /** Whether a token can be a name. */
    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(token.getType());
    }

    /**
     * Whether {@code close} closes no group at all: more closing than opening parentheses stand between the
     * statement's own start and it.
     */
    private static boolean closesNoOpenGroup(final Parser parser, final Token close) {
        final TokenStream tokens = parser.getInputStream();
        int depth = 0;
        for (int i = close.getTokenIndex(); i >= 0; i--) {
            final Token token = tokens.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.SEMI && i != close.getTokenIndex()) {
                break;
            }
            if (token.getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                depth--;
            }
        }
        return depth > 0;
    }

    /**
     * Whether the parse stands inside a scripting block: a statement list encloses the rule it is in, or the input
     * opens with BEGIN or DECLARE.
     */
    private static boolean insideABlock(final Parser parser) {
        for (ParserRuleContext up = parser.getContext(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.StatementListContext) {
                return true;
            }
        }
        final TokenStream tokens = parser.getInputStream();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return token.getType() == FrostlakeLexer.BEGIN || token.getType() == FrostlakeLexer.DECLARE;
            }
        }
        return false;
    }
}
