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
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.util.List;

/**
 * INSERT(base, position, length, insertion). The account plans it as
 * {@code SUBSTR(base, 1, position - 1) || insertion || SUBSTR(base, position + length)} and answers
 * exactly that, SUBSTR's windows included (all live-verified):
 *
 * <pre>
 *   INSERT('abc', 2, 1, 'x')     axc        INSERT('abc', 0, 1, 'x')    xabc
 *   INSERT('abc', 2, -1, 'x')    axabc      INSERT('abc', -2, 1, 'x')   xc      a negative start counts back
 *   INSERT('abc', 5, 1, 'x')     abcx       INSERT('abc', 1, 1, NULL)   NULL    any NULL argument
 *   INSERT(X'6162', 1, 1, X'63') 6362       two binaries splice their BYTES
 * </pre>
 *
 * <p>A BINARY beside another family is judged as that concatenation — see
 * {@link SemiStructuredRejection#INSERT_REWRITE_OPERANDS}.
 */
public class Insert extends TextArgumentFunction {
    public Insert() { super("INSERT", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        final int position = ((Number) args.get(1)).intValue();
        final int length = ((Number) args.get(2)).intValue();
        if (args.get(0) instanceof BinaryValue && args.get(3) instanceof BinaryValue) {
            final byte[] base = ((BinaryValue) args.get(0)).bytes();
            final byte[] insertion = ((BinaryValue) args.get(3)).bytes();
            final int[] head = Substring.window(base.length, 1, position - 1);
            final int[] tail = Substring.window(base.length, position + length, Integer.MAX_VALUE);
            final int headLength = head[1] - head[0];
            final byte[] spliced = new byte[headLength + insertion.length + tail[1] - tail[0]];
            System.arraycopy(base, head[0], spliced, 0, headLength);
            System.arraycopy(insertion, 0, spliced, headLength, insertion.length);
            System.arraycopy(base, tail[0], spliced, headLength + insertion.length, tail[1] - tail[0]);
            return BinaryValue.of(spliced);
        }
        final String base = SharedFunctionHelpers.textOf(args.get(0));
        final int characters = CodePointText.length(base);
        final int[] head = Substring.window(characters, 1, position - 1);
        final int[] tail = Substring.window(characters, position + length, Integer.MAX_VALUE);
        return CodePointText.slice(base, head[0], head[1]) + SharedFunctionHelpers.textOf(args.get(3))
            + CodePointText.slice(base, tail[0], tail[1]);
    }

    @Override
    public int getMinArgCount() { return 4; }
    @Override
    public int getMaxArgCount() { return 4; }

    /** A BINARY anywhere is judged as the concatenation the call is planned as (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.INSERT_REWRITE_OPERANDS;
    }

    /**
     * A BOOLEAN position or length is refused by the arithmetic that reads it (live-verified); a BOOLEAN text
     * or insertion reads as its text.
     */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 1 || position == 2 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
