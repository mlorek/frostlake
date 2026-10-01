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

import org.antlr.v4.runtime.Token;

/**
 * Words a clause is written with that the lexer keeps as plain names, because each one may also name a column:
 * {@code GRAPH} and {@code RUN} of {@code EXECUTE TASK … RETRY GRAPH RUN GROUP}, {@code INSERT_ONLY} of
 * {@code CREATE STREAM} and {@code SUBPATH} of {@code ALTER STAGE … REFRESH}. A parse predicate reads the word
 * off the token ahead, so the clause is recognised without the word becoming a keyword.
 */
public final class ClauseWords {

    private ClauseWords() {
    }

    /**
     * Whether a token is the unquoted word, in any letter case.
     *
     * @param token the token ahead
     * @param word  the word, upper case
     * @return whether the token spells the word
     */
    public static boolean isWord(final Token token, final String word) {
        return token != null && token.getType() == FrostlakeParser.IDENTIFIER && word.equalsIgnoreCase(token.getText());
    }
}
