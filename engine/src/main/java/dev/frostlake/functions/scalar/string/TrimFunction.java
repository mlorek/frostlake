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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;
import dev.frostlake.values.CodePointText;

import java.util.List;

/**
 * TRIM, LTRIM and RTRIM ({@code expr [, chars]}): the text with every character in the set (default whitespace) cut
 * from the ends each names.
 */
public abstract class TrimFunction extends TextArgumentFunction {

    /** Whether the start is trimmed. */
    private final boolean leading;
    /** Whether the end is trimmed. */
    private final boolean trailing;

    protected TrimFunction(final String name, final boolean leading, final boolean trailing) {
        super(name, StringType.VARCHAR);
        this.leading = leading;
        this.trailing = trailing;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final String str = SharedFunctionHelpers.textOf(args.get(0));
        final String chars = args.size() > 1 ? SharedFunctionHelpers.textOf(args.get(1)) : null;
        final int start = leading ? CodePointText.trimmedStart(str, chars) : 0;
        return str.substring(start, trailing ? CodePointText.trimmedEnd(str, chars, start) : str.length());
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
