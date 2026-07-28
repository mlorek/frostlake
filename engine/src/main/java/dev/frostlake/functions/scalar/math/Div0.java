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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * DIV0(a, b) divides like the {@code /} operator but returns 0 when the divisor is 0.
 * DIV0NULL(a, b) additionally returns 0 when the divisor is NULL. In both cases a NULL
 * dividend (with a normal, non-zero divisor) yields NULL, matching standard division.
 */
public class Div0 extends BuiltInFunction {
    private final boolean nullDivisorToZero;

    public Div0(final boolean nullDivisorToZero) {
        super(nullDivisorToZero ? "DIV0NULL" : "DIV0", NumericType.NUMBER);
        this.nullDivisorToZero = nullDivisorToZero;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object divisorArg = args.get(1);
        // DIV0NULL treats a NULL divisor as 0; DIV0 keeps standard division (x / NULL -> NULL).
        if (divisorArg == null) {
            return nullDivisorToZero ? BigDecimal.ZERO : null;
        }
        final BigDecimal divisor = new BigDecimal(divisorArg.toString());
        // Both DIV0 and DIV0NULL return 0 for a zero divisor instead of raising an error.
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Otherwise divide as usual; a NULL dividend yields NULL.
        final Object dividendArg = args.get(0);
        if (dividendArg == null) {
            return null;
        }
        final BigDecimal dividend = new BigDecimal(dividendArg.toString());
        return SharedFunctionHelpers.divideWithSnowflakeScale(dividend, divisor);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
