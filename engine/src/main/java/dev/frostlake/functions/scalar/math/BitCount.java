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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;
import java.util.List;

/** BITCOUNT(n) — the number of set bits in the integer value. */
public class BitCount extends BuiltInFunction {
    public BitCount() { super("BITCOUNT", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        return (long) new BigDecimal(args.get(0).toString()).toBigInteger().bitCount();
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
