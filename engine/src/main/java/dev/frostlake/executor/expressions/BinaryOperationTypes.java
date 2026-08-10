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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

/**
 * The type an arithmetic or concatenation expression is statically KNOWN to produce. Every rule below
 * was measured against a live account with both operand types pinned by an explicit cast, so the
 * precision algebra is read off real answers rather than derived from the documentation. Writing
 * {@code L} for an operand's leading digits ({@code p - s}):
 *
 * <ul>
 *   <li>{@code +} and {@code -}: scale is {@code max(s1, s2)} and precision
 *       {@code max(L1, L2) + 1 + scale} — the carry digit is why {@code (1,0) + (1,0)} answers
 *       NUMBER(2,0) and {@code (5,1) + (3,2)} answers NUMBER(7,2).</li>
 *   <li>{@code *}: scale is {@code s1 + s2} and precision {@code p1 + p2} — {@code (10,4) * (10,4)}
 *       answers NUMBER(20,8).</li>
 *   <li>{@code /}: the DIVIDEND alone drives the scale, which gains six digits — scale is
 *       {@code s1 + 6} and precision {@code L1 + s2 + scale}, so {@code (1,0) / (1,0)} answers
 *       NUMBER(7,6) and {@code (5,1) / (3,2)} NUMBER(13,7).</li>
 *   <li>{@code %}: scale is {@code max(s1, s2)} and the leading digits {@code max(L1, L2, 2)} — the
 *       floor of TWO leading digits is what makes {@code (1,0) % (1,0)} answer NUMBER(2,0) and
 *       {@code (3,3) % (3,3)} answer NUMBER(5,3), where the operands' own widths would give one and
 *       three.</li>
 *   <li>Precision saturates at 38 rather than overflowing: {@code (38,0) + (1,0)} stays NUMBER(38,0),
 *       {@code (38,0) / (1,0)} answers NUMBER(38,6) and {@code (38,0) % (11,10)} NUMBER(38,10).</li>
 *   <li>{@code ||}: two strings add their lengths, saturating at a VARCHAR's maximum —
 *       {@code VARCHAR(4)} with {@code VARCHAR(9)} answers VARCHAR(13) — and two binaries add theirs.
 *       A string beside a number, a temporal, a boolean or a VARIANT answers the FULL-WIDTH VARCHAR in
 *       either order, and a BINARY beside anything else is refused by live rather than typed.</li>
 *   <li>FLOAT is contagious: mixed with any exact numeric the answer is FLOAT.</li>
 *   <li>A DATE plus or minus a number of days is still a DATE, in either order for addition, and a
 *       DATE difference is a day count — {@code NUMBER(9,0)}.</li>
 * </ul>
 *
 * <p>Anything not on that list answers null, which leaves the expression untyped rather than guessed at.
 */
public final class BinaryOperationTypes {

    /** Snowflake's widest NUMBER, and where the arithmetic rules saturate. */
    private static final int MAX_PRECISION = 38;

    /**
     * Where a concatenation saturates, and the width it takes when the other operand carries none:
     * the 128MB conversion width, not the 16MB a declared column defaults to. Live reads
     * {@code t || t} over two 16MB columns as VARCHAR(33554432) — twice 16MB, so nothing saturates
     * there — and nine of them as VARCHAR(134217728), which is where it stops.
     */
    private static final int MAX_LENGTH = StringResultWidths.UNBOUNDED;

    /** A BINARY's maximum length. */
    private static final int MAX_BINARY_LENGTH = 8388608;

    /** Division gains this much scale over the dividend's own. */
    private static final int DIVISION_SCALE_GAIN = 6;

    /** A modulo result never has fewer leading digits than this, however narrow its operands are. */
    private static final int MIN_MODULO_LEADING = 2;

    /** The width of the day count a DATE difference answers. */
    private static final int DATE_DIFFERENCE_PRECISION = 9;

    private BinaryOperationTypes() {
    }

    /**
     * The result type of {@code left operator right}, or null when it cannot be determined.
     *
     * @param operator the operator applied
     * @param left     the left operand's static type, or null when it has none
     * @param right    the right operand's static type, or null when it has none
     * @return the statically known result type, or null
     */
    public static DataType resultOf(final BinaryOperator operator, final DataType left,
                                    final DataType right) {
        if (operator == null || left == null || right == null) {
            return null;
        }
        if (operator == BinaryOperator.CONCAT) {
            return concatenation(left, right);
        }
        if (!isArithmetic(operator)) {
            return null;
        }
        if (left instanceof DateTimeType || right instanceof DateTimeType) {
            return dateArithmetic(operator, left, right);
        }
        if (!(left instanceof NumericType) || !(right instanceof NumericType)) {
            return null;
        }
        if (isApproximate(left) || isApproximate(right)) {
            return NumericType.FLOAT;
        }
        return exactArithmetic(operator, (NumericType) left, (NumericType) right);
    }

    /**
     * The refusal an operand pair earns, or null when the pair is legal or unjudged. Live answers
     * {@code Invalid argument types for function '||': (BINARY(4), VARCHAR(4))} — the operator quoted,
     * both DECLARED types spelled in the order written. Deriving it from the static types rather than
     * from the runtime values is what makes those declared widths available at all: a {@code X'AB'}
     * sitting in a BINARY(4) column is one byte long, so a value-based check could only ever say
     * BINARY(1).
     *
     * <p>Only the BINARY concatenation mix is judged here so far. Two binaries concatenate legally,
     * and every other pairing this class knows about is either legal or was measured without its full
     * sentence being captured.
     *
     * @param operator the operator applied
     * @param left     the left operand's static type, or null when it has none
     * @param right    the right operand's static type, or null when it has none
     * @return the refusal detail, or null
     */
    public static String refusalFor(final BinaryOperator operator, final DataType left,
                                    final DataType right) {
        if (left == null || right == null) {
            return null;
        }
        if (operator == BinaryOperator.CONCAT) {
            // Only the BINARY mix is judged here. The CONTAINER families (OBJECT, ARRAY, the geo
            // pair) are refused too, but by rejectSemiStructuredConcatOperand, which already owns
            // that rule and sees the operands themselves — judging it in two places is how the two
            // sites drift apart.
            return left instanceof BinaryType ^ right instanceof BinaryType
                ? invalidArguments("||", left, right) : null;
        }
        if (operator != BinaryOperator.ADD && operator != BinaryOperator.SUBTRACT) {
            return null;
        }
        return refusedTemporalPair(operator, left, right)
            ? invalidArguments(operator == BinaryOperator.ADD ? "+" : "-", left, right) : null;
    }

    /**
     * The temporal pairs live refuses, and ONLY those — each measured in the order written here, so an
     * unmeasured order stays unjudged rather than being refused on symmetry:
     *
     * <ul>
     *   <li>a TIMESTAMP or a TIME shifted by a number, either operator — where the same shift of a
     *       DATE is legal;</li>
     *   <li>a TIME against another TIME, either operator — where TIMESTAMP minus TIMESTAMP is legal
     *       and answers an interval;</li>
     *   <li>a TIMESTAMP and a DATE subtracted in either order — where DATE minus DATE is legal.</li>
     * </ul>
     */
    /**
     * Whether {@code +} or {@code -} over this operand pair is refused, given at least one temporal
     * operand. Stated as the LEGAL set and refusing the rest, because that set is small and the
     * refused one is not: over DATE / TIME / TIMESTAMP / NUMBER there are 32 pairs, and only these
     * five are accepted (live-verified, whole matrix measured):
     *
     * <pre>
     *   +   DATE + NUMBER      NUMBER + DATE
     *   -   DATE - NUMBER      DATE - DATE      TIMESTAMP - TIMESTAMP
     * </pre>
     *
     * <p>Note the asymmetry that a "temporal ± numeric is fine" reading would miss: {@code NUMBER + DATE}
     * is accepted but {@code NUMBER - DATE} is refused, and a TIMESTAMP takes no numeric operand at all
     * on either side. TIME is refused against everything, itself included.
     */
    private static boolean refusedTemporalPair(final BinaryOperator operator, final DataType left,
                                               final DataType right) {
        if (!isTemporal(left) && !isTemporal(right)) {
            return false;
        }
        if (operator == BinaryOperator.ADD) {
            return !(isDate(left) && right instanceof NumericType
                || left instanceof NumericType && isDate(right));
        }
        return !(isDate(left) && right instanceof NumericType
            || isDate(left) && isDate(right)
            || isTimestamp(left) && isTimestamp(right));
    }

    private static boolean isTemporal(final DataType type) {
        return isDate(type) || isTime(type) || isTimestamp(type);
    }

    /** Live's argument-type sentence: the operator quoted, both types spelled, in written order. */
    private static String invalidArguments(final String operator, final DataType left,
                                           final DataType right) {
        return "Invalid argument types for function '" + operator + "': ("
            + SqlTypeNames.canonical(left) + ", " + SqlTypeNames.canonical(right) + ")";
    }

    /** A TIME, not a TIMESTAMP — the canonical spellings of both begin with the same four letters. */
    private static boolean isTime(final DataType type) {
        return type instanceof DateTimeType && SqlTypeNames.canonical(type).startsWith("TIME(");
    }

    /** Any of the TIMESTAMP variants. */
    private static boolean isTimestamp(final DataType type) {
        return type instanceof DateTimeType && SqlTypeNames.canonical(type).startsWith("TIMESTAMP");
    }

    private static boolean isArithmetic(final BinaryOperator operator) {
        return operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
            || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
            || operator == BinaryOperator.MODULO;
    }

    private static boolean isApproximate(final DataType type) {
        return "FLOAT".equals(SqlTypeNames.canonical(type));
    }

    private static DataType exactArithmetic(final BinaryOperator operator, final NumericType left,
                                            final NumericType right) {
        final int leadingLeft = left.getPrecision() - left.getScale();
        final int leadingRight = right.getPrecision() - right.getScale();
        if (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT) {
            final int scale = Math.max(left.getScale(), right.getScale());
            return number(Math.max(leadingLeft, leadingRight) + 1 + scale, scale);
        }
        if (operator == BinaryOperator.MULTIPLY) {
            return number(left.getPrecision() + right.getPrecision(),
                left.getScale() + right.getScale());
        }
        if (operator == BinaryOperator.DIVIDE) {
            final int scale = left.getScale() + DIVISION_SCALE_GAIN;
            return number(leadingLeft + right.getScale() + scale, scale);
        }
        if (operator == BinaryOperator.MODULO) {
            final int scale = Math.max(left.getScale(), right.getScale());
            final int leading = Math.max(Math.max(leadingLeft, leadingRight), MIN_MODULO_LEADING);
            return number(leading + scale, scale);
        }
        return null;
    }

    /** A NUMBER saturated at the widest Snowflake allows, or null if the scale cannot be honoured. */
    private static NumericType number(final int precision, final int scale) {
        if (scale > MAX_PRECISION) {
            return null;
        }
        return new NumericType("NUMBER", Math.min(precision, MAX_PRECISION), scale);
    }

    /**
     * Concatenation, all live-measured: two strings add their lengths, two binaries likewise, and a
     * string beside a number, a temporal, a boolean or a VARIANT answers the FULL-WIDTH VARCHAR in
     * either order — the converted operand's width is not carried through.
     *
     * <p>A BINARY mixed with anything but another BINARY is REFUSED by live rather than typed
     * ({@code Invalid argument types for function '||': (BINARY(4), VARCHAR(4))}), so it answers null
     * here rather than being given a type Frostlake would then happily evaluate.
     */
    private static DataType concatenation(final DataType left, final DataType right) {
        if (left instanceof StringType && right instanceof StringType) {
            return new StringType("VARCHAR", Math.min(((StringType) left).getMaxLength()
                + ((StringType) right).getMaxLength(), MAX_LENGTH));
        }
        if (left instanceof BinaryType && right instanceof BinaryType) {
            // A binary pair saturates at BINARY's own maximum, exactly as a string pair saturates at
            // VARCHAR's: BINARY(8388608) || BINARY(4) is BINARY(8388608), not an error (live-verified).
            final int combined = ((BinaryType) left).getMaxLength() + ((BinaryType) right).getMaxLength();
            // Not the fixed spelling: a concatenation is a width nobody declared, and live reads it
            // fixed false even when both operands are declared BINARY columns.
            return new BinaryType("VARBINARY", Math.min(combined, MAX_BINARY_LENGTH));
        }
        if (left instanceof BinaryType || right instanceof BinaryType) {
            return null;
        }
        if (left instanceof StringType && convertsToFullWidthText(right)
                || right instanceof StringType && convertsToFullWidthText(left)) {
            return new StringType("VARCHAR", MAX_LENGTH);
        }
        return null;
    }

    /** The families measured to give a full-width VARCHAR when concatenated with a string. */
    private static boolean convertsToFullWidthText(final DataType type) {
        return type instanceof NumericType || type instanceof DateTimeType
            || type instanceof BooleanType || type instanceof VariantType;
    }

    /**
     * The temporal shapes live accepts AND that can be typed here, all measured:
     *
     * <ul>
     *   <li>A DATE shifted by any numeric is still a DATE, and ADDITION commutes — {@code 1 + d}
     *       answers DATE too. Only addition: the reversed subtraction is not claimed.</li>
     *   <li>A DATE difference is a count of DAYS, {@code NUMBER(9,0)}.</li>
     * </ul>
     *
     * <p>Two accepted shapes are deliberately left untyped because Frostlake has no INTERVAL type to
     * answer with: {@code timestamp - timestamp} and {@code ltz - ntz} both give
     * {@code INTERVAL DAY(9) TO SECOND(9)} on live. Everything else in this area is REFUSED by live
     * rather than typed — a TIMESTAMP or TIME shifted by a number, a TIME difference, and any
     * DATE/TIMESTAMP mix — so answering null here keeps this class out of the way of the refusal
     * those shapes deserve.
     */
    private static DataType dateArithmetic(final BinaryOperator operator, final DataType left,
                                           final DataType right) {
        final boolean shift = operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT;
        if (shift && isDate(left) && right instanceof NumericType) {
            return left;
        }
        if (operator == BinaryOperator.ADD && left instanceof NumericType && isDate(right)) {
            return right;
        }
        if (operator == BinaryOperator.SUBTRACT && isDate(left) && isDate(right)) {
            return new NumericType("NUMBER", DATE_DIFFERENCE_PRECISION, 0);
        }
        return null;
    }

    private static boolean isDate(final DataType type) {
        return type instanceof DateTimeType && "DATE".equals(SqlTypeNames.canonical(type));
    }
}
