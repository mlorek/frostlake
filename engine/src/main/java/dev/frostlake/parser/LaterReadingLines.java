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
 * Lines live reports in the statements after a script's first one where this parse raises none (all live-verified):
 *
 * <pre>
 *   SELECT 1; SHOW TABLES IN t x; SELECT 1 x y      'x' alone: a later SHOW whose scope is a word the account keeps
 *   SELECT 1 +; SHOW TABLES IN t x; SELECT 1 x y    as a keyword refuses the name after it, as a first statement's
 *                                                   does, and the report ends there
 *   CREATE TABLE IF NOT; SELECT 1 x y               ';', 'y', then the end of the input: a semicolon where IF NOT
 *                                                   wants its EXISTS adds the end after the next statement's line
 * </pre>
 *
 * <p>A later SHOW is judged where the report reads past it: some line stands after its name, and none inside it.
 */
final class LaterReadingLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private LaterReadingLines() {
    }

    /**
     * The lines with a later SHOW's reading applied, or the lines as they are.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<String> lines) {
        if (spoken == null || lines == null || lines.isEmpty()) {
            return lines;
        }
        for (int s = 1; s + 4 < spoken.size(); s++) {
            if (spoken.get(s - 1).getType() != FrostlakeLexer.SEMI || spoken.get(s).getType() != FrostlakeLexer.SHOW
                    || spoken.get(s + 2).getType() != FrostlakeLexer.IN
                    || spoken.get(s + 3).getType() != FrostlakeLexer.IDENTIFIER
                    || ClassNameWords.isClassWord(spoken.get(s + 3))
                    || !WORDS.contains(spoken.get(s + 4).getType())) {
                continue;
            }
            final Token name = spoken.get(s + 4);
            final List<String> kept = new ArrayList<>();
            boolean past = false;
            for (final String line : lines) {
                final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
                if (at == null) {
                    return lines;
                }
                if (before(at, spoken.get(s))) {
                    kept.add(line);
                } else if (before(at, name) || same(at, name)) {
                    return lines;
                } else {
                    past = true;
                }
            }
            if (!past) {
                return lines;
            }
            kept.add(SyntaxErrorListener.sentence(name));
            return kept;
        }
        return lines;
    }

    /**
     * The lines with the end of the input added after a later statement's line when the first names a semicolon where
     * a CREATE's IF NOT wants its EXISTS; the lines as they are otherwise.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param lines  the lines reported
     * @return the lines
     */
    static List<String> endAfterExists(final List<Token> spoken, final List<String> lines) {
        return spoken != null && lines != null && lines.size() > 1 && semicolonForExists(spoken, lines.get(0))
            && inNextStatement(spoken, lines.get(0), lines.get(1)) ? withEnd(spoken, lines) : lines;
    }

    /**
     * Whether the second line stands in the statement right after the first line's semicolon: CREATE TABLE IF NOT;
     * SELECT 2; SELECT 3 x y adds no end (live-verified).
     */
    private static boolean inNextStatement(final List<Token> spoken, final String first, final String second) {
        final int[] from = SyntaxErrorListener.sentenceCoordinates(first);
        final int[] to = SyntaxErrorListener.sentenceCoordinates(second);
        if (from == null || to == null) {
            return false;
        }
        for (final Token token : spoken) {
            if (token.getType() == FrostlakeLexer.SEMI && before(from, token) && !before(to, token)
                    && !same(to, token)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a line names a semicolon standing where a CREATE's IF NOT wants its EXISTS. */
    private static boolean semicolonForExists(final List<Token> spoken, final String line) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        for (int i = 3; at != null && i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (same(at, token)) {
                int start = i;
                while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
                    start--;
                }
                return token.getType() == FrostlakeLexer.SEMI && spoken.get(i - 1).getType() == FrostlakeLexer.NOT
                    && spoken.get(i - 2).getType() == FrostlakeLexer.IF
                    && spoken.get(start).getType() == FrostlakeLexer.CREATE;
            }
        }
        return false;
    }

    private static List<String> withEnd(final List<Token> spoken, final List<String> lines) {
        final List<String> ended = new ArrayList<>(lines);
        ended.add(SyntaxErrorListener.sentence(spoken.get(spoken.size() - 1)));
        return ended;
    }

    private static boolean before(final int[] at, final Token token) {
        return at[0] < token.getLine() || at[0] == token.getLine() && at[1] < token.getCharPositionInLine();
    }

    private static boolean same(final int[] at, final Token token) {
        return at[0] == token.getLine() && at[1] == token.getCharPositionInLine();
    }
}
