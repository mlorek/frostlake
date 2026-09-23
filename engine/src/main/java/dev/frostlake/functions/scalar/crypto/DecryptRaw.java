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

/**
 * DECRYPT_RAW(value, key, iv [, additional_authenticated_data [, encryption_method [, aead_tag]]]) —
 * reverses {@link EncryptRaw}, in any mode the method names. The optionals nest from the LEFT
 * (live-measured): the fourth argument is ALWAYS the AAD, the fifth the method, and the AEAD tag sits
 * strictly SIXTH — a four-argument call putting the tag fourth fails "Decryption mode requires an AEAD
 * tag as parameter", exactly as any authenticating call that never reaches the sixth argument does, and
 * a sixth argument of the wrong length fails "Wrong AEAD tag size. Expected 16, but got N" (both
 * messages verbatim). A mode that does not authenticate needs no tag.
 *
 * <p>All BINARY arguments are hex strings; the returned decrypted value is BINARY (hex). Anything the
 * cipher itself refuses — a wrong key, IV, AAD or tag, a ciphertext whose padding does not read — is one
 * sentence: "Decryption failed. Check encrypted data, key, AAD, or AEAD tag."
 */
public class DecryptRaw extends BuiltInFunction {

    public DecryptRaw() { super("DECRYPT_RAW", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Only the value and the key make the answer NULL: a mode that takes no IV reads a NULL one as
        // none at all, and a mode that takes one reads it as an IV that will not match.
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final int n = args.size();
        final Object aadArg = n >= 4 ? args.get(3) : null;
        final Object methodArg = n >= 5 ? args.get(4) : null;
        final Object tagArg = n >= 6 ? args.get(5) : null;
        // Live's order: the method, the key's size, the IV's size, whether the mode takes AAD, then the tag.
        final EncryptionMethod method = EncryptionMethod.of(methodArg);
        RawCipherSupport.requireKeySize(RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(1)));
        if (args.get(2) != null) {
            RawCipherSupport.requireIvSize(method, RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(2)));
        }
        method.requireAadSupport(aadArg != null);
        byte[] tag = null;
        if (method.mode().isAead()) {
            if (tagArg == null) {
                throw new RuntimeException("Decryption mode requires an AEAD tag as parameter");
            }
            tag = RawCipherSupport.binaryBytes("DECRYPT_RAW", tagArg);
            if (tag.length != RawCipherSupport.GCM_TAG_BYTES) {
                throw new RuntimeException("Wrong AEAD tag size. Expected "
                    + RawCipherSupport.GCM_TAG_BYTES + ", but got " + tag.length);
            }
        }
        try {
            final byte[] ciphertext = RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(0));
            final byte[] key = RawCipherSupport.binaryBytes("DECRYPT_RAW", args.get(1));
            final byte[] iv = RawCipherSupport.ivOrDrawn("DECRYPT_RAW", method, args.get(2));
            byte[] input = ciphertext;
            if (tag != null) {
                // Java's authenticated ciphers expect the tag appended to the ciphertext; reassemble the two.
                input = new byte[ciphertext.length + tag.length];
                System.arraycopy(ciphertext, 0, input, 0, ciphertext.length);
                System.arraycopy(tag, 0, input, ciphertext.length, tag.length);
            }
            final Cipher cipher = method.cipher(Cipher.DECRYPT_MODE, key, iv);
            if (aadArg != null) {
                cipher.updateAAD(RawCipherSupport.binaryBytes("DECRYPT_RAW", aadArg));
            }
            return BinaryValue.of(cipher.doFinal(input));
        } catch (final Exception e) {
            throw new RuntimeException("Decryption failed. Check encrypted data, key, AAD, or AEAD tag.");
        }
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 6; }
}
