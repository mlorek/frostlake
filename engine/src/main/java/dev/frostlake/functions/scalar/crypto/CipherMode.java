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

/**
 * The block cipher modes an encryption method may name, with what each writes ahead of the ciphertext and
 * whether a padding applies to it (live-verified lengths for one byte under a passphrase: GCM 29, CBC 32,
 * ECB 16, CTR / OFB / CFB 17).
 */
public enum CipherMode {
    /** Galois/Counter Mode: a 12-byte nonce, the ciphertext, then a 16-byte tag; the one mode that takes AAD. */
    GCM(12, false, true),
    /** Cipher block chaining: a 16-byte IV, then whole blocks. */
    CBC(16, true, false),
    /** Electronic codebook: whole blocks and no IV. */
    ECB(0, true, false),
    /** Counter mode: a 16-byte IV, then as many bytes as the value. */
    CTR(16, false, false),
    /** Output feedback: a 16-byte IV, then as many bytes as the value. */
    OFB(16, false, false),
    /** Cipher feedback: a 16-byte IV, then as many bytes as the value. */
    CFB(16, false, false);

    private final int ivBytes;
    private final boolean block;
    private final boolean aead;

    CipherMode(final int ivBytes, final boolean block, final boolean aead) {
        this.ivBytes = ivBytes;
        this.block = block;
        this.aead = aead;
    }

    /** The bytes of IV or nonce written ahead of the ciphertext. */
    public int ivBytes() {
        return ivBytes;
    }

    /** Whether the mode works in whole blocks, so that a padding applies. */
    public boolean isBlock() {
        return block;
    }

    /** Whether the mode authenticates, and so takes additional authenticated data. */
    public boolean isAead() {
        return aead;
    }
}
