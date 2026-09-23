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

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;

import java.util.Arrays;
import java.util.List;

/**
 * RPAD(expr, len [, pad]) — a VARCHAR padded to len characters, or a BINARY padded to len BYTES.
 *
 * <p>{@code RPAD(TO_BINARY('4845','HEX'), 5, TO_BINARY('0102','HEX'))} is {@code 4845010201}: the pad
 * pattern repeats byte-wise and the result is BINARY.
 */
public class RPad extends TextArgumentFunction {
    public RPad() {
        super("RPAD", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof BinaryValue) {
            return padBytes((BinaryValue) args.get(0), args);
        }
        final String str = args.get(0).toString();
        final int targetLength = ((Number) args.get(1)).intValue();
        final String padStr = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : " ";

        // Live-verified: an input longer than the target length is TRUNCATED to it (RPAD('world', 3, '*')
        // is 'wor'), not returned unchanged.
        // Lengths count characters, a supplementary one included: RPAD('😀', 3, 'x') is 😀xx.
        final int characters = CodePointText.length(str);
        if (characters >= targetLength) return CodePointText.slice(str, 0, Math.max(targetLength, 0));
        if (padStr.isEmpty()) return str;
        return str + CodePointText.repeatTo(padStr, targetLength - characters);
    }

    /** The BINARY form: pad bytes on the right, truncating to the leading bytes when already longer. */
    private Object padBytes(final BinaryValue value, final List<Object> args) {
        final byte[] bytes = value.bytes();
        final int targetLength = ((Number) args.get(1)).intValue();
        if (bytes.length >= targetLength) {
            return BinaryValue.of(Arrays.copyOfRange(bytes, 0, Math.max(targetLength, 0)));
        }
        final byte[] pad = args.size() > 2 && args.get(2) != null
            ? SharedFunctionHelpers.toUtf8(args.get(2)) : new byte[] { (byte) ' ' };
        if (pad.length == 0) {
            return value;
        }
        final byte[] padded = new byte[targetLength];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        for (int i = bytes.length; i < targetLength; i++) {
            padded[i] = pad[(i - bytes.length) % pad.length];
        }
        return BinaryValue.of(padded);
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 3; }
}
