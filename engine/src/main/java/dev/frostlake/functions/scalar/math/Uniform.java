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

import dev.frostlake.executor.StatementTokens;
import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * UNIFORM(min, max, gen) — a uniformly random value in the range. With exact bounds the result is drawn
 * at the larger of the bounds' scales, INCLUSIVE of both bounds — an integer for integer bounds, one
 * decimal for UNIFORM(1.5, 10, g) — and with a FLOAT bound it is a double in [min, max). The evaluator
 * hands the bounds over in the family and at the scale the call's type draws in. The generator argument's value
 * seeds the account's draws (see {@link GeneratorDraws}), so a constant generator answers one value for every row,
 * and RANDOM(), or RANDOM(seed), which differs from row to row, a value per row. Exact bounds draw a whole number of
 * their units by downscaling, a FLOAT bound the draw's fraction of the way from min to max (live-verified: UNIFORM(0,
 * 61, 1) is 38, UNIFORM(0, 999999999999999, 1) 618058918970139 and UNIFORM(0::FLOAT, 1::FLOAT, 1) 0.6180339887).
 */
public class Uniform extends NumericArgumentFunction {
    public Uniform() { super("UNIFORM", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        // The bounds are judged left to right before the generator is read, and a NULL generator beside them
        // answers NULL (live-verified).
        if (args.get(0) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: a must not be NULL");
        }
        if (args.get(1) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: b must not be NULL");
        }
        final Object loArg = args.get(0);
        final Object hiArg = args.get(1);
        if (loArg instanceof Number && hiArg instanceof Number
                && DecimalText.parse(loArg.toString()).compareTo(DecimalText.parse(hiArg.toString())) > 0) {
            // Exact bounds are told at the scale they are drawn at: UNIFORM(1.5, 1, g) names 15 and 10.
            final boolean exact = !isFloatBound(loArg) && !isFloatBound(hiArg);
            final BigDecimal lo = DecimalText.parse(loArg.toString());
            final BigDecimal hi = DecimalText.parse(hiArg.toString());
            final int scale = Math.max(0, Math.max(lo.scale(), hi.scale()));
            throw new RuntimeException("Invalid parameter value: "
                + (exact ? lo.setScale(scale).unscaledValue().toString() : loArg.toString()) + ", "
                + (exact ? hi.setScale(scale).unscaledValue().toString() : hiArg.toString())
                + ". Reason: a must be smaller than or equal to b");
        }
        if (args.size() >= 3 && args.get(2) == null) return null;
        // The generator is read as RANDOM reads its seed, a whole number past 64 bits being no number, and its value
        // seeds the draws (see GeneratorDraws).
        final GeneratorDraws draws = new GeneratorDraws(args.size() >= 3 ? Random.seedOf(args.get(2))
            : StatementTokens.nextLong());

        if (isFloatBound(loArg) || isFloatBound(hiArg)) {
            final double lo = ((Number) loArg).doubleValue();
            final double hi = ((Number) hiArg).doubleValue();
            return draws.canonical() * (hi - lo) + lo;
        }
        if (loArg instanceof BigDecimal || hiArg instanceof BigDecimal) {
            final BigDecimal lo = DecimalText.parse(loArg.toString());
            final BigDecimal hi = DecimalText.parse(hiArg.toString());
            final int scale = Math.max(0, Math.max(lo.scale(), hi.scale()));
            final BigInteger loUnits = lo.setScale(scale).unscaledValue();
            final BigInteger hiUnits = hi.setScale(scale).unscaledValue();
            final BigInteger units = loUnits.bitLength() < 64 && hiUnits.bitLength() < 64
                ? BigInteger.valueOf(draws.uniform(loUnits.longValue(), hiUnits.longValue()))
                : loUnits.add(new BigInteger(Long.toUnsignedString(draws.next()))
                    .mod(hiUnits.subtract(loUnits).add(BigInteger.ONE)));
            return scale == 0 && units.bitLength() < 64 ? (Object) units.longValue() : new BigDecimal(units, scale);
        }
        return draws.uniform(((Number) loArg).longValue(), ((Number) hiArg).longValue());
    }

    /** Snowflake dispatches by bound TYPE: a FLOAT bound draws a double, an exact one draws at its scale. */
    private static boolean isFloatBound(final Object o) {
        return o instanceof Double || o instanceof Float;
    }

    // Snowflake requires the generator argument: UNIFORM(5, 10) errors "not enough arguments for
    // function [UNIFORM(5, 10)], expected 3, got 2" (live-verified) — RANDOM() is the usual third.
    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
