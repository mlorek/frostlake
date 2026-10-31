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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.functions.CollationMatching;

/** ENDSWITH(text, suffix) — whether the text ends with the suffix. */
public class EndsWith extends TextMatchFunction {
    public EndsWith() { super("ENDSWITH"); }

    @Override
    protected boolean holds(final String text, final String needle) {
        return text.endsWith(needle);
    }

    /** Under a collation the suffix is the one the collation matches, not the byte-for-byte one. */
    @Override
    protected boolean holdsUnder(final CollationSpec collation, final String text, final String needle) {
        return CollationMatching.endsWithUnder(collation, text, needle);
    }
}
