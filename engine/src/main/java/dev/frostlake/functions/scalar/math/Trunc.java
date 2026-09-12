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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.datetime.DateTrunc;
import dev.frostlake.types.NumericType;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

public class Trunc extends NumericArgumentFunction {

    private static final DateTrunc DATE_TRUNC = new DateTrunc();

    public Trunc() { super("TRUNC", NumericType.NUMBER); }

    /**
     * The temporal answer depends on the ARITY, because the temporal TRUNC is DATE_TRUNC with a
     * unit (live-verified): {@code TRUNC(d, 'MONTH')} answers, {@code TRUNC(d)} alone is "Invalid
     * argument types for function 'TRUNC': (DATE)" — and so are {@code TRUNC(t)}, {@code TRUNC(ts)}
     * and the TRUNCATE spelling — while a temporal in the SECOND position of a numeric call is the
     * family refusal, "(NUMBER(2,1), DATE)". A temporal first argument's unit is judged by the
     * unit rule instead, which is why the second position stays unconstrained when the first is
     * temporal: {@code TRUNC(d, d)} is the date-part sentence, not "(DATE, DATE)".
     */
    @Override
    public SemiStructuredRejection temporalRejection(final int position, final int argumentCount) {
        if (position == 0) {
            return argumentCount == 1 ? SemiStructuredRejection.ARGUMENT_TYPES
                : SemiStructuredRejection.NONE;
        }
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // Snowflake's TRUNC is overloaded: over a temporal it is DATE_TRUNC with swapped
        // arguments — TRUNC(date, 'MONTH') = DATE_TRUNC('MONTH', date), part defaulting to DAY.
        // The FIRST argument decides which TRUNC this is, and only the first: over a number the
        // second argument is always a scale, so `TRUNC(123.45, '1')` truncates to one decimal place
        // where it once looked for a date part and refused (live-verified).
        final Object first = args.get(0);
        if (first instanceof LocalDate || first instanceof LocalDateTime || first instanceof LocalTime) {
            if (args.size() > 1 && args.get(1) == null) {
                return null;   // a NULL unit is NULL, not the DAY default an ABSENT one takes
            }
            final Object part = args.size() > 1 ? args.get(1) : "DAY";
            return DATE_TRUNC.evaluate(Arrays.asList(part, first), "TRUNC");
        }
        return RoundedValue.of(args, RoundingMode.DOWN);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
