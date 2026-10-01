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
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;

/**
 * DECRYPT(value, passphrase [, aad [, encryption_method]]) — reads back what ENCRYPT wrote in the method's mode
 * (AES-GCM by default): the mode's IV, then the ciphertext, under the passphrase-derived key. Returns BINARY.
 * The checks run in live's order (live-verified):
 *
 * <ol>
 *   <li>the method, as {@link EncryptionMethod} reads it;</li>
 *   <li>input too short to hold the mode's IV — and for GCM its 16-byte tag — is not the method's input: 27
 *       bytes under GCM, 15 under CBC, CTR, OFB or CFB;</li>
 *   <li>an AAD beside a mode other than GCM is refused;</li>
 *   <li>an unpadded block mode refuses a ciphertext that does not fill whole blocks;</li>
 *   <li>and a tag that does not verify, or padding that does not read, is a failed decryption — as are a wrong
 *       passphrase and a tampered ciphertext. CTR, OFB and CFB verify nothing, so they answer bytes.</li>
 * </ol>
 */
public class Decrypt extends BuiltInFunction {

    /** Live's refusal for input that cannot hold the mode's IV and tag. */
    static final String MALFORMED_INPUT =
        "Encrypted data input does not comply with the expected input of the selected encryption method";

    /** Live's refusal for input whose tag does not verify. */
    static final String DECRYPTION_FAILED = "Decryption failed. Check encrypted data, key, AAD, or AEAD tag.";

    public Decrypt() { super("DECRYPT", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final EncryptionMethod method = EncryptionMethod.of(args.size() >= 4 ? args.get(3) : null);
        final byte[] combined = SharedFunctionHelpers.binaryArgBytes(args.get(0), "DECRYPT");
        if (combined.length < method.minimumInput()) {
            throw new RuntimeException(MALFORMED_INPUT);
        }
        final Object aad = args.size() >= 3 ? args.get(2) : null;
        method.requireAadSupport(aad != null);
        final int ivBytes = method.mode().ivBytes();
        method.requireWholeBlocks(combined.length - ivBytes);
        if (method.isPadded() && combined.length == ivBytes) {
            // A padded block mode needs at least the one block its padding writes.
            throw new RuntimeException(DECRYPTION_FAILED);
        }
        final byte[] key = Arrays.copyOf(
            SharedFunctionHelpers.digest("SHA-256", SharedFunctionHelpers.toUtf8(args.get(1))), 32);
        final Cipher cipher;
        try {
            cipher = method.cipher(Cipher.DECRYPT_MODE, key, combined);
        } catch (final GeneralSecurityException unavailable) {
            throw new IllegalStateException("AES is unavailable", unavailable);
        }
        try {
            if (aad != null) {
                cipher.updateAAD(SharedFunctionHelpers.toUtf8(aad));
            }
            // Snowflake's DECRYPT returns BINARY (cast the result to VARCHAR to read text).
            return BinaryValue.of(cipher.doFinal(combined, ivBytes, combined.length - ivBytes));
        } catch (final GeneralSecurityException failed) {
            throw new RuntimeException(DECRYPTION_FAILED);
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 4; }
}
