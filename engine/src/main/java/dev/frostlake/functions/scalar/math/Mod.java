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

import dev.frostlake.executor.expressions.ExpressionArithmetic;
import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ApproximateValues;

import java.math.BigDecimal;
import java.util.List;

public class Mod extends NumericArgumentFunction {
    public Mod() { super("MOD", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        if (ApproximateValues.isApproximate(args.get(0)) || ApproximateValues.isApproximate(args.get(1))) {
            final double divisor = ((Number) args.get(1)).doubleValue();
            if (divisor != 0.0) {
                // A double remainder stays a double; a zero result is a POSITIVE zero, as live has it
                // for MOD(-0.0::FLOAT, 1), where IEEE remainder would keep the dividend's sign.
                return Double.valueOf(((Number) args.get(0)).doubleValue() % divisor + 0.0);
            }
        }
        // The operator's own step, rescale check included: MOD(a, 0.5) over 38 nines is refused at the
        // aligned NUMBER(38,1) exactly as a % 0.5 is (live-verified).
        return ExpressionArithmetic.checkedRemainder(new BigDecimal(args.get(0).toString()),
            new BigDecimal(args.get(1).toString()));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
