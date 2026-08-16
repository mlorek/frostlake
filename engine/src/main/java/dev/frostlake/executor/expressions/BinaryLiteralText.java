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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.values.BinaryValue;

/**
 * A hex BINARY literal's text, {@code X'..'}, read the way live reads it: SPACES inside the quotes are
 * dropped wherever they fall ({@code X'00 11'} is two bytes, {@code X'0 0'} one), and what remains
 * must be pairs of hexadecimal digits. Anything else — an odd count, a letter past F, a tab — is
 * refused while the statement compiles, echoing the literal as written:
 * {@code Invalid binary literal x'0'; should contain pairs of hexadecimal digits.} (all live-verified).
 */
public final class BinaryLiteralText {

    private BinaryLiteralText() {
    }

    /**
     * The bytes a literal token spells.
     *
     * @param token the token as written, {@code X} and quotes included
     * @return its value
     */
    public static BinaryValue decode(final String token) {
        final String body = token.substring(2, token.length() - 1).replace(" ", "");
        boolean valid = body.length() % 2 == 0;
        for (int i = 0; valid && i < body.length(); i++) {
            valid = Character.digit(body.charAt(i), 16) >= 0;
        }
        if (!valid) {
            throw new RuntimeException(SqlCompilationError.inline("Invalid binary literal " + token
                + "; should contain pairs of hexadecimal digits."));
        }
        return BinaryValue.fromHex(body);
    }

    /**
     * The width a literal declares: its byte count, and one for the empty {@code X''} (live types it
     * BINARY(1), as it does every literal of one byte).
     *
     * @param value the literal's value
     * @return the declared width
     */
    public static int declaredWidth(final BinaryValue value) {
        return Math.max(1, value.bytes().length);
    }
}
