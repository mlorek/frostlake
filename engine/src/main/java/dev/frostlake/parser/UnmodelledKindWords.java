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
 * The plain words among those of the object kinds the account drops and describes that Frostlake models no object
 * of. {@code SNAPSHOT} and {@code CONNECTION} name a kind alone, and {@code PROFILE}, {@code LIFECYCLE} and
 * {@code PROJECT} follow the keywords {@code ORGANIZATION}, {@code STORAGE} and {@code DBT} in the grammar's
 * {@code unmodelledKind}. A word is matched whatever its case and never in quotes (live-verified).
 */
public final class UnmodelledKindWords {

    /** The kinds one plain word names. */
    private static final String[] ONE_WORD = {"SNAPSHOT", "CONNECTION"};

    private UnmodelledKindWords() {
    }

    /**
     * Whether a plain word names a kind alone.
     *
     * @param word the token
     * @return whether it is such a word
     */
    public static boolean isOneWord(final Token word) {
        for (final String kind : ONE_WORD) {
            if (is(word, kind)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a token is the given word, written plain.
     *
     * @param word the token
     * @param text the word, upper-cased
     * @return whether the token spells it
     */
    public static boolean is(final Token word, final String text) {
        return word != null && word.getType() == FrostlakeLexer.IDENTIFIER && text.equalsIgnoreCase(word.getText());
    }
}
