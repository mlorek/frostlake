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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;
import dev.frostlake.values.LobSize;

import java.util.List;

public class Replace extends TextArgumentFunction {
    public Replace() {
        super("REPLACE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        if (args.get(0) == null) return null;
        final String str = SharedFunctionHelpers.textOf(args.get(0));
        final String search = args.get(1) != null ? SharedFunctionHelpers.textOf(args.get(1)) : "";
        final String replacement = args.size() > 2 && args.get(2) != null
            ? SharedFunctionHelpers.textOf(args.get(2)) : "";
        // An empty text is found nowhere: the subject is as it was (live-verified).
        if (search.isEmpty()) {
            return str;
        }
        requireFits(str, search, replacement);
        return str.replace(search, replacement);
    }

    /**
     * Refuse a replacement whose result would be longer than a text can be, before any of it is built: a longer
     * replacement multiplies the subject's length by its occurrences, which are counted first.
     */
    private static void requireFits(final String str, final String search, final String replacement) {
        final int grows = replacement.length() - search.length();
        if (grows <= 0) {
            return;
        }
        long occurrences = 0;
        for (int at = str.indexOf(search); at >= 0; at = str.indexOf(search, at + search.length())) {
            occurrences++;
        }
        if (str.length() + occurrences * grows <= LobSize.MAX_TEXT_BYTES) {
            return;
        }
        final long characters = str.codePointCount(0, str.length()) + occurrences
            * (replacement.codePointCount(0, replacement.length()) - search.codePointCount(0, search.length()));
        if (characters <= LobSize.MAX_TEXT_BYTES) {
            return;
        }
        throw LobSize.tooLongNamed("REPLACE result");
    }

    /** Under a collation every substring the collation calls equal to the search text is replaced. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        if (collation == null || args.get(0) == null) {
            return evaluate(args);
        }
        return CollationMatching.replaceUnder(collation, SharedFunctionHelpers.textOf(args.get(0)),
            args.get(1) != null ? SharedFunctionHelpers.textOf(args.get(1)) : "",
            args.size() > 2 && args.get(2) != null ? SharedFunctionHelpers.textOf(args.get(2)) : "");
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 3; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
