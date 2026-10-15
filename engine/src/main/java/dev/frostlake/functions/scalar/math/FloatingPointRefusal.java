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

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * The refusal of a floating-point operation its arguments have no value for, worded as the account words it: the
 * operation as its runtime names it, over the arguments at six significant digits — "Invalid floating point
 * operation: sqrt(-1)", "… acos(2)", "… log(e,0)", "… pow(-8, 0.333333)" (live-verified).
 */
final class FloatingPointRefusal {

    private static final MathContext SIX_DIGITS = new MathContext(6);

    private FloatingPointRefusal() {
    }

    /**
     * @param operation the operation with its arguments, as the runtime names it
     * @return the refusal
     */
    static RuntimeException of(final String operation) {
        return new RuntimeException("Invalid floating point operation: " + operation);
    }

    /**
     * @param value an argument
     * @return the argument as the refusal prints it: a whole number bare, any other at six significant digits
     */
    static String number(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value > 0 ? "inf" : Double.isNaN(value) ? "nan" : "-inf";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return new BigDecimal(value).round(SIX_DIGITS).stripTrailingZeros().toPlainString();
    }
}
