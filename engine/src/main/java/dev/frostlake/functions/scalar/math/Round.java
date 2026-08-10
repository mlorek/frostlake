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
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class Round extends NumericArgumentFunction {
    public Round() { super("ROUND", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final BigDecimal num = new BigDecimal(args.get(0).toString());
        final int scale = args.size() > 1 && args.get(1) != null ? ((Number) args.get(1)).intValue() : 0;
        final RoundingMode mode = args.size() > 2 ? roundingMode(args.get(2)) : RoundingMode.HALF_UP;
        return num.setScale(scale, mode);
    }

    /**
     * Map Snowflake's optional rounding-mode argument to a {@link RoundingMode}: {@code HALF_TO_EVEN} is
     * banker's rounding; the default {@code HALF_AWAY_FROM_ZERO} rounds a tie away from zero (Java's
     * {@code HALF_UP}). Matching is case-insensitive; any other value keeps the default.
     */
    private static RoundingMode roundingMode(final Object modeArg) {
        if (modeArg != null && "HALF_TO_EVEN".equalsIgnoreCase(modeArg.toString().trim())) {
            return RoundingMode.HALF_EVEN;
        }
        return RoundingMode.HALF_UP;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
