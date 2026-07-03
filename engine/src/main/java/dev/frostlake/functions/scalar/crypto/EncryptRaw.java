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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * ENCRYPT_RAW(value, key, iv [, additional_authenticated_data [, encryption_method]]) — raw-key AES-GCM
 * authenticated encryption. All BINARY arguments (value, key, iv, aad) are the engine's hex-string BINARY
 * representation; {@code key} is used verbatim (16 / 24 / 32 bytes select AES-128 / 192 / 256) and the IV
 * must be 12 bytes (96 bits) for GCM. Only AES-GCM (the Snowflake default) is supported; a non-GCM method
 * is rejected rather than silently mis-encrypted.
 *
 * <p>Returns a VARIANT OBJECT with the Snowflake-documented keys {@code ciphertext}, {@code iv} and
 * {@code tag} (each a hex BINARY), where {@code tag} is the 128-bit GCM authentication tag split off from
 * the cipher output. {@link DecryptRaw} consumes these to reverse the operation.
 */
public class EncryptRaw extends BuiltInFunction {

    public EncryptRaw() { super("ENCRYPT_RAW", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null) {
            return null;
        }
        final byte[] aad = args.size() >= 4 && args.get(3) != null
            ? RawCipherSupport.hexToBytes("ENCRYPT_RAW", args.get(3).toString()) : null;
        RawCipherSupport.requireGcm("ENCRYPT_RAW", args.size() >= 5 ? args.get(4) : null);
        try {
            final byte[] plaintext = RawCipherSupport.hexToBytes("ENCRYPT_RAW", args.get(0).toString());
            final byte[] key = RawCipherSupport.hexToBytes("ENCRYPT_RAW", args.get(1).toString());
            final byte[] iv = RawCipherSupport.hexToBytes("ENCRYPT_RAW", args.get(2).toString());
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(RawCipherSupport.GCM_TAG_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            final byte[] out = cipher.doFinal(plaintext);
            // GCM appends the authentication tag to the ciphertext; split it back out for the returned object.
            final byte[] ciphertext = Arrays.copyOfRange(out, 0, out.length - RawCipherSupport.GCM_TAG_BYTES);
            final byte[] tag = Arrays.copyOfRange(out, out.length - RawCipherSupport.GCM_TAG_BYTES, out.length);
            final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
            result.put("ciphertext", RawCipherSupport.bytesToHex(ciphertext));
            result.put("iv", RawCipherSupport.bytesToHex(iv));
            result.put("tag", RawCipherSupport.bytesToHex(tag));
            return result.toString();
        } catch (final Exception e) {
            throw new RuntimeException("ENCRYPT_RAW failed: " + e.getMessage());
        }
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 5; }
}
