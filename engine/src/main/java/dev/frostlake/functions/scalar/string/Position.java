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
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.util.List;

/**
 * POSITION(needle, haystack [, start]) — the 1-based position of needle, or 0 when absent.
 *
 * <p>Two BINARY arguments are searched BYTE-wise:
 * {@code POSITION(TO_BINARY('45','HEX'), TO_BINARY('48454C','HEX'))} is 2 — the second byte — where
 * searching the hex renderings found the digit pair at character 3.
 */
public class Position extends TextArgumentFunction {
    public Position() { super("POSITION", IntegerResultWidths.POSITION); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — a NULL haystack or needle yields NULL, not 0/false
        for (int i = 0; i < Math.min(args.size(), 2); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        if (args.get(0) == null || args.get(1) == null) return 0L;
        int startPos = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() - 1 : 0;
        startPos = Math.max(0, startPos);
        if (args.get(0) instanceof BinaryValue && args.get(1) instanceof BinaryValue) {
            return SharedFunctionHelpers.indexOfBytes(((BinaryValue) args.get(1)).bytes(),
                ((BinaryValue) args.get(0)).bytes(), startPos);
        }
        final String needle = args.get(0).toString();
        final String haystack = args.get(1).toString();
        final int idx = CodePointText.indexOf(haystack, needle, startPos);
        return idx < 0 ? 0L : (long) (idx + 1);
    }

    /** Under a collation the needle is found where the collation matches it, not byte for byte. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.get(0) == null || args.get(1) == null
                || args.get(0) instanceof BinaryValue || args.get(1) instanceof BinaryValue) {
            return evaluate(args);
        }
        int startPos = args.size() > 2 && args.get(2) != null ? ((Number) args.get(2)).intValue() - 1 : 0;
        startPos = Math.max(0, startPos);
        final String haystack = args.get(1).toString();
        final int[] found = CollationMatching.findUnder(collation, haystack,
            args.get(0).toString(), CodePointText.offset(haystack, startPos));
        return found == null ? 0L : (long) (CodePointText.characterIndex(haystack, found[0]) + 1);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A BOOLEAN start position is refused by the argument types as the call compiles (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 2 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
