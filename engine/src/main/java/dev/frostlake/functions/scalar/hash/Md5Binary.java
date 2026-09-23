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
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;
import java.util.List;

/** MD5_BINARY(msg) — the 16-byte MD5 digest as BINARY. */
public class Md5Binary extends BuiltInFunction {
    public Md5Binary() { super("MD5_BINARY", new BinaryType("VARBINARY", 16)); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        return BinaryValue.of(SharedFunctionHelpers.digest("MD5", SharedFunctionHelpers.toUtf8(args.get(0))));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /**
     * A predicate as MD5_BINARY's first argument is refused by the argument types, where a BOOLEAN
     * value is read as text (live-verified).
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
