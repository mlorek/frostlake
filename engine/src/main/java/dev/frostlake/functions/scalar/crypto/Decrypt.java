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

package dev.frostlake.functions.scalar.crypto;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Decrypt extends BuiltInFunction {
    public Decrypt() { super("DECRYPT", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        try {
            final byte[] key = Arrays.copyOf(
                SharedFunctionHelpers.digest("SHA-256", SharedFunctionHelpers.toUtf8(args.get(1))), 32);
            final byte[] combined = SharedFunctionHelpers.binaryArgBytes(args.get(0), "DECRYPT");
            final byte[] iv  = Arrays.copyOf(combined, 12);
            final byte[] enc = Arrays.copyOfRange(combined, 12, combined.length);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv));
            // Snowflake's DECRYPT returns BINARY (cast the result to VARCHAR to read text).
            return BinaryValue.of(cipher.doFinal(enc));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("DECRYPT failed: " + e.getMessage());
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
