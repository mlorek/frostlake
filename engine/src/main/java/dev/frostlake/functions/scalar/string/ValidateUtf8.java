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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * VALIDATE_UTF8(x) — the input as a string when its bytes are valid UTF-8.
 *
 * <p>It reads its argument as TEXT, so a BINARY is an argument-type error rather than the obvious
 * input: live refuses {@code VALIDATE_UTF8(bn)} at compile time and answers over a VARCHAR, a NUMBER
 * and a VARIANT alike.
 *
 * <p><b>Indistinguishable from {@link TryValidateUtf8} on every reachable input</b>, which is measured
 * rather than assumed. The pair would normally differ over INVALID UTF-8 — the TRY_ spelling answering
 * NULL where the plain one raises — but no argument this function accepts can carry any: a BINARY is
 * refused before evaluation, a lone surrogate is refused by the string-literal reader itself
 * ("high surrogate '\\uD800' must be followed by a low surrogate"), and a binary CAST to VARCHAR has
 * already been decoded by the time it arrives. So the divergent branch is unreachable, and inventing a
 * refusal for it would be inventing behaviour nothing can observe.
 */
public class ValidateUtf8 extends TextArgumentFunction {
    public ValidateUtf8() { super("VALIDATE_UTF8", StringType.VARCHAR); }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof BinaryValue) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(((BinaryValue) args.get(0)).bytes())).toString();
            } catch (final CharacterCodingException invalid) {
                return null;
            }
        }
        return args.get(0).toString();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
