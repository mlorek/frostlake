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
import dev.frostlake.functions.scalar.WholeNumberArgument;
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
    public Position() { this("POSITION"); }

    /** POSITION under another name, CHARINDEX's. */
    protected Position(final String name) { super(name, IntegerResultWidths.POSITION); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final int startPos = startOf(args);
        if (args.get(0) instanceof BinaryValue && args.get(1) instanceof BinaryValue) {
            return SharedFunctionHelpers.indexOfBytes(((BinaryValue) args.get(1)).bytes(),
                ((BinaryValue) args.get(0)).bytes(), startPos);
        }
        final String needle = SharedFunctionHelpers.textOf(args.get(0));
        final String haystack = SharedFunctionHelpers.textOf(args.get(1));
        final int idx = CodePointText.indexOf(haystack, needle, startPos);
        return idx < 0 ? 0L : (long) (idx + 1);
    }

    /** Under a collation the needle is found where the collation matches it, not byte for byte. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        // A NULL in any argument answers NULL, a collated search's too (live-verified).
        if (anyNull(args)) {
            return null;
        }
        if (collation == null || args.get(0) instanceof BinaryValue || args.get(1) instanceof BinaryValue) {
            return evaluate(args);
        }
        final int startPos = startOf(args);
        final String haystack = SharedFunctionHelpers.textOf(args.get(1));
        final int[] found = CollationMatching.findUnder(collation, haystack,
            SharedFunctionHelpers.textOf(args.get(0)), CodePointText.offset(haystack, startPos));
        return found == null ? 0L : (long) (CodePointText.characterIndex(haystack, found[0]) + 1);
    }

    /**
     * The 0-based character the search starts at: the start is a 32-bit whole number (see WholeNumberArgument#part).
     */
    private static int startOf(final List<Object> args) {
        return args.size() > 2 ? (int) Math.max(0L, WholeNumberArgument.part(args.get(2)) - 1L) : 0;
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
