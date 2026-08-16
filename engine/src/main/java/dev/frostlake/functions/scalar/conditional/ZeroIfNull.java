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

package dev.frostlake.functions.scalar.conditional;

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * ZEROIFNULL refuses more DECLARED families than its numeric-argument neighbours: a SQL BOOLEAN,
 * every temporal flavour and a BINARY are each a compile-time argument-type error — positioned at
 * the call, the family named with its parameters ((BOOLEAN), (DATE), (TIME(9)), (TIMESTAMP_NTZ(9)),
 * (BINARY(67108864))) — while NUMBER, FLOAT, VARCHAR and VARIANT convert, a VARIANT even when it
 * holds the same boolean that is refused declared (all live-verified).
 */
public class ZeroIfNull extends NumericArgumentFunction {
    public ZeroIfNull() { super("ZEROIFNULL", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object v = args.get(0);
        if (v == null) return 0L;
        return v;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
