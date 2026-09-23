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
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;

/**
 * ENCRYPT_RAW(value, key, iv [, additional_authenticated_data [, encryption_method]]) — raw-key AES
 * encryption in any mode the method names: GCM, CBC, ECB, CTR, OFB or CFB. All BINARY arguments (value,
 * key, iv, aad) are the engine's hex-string BINARY representation; {@code key} is used verbatim (16 / 24 /
 * 32 bytes select AES-128 / 192 / 256) and the IV must be exactly the size its mode takes — 12 bytes for
 * GCM, 16 for the other IV modes, none at all for ECB.
 *
 * <p>An IV of NULL is not a missing argument: one is DRAWN at random and returned with the answer, so the
 * call stays reversible. Only a NULL value or key makes the answer NULL.
 *
 * <p>Returns a VARIANT OBJECT with the Snowflake-documented keys {@code ciphertext} and {@code iv} (each a
 * hex BINARY), plus {@code tag} for an authenticating mode — the 128-bit GCM tag split off from the cipher
 * output. A mode that takes no IV answers with a NULL {@code iv} member. {@link DecryptRaw} consumes these
 * to reverse the operation.
 */
public class EncryptRaw extends BuiltInFunction {

    public EncryptRaw() { super("ENCRYPT_RAW", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Only the value and the key make the answer NULL: a NULL IV means one is drawn here.
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final Object aadArg = args.size() >= 4 ? args.get(3) : null;
        // Live's order: the method, the key's size, the IV's size, then whether the mode takes AAD.
        final EncryptionMethod method = EncryptionMethod.of(args.size() >= 5 ? args.get(4) : null);
        RawCipherSupport.requireKeySize(RawCipherSupport.binaryBytes("ENCRYPT_RAW", args.get(1)));
        if (args.get(2) != null) {
            RawCipherSupport.requireIvSize(method, RawCipherSupport.binaryBytes("ENCRYPT_RAW", args.get(2)));
        }
        method.requireAadSupport(aadArg != null);
        final byte[] plaintext = RawCipherSupport.binaryBytes("ENCRYPT_RAW", args.get(0));
        method.requireWholeBlocks(plaintext.length);
        try {
            final byte[] key = RawCipherSupport.binaryBytes("ENCRYPT_RAW", args.get(1));
            final byte[] iv = RawCipherSupport.ivOrDrawn("ENCRYPT_RAW", method, args.get(2));
            final Cipher cipher = method.cipher(Cipher.ENCRYPT_MODE, key, iv);
            if (aadArg != null) {
                cipher.updateAAD(RawCipherSupport.binaryBytes("ENCRYPT_RAW", aadArg));
            }
            final byte[] out = cipher.doFinal(plaintext);
            final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
            // The members keep their BINARY type (live: TYPEOF(o:ciphertext) is BINARY and
            // AS_BINARY over it returns the bytes) while rendering as the same hex text.
            if (method.mode().isAead()) {
                // An authenticating mode appends its tag to the ciphertext; split it back out.
                result.set("ciphertext",
                    binaryMember(Arrays.copyOfRange(out, 0, out.length - RawCipherSupport.GCM_TAG_BYTES)));
            } else {
                result.set("ciphertext", binaryMember(out));
            }
            if (method.mode().ivBytes() == 0) {
                result.putNull("iv");
            } else {
                result.set("iv", binaryMember(iv));
            }
            if (method.mode().isAead()) {
                result.set("tag", binaryMember(
                    Arrays.copyOfRange(out, out.length - RawCipherSupport.GCM_TAG_BYTES, out.length)));
            }
            // Return the typed variant, not its text: as text the members' BINARY typing would be
            // discarded before the caller ever sees them (TYPEOF/AS_BINARY then say VARCHAR/NULL).
            return dev.frostlake.values.VariantValue.ofNode(result);
        } catch (final Exception e) {
            throw new RuntimeException("ENCRYPT_RAW failed: " + e.getMessage());
        }
    }


    /** One BINARY member of the returned object: hex text on the wire, BINARY to TYPEOF / AS_BINARY. */
    private static TypedScalarNode binaryMember(final byte[] bytes) {
        return new TypedScalarNode(RawCipherSupport.bytesToHex(bytes), BinaryValue.of(bytes));
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 5; }
}
