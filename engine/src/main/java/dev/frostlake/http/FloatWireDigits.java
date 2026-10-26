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

package dev.frostlake.http;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The value a FLOAT result cell carries in a JSON result set, as the account's JSON results carry it: rounded to the
 * digits the account prints — at most nine decimals and fifteen significant digits for a value of 1 or more, ten
 * significant digits below 1 (all live-verified):
 *
 * <pre>
 *   1.100000023841858     1.100000024
 *   0.3333333333333333    0.3333333333
 *   123456.7890625        123456.7890625
 *   1234567890.123456789  1.23456789012346E9
 *   9007199254740993      9.00719925474099E15
 *   -0.000123456789012    -1.23456789E-4
 * </pre>
 *
 * <p>The cell stays a JSON number, and NaN, the infinities and zero pass as they are. The engine's own value is
 * unchanged: only the text a driver reading JSON results is handed follows the account's.
 */
final class FloatWireDigits {

    /** The most significant digits a cell carries. */
    private static final int MOST_DIGITS = 15;

    /** The decimals a value of 1 or more keeps. */
    private static final int DECIMALS = 9;

    /** The significant digits a value below 1 keeps. */
    private static final int DIGITS_BELOW_ONE = 10;

    private FloatWireDigits() {
    }

    /**
     * A FLOAT cell rounded as the account's JSON results carry it.
     *
     * @param value the engine's value
     * @return the value the wire carries
     */
    static Double of(final Double value) {
        final double held = value.doubleValue();
        if (Double.isNaN(held) || Double.isInfinite(held) || held == 0) {
            return value;
        }
        final double magnitude = Math.abs(held);
        final int digits = magnitude < 1 ? DIGITS_BELOW_ONE
            : Math.min(MOST_DIGITS, wholeDigits(magnitude) + DECIMALS);
        final MathContext rounding = new MathContext(digits, RoundingMode.HALF_EVEN);
        return Double.valueOf(new BigDecimal(held).round(rounding).doubleValue());
    }

    /** How many digits stand before the point of a value of 1 or more. */
    private static int wholeDigits(final double magnitude) {
        return new BigDecimal(magnitude).setScale(0, RoundingMode.DOWN).precision();
    }
}
