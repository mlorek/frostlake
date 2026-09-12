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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.Base64;
import java.util.List;

/**
 * BASE64_ENCODE(expr [, max_line_length [, alphabet]]) — the base64 encoding of the input's bytes.
 *
 * <p>A BINARY argument contributes its OWN bytes, so
 * {@code BASE64_ENCODE(COMPRESS('hello','snappy'))} is {@code BRBoZWxsbw==}. Encoding
 * {@code toString()} instead would base64 the hex RENDERING and yield {@code MDUxMDY4NjU2QzZDNkY=}.
 */
public class Base64Encode extends BuiltInFunction {
    public Base64Encode() { super("BASE64_ENCODE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL max line length makes the whole call NULL, rather than meaning "do not wrap" —
        // live-verified, and the two are easy to confuse because an ABSENT one does mean that.
        if (args.get(0) == null || args.size() > 1 && args.get(1) == null) return null;
        final String standard =
            Base64.getEncoder().encodeToString(SharedFunctionHelpers.toUtf8(args.get(0)));
        return Base64Options.wrap(
            Base64Options.toCustomAlphabet(standard, Base64Options.alphabetOf(args, 2)),
            Base64Options.lineLengthOf(args, 1));
    }

    /** A VECTOR is refused by its argument type, in every position (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
