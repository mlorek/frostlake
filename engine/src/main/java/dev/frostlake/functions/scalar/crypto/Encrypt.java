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
import java.security.SecureRandom;

import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * ENCRYPT(value, passphrase [, aad]) — AES-GCM encryption of the value (a string's UTF-8 bytes or
 * a BINARY value's bytes) under a passphrase-derived key. Returns {@code iv || ciphertext} as
 * BINARY, matching Snowflake's return type.
 */
public class Encrypt extends BuiltInFunction {
    public Encrypt() { super("ENCRYPT", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        try {
            final byte[] key = Arrays.copyOf(
                SharedFunctionHelpers.digest("SHA-256", SharedFunctionHelpers.toUtf8(args.get(1))), 32);
            final byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv));
            final byte[] enc = cipher.doFinal(SharedFunctionHelpers.toUtf8(args.get(0)));
            final byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return BinaryValue.of(combined);
        } catch (final Exception e) {
            throw new RuntimeException("ENCRYPT failed: " + e.getMessage());
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
