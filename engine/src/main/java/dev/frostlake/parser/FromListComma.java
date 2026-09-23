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
import java.util.Deque;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A comma after a FROM list's last table reference carries the list on for live: it refuses the first token that
 * cannot begin the next table reference — inside a bracket, the first token in it — where this parser, whose FROM list
 * may end in a comma, declined the list and refused the comma itself (all live-verified):
 *
 * <pre>
 *   SELECT 1 FROM t, 1                      unexpected '1' at 17
 *   SELECT 1 FROM t, (1)                    unexpected '1' at 18
 *   SELECT 1 FROM t, +                      unexpected '+' at 17
 *   SELECT 1 FROM t JOIN t u ON TRUE, 1     unexpected '1' at 34
 * </pre>
 */
final class FromListComma {

    private FromListComma() {
    }

    /**
     * The token live refuses instead of {@code comma}, or null when the comma does not follow a FROM list.
     *
     * @param recognizer the parser that refused the comma
     * @param comma      the refused token
     * @return the token to refuse, or null
     */
    static Token refusedInstead(final Recognizer<?, ?> recognizer, final Token comma) {
        if (!(recognizer instanceof Parser) || comma.getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        final Parser parser = (Parser) recognizer;
        final TokenStream stream = parser.getInputStream();
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final Token previous = spokenBefore(stream, comma.getTokenIndex());
        if (previous == null || parser.getContext() == null || !endsAFromList(parser.getContext(), previous)) {
            return null;
        }
        final Token next = spokenAfter(stream, comma.getTokenIndex());
        if (next == null || next.getType() == Token.EOF) {
            return null;
        }
        if (next.getType() == FrostlakeLexer.LPAREN) {
            final Token inside = spokenAfter(stream, next.getTokenIndex());
            return inside == null || inside.getType() == Token.EOF ? null : inside;
        }
        return next;
    }

    /** Whether a FROM list of the parse so far ends at {@code last}. */
    private static boolean endsAFromList(final ParserRuleContext current, final Token last) {
        ParserRuleContext root = current;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof FrostlakeParser.TableExpressionContext) {
                final Token stop = ((ParserRuleContext) node).getStop();
                if (stop != null && stop.getTokenIndex() == last.getTokenIndex()) {
                    return true;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return false;
    }

    private static Token spokenBefore(final TokenStream stream, final int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }

    private static Token spokenAfter(final TokenStream stream, final int index) {
        for (int i = index + 1; i < stream.size(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
