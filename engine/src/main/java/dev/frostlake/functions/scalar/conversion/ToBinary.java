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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * TO_BINARY(string [, format]) — decodes the input string into a {@link BinaryValue} using
 * {@code format} (default HEX). HEX: the input is hexadecimal digits; BASE64: base64-decoded;
 * UTF-8: the input's UTF-8 bytes. A binary input passes through unchanged. NULL → NULL.
 */
public class ToBinary extends BuiltInFunction {
    public ToBinary() { super("TO_BINARY", BinaryType.BINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        if (args.get(0) instanceof BinaryValue) {
            return args.get(0);
        }
        final String input = args.get(0).toString();
        final BinaryFormat format = args.size() > 1 && args.get(1) != null
            ? BinaryFormat.fromString(args.get(1).toString()) : BinaryFormat.HEX;
        if (format == null) {
            throw new RuntimeException("Unsupported TO_BINARY format: " + args.get(1).toString().toUpperCase()
                + " (expected HEX, BASE64, or UTF-8)");
        }
        final byte[] bytes;
        switch (format) {
            case HEX:
                bytes = hexToBytes(input);
                break;
            case BASE64:
                bytes = Base64.getDecoder().decode(input);
                break;
            case UTF8:
                bytes = input.getBytes(StandardCharsets.UTF_8);
                break;
            default:
                throw new IllegalStateException("Unhandled TO_BINARY format: " + format);
        }
        return BinaryValue.of(bytes);
    }

    private static byte[] hexToBytes(final String hex) {
        if (hex.length() % 2 != 0) {
            throw new RuntimeException("Invalid hex string length: " + hex);
        }
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
