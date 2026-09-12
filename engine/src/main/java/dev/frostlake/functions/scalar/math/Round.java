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
import dev.frostlake.types.NumericType;

import java.math.RoundingMode;
import java.util.List;

/**
 * ROUND(n [, scale [, mode]]) — the input rounded to {@code scale} decimal places, zero by default.
 *
 * <p>The scale becomes the RESULT's declared scale as well as the value's, and a NULL scale — or a
 * NULL mode — makes the whole call NULL rather than meaning "no rounding". See {@link RoundedValue}.
 */
public class Round extends NumericArgumentFunction {
    public Round() { super("ROUND", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL mode makes the whole call NULL, the same way a NULL scale does (live-verified).
        if (args.size() > 2 && args.get(2) == null) {
            return null;
        }
        return RoundedValue.of(args, args.size() > 2 ? roundingMode(args.get(2)) : RoundingMode.HALF_UP);
    }

    /**
     * Map Snowflake's optional rounding-mode argument to a {@link RoundingMode}: {@code HALF_TO_EVEN} is
     * banker's rounding; the default {@code HALF_AWAY_FROM_ZERO} rounds a tie away from zero (Java's
     * {@code HALF_UP}). Matching is case-insensitive and untrimmed, as the compile-time check's is; any other value keeps
     * the default.
     */
    private static RoundingMode roundingMode(final Object modeArg) {
        if (modeArg != null && "HALF_TO_EVEN".equalsIgnoreCase(modeArg.toString())) {
            return RoundingMode.HALF_EVEN;
        }
        return RoundingMode.HALF_UP;
    }

    /**
     * ★ The MODE slot is not part of the numeric signature. A BOOLEAN or a temporal value there is not
     * the wrong TYPE but a mode that is not constant — live refuses {@code ROUND(n, 0, TRUE)} and
     * {@code ROUND(n, 0, CURRENT_DATE)} as "needs to be constant", which the mode rule decides. An
     * ARRAY, OBJECT or BINARY mode is still the wrong type, and so is a BOOLEAN in either numeric slot.
     */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 2 ? SemiStructuredRejection.NONE : super.booleanRejection(position);
    }

    /** The same exemption for a DATE, TIME or TIMESTAMP mode — see {@link #booleanRejection}. */
    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return position == 2 ? SemiStructuredRejection.NONE : super.temporalRejection(position);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
