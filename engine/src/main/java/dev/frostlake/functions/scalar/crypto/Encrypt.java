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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;

/**
 * ENCRYPT(value, passphrase [, aad [, encryption_method]]) — AES encryption of the value (a string's UTF-8 bytes or
 * a BINARY value's bytes) under a passphrase-derived key, in the method's mode (AES-GCM by default). Returns the
 * method's IV followed by the ciphertext as BINARY — for GCM the ciphertext carries its tag — which is what
 * {@link Decrypt} reads back. The third argument is always the additional authenticated data, bound into a GCM
 * tag and refused by any other mode, and the fourth the method (see {@link EncryptionMethod}).
 */
public class Encrypt extends BuiltInFunction {
    public Encrypt() { super("ENCRYPT", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final EncryptionMethod method = EncryptionMethod.of(args.size() >= 4 ? args.get(3) : null);
        final Object aad = args.size() >= 3 ? args.get(2) : null;
        method.requireAadSupport(aad != null);
        final byte[] value = SharedFunctionHelpers.toUtf8(args.get(0));
        method.requireWholeBlocks(value.length);
        try {
            final byte[] key = Arrays.copyOf(
                SharedFunctionHelpers.digest("SHA-256", SharedFunctionHelpers.toUtf8(args.get(1))), 32);
            final byte[] iv = new byte[method.mode().ivBytes()];
            new SecureRandom().nextBytes(iv);
            final Cipher cipher = method.cipher(Cipher.ENCRYPT_MODE, key, iv);
            if (aad != null) {
                cipher.updateAAD(SharedFunctionHelpers.toUtf8(aad));
            }
            final byte[] enc = cipher.doFinal(value);
            final byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return BinaryValue.of(combined);
        } catch (final GeneralSecurityException e) {
            throw new RuntimeException("ENCRYPT failed: " + e.getMessage());
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 4; }
}
