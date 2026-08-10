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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * HEX_ENCODE(expr) — the uppercase hex encoding of the input's bytes.
 *
 * <p>A BINARY argument contributes its OWN bytes: {@code HEX_ENCODE(TO_BINARY('48454C','HEX'))} is
 * {@code 48454C}. Encoding {@code toString()} instead would encode the hex RENDERING and yield
 * {@code 343834353443} — valid-looking hex, entirely wrong.
 */
public class HexEncode extends BuiltInFunction {

    // Digit-table loop — String.format("%02X", b) parsed a format string PER BYTE.
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();
    public HexEncode() { super("HEX_ENCODE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final byte[] bytes = SharedFunctionHelpers.toUtf8(args.get(0));
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(HEX_UPPER[(b >> 4) & 0xF]).append(HEX_UPPER[b & 0xF]);
        }
        return sb.toString();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
