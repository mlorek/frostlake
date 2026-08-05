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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;
import java.util.List;

/** SHA2_BINARY(msg [, bits]) — the SHA-2 digest (256 default; 224/384/512) as BINARY. */
public class Sha2Binary extends BuiltInFunction {
    public Sha2Binary() { super("SHA2_BINARY", BinaryType.BINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final int bits = args.size() > 1 && args.get(1) != null
            ? (int) Double.parseDouble(args.get(1).toString()) : 256;
        return BinaryValue.of(SharedFunctionHelpers.digest("SHA-" + bits,
            SharedFunctionHelpers.toUtf8(args.get(0))));
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
