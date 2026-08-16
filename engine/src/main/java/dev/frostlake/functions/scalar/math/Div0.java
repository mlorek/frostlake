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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ApproximateValues;

import java.math.BigDecimal;
import java.util.List;

/**
 * DIV0(a, b) divides like the {@code /} operator but returns 0 when the divisor is 0.
 * DIV0NULL(a, b) additionally returns 0 when the divisor is NULL. In both cases a NULL
 * dividend (with a normal, non-zero divisor) yields NULL, matching standard division.
 */
public class Div0 extends NumericArgumentFunction {
    private final boolean nullDivisorToZero;

    public Div0(final boolean nullDivisorToZero) {
        super(nullDivisorToZero ? "DIV0NULL" : "DIV0", NumericType.NUMBER);
        this.nullDivisorToZero = nullDivisorToZero;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object dividendArg = args.get(0);
        final Object divisorArg = args.get(1);
        // A NULL dividend is NULL whatever the divisor: DIV0(NULL, 0), DIV0NULL(NULL, NULL) and
        // DIV0NULL(NULL, 0) are all NULL on the account.
        if (dividendArg == null) {
            return null;
        }
        // A FLOAT operand makes the whole division a double, as the operator's does: DIV0(f, 2) is the
        // double 8.625 and DIV0(f, 0) the double 0.
        final boolean approximate = ApproximateValues.isApproximate(dividendArg)
            || (divisorArg != null && ApproximateValues.isApproximate(divisorArg));
        // DIV0NULL treats a NULL divisor as 0; DIV0 keeps standard division (x / NULL -> NULL).
        if (divisorArg == null) {
            if (!nullDivisorToZero) {
                return null;
            }
            return approximate ? Double.valueOf(0.0) : zeroAtDerivedScale(dividendArg);
        }
        if (approximate) {
            final double divisor = Double.parseDouble(divisorArg.toString());
            return divisor == 0.0 ? Double.valueOf(0.0)
                : Double.valueOf(Double.parseDouble(dividendArg.toString()) / divisor);
        }
        final BigDecimal divisor = new BigDecimal(divisorArg.toString());
        // Both DIV0 and DIV0NULL return 0 for a zero divisor instead of raising an error.
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return zeroAtDerivedScale(dividendArg);
        }
        final BigDecimal dividend = new BigDecimal(dividendArg.toString());
        return SharedFunctionHelpers.divideWithSnowflakeScale(dividend, divisor);
    }

    /**
     * The zero a zero (or, for DIV0NULL, a NULL) divisor answers, at the scale the division would
     * have derived from the dividend: DIV0(n10_2, 0) is 0.00000000 and DIV0(n38_12, 0) is
     * 0.000000000000 on the account, the quotient's own scale rather than a bare 0.
     */
    private static BigDecimal zeroAtDerivedScale(final Object dividendArg) {
        final int dividendScale = Math.max(new BigDecimal(dividendArg.toString()).scale(), 0);
        return BigDecimal.ZERO.setScale(Math.max(dividendScale, Math.min(dividendScale + 6, 12)));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
