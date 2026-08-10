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

package dev.frostlake.executor.commands;

import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the one positively-known-bad shape inside a routine body the grammar cannot otherwise
 * read: a Snowflake-Scripting condition written WITHOUT its parentheses ({@code IF n > 10 THEN},
 * {@code ELSEIF}, {@code WHILE … DO}, {@code REPEAT … UNTIL … END}) — a real account requires
 * {@code IF (<condition>) THEN} and rejects the routine at CREATE time. The scan is token-level
 * (the engine's own lexer, never text sniffing) and deliberately narrow: the keyword must not
 * follow {@code END} (closing {@code END IF} / {@code END WHILE}), {@code IF EXISTS} /
 * {@code IF NOT EXISTS} clauses of ordinary SQL are excluded, and the matching {@code THEN} /
 * {@code DO} / {@code END} must actually follow — anything less stays fail-open, because a body
 * this grammar cannot read is unknown, not invalid.
 */
final class BareScriptingConditionScanner {

    private BareScriptingConditionScanner() {
    }

    /**
     * The two syntax-error lines a real account reports for the first bare condition in
     * {@code body}, or null when the body carries no positively-known-bad shape.
     *
     * @param anchorOnKeyword the account's two flavors: a FUNCTION block body anchors the first
     *                        line on the condition keyword itself, one column right of the token
     *                        ({@code unexpected 'IF'}); a PROCEDURE body anchors on the condition's
     *                        first token at its own column ({@code unexpected 'n'}).
     */
    static String describe(final String body, final boolean anchorOnKeyword) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        final List<Token> visible = new ArrayList<Token>();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != Token.EOF) {
                visible.add(token);
            }
        }
        for (int i = 0; i < visible.size() - 1; i++) {
            final Token keyword = visible.get(i);
            final int type = keyword.getType();
            if (type != FrostlakeLexer.IF && type != FrostlakeLexer.ELSEIF
                    && type != FrostlakeLexer.WHILE && type != FrostlakeLexer.UNTIL) {
                continue;
            }
            if (i > 0 && visible.get(i - 1).getType() == FrostlakeLexer.END) {
                continue;
            }
            final Token next = visible.get(i + 1);
            if (next.getType() == FrostlakeLexer.LPAREN) {
                continue;
            }
            if (type == FrostlakeLexer.IF
                    && (next.getType() == FrostlakeLexer.EXISTS || next.getType() == FrostlakeLexer.NOT)) {
                continue;
            }
            final int followerType;
            if (type == FrostlakeLexer.WHILE) {
                followerType = FrostlakeLexer.DO;
            } else if (type == FrostlakeLexer.UNTIL) {
                followerType = FrostlakeLexer.END;
            } else {
                followerType = FrostlakeLexer.THEN;
            }
            Token follower = null;
            for (int j = i + 2; j < visible.size(); j++) {
                if (visible.get(j).getType() == followerType) {
                    follower = visible.get(j);
                    break;
                }
            }
            if (follower == null) {
                continue;
            }
            if (anchorOnKeyword) {
                return line(keyword.getLine(), keyword.getCharPositionInLine() + 1, keyword.getText())
                    + "\n" + line(follower.getLine(), follower.getCharPositionInLine() + 1, follower.getText());
            }
            return line(next.getLine(), next.getCharPositionInLine(), next.getText())
                + "\n" + line(follower.getLine(), follower.getCharPositionInLine(), follower.getText());
        }
        return null;
    }

    private static String line(final int lineNumber, final int position, final String text) {
        return "syntax error line " + lineNumber + " at position " + position
            + " unexpected '" + text + "'.";
    }
}
