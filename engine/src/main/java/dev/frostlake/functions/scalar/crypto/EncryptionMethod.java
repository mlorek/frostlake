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

import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * An encryption method argument, {@code <algorithm>-<mode>[/pad:<padding>]}, read as live reads it
 * (live-verified):
 *
 * <ul>
 *   <li>NULL is the default AES-GCM;</li>
 *   <li>each part is letters and digits, the three words match without regard to case, and {@code pad:} is written
 *       in lower case: anything else, surrounding blanks included, is
 *       {@code Malformed encryption method parameter: <text>};</li>
 *   <li>then the algorithm, the mode and the padding are checked in that order, each named upper-cased:
 *       {@code Unsupported encryption algorithm: DES}, {@code … mode: XYZ}, {@code … padding: ZERO};</li>
 *   <li>a padding is PKCS or NONE, PKCS by default, and matters only to the block modes CBC and ECB.</li>
 * </ul>
 */
public final class EncryptionMethod {

    /** The bytes in one AES block. */
    static final int BLOCK_BYTES = 16;
    /** The bits of a GCM tag. */
    static final int TAG_BITS = 128;

    private static final Pattern SPELLING = Pattern.compile("([A-Za-z0-9]+)-([A-Za-z0-9]+)(/pad:([A-Za-z0-9]+))?");

    private final CipherMode mode;
    private final boolean padded;

    private EncryptionMethod(final CipherMode mode, final boolean padded) {
        this.mode = mode;
        this.padded = padded;
    }

    /**
     * The method an argument names.
     *
     * @param method the argument, or null for the default
     * @return the method
     */
    public static EncryptionMethod of(final Object method) {
        if (method == null) {
            return new EncryptionMethod(CipherMode.GCM, false);
        }
        final String text = method.toString();
        final Matcher parts = SPELLING.matcher(text);
        if (!parts.matches()) {
            throw new RuntimeException("Malformed encryption method parameter: " + text);
        }
        final String algorithm = parts.group(1).toUpperCase(Locale.ROOT);
        if (!"AES".equals(algorithm)) {
            throw new RuntimeException("Unsupported encryption algorithm: " + algorithm);
        }
        final String modeName = parts.group(2).toUpperCase(Locale.ROOT);
        CipherMode named = null;
        for (final CipherMode each : CipherMode.values()) {
            if (each.name().equals(modeName)) {
                named = each;
            }
        }
        if (named == null) {
            throw new RuntimeException("Unsupported encryption mode: " + modeName);
        }
        final String padding = parts.group(4) == null ? "PKCS" : parts.group(4).toUpperCase(Locale.ROOT);
        if (!"PKCS".equals(padding) && !"NONE".equals(padding)) {
            throw new RuntimeException("Unsupported encryption padding: " + padding);
        }
        return new EncryptionMethod(named, named.isBlock() && "PKCS".equals(padding));
    }

    /** The mode. */
    public CipherMode mode() {
        return mode;
    }

    /** Whether the method pads its blocks. */
    public boolean isPadded() {
        return padded;
    }

    /**
     * Refuse additional authenticated data for a mode that does not authenticate: {@code Encryption mode CBC does
     * not support AAD}, for an empty AAD too.
     *
     * @param aad whether the call passes AAD
     */
    public void requireAadSupport(final boolean aad) {
        if (aad && !mode.isAead()) {
            throw new RuntimeException("Encryption mode " + mode.name() + " does not support AAD");
        }
    }

    /**
     * Refuse data that does not fill whole blocks when a block mode pads nothing.
     *
     * @param dataBytes the bytes to encrypt, or the ciphertext's bytes after its IV
     */
    public void requireWholeBlocks(final int dataBytes) {
        if (mode.isBlock() && !padded && dataBytes % BLOCK_BYTES != 0) {
            throw new RuntimeException("Data size (" + dataBytes + " bytes) needs to be a multiple of block size ("
                + BLOCK_BYTES + " bytes) if padding is disabled");
        }
    }

    /** The fewest bytes a ciphertext of this method holds: its IV, and a GCM tag. */
    public int minimumInput() {
        return mode.ivBytes() + (mode.isAead() ? TAG_BITS / Byte.SIZE : 0);
    }

    /**
     * A cipher for this method, initialised.
     *
     * @param opmode Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE
     * @param key the key's bytes
     * @param iv the bytes holding the IV, which starts at their beginning
     * @return the cipher
     * @throws GeneralSecurityException when the platform refuses the key or the parameters
     */
    public Cipher cipher(final int opmode, final byte[] key, final byte[] iv) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/" + mode.name() + "/" + (padded ? "PKCS5Padding" : "NoPadding"));
        final SecretKeySpec spec = new SecretKeySpec(key, "AES");
        if (mode == CipherMode.ECB) {
            cipher.init(opmode, spec);
        } else if (mode == CipherMode.GCM) {
            cipher.init(opmode, spec, new GCMParameterSpec(TAG_BITS, iv, 0, mode.ivBytes()));
        } else {
            cipher.init(opmode, spec, new IvParameterSpec(iv, 0, mode.ivBytes()));
        }
        return cipher;
    }
}
