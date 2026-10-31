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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * NORMAL(mean, stddev, generator) — a normally distributed FLOAT, drawn from the generator's value: one
 * value of the generator always answers the same number, so {@code NORMAL(0, 1, 7)} equals itself while
 * {@code NORMAL(0, 1, RANDOM())} differs from row to row. The numbers are the account's (see
 * {@link GeneratorDraws#normal}), scaled by the deviation and moved by the mean (live-verified: NORMAL(0, 1, 1) is
 * -1.143416214 and NORMAL(5, 2, 1) 2.713167572).
 *
 * <p>The mean and the deviation are numbers, or texts that spell one ("Numeric value 'a' is not
 * recognized" otherwise), and are judged left to right before the generator is read: a NULL mean is
 * "Invalid parameter value: NULL. Reason: mean must not be NULL", a NULL deviation the same sentence for
 * {@code stddev}, a negative one "Invalid parameter value: -1. Reason: stddev must not be negative". A
 * NULL generator beside them answers NULL, and the generator is held to RANDOM's seed rule: a whole number past
 * 64 bits is "Numeric value '1000000000000000000000000000000' is not recognized". A BOOLEAN in any argument, and
 * a date as the generator, are refused by the argument types (live-verified).
 */
public class Normal extends BuiltInFunction {
    private static final int GENERATOR = 2;

    public Normal() { super("NORMAL", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.size() <= GENERATOR) {
            throw new RuntimeException("Invalid argument types for function 'NORMAL'");
        }
        if (args.get(0) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: mean must not be NULL");
        }
        final double mean = number(args.get(0));
        if (args.get(1) == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: stddev must not be NULL");
        }
        final double stddev = number(args.get(1));
        if (stddev < 0) {
            throw new RuntimeException("Invalid parameter value: " + written(args.get(1))
                + ". Reason: stddev must not be negative");
        }
        if (args.get(GENERATOR) == null) {
            return null;
        }
        // The generator is read as RANDOM reads its seed, a whole number past 64 bits being no number, and its value
        // seeds the draws (see GeneratorDraws).
        return new GeneratorDraws(Random.seedOf(args.get(GENERATOR))).normal() * stddev + mean;
    }

    private static double number(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return DecimalText.parse(value.toString().trim()).doubleValue();
        } catch (final NumberFormatException noNumber) {
            throw new RuntimeException("Numeric value '" + value + "' is not recognized");
        }
    }

    private static String written(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString().trim();
    }

    // Two arguments or three: fewer is counted against two, and two alone are refused by their types.
    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    @Override
    public boolean refusesArgumentCountByType(final int count) {
        return count == GENERATOR;
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position, final int argumentCount,
                                                     final DataType temporal) {
        return position == GENERATOR ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }
}
