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
import dev.frostlake.functions.scalar.datetime.DateTrunc;

import java.util.Arrays;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class Trunc extends NumericArgumentFunction {

    private static final DateTrunc DATE_TRUNC = new DateTrunc();

    public Trunc() { super("TRUNC", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // Snowflake's TRUNC is overloaded: over a temporal it is DATE_TRUNC with swapped
        // arguments — TRUNC(date, 'MONTH') = DATE_TRUNC('MONTH', date), part defaulting to DAY.
        final Object first = args.get(0);
        final boolean temporalInput = first instanceof LocalDate || first instanceof LocalDateTime
            || first instanceof LocalTime;
        final boolean partSecond = args.size() > 1 && args.get(1) instanceof CharSequence;
        if (temporalInput || partSecond) {
            final Object part = partSecond ? args.get(1) : "DAY";
            return DATE_TRUNC.evaluate(Arrays.asList(part, first));
        }
        BigDecimal num = new BigDecimal(first.toString());

        if (args.size() > 1 && args.get(1) != null) {
            int scale = ((Number) args.get(1)).intValue();
            return num.setScale(scale, RoundingMode.DOWN);
        }

        return num.setScale(0, RoundingMode.DOWN);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
