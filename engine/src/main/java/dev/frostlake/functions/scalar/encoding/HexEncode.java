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

package dev.frostlake.functions.scalar.encoding;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * HEX_ENCODE(expr [, case]) — the hex encoding of the input's bytes, uppercase by default.
 *
 * <p>A BINARY argument contributes its OWN bytes: {@code HEX_ENCODE(TO_BINARY('48454C','HEX'))} is
 * {@code 48454C}. Encoding {@code toString()} instead would encode the hex RENDERING and yield
 * {@code 343834353443} — valid-looking hex, entirely wrong.
 *
 * <p>The optional second argument chooses the case: 1 (the default) upper, 0 lower. It takes no other
 * value — the account answers "Numeric value is out of range, error: 2" at ROW time, not a
 * compile-time refusal.
 */
public class HexEncode extends BuiltInFunction {

    // Digit-table loop — String.format("%02X", b) parsed a format string PER BYTE.
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();
    private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();
    public HexEncode() { super("HEX_ENCODE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final char[] digits = args.size() > 1 && args.get(1) != null
            ? digitsFor(args.get(1)) : HEX_UPPER;
        final byte[] bytes = SharedFunctionHelpers.toUtf8(args.get(0));
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(digits[(b >> 4) & 0xF]).append(digits[b & 0xF]);
        }
        return sb.toString();
    }

    /** The digit table the case flag selects; anything but 0 or 1 is out of range. */
    private char[] digitsFor(final Object caseFlag) {
        final int chosen = new java.math.BigDecimal(caseFlag.toString()).intValue();
        if (chosen == 1) {
            return HEX_UPPER;
        }
        if (chosen == 0) {
            return HEX_LOWER;
        }
        throw new RuntimeException("Numeric value is out of range, error: " + chosen);
    }

    /** A VECTOR is refused by its argument type, in every position (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
