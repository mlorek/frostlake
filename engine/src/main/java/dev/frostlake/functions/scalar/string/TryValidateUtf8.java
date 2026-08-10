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
 * TRY_VALIDATE_UTF8(x) — the input as a string when its bytes are valid UTF-8, else NULL.
 *
 * <p>It reads its argument as TEXT, so a BINARY is an argument-type error rather than the obvious
 * input: live refuses {@code TRY_VALIDATE_UTF8(bn)} at compile time and answers over a VARCHAR and
 * even over a NUMBER. The byte path below therefore only ever sees a value the static channel could
 * not type.
 */
public class TryValidateUtf8 extends TextArgumentFunction {
    public TryValidateUtf8() { super("TRY_VALIDATE_UTF8", StringType.VARCHAR); }

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
