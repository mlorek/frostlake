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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;

public class InitCap extends TextArgumentFunction {

    // Snowflake's default INITCAP delimiter set: whitespace plus this fixed punctuation set. A word
    // boundary is any of these characters; the first letter after one is capitalized, the rest lowercased.
    private static final String DEFAULT_DELIMITERS = " \t\n\r\f!?@\"^#$&~_,.:;+-*%/|\\[](){}<>";

    public InitCap() {
        super("INITCAP", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        if (str.isEmpty()) return str;

        // A supplied second argument overrides the default delimiter set entirely; an empty string means
        // there are NO delimiters, so the whole input is one word — capitalize the first letter, lowercase
        // the rest. (These characters are matched literally via indexOf, not as a regex.)
        final String delimiters = args.size() > 1 && args.get(1) != null
            ? args.get(1).toString() : DEFAULT_DELIMITERS;

        final StringBuilder result = new StringBuilder(str.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if (delimiters.indexOf(c) >= 0) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    @Override
    public int getMinArgCount() { return 1; }

    @Override
    public int getMaxArgCount() { return 2; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
