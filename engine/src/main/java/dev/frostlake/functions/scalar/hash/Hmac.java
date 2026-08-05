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

package dev.frostlake.functions.scalar.hash;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hmac extends BuiltInFunction {
    public Hmac() { super("HMAC", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        // A BINARY message or key contributes its OWN bytes, not its hex rendering.
        final byte[] msg = SharedFunctionHelpers.toUtf8(args.get(0));
        final byte[] key = SharedFunctionHelpers.toUtf8(args.get(1));
        final String algo = args.size() > 2 && args.get(2) != null
            ? "HmacSHA" + args.get(2).toString().toUpperCase().replace("SHA", "").replace("-", "")
            : "HmacSHA256";
        try {
            final Mac mac = Mac.getInstance(algo);
            mac.init(new SecretKeySpec(key, algo));
            return SharedFunctionHelpers.toHex(mac.doFinal(msg));
        } catch (final Exception e) {
            throw new RuntimeException("HMAC failed: " + e.getMessage());
        }
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
