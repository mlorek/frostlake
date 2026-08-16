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
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DECRYPT_RAW(value, key, iv [, additional_authenticated_data [, encryption_method [, aead_tag]]]) —
 * reverses {@link EncryptRaw}. The optionals nest from the LEFT (live-measured):
 * the fourth argument is ALWAYS the AAD, the fifth the method, and the AEAD tag sits strictly SIXTH —
 * a four-argument call putting the tag fourth fails "Decryption mode requires an AEAD tag as
 * parameter", exactly as any GCM call that never reaches the sixth argument does, and a sixth argument
 * of the wrong length fails "Wrong AEAD tag size. Expected 16, but got N" (both messages verbatim).
 * All BINARY arguments are hex strings; the returned decrypted value is BINARY (hex). Only AES-GCM is
 * supported. A wrong key, IV, AAD or tag fails authentication and raises, exactly as Snowflake's GCM
 * verification does.
 */
public class DecryptRaw extends BuiltInFunction {

    public DecryptRaw() { super("DECRYPT_RAW", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null) {
            return null;
        }
        final int n = args.size();
        final Object aadArg = n >= 4 ? args.get(3) : null;
        final Object methodArg = n >= 5 ? args.get(4) : null;
        final Object tagArg = n >= 6 ? args.get(5) : null;
        RawCipherSupport.requireGcm("DECRYPT_RAW", methodArg);
        if (tagArg == null) {
            throw new RuntimeException("Decryption mode requires an AEAD tag as parameter");
        }
        final byte[] tag = RawCipherSupport.binaryBytes("DECRYPT_RAW", tagArg);
        if (tag.length != RawCipherSupport.GCM_TAG_BITS / 8) {
            throw new RuntimeException("Wrong AEAD tag size. Expected "
                + (RawCipherSupport.GCM_TAG_BITS / 8) + ", but got " + tag.length);
        }
        try {
            final byte[] ciphertext = RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(0));
            final byte[] key = RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(1));
            final byte[] iv = RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(2));
            // Java's GCM cipher expects the tag appended to the ciphertext; reassemble the two.
            final byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(RawCipherSupport.GCM_TAG_BITS, iv));
            if (aadArg != null) {
                cipher.updateAAD(RawCipherSupport.binaryBytes("DECRYPT_RAW", aadArg));
            }
            return BinaryValue.of(cipher.doFinal(combined));
        } catch (final Exception e) {
            throw new RuntimeException("DECRYPT_RAW failed: " + e.getMessage());
        }
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 6; }
}
