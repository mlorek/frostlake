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

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.IntegerResultWidths;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

public class Factorial extends NumericArgumentFunction {
    public Factorial() { super("FACTORIAL", IntegerResultWidths.FACTORIAL); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // A fraction goes to the nearest whole number; 33 is the last whose factorial a NUMBER holds.
        final long n = new BigDecimal(args.get(0).toString()).setScale(0, RoundingMode.HALF_UP).longValue();
        if (n < 0) {
            throw new RuntimeException("Numeric value is out of range, error: Input of factorial function can't be"
                + " negative. " + n + " is invalid.");
        }
        if (n > 33) {
            throw new RuntimeException("Numeric value is out of range, error: " + n
                + " is too large for factorial function.");
        }
        BigInteger result = BigInteger.ONE;
        for (long i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result.bitLength() < 63 ? (Object) Long.valueOf(result.longValue()) : new BigDecimal(result);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
