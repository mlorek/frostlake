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
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.util.List;

/** BASE64_DECODE_BINARY(string) — decodes a base64 string into a BINARY value. */
public class Base64DecodeBinary extends BuiltInFunction {
    public Base64DecodeBinary() { super("BASE64_DECODE_BINARY", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String text = args.get(0).toString();
        try {
            return BinaryValue.fromBase64(
                Base64Options.toStandardAlphabet(text, Base64Options.alphabetOf(args, 1)));
        } catch (final IllegalArgumentException notBase64) {
            throw Base64Options.notBase64(text);
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
