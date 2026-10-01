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
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.util.Arrays;
import java.util.List;

/**
 * SUBSTRING(expr, start [, length]) — a slice of a VARCHAR by characters or of a BINARY by BYTES.
 *
 * <p>A BINARY input is sliced over its own bytes and yields BINARY:
 * {@code SUBSTR(TO_BINARY('48454C4C4F','HEX'), 2, 2)} is {@code 454C}. Slicing the hex rendering
 * instead returned {@code 84} — half of each of two different bytes.
 */
public class Substring extends TextArgumentFunction {
    public Substring() {
        super("SUBSTRING", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        final int start = ((Number) args.get(1)).intValue();
        final int length = args.size() == 3 && args.get(2) != null
            ? ((Number) args.get(2)).intValue() : Integer.MAX_VALUE;
        if (value instanceof BinaryValue) {
            final byte[] bytes = ((BinaryValue) value).bytes();
            final int[] window = window(bytes.length, start, length);
            return BinaryValue.of(Arrays.copyOfRange(bytes, window[0], window[1]));
        }
        final String str = value.toString();
        final int[] window = window(CodePointText.length(str), start, length);
        return CodePointText.slice(str, window[0], window[1]);
    }

    /**
     * The 0-based {@code [from, to)} window Snowflake selects, live-verified: a start of 0
     * behaves as 1 ({@code SUBSTR('hello',0,2)} is {@code he}), a negative start counts back from the
     * end ({@code SUBSTR('hello',-2,2)} is {@code lo}), a window falling entirely outside the value is
     * empty rather than clamped ({@code SUBSTR('hello',-99,2)} and {@code SUBSTR('hello',9,2)} are both
     * empty), and a negative length is empty too ({@code SUBSTR('hello',2,-1)}). INSERT cuts with the
     * same windows, being planned as two SUBSTRs.
     *
     * @param total  the value's length in its own units — characters, or bytes for a BINARY
     * @param start  the 1-based start as written
     * @param length the length as written, {@link Integer#MAX_VALUE} for "to the end"
     * @return the {@code [from, to)} window
     */
    static int[] window(final int total, final int start, final int length) {
        final long first = start == 0 ? 1L : (start > 0 ? start : (long) total + start + 1L);
        final long lastExclusive = length == Integer.MAX_VALUE
            ? (long) total + 1L : first + Math.max(length, 0);
        final int from = (int) Math.min(Math.max(first, 1L), total + 1L);
        final int to = (int) Math.min(Math.max(lastExclusive, 1L), total + 1L);
        return new int[] { from - 1, Math.max(to - 1, from - 1) };
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 3; }

    /**
     * A BOOLEAN start or length is refused by the argument types as the call compiles, where a BOOLEAN
     * string is read as its text: SUBSTR('abc', TRUE) is 'SUBSTR': (VARCHAR(3), BOOLEAN) (live-verified).
     */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position >= 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
