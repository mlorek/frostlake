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
import dev.frostlake.types.StringType;

import java.util.List;

public class TryHexDecode extends BuiltInFunction {
    public TryHexDecode() { super("TRY_HEX_DECODE_STRING", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        try {
            final String hex = args.get(0).toString();
            if (hex.length() % 2 != 0) return null;
            final byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(2*i, 2*i+2), 16);
            return DecodedText.lenient(bytes);
        } catch (final Exception e) { return null; }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
