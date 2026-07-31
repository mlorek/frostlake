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
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;

import java.util.List;

/**
 * REVERSE(expr) — a VARCHAR reversed by characters, or a BINARY reversed by BYTES.
 *
 * <p>{@code REVERSE(TO_BINARY('AABBCC','HEX'))} is {@code CCBBAA}; reversing the hex rendering
 * instead swapped the nibbles within each byte too.
 */
public class Reverse extends TextArgumentFunction {
    public Reverse() {
        super("REVERSE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        if (value instanceof BinaryValue) {
            final byte[] source = ((BinaryValue) value).bytes();
            final byte[] reversed = new byte[source.length];
            for (int i = 0; i < source.length; i++) {
                reversed[i] = source[source.length - 1 - i];
            }
            return BinaryValue.of(reversed);
        }
        return new StringBuilder(value.toString()).reverse().toString();
    }

    @Override
    public int getMinArgCount() { return 1; }

    @Override
    public int getMaxArgCount() { return 1; }
}
