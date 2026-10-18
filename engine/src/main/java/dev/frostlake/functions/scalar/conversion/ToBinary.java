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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.executor.SessionInputFormats;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TO_BINARY(string [, format]) — decodes the input string into a {@link BinaryValue} using {@code format}: HEX, the
 * input's hex digits, a space anywhere passed over; BASE64, whole padded groups of four, a space anywhere passed over;
 * UTF-8 (also UTF8), the input's UTF-8 bytes. With no format the session's BINARY_INPUT_FORMAT reads it, HEX unless
 * set, and a format that is none of these is refused, {@code Invalid binary format string 'FOO'} (live-verified). A
 * binary input passes through unchanged. NULL → NULL.
 */
public class ToBinary extends BuiltInFunction {
    public ToBinary() { super("TO_BINARY", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        if (args.get(0) instanceof BinaryValue) {
            return args.get(0);
        }
        final String input = args.get(0).toString();
        if (args.size() < 2) {
            return SessionInputFormats.binary(input);
        }
        final BinaryFormat format = args.get(1) != null
            ? BinaryFormat.fromString(args.get(1).toString()) : BinaryFormat.HEX;
        if (format == null) {
            throw new RuntimeException("Invalid binary format string '" + args.get(1) + "'");
        }
        switch (format) {
            case HEX:
                return BinaryValue.fromHexInput(input);
            case BASE64:
                return BinaryValue.fromBase64Input(input);
            case UTF8:
                return BinaryValue.of(input.getBytes(StandardCharsets.UTF_8));
            default:
                throw new IllegalStateException("Unhandled TO_BINARY format: " + format);
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
