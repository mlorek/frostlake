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

package dev.frostlake.functions.scalar.hash;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * SHA2(msg [, bits]) — the lowercase hex SHA-2 digest (256 by default) of the message bytes.
 *
 * <p>A BINARY message is digested as its OWN bytes, so {@code SHA2(TO_BINARY('61','HEX'))} equals
 * {@code SHA2('a')}.
 */
public class Sha2 extends BuiltInFunction {
    public Sha2() { super("SHA2", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final int bits = args.size() > 1 && args.get(1) != null ? ((Number) args.get(1)).intValue() : 256;
        return SharedFunctionHelpers.toHex(
            SharedFunctionHelpers.digest("SHA-" + bits, SharedFunctionHelpers.toUtf8(args.get(0))));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }

    /**
     * A predicate as SHA2's first argument is refused by the argument types, where a BOOLEAN
     * value is read as text (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
