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

package dev.frostlake.functions.aggregate;

import dev.frostlake.executor.AggregateRangeRefusal;
import dev.frostlake.executor.NumericConversionException;
import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.executor.expressions.RawOverflowKind;
import dev.frostlake.executor.expressions.RawRangeOverflow;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Numeric helpers shared by every SUM / AVG code path (the fast-path plan, the general grouped evaluator,
 * the generic dispatch, the accumulators and the window frame). SUM preserves its argument's integer-ness
 * the way Snowflake does: summing an INTEGER / NUMBER(p,0) column yields a whole number (SUM(i)::VARCHAR is
 * "6", not "6.0"), while a DECIMAL or FLOAT argument keeps the prior double result. The integer sum is exact
 * (BigInteger), returned as a {@code Long} when it fits and a {@code BigInteger} otherwise (Snowflake's
 * NUMBER(38,0) range). A VARIANT input makes either aggregate DOUBLE (live-verified: SUM over
 * PARSE_JSON('1'), PARSE_JSON('2') is 3.0 with SYSTEM$TYPEOF FLOAT). AVG of fixed-point inputs is a
 * BigDecimal with scale = (max input scale) + 6, rounded HALF_UP (live-verified: AVG of the integers 90 and
 * 95 is exactly 92.500000).
 */
public final class AggregateNumerics {

    private AggregateNumerics() {
    }

    /**
     * SUM over {@code values} (nulls are ignored). Returns null when no non-null value is present (Snowflake
     * SUM of an empty set is NULL). All-integer input yields a Long/BigInteger; any decimal/float/VARIANT
     * input keeps the double sum (live-verified: SUM over a VARIANT column is DOUBLE even for whole-number
     * JSON values).
     *
     * @param values the aggregated argument values, in group order
     * @return the sum — Long/BigInteger for all-integral input, BigDecimal for fixed-point, Double
     *     otherwise — or null for an empty set
     */
    public static Object sum(final Iterable<Object> values) {
        return sum(values, false);
    }

    /**
     * The {@code variantArgument} flag is for a SUM whose ARGUMENT is declared VARIANT even though the
     * runtime values arrive as plain numbers — live, {@code SUM(v:b)} over
     * whole-number JSON values is DOUBLE (7.0, SYSTEM$TYPEOF FLOAT): the declared type decides the
     * tier exactly as a runtime {@link VariantValue} does.
     *
     * <p>A DOUBLE sum is the aggregate's: added with Kahan compensation and read back with its last
     * compensation applied — live, SUM(x * x) over 0.3 then 0.7 is 0.57999999999999984901, where the
     * running sum is 0.57999999999999996003 (see {@link PartialFloatSum}).
     *
     * @param values the aggregated argument values, in group order
     * @param variantArgument whether the argument expression is statically VARIANT-typed, which forces
     *     the DOUBLE tier
     * @return the sum on the tier the inputs select, or null for an empty set
     */
    public static Object sum(final Iterable<Object> values, final boolean variantArgument) {
        if (IntervalSums.holdsIntervals(values)) {
            return IntervalSums.sum(values);
        }
        return sumOfPartials(Collections.singletonList(values), variantArgument, false);
    }

    /**
     * SUM read as a RUNNING sum: every value its own partial, so the double's last compensation is left
     * unapplied (see {@link PartialFloatSum}). SUM(DISTINCT) answers this way — live, over 0.3, 0.7 and
     * 0.3 squared it is 0.57999999999999996003 where SUM is 0.57999999999999984901. The exact tiers are
     * the same sum either way.
     *
     * @param values the aggregated argument values, in group order
     * @param variantArgument whether the argument expression is statically VARIANT-typed
     * @return the sum on the tier the inputs select, or null for an empty set
     */
    public static Object runningSum(final Iterable<Object> values, final boolean variantArgument) {
        if (IntervalSums.holdsIntervals(values)) {
            return IntervalSums.sum(values);
        }
        return sumOfPartials(Collections.singletonList(values), variantArgument, true);
    }

    /**
     * SUM over a window frame given as the PARTIALS its double is read in (see {@link PartialFloatSum}):
     * the whole partition is one partial, a cumulative RANGE frame one per peer group, and
     * {@code rowWise} makes every value a partial of its own, as a running or sliding frame reads it.
     *
     * @param partials the frame's values, partial by partial, in partition order
     * @param variantArgument whether the argument expression is statically VARIANT-typed
     * @param rowWise whether every value is a partial of its own
     * @return the sum on the tier the inputs select, or null for an empty frame
     */
    public static Object windowSum(final List<? extends Iterable<Object>> partials, final boolean variantArgument,
                                   final boolean rowWise) {
        return sumOfPartials(partials, variantArgument, rowWise);
    }

    private static Object sumOfPartials(final List<? extends Iterable<Object>> partials,
                                        final boolean variantArgument, final boolean rowWise) {
        // Three tiers, live-verified: integral inputs sum to an integer; FIXED-POINT decimals (incl.
        // scale-0 BigDecimals produced by ::NUMBER casts) sum to a BigDecimal keeping their scale
        // (SUM over NUMBER(5,1) is NUMBER(17,1)); any FLOAT or VARIANT input makes the sum DOUBLE.
        boolean any = false;
        boolean anyDouble = variantArgument;
        boolean anyDecimalScale = false;
        // Every input joins the double sum IN ORDER, exact ones converted at their own position — the
        // sum only counts when some input makes the result approximate, and then that is how the
        // account adds them: each value a double, compensated, each partial read with its correction.
        final PartialFloatSum doubleSum = new PartialFloatSum();
        BigDecimal decimalSum = BigDecimal.ZERO;
        for (final Iterable<Object> partial : partials) {
            for (final Object v : partial) {
                if (v == null) {
                    continue;
                }
                any = true;
                if (v instanceof VariantValue) {
                    anyDouble = true;
                    doubleSum.add(variantDouble((VariantValue) v));
                } else if (isApproximateInput(v)) {
                    anyDouble = true;
                    doubleSum.add(NumericAggregateInput.asDouble(v));
                } else {
                    final BigDecimal fixed = fixedPointValue(v);
                    if (fixed != null) {
                        if (fixed.scale() > 0) {
                            anyDecimalScale = true;
                        }
                        decimalSum = decimalSum.add(fixed);
                        // The grouped, DISTINCT and window spellings all sum here, and all three are refused
                        // at the row whose raw total leaves the carrier (live-verified).
                        SumAccumulator.requireSumFits(decimalSum);
                        doubleSum.add(fixed.doubleValue());
                    } else {
                        anyDouble = true;
                        doubleSum.add(toDouble(v));
                    }
                }
                if (rowWise) {
                    doubleSum.endPartial();
                }
            }
            doubleSum.endPartial();
        }
        if (!any) {
            return null;
        }
        if (anyDouble) {
            return doubleSum.result();
        }
        if (anyDecimalScale) {
            return decimalSum;
        }
        final BigInteger integralSum = decimalSum.toBigIntegerExact();
        if (integralSum.bitLength() < 63) {
            return integralSum.longValue();
        }
        return integralSum;
    }

    /**
     * AVG over {@code values} (nulls are ignored). Returns null when no non-null value is present (Snowflake
     * AVG of an empty set is NULL). All fixed-point input (Long/Integer/BigDecimal — not Double/Float) yields
     * a BigDecimal with scale = (max input scale) + 6, rounded HALF_UP and with trailing zeros KEPT — AVG of
     * the integers 90 and 95 is exactly 92.500000 and AVG(2, 2) renders 2.000000, matching live Snowflake.
     * Any Double/Float or VARIANT input keeps the double average.
     *
     * @param values the aggregated argument values, in group order
     * @return the average — a scaled BigDecimal for all fixed-point input, a Double otherwise — or
     *     null for an empty set
     */
    public static Object avg(final Iterable<Object> values) {
        return avg(values, false);
    }

    /**
     * See {@link #sum(Iterable, boolean)} — the same declared-VARIANT rule applied to AVG.
     *
     * @param values the aggregated argument values, in group order
     * @param variantArgument whether the argument expression is statically VARIANT-typed, which keeps
     *     the average on the double path
     * @return the average on the tier the inputs select, or null for an empty set
     */
    public static Object avg(final Iterable<Object> values, final boolean variantArgument) {
        if (IntervalSums.holdsIntervals(values)) {
            return IntervalSums.average(values);
        }
        return average(values, variantArgument, false);
    }

    /**
     * AVG over a NON-CUMULATIVE window — a bare {@code OVER ()}, a PARTITION BY, or an ORDER BY carrying
     * a ROWS frame. Live declares three decimals wider than the input there rather than six, and
     * TRUNCATES to that scale instead of rounding.
     *
     * <p>Both halves are measured. Over NUMBER(10,2) the declared type is NUMBER(25,5) against the
     * cumulative NUMBER(28,8); and seven rows summing to 1.00 average to 0.14285, where a half-up round
     * of 0.142857142857… would give 0.14286. Rounding here would produce the wrong last digit.
     *
     * <p>A RANGE frame is CUMULATIVE and does not come here — see
     * {@code WindowFunctionExpression.isRowsFramed}.
     *
     * @param values the aggregated argument values, in partition order
     * @param variantArgument whether the argument is statically VARIANT, which keeps the double path
     * @return the average at the window's own declared scale, or null for an empty set
     */
    public static Object nonCumulativeWindowAvg(final Iterable<Object> values,
                                                final boolean variantArgument) {
        return average(values, variantArgument, true);
    }

    /**
     * The scale an exact AVG answers at: six more than the input's, capped at twelve, and never
     * narrower than the input — the declared width's own rule, live-verified from NUMBER(5,3) to
     * NUMBER(38,37).
     *
     * @param inputScale the widest input scale
     * @return the answer's scale
     */
    static int averageScale(final int inputScale) {
        return Math.max(inputScale, Math.min(inputScale + 6, 12));
    }

    /**
     * The scale an exact VARIANCE answers at: twice the input's plus six, capped at twelve, and never
     * narrower than the input.
     *
     * @param inputScale the widest input scale
     * @return the answer's scale
     */
    static int varianceScale(final int inputScale) {
        return Math.max(inputScale, Math.min(12, 2 * inputScale + 6));
    }

    /**
     * A value presented at a declared scale that has reached 38, judged the way the account judges
     * it: the declared type is NUMBER(38,38) and a value with any digit before the point cannot be
     * held in it, which live refuses at ROW time — "Number out of representable range: type
     * FIXED[SB16](38,38){nullable}, value 3" — where a compile-time refusal was not possible
     * because the type itself is still legal.
     *
     * @param presented the value at its declared scale
     * @param shownText the value as the refusal prints it
     * @param scale     the declared scale
     * @return the value, when it fits
     */
    private static BigDecimal fitOrRefuse(final BigDecimal presented, final String shownText,
                                          final int scale) {
        if (presented.precision() > 38) {
            throw new AggregateRangeRefusal(NumericRangeRefusal.typedText("SB16", 38, scale, true, shownText));
        }
        return presented;
    }

    /**
     * @param wholePartitionWindow whether this is the non-cumulative WINDOW spelling, which answers
     *     three decimals wider than the input, truncated, where the aggregate answers at
     *     {@link #averageScale} rounded half up
     */
    private static Object average(final Iterable<Object> values, final boolean variantArgument,
                                  final boolean wholePartitionWindow) {
        return averageOfPartials(Collections.singletonList(values), variantArgument, wholePartitionWindow, false);
    }

    /**
     * AVG over a window frame given as the PARTIALS its double sum is read in, as
     * {@link #windowSum(List, boolean, boolean)} reads SUM's — the whole partition one partial, a
     * cumulative RANGE frame one per peer group, and {@code rowWise} for a running or sliding frame —
     * at a cumulative window's scale, or three decimals narrower and truncated for every other shape
     * ({@link #nonCumulativeWindowAvg}). Live, AVG(x * x) over 0.3 then 0.7 is 0.28999999999999992450
     * over the whole partition and 0.28999999999999998002 on a running frame's last row.
     *
     * @param partials the frame's values, partial by partial, in partition order
     * @param variantArgument whether the argument keeps the double path whatever its values
     * @param nonCumulative whether the window is not cumulative, which narrows and truncates the scale
     * @param rowWise whether every value is a partial of its own
     * @return the average, or null for an empty frame
     */
    public static Object windowAvg(final List<? extends Iterable<Object>> partials, final boolean variantArgument,
                                   final boolean nonCumulative, final boolean rowWise) {
        return averageOfPartials(partials, variantArgument, nonCumulative, rowWise);
    }

    private static Object averageOfPartials(final List<? extends Iterable<Object>> partials,
                                            final boolean variantArgument, final boolean wholePartitionWindow,
                                            final boolean rowWise) {
        boolean any = false;
        boolean anyDouble = variantArgument;
        int maxScale = 0;
        BigDecimal decimalSum = BigDecimal.ZERO;
        final PartialFloatSum doubleSum = new PartialFloatSum();
        long count = 0L;
        for (final Iterable<Object> partial : partials) {
            for (final Object v : partial) {
                if (v == null) {
                    continue;
                }
                any = true;
                count++;
                if (v instanceof VariantValue || isApproximateInput(v)) {
                    anyDouble = true;
                    doubleSum.add(NumericAggregateInput.asDouble(v));
                } else {
                    final BigDecimal fixed = fixedPointValue(v);
                    if (fixed == null) {
                        anyDouble = true;
                        doubleSum.add(toDouble(v));
                    } else {
                        if (wholePartitionWindow) {
                            // The whole-partition window keeps an intermediate three decimals wider than its
                            // input, and live refuses the FIRST input that cannot be widened into it — over a
                            // NUMBER(38,35) the sentence names that input at its own scale, not the average.
                            fitOrRefuse(fixed.setScale(fixed.scale() + 3), fixed.toPlainString(), fixed.scale() + 3);
                        }
                        decimalSum = decimalSum.add(fixed);
                        // The running total is a SUM's, refused as one when it leaves the carrier (live's AVG
                        // over two values summing to 2^127 says "Value overflow in a SUM aggregate").
                        SumAccumulator.requireSumFits(decimalSum);
                        doubleSum.add(fixed.doubleValue());
                        if (fixed.scale() > maxScale) {
                            maxScale = fixed.scale();
                        }
                    }
                }
                if (rowWise) {
                    doubleSum.endPartial();
                }
            }
            doubleSum.endPartial();
        }
        if (!any) {
            return null;
        }
        if (anyDouble) {
            // The compensated sum over the count, both as doubles: AVG over three tenths is
            // 0.10000000000000001943 on the account, which is exactly this quotient, and AVG(x * x) over
            // 0.3 then 0.7 is half the corrected sum, 0.28999999999999992450.
            return doubleSum.result() / count;
        }
        // The division is the operator's: a quotient whose raw at the answer's scale leaves the carrier
        // is refused with the derived type and the quotient as a double — AVG over two values summing
        // to 1e38 is "(38,6){nullable}, value 5e+37" (live-verified).
        final int quotientScale = wholePartitionWindow ? maxScale + 3 : averageScale(maxScale);
        final BigDecimal quotient = decimalSum.divide(BigDecimal.valueOf(count), quotientScale,
            wholePartitionWindow ? RoundingMode.DOWN : RoundingMode.HALF_UP);
        if (quotient.precision() > 38 && NumericRangeRefusal.outsideSb16Window(quotient.unscaledValue())) {
            throw new RawRangeOverflow(RawOverflowKind.QUOTIENT, quotient, quotientScale);
        }
        return quotient;
    }

    /**
     * The exact whole-number value of {@code v} when it is an integer-typed number (Long/Integer/Short/Byte/
     * BigInteger) or a numeric string with no fractional part, else null. A BigDecimal / Double / Float is
     * deliberately treated as non-integral so a DECIMAL / FLOAT column's SUM stays a double.
     */
    private static BigInteger integralValue(final Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return BigInteger.valueOf(((Number) v).longValue());
        }
        if (v instanceof BigInteger) {
            return (BigInteger) v;
        }
        if (v instanceof BigDecimal) {
            // A scale-0 BigDecimal (e.g. a ::NUMBER cast result) IS integral; a scaled one is not.
            final BigDecimal decimal = (BigDecimal) v;
            return decimal.stripTrailingZeros().scale() <= 0 ? decimal.toBigIntegerExact() : null;
        }
        if (v instanceof Number) {
            return null;
        }
        try {
            final BigDecimal decimal = new BigDecimal(v.toString().trim());
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return decimal.toBigIntegerExact();
            }
        } catch (final NumberFormatException notNumeric) {
            // fall through — treated as non-integral / zero
        }
        return null;
    }

    /**
     * The exact fixed-point value of {@code v} when it is an integer-typed number (scale 0), a BigDecimal
     * (its own scale), or an integral numeric string, else null. Double / Float — and fractional strings,
     * mirroring {@link #integralValue(Object)} — are deliberately non-fixed-point so they keep AVG on the
     * double path.
     */
    private static BigDecimal fixedPointValue(final Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return BigDecimal.valueOf(((Number) v).longValue());
        }
        if (v instanceof BigInteger) {
            return new BigDecimal((BigInteger) v);
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return null;
        }
        try {
            final BigDecimal decimal = new BigDecimal(v.toString().trim());
            if (decimal.stripTrailingZeros().scale() <= 0) {
                return new BigDecimal(decimal.toBigIntegerExact());
            }
        } catch (final ArithmeticException | NumberFormatException notIntegral) {
            // fall through — treated as non-fixed-point
        }
        return null;
    }

    /**
     * The double a non-NULL value joins a FLOAT sum as: a VARIANT's or an approximate number's own, an exact
     * number's nearest — the same term {@link #windowSum} and {@link #windowAvg} add.
     *
     * @param value the value
     * @return its double
     */
    public static double doubleTerm(final Object value) {
        if (value instanceof VariantValue || isApproximateInput(value)) {
            return NumericAggregateInput.asDouble(value);
        }
        final BigDecimal fixed = fixedPointValue(value);
        return fixed != null ? fixed.doubleValue() : NumericAggregateInput.asDouble(value);
    }

    /**
     * The double value of a VARIANT member — see {@link NumericAggregateInput}, which owns the
     * conversion and its refusal. Live Snowflake makes SUM / AVG over VARIANT values DOUBLE regardless
     * of the JSON number's shape.
     */
    private static double variantDouble(final VariantValue v) {
        return NumericAggregateInput.asDouble(v);
    }

    private static double toDouble(final Object v) {
        return NumericAggregateInput.asDouble(v);
    }
    /** The extra decimals an INTERPOLATING percentile adds to its input's scale (live-verified). */
    private static final int PERCENTILE_EXTRA_SCALE = 3;
    /** The width a text or VARIANT percentile input is converted to, live: NUMBER(9,0), nine digits and no decimals. */
    private static final int COERCED_WHOLE_DIGITS = 9;

    /**
     * MEDIAN — the interpolating percentile at one half, and typed as one.
     *
     * @param values the aggregated argument values, in group order
     * @return the median on the tier the inputs select, or null for an empty set
     */
    public static Object median(final Iterable<Object> values) {
        return percentileCont(values, 0.5d, false);
    }

    /**
     * MEDIAN with the argument's declared tier — see
     * {@link #percentileCont(Iterable, double, boolean)}.
     *
     * @param values the aggregated argument values, in group order
     * @param approximateArgument whether the argument is declared FLOAT / DOUBLE / REAL
     * @return the median on the tier the declaration and the inputs select, or null for an empty set
     */
    public static Object median(final Iterable<Object> values, final boolean approximateArgument) {
        return percentileCont(values, 0.5d, approximateArgument);
    }

    /**
     * PERCENTILE_CONT — a value INTERPOLATED between two of the inputs, so it is a computed number and
     * carries a computed scale: the widest input scale plus three, live-verified across seven NUMBER
     * widths (a NUMBER(10,2) column gives NUMBER(13,5), and the value 2.00000 rather than 2.0).
     *
     * <p>The exact tier is what makes that possible. Collapsing every input to a double — as this did —
     * loses the scale before there is anything to present at, and prints a small value in SCIENTIFIC
     * notation besides: a median over NUMBER(5,4) came back as 2.0E-4 where live gives 0.0002000. A
     * FLOAT or VARIANT input still selects the double tier, as it does for AVG and SUM.
     *
     * @param values the aggregated argument values, in group order
     * @param fraction the percentile, between 0 and 1
     * @return the interpolated value, or null for an empty set
     */
    public static Object percentileCont(final Iterable<Object> values, final double fraction) {
        return percentileCont(values, fraction, false);
    }

    /**
     * PERCENTILE_CONT with the argument's DECLARED tier, which the values alone cannot always give: a
     * FLOAT-declared value can still arrive in an exact carrier — computed exactly, or restored from an
     * older snapshot — indistinguishable from an exact one, and only the declaration says there is no
     * scale to build on. Live answers a FLOAT
     * percentile as the plain double — 2.5, not 2.5000.
     *
     * @param values the aggregated argument values, in group order
     * @param fraction the percentile, between 0 and 1
     * @param approximateArgument whether the argument is declared FLOAT / DOUBLE / REAL
     * @return the interpolated value, or null for an empty set
     */
    public static Object percentileCont(final Iterable<Object> values, final double fraction,
                                        final boolean approximateArgument) {
        final List<BigDecimal> exact = new ArrayList<>();
        final List<Double> approximate = new ArrayList<>();
        boolean anyDouble = approximateArgument;
        int maxScale = 0;
        for (final Object v : values) {
            if (v == null) {
                continue;
            }
            final BigDecimal fixed = v instanceof VariantValue ? null : fixedPointValue(v);
            if (fixed == null) {
                anyDouble = true;
            } else if (fixed.scale() > maxScale) {
                maxScale = fixed.scale();
            }
            exact.add(fixed);
            approximate.add(Double.valueOf(v instanceof VariantValue
                ? variantDouble((VariantValue) v) : toDouble(v)));
        }
        if (approximate.isEmpty()) {
            return null;
        }
        if (anyDouble) {
            Collections.sort(approximate);
            final double index = fraction * (approximate.size() - 1);
            final int low = (int) index;
            final int high = Math.min(low + 1, approximate.size() - 1);
            final double weight = index - low;
            return approximate.get(low).doubleValue() * (1 - weight)
                + approximate.get(high).doubleValue() * weight;
        }
        Collections.sort(exact);
        final double index = fraction * (exact.size() - 1);
        final int low = (int) index;
        final int high = Math.min(low + 1, exact.size() - 1);
        final BigDecimal weight = BigDecimal.valueOf(index - low);
        final BigDecimal interpolated = exact.get(low).multiply(BigDecimal.ONE.subtract(weight))
            .add(exact.get(high).multiply(weight));
        return fitOrRefuse(interpolated.setScale(maxScale + PERCENTILE_EXTRA_SCALE, RoundingMode.HALF_UP),
            NumericRangeRefusal.valueText(interpolated), maxScale + PERCENTILE_EXTRA_SCALE);
    }

    /**
     * PERCENTILE_DISC — one of the inputs, handed back as it arrived. It computes nothing, so it adds
     * nothing to the scale: the value already carries its column's, which is why this family was right
     * all along wherever the value reached the caller unconverted.
     *
     * @param values the aggregated argument values, in group order
     * @param fraction the percentile, between 0 and 1
     * @return the selected input value, or null for an empty set
     */
    public static Object percentileDisc(final Iterable<Object> values, final double fraction) {
        final List<Object> present = new ArrayList<>();
        for (final Object v : values) {
            if (v != null) {
                // Read for the refusal alone: the value handed back is the input untouched, but live
                // still refuses a set it cannot order numerically, and names the first one in SCAN
                // order — which only holds while this runs before the sort below.
                NumericAggregateInput.requireNumeric(v);
                present.add(v);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        Collections.sort(present, new Comparator<Object>() {
            @Override
            public int compare(final Object left, final Object right) {
                final BigDecimal leftFixed = fixedPointValue(left);
                final BigDecimal rightFixed = fixedPointValue(right);
                if (leftFixed != null && rightFixed != null) {
                    return leftFixed.compareTo(rightFixed);
                }
                return Double.compare(toDouble(left), toDouble(right));
            }
        });
        final int index = (int) Math.ceil(fraction * present.size()) - 1;
        return present.get(Math.max(0, Math.min(index, present.size() - 1)));
    }

    /**
     * MEDIAN over a VARCHAR or VARIANT argument — see {@link #percentileContOverCoerced(Iterable, double)}.
     *
     * @param values the aggregated argument values, in group order
     * @return the median of the whole numbers the values convert to, or null for an empty set
     */
    public static Object medianOverCoerced(final Iterable<Object> values) {
        return percentileContOverCoerced(values, 0.5d);
    }

    /**
     * PERCENTILE_CONT over a VARCHAR or VARIANT key, which live neither reads at its decimals nor orders
     * as numbers: every value is converted to a WHOLE number, NUMBER(9,0), and the inputs are ordered by
     * their OWN type — text as text, so '10' sorts before '8' and '-2.5' before '0.5'. The interpolation
     * then runs between the converted neighbours, at the three extra decimals the family always adds.
     * Live-verified: over '1.5' and '2.25' the median is 2.000 (each rounds to 2, half away from zero),
     * over '2.5' and '3.5' it is 3.500, over '10', '9', '8' it is 8.000, over '-1.5', '-2.5', '0.5' it is
     * -3.000, and a VARCHAR(4) and a VARCHAR(16777216) agree — the nine digits are the conversion's,
     * not the column's.
     *
     * @param values the aggregated key values, in group order
     * @param fraction the percentile, between 0 and 1
     * @return the interpolated value at scale three, or null for an empty set
     */
    public static Object percentileContOverCoerced(final Iterable<Object> values, final double fraction) {
        final List<Object> ordered = orderedByOwnType(values);
        if (ordered.isEmpty()) {
            return null;
        }
        final double index = fraction * (ordered.size() - 1);
        final int low = (int) index;
        final int high = Math.min(low + 1, ordered.size() - 1);
        final BigDecimal weight = BigDecimal.valueOf(index - low);
        final BigDecimal interpolated = wholeNumberOf(ordered.get(low)).multiply(BigDecimal.ONE.subtract(weight))
            .add(wholeNumberOf(ordered.get(high)).multiply(weight));
        return interpolated.setScale(PERCENTILE_EXTRA_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * PERCENTILE_DISC over a VARCHAR or VARIANT key: the inputs ordered by their own type, as for
     * {@link #percentileContOverCoerced(Iterable, double)}, and the picked one handed back as the whole
     * number it converts to — live declares it NUMBER(9,0) and answers 2 for the lower of '1.5' and
     * '2.25'.
     *
     * @param values the aggregated key values, in group order
     * @param fraction the percentile, between 0 and 1
     * @return the selected value's whole number, or null for an empty set
     */
    public static Object percentileDiscOverCoerced(final Iterable<Object> values, final double fraction) {
        final List<Object> ordered = orderedByOwnType(values);
        if (ordered.isEmpty()) {
            return null;
        }
        final int index = (int) Math.ceil(fraction * ordered.size()) - 1;
        final Object picked = ordered.get(Math.max(0, Math.min(index, ordered.size() - 1)));
        return Long.valueOf(wholeNumberOf(picked).longValueExact());
    }

    /**
     * The non-null inputs in the order their OWN type gives — text by text, a VARIANT member by its
     * kind (numbers as numbers, strings as text) — each already proven convertible. Live refuses the
     * whole set for one value that is not a number or does not fit nine digits, whichever position it
     * would take: PERCENTILE_DISC(0) over '1', '2', 'x' is refused although it would never pick the 'x'.
     */
    private static List<Object> orderedByOwnType(final Iterable<Object> values) {
        final List<Object> present = new ArrayList<>();
        for (final Object v : values) {
            if (v != null) {
                wholeNumberOf(v);
                present.add(v);
            }
        }
        Collections.sort(present, new Comparator<Object>() {
            @Override
            public int compare(final Object left, final Object right) {
                return compareByOwnType(left, right);
            }
        });
        return present;
    }

    /** Two VARIANT numbers order as numbers and two VARIANT strings as text; everything else as the engine orders it. */
    private static int compareByOwnType(final Object left, final Object right) {
        if (left instanceof VariantValue && right instanceof VariantValue) {
            // The one VARIANT order: numbers as numbers, text as text, kinds apart — see VariantOrder.
            return ((VariantValue) left).compareTo((VariantValue) right);
        }
        return ValueComparisons.compareValues(left, right);
    }

    /**
     * One text or VARIANT input as the whole number live converts it to: the number the text spells
     * (surrounding whitespace ignored, a sign and an exponent accepted), rounded half away from zero
     * to no decimals, and refused past nine digits with the value's own text — '1234567890' is
     * "out of range" before it rounds and '999999999.5' after, since it rounds to ten digits.
     */
    private static BigDecimal wholeNumberOf(final Object v) {
        final String shown;
        final BigDecimal number;
        if (v instanceof VariantValue) {
            final JsonNode node = ((VariantValue) v).node();
            if (node.isNumber()) {
                shown = node.toString();
                number = node.decimalValue();
            } else if (node.isTextual()) {
                // A string member spelling no number is refused as the VARIANT cast it is, not as the
                // VARCHAR conversion: live says `Failed to cast variant value "x" to FIXED`.
                shown = node.asText();
                number = numberSpelledBy(shown, "Failed to cast variant value " + node.toString() + " to FIXED");
            } else if (node.isBoolean()) {
                // A boolean member converts as 1 / 0: MEDIAN over true and 7 is 4.000 (live-verified).
                shown = node.toString();
                number = node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
            } else {
                throw new NumericConversionException(
                    "Failed to cast variant value " + node.toString() + " to FIXED");
            }
        } else if (v instanceof Number) {
            shown = v.toString();
            number = new BigDecimal(shown);
        } else if (IntervalCells.isInterval(v)) {
            // An interval converts as its cast to a number does: its span in units of its trailing field, so
            // MEDIAN over a TIMESTAMP difference reads whole seconds (live-verified).
            shown = v.toString();
            number = IntervalCasts.numberOf(v);
        } else {
            shown = v.toString();
            number = numberSpelledBy(shown);
        }
        final BigDecimal whole = number.setScale(0, RoundingMode.HALF_UP);
        if (whole.precision() > COERCED_WHOLE_DIGITS) {
            throw new NumericConversionException("Numeric value '" + shown + "' is out of range");
        }
        return whole;
    }

    private static BigDecimal numberSpelledBy(final String text) {
        return numberSpelledBy(text, "Numeric value '" + text + "' is not recognized");
    }

    private static BigDecimal numberSpelledBy(final String text, final String refusal) {
        try {
            return new BigDecimal(text.trim());
        } catch (final NumberFormatException notNumeric) {
            throw new NumericConversionException(refusal);
        }
    }

    /**
     * A VARIANCE-family result presented at the scale its column DECLARES. The declared type is
     * {@code NUMBER(38, min(12, 2s + 6))} for an exact input of scale s — the SQUARE of a scale, capped at
     * twelve, the rule TypeInferencer already applies — and live pads the value out to it: over a
     * NUMBER(10,2) column, VARIANCE answers 2.9166666667 and VARIANCE_POP answers 2.1875000000, both at ten
     * decimals. Computing the statistic and then presenting it are separate steps, and only this one knows
     * the scale.
     *
     * <p>An APPROXIMATE input keeps the double: live types VARIANCE over a FLOAT as FLOAT, where a scaled
     * decimal would render the other way.
     */
    public static Object varianceAtDeclaredScale(final double variance, final int maxInputScale,
                                                 final boolean approximate) {
        if (approximate) {
            return Double.valueOf(variance);
        }
        return BigDecimal.valueOf(variance)
            .setScale(varianceScale(maxInputScale), RoundingMode.HALF_UP);
    }

    /**
     * The STDDEV family's value: the square root of the variance ROUNDED TO ITS OWN DECLARED SCALE.
     *
     * <p>Live-verified, and the reason the same three numbers give two different answers: STDDEV over
     * NUMBER(38,0) is sqrt(2.333333) — the scale-6 variance — while over NUMBER(10,2) it is
     * sqrt(2.3333333333), the scale-10 one. Live is not computing at a hidden precision; it takes the
     * root of a variance that has already been rounded.
     *
     * <p>The result is always a DOUBLE, because live declares FLOAT for every input the family takes,
     * NUMBER ones included — so it renders at the FLOAT text width rather than at a scale.
     */
    public static Double rootOfDeclaredVariance(final double variance, final int maxInputScale,
                                                final boolean approximate) {
        final Object atScale = varianceAtDeclaredScale(variance, maxInputScale, approximate);
        return Double.valueOf(Math.sqrt(((Number) atScale).doubleValue()));
    }

    /** The scale an accumulated input contributes: a decimal's own, zero for an integral value. */
    public static int scaleOfInput(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).scale() : 0;
    }

    /** Whether an accumulated input is APPROXIMATE, which keeps the whole result a double. */
    public static boolean isApproximateInput(final Object value) {
        // TEXT and a VARIANT are here with the doubles because neither carries a declared scale to
        // compute an exact result on: live puts SUM, AVG and the whole variance family on the FLOAT
        // tier the moment their input is a VARCHAR or a VARIANT, whether it holds integers, decimals,
        // exponents or numeric strings (VARIANCE over a VARIANT of 2 and 4 is the FLOAT 2, not 2.000000).
        return value instanceof Double || value instanceof Float || value instanceof CharSequence
            || value instanceof VariantValue;
    }
}
