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

import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * DECRYPT_RAW(value, key, iv [, [ [additional_authenticated_data,] encryption_method,] aead_tag]) — reverses
 * {@link EncryptRaw}. Following Snowflake's nested-optional signature the AEAD tag is the LAST argument, so
 * the argument list is disambiguated by count:
 * <ul>
 *   <li>4 args: value, key, iv, tag</li>
 *   <li>5 args: value, key, iv, method, tag</li>
 *   <li>6 args: value, key, iv, aad, method, tag</li>
 * </ul>
 * All BINARY arguments are hex strings; the returned decrypted value is BINARY (hex). Only AES-GCM is
 * supported, so the tag is required (a 3-argument call is rejected). A wrong key, IV, AAD or tag fails
 * authentication and raises, exactly as Snowflake's GCM verification does.
 */
public class DecryptRaw extends BuiltInFunction {

    public DecryptRaw() { super("DECRYPT_RAW", BinaryType.BINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null) {
            return null;
        }
        final int n = args.size();
        final Object aadArg;
        final Object methodArg;
        final Object tagArg;
        switch (n) {
            case 4:
                aadArg = null; methodArg = null; tagArg = args.get(3);
                break;
            case 5:
                aadArg = null; methodArg = args.get(3); tagArg = args.get(4);
                break;
            case 6:
                aadArg = args.get(3); methodArg = args.get(4); tagArg = args.get(5);
                break;
            default:
                throw new RuntimeException("DECRYPT_RAW: the AEAD tag argument is required for AES-GCM");
        }
        RawCipherSupport.requireGcm("DECRYPT_RAW", methodArg);
        if (tagArg == null) {
            throw new RuntimeException("DECRYPT_RAW: the AEAD tag argument is required for AES-GCM");
        }
        try {
            final byte[] ciphertext = RawCipherSupport.hexToBytes("DECRYPT_RAW", args.get(0).toString());
            final byte[] key = RawCipherSupport.hexToBytes("DECRYPT_RAW", args.get(1).toString());
            final byte[] iv = RawCipherSupport.hexToBytes("DECRYPT_RAW", args.get(2).toString());
            final byte[] tag = RawCipherSupport.hexToBytes("DECRYPT_RAW", tagArg.toString());
            // Java's GCM cipher expects the tag appended to the ciphertext; reassemble the two.
            final byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(RawCipherSupport.GCM_TAG_BITS, iv));
            if (aadArg != null) {
                cipher.updateAAD(RawCipherSupport.hexToBytes("DECRYPT_RAW", aadArg.toString()));
            }
            return RawCipherSupport.bytesToHex(cipher.doFinal(combined));
        } catch (final Exception e) {
            throw new RuntimeException("DECRYPT_RAW failed: " + e.getMessage());
        }
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 6; }
}
