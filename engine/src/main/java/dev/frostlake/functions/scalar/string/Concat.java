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

import java.util.List;

public class Concat extends TextArgumentFunction {
    public Concat() {
        super("CONCAT", StringType.VARCHAR);
    }


    /**
     * CONCAT refuses a VECTOR in ANY position, listing the argument types and carrying a position —
     * the other of the two shapes a vector-to-text refusal takes. The {@code ||} spelling of the same
     * operation is refused by the operator's own rule, which reports the function as '||'.
     */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: CONCAT returns NULL if ANY input is NULL (skipping them silently turned a missed
        // lookup's NULL prefix into ':' and the value survived where Snowflake yields NULL).
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        // All-BINARY inputs concatenate BYTE-wise and yield BINARY, so a downstream HEX_ENCODE /
        // LENGTH / SUBSTR still sees bytes rather than the hex rendering.
        if (isAllBinary(args)) {
            return concatBytes(args);
        }
        final StringBuilder result = new StringBuilder();
        for (final Object arg : args) {
            result.append(SharedFunctionHelpers.textOf(arg));
        }
        return result.toString();
    }

    private boolean isAllBinary(final List<Object> args) {
        if (args.isEmpty()) {
            return false;
        }
        for (final Object arg : args) {
            if (!(arg instanceof BinaryValue)) {
                return false;
            }
        }
        return true;
    }

    private BinaryValue concatBytes(final List<Object> args) {
        int total = 0;
        for (final Object arg : args) {
            total += ((BinaryValue) arg).length();
        }
        final byte[] joined = new byte[total];
        int at = 0;
        for (final Object arg : args) {
            final byte[] bytes = ((BinaryValue) arg).bytes();
            System.arraycopy(bytes, 0, joined, at, bytes.length);
            at += bytes.length;
        }
        return BinaryValue.of(joined);
    }

    @Override
    public int getMinArgCount() { return 1; }

    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
