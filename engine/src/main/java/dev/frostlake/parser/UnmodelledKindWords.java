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
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The plain words among those of the object kinds the account drops and describes that Frostlake models no object
 * of. {@code SNAPSHOT}, {@code CONNECTION}, {@code DATASET} and {@code MODEL} name a kind alone, and
 * {@code LIFECYCLE} and {@code PROJECT} follow the keywords {@code STORAGE} and {@code DBT} in the grammar's
 * {@code unmodelledKind}. A word is matched whatever its case and never in quotes (live-verified).
 */
public final class UnmodelledKindWords {

    /**
     * The kinds one plain word names: a DESCRIBE reads a signature and properties after their object's name, as it
     * does after a kind named by keywords, and a DROP a signature.
     */
    private static final String[] DESCRIBED = {"SNAPSHOT", "CONNECTION", "DATASET", "MODEL"};

    private UnmodelledKindWords() {
    }

    /**
     * Whether the class a DROP names is a kind the account reads a signature after the instance of: the words a
     * DESCRIBE reads its tail after.
     *
     * @param name the class name, or null
     * @return whether a signature may follow the instance's name
     */
    public static boolean takesSignature(final FrostlakeParser.ClassNameContext name) {
        return name != null && readsDescribeTail(name.IDENTIFIER());
    }

    /**
     * Whether the plain word a DESCRIBE names its kind by is one the account reads a signature and properties
     * after.
     *
     * @param word the word's node, or null
     * @return whether it is such a word
     */
    public static boolean readsDescribeTail(final TerminalNode word) {
        if (word == null) {
            return false;
        }
        for (final String kind : DESCRIBED) {
            if (is(word.getSymbol(), kind)) {
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
