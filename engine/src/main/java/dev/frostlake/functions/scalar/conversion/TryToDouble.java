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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.HexDoubleText;

import java.util.List;

/**
 * TRY_TO_DOUBLE(expr [, format]) — {@link ToDouble} answering NULL where TO_DOUBLE fails on the value. It
 * reads what TO_DOUBLE reads: TRY_TO_DOUBLE('inf') is inf and TRY_TO_DOUBLE('NaN') NaN, while a text that
 * spells no number, a text its format model cannot read, and a model that is not one are NULL
 * (live-verified).
 */
public class TryToDouble extends BuiltInFunction {

    private static final ToDouble BASE = new ToDouble();

    public TryToDouble() { super("TRY_TO_DOUBLE", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // A sign before a hexadecimal number with no exponent reads in TO_DOUBLE, never here.
        if (args.size() == 1 && args.get(0) instanceof String
                && HexDoubleText.isSignedWithoutExponent(((String) args.get(0)).trim())) {
            return null;
        }
        try {
            return BASE.evaluate(args);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
