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

import java.util.Arrays;
import java.util.List;

/**
 * LPAD(expr, len [, pad]) — a VARCHAR padded to len characters, or a BINARY padded to len BYTES.
 *
 * <p>{@code LPAD(TO_BINARY('4845','HEX'), 5, TO_BINARY('0102','HEX'))} is {@code 0102014845}: the pad
 * pattern repeats byte-wise and the result is BINARY.
 */
public class LPad extends TextArgumentFunction {
    public LPad() {
        super("LPAD", StringType.VARCHAR);
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

        // Live-verified: an input longer than the target length is TRUNCATED to it (LPAD('world', 3, '*')
        // is 'wor'), not returned unchanged.
        if (str.length() >= targetLength) return str.substring(0, Math.max(targetLength, 0));
        if (padStr.isEmpty()) return str;

        final int padLength = targetLength - str.length();
        final StringBuilder result = new StringBuilder();

        while (result.length() < padLength) {
            result.append(padStr);
        }
        result.setLength(padLength);
        result.append(str);

        return result.toString();
    }

    /** The BINARY form: pad bytes on the left, truncating to the leading bytes when already longer. */
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
        final int padLength = targetLength - bytes.length;
        final byte[] padded = new byte[targetLength];
        for (int i = 0; i < padLength; i++) {
            padded[i] = pad[i % pad.length];
        }
        System.arraycopy(bytes, 0, padded, padLength, bytes.length);
        return BinaryValue.of(padded);
    }

    @Override
    public int getMinArgCount() { return 2; }

    @Override
    public int getMaxArgCount() { return 3; }
}
