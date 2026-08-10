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

package dev.frostlake.functions.scalar.encoding;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decoded bytes read back as TEXT, which they are only allowed to be when they are valid UTF-8.
 *
 * <p>The {@code _STRING} decoders hand back a VARCHAR, so bytes that spell no string are an error and
 * not a lossy conversion: live answers {@code Invalid UTF8 detected while decoding 'qw=='} — quoting
 * the INPUT as written, not the bytes — for BASE64_DECODE_STRING and HEX_DECODE_STRING alike, while
 * their TRY_ spellings answer NULL. Java's {@code new String(bytes, UTF_8)} silently substitutes the
 * replacement character instead, which is how a one-byte 0xAB used to come back as a one-character
 * string. The {@code _BINARY} decoders are unaffected: they return the bytes and never read them.
 */
public final class DecodedText {

    private DecodedText() {
    }

    /**
     * The bytes as text, refusing anything that is not valid UTF-8.
     *
     * @param bytes  the decoded bytes
     * @param source the input exactly as written, for the message
     * @return the decoded text
     */
    public static String strict(final byte[] bytes, final String source) {
        final String decoded = lenient(bytes);
        if (decoded == null) {
            throw new RuntimeException("Invalid UTF8 detected while decoding '" + source + "'");
        }
        return decoded;
    }

    /**
     * The bytes as text, or null when they are not valid UTF-8 — the TRY_ spellings' answer.
     *
     * @param bytes the decoded bytes
     * @return the decoded text, or null
     */
    public static String lenient(final byte[] bytes) {
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (final CharacterCodingException notText) {
            return null;
        }
    }
}
