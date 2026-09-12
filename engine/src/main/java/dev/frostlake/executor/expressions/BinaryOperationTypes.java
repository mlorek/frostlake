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
import dev.frostlake.types.BinaryWidthSpelling;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import java.util.Map;

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

    /** Division gains this much scale over the dividend's own. */
    private static final int DIVISION_SCALE_GAIN = 6;
    /** The scale a product keeps at least once its operands' scales no longer add within the cap. */
    private static final int MULTIPLICATION_SCALE_FLOOR = 12;
    /** The most a quotient's scale grows to from its dividend's, unless the dividend already had more. */
    private static final int DIVISION_SCALE_CAP = 12;

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
        if (operator == null) {
            return null;
        }
        if (operator == BinaryOperator.CONCAT) {
            // Concatenation alone survives ONE untyped operand — the width is unknown, not the type.
            return concatenation(left, right);
        }
        if (isPredicate(operator)) {
            // A comparison, AND, OR and the LIKE family answer a BOOLEAN whatever their operands —
            // live declares NULL = 1, 1 AND 0, NOT 1 and a VARIANT beside a number as BOOLEAN alike —
            // so an untyped operand does not untype the predicate the way it does an arithmetic result.
            return BooleanType.BOOLEAN;
        }
        if (left == null || right == null) {
            return null;
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
        if (operator == BinaryOperator.AND || operator == BinaryOperator.OR) {
            // A logical operator reads a BOOLEAN, a NUMBER, or a text it converts at run time; every
            // other family is an argument-type refusal at compile time, so it lands over no rows too.
            return isLogicalOperand(left) && isLogicalOperand(right) ? null
                : invalidArguments(operator == BinaryOperator.AND ? "AND" : "OR", left, right);
        }
        final String matching = MATCHING_NAMES.get(operator);
        if (matching != null && (left instanceof VectorType || right instanceof VectorType)) {
            // A VECTOR is no text for a pattern match either — refused by the argument types, under
            // the name the NOT spellings share with their plain form (live-verified).
            return invalidArguments(matching, left, right);
        }
        final String arithmetic = ARITHMETIC_SYMBOLS.get(operator);
        if (arithmetic != null && isScaling(operator)
                && (left instanceof DateTimeType || right instanceof DateTimeType)) {
            // A temporal value is shifted, never scaled: only + and - take one, and which pairs they
            // take is the measured table below.
            return invalidArguments(arithmetic, left, right);
        }
        if (arithmetic != null && (!isArithmeticOperand(left) || !isArithmeticOperand(right))) {
            // A family arithmetic does not read at all. A text is NOT one of them: it converts where
            // it is read, and an unreadable one is a run-time conversion on the account too.
            return invalidArguments(arithmetic, left, right);
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
    /** The name each matching operator is refused under — a NOT spelling reports its plain form. */
    private static final Map<BinaryOperator, String> MATCHING_NAMES = Map.of(
        BinaryOperator.LIKE, "LIKE",
        BinaryOperator.NOT_LIKE, "LIKE",
        BinaryOperator.ILIKE, "ILIKE",
        BinaryOperator.NOT_ILIKE, "ILIKE");

    /** The symbol each arithmetic operator is named by in a refusal. */
    private static final Map<BinaryOperator, String> ARITHMETIC_SYMBOLS = Map.of(
        BinaryOperator.ADD, "+",
        BinaryOperator.SUBTRACT, "-",
        BinaryOperator.MULTIPLY, "*",
        BinaryOperator.DIVIDE, "/",
        BinaryOperator.MODULO, "%");

    /** Whether an operator SCALES rather than shifts — the three no temporal value may stand beside. */
    private static boolean isScaling(final BinaryOperator operator) {
        return operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
            || operator == BinaryOperator.MODULO;
    }

    /**
     * Whether a family may stand beside an arithmetic operator: a number, a text or a VARIANT it
     * converts, or a temporal value the measured temporal table then judges. BOOLEAN, BINARY, ARRAY and
     * OBJECT are refused before any row (live-verified).
     *
     * @param type the operand's type, or null when it could not be typed
     * @return whether arithmetic takes it
     */
    public static boolean isArithmeticOperand(final DataType type) {
        return type == null || type instanceof NumericType || type instanceof StringType
            || type instanceof VariantType || type instanceof DateTimeType;
    }

    /**
     * Whether a family may stand beside a logical operator. BOOLEAN and NUMBER are read directly, and a
     * text is converted where it is READ — {@code TRUE OR 'a'} is true on the account because OR never
     * reads the second operand, while {@code 'a' OR 'a'} raises the conversion at run time. Everything
     * else — BINARY, ARRAY, OBJECT and the temporal families — is refused before any row (live-verified).
     *
     * @param type the operand's type, or null when it could not be typed
     * @return whether the operator takes it
     */
    public static boolean isLogicalOperand(final DataType type) {
        return type == null || type instanceof BooleanType || type instanceof NumericType
            || type instanceof StringType || type instanceof VariantType;
    }

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

    private static boolean isPredicate(final BinaryOperator operator) {
        return operator == BinaryOperator.EQUAL || operator == BinaryOperator.NOT_EQUAL
            || operator == BinaryOperator.LESS_THAN || operator == BinaryOperator.LESS_THAN_OR_EQUAL
            || operator == BinaryOperator.GREATER_THAN || operator == BinaryOperator.GREATER_THAN_OR_EQUAL
            || operator == BinaryOperator.AND || operator == BinaryOperator.OR
            || operator == BinaryOperator.LIKE || operator == BinaryOperator.ILIKE
            || operator == BinaryOperator.NOT_LIKE || operator == BinaryOperator.NOT_ILIKE;
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
            // Operands at DIFFERENT scales give an integer part of three digits at least: live types
            // 2 + 3.5 as NUMBER(4,1), 1.5 + 2.25 as NUMBER(5,2) and NUMBER(1,1) + NUMBER(2,2) as
            // NUMBER(5,2), where equal scales keep the plain width — 2 + 3 is NUMBER(2,0) and
            // NUMBER(1,1) + NUMBER(1,1) NUMBER(2,1). Live-verified over every pair of eighteen declared
            // types, sums and differences alike; wider operands follow the plain rule either way.
            final int floor = left.getScale() != right.getScale() ? 2 : 0;
            return number(Math.max(Math.max(leadingLeft, leadingRight), floor) + 1 + scale, scale);
        }
        if (operator == BinaryOperator.MULTIPLY) {
            // The scales add until the sum runs past twelve, and then the wider operand's scale is
            // kept, twelve at least: NUMBER(38,12) * 1.5 is NUMBER(38,12), NUMBER(20,10) squared
            // NUMBER(32,12), NUMBER(30,20) squared NUMBER(38,20), NUMBER(38,35) squared NUMBER(38,35)
            // (live-verified); the integer digits add, and the whole saturates at 38.
            final int scale = Math.min(left.getScale() + right.getScale(),
                Math.max(Math.max(left.getScale(), right.getScale()), MULTIPLICATION_SCALE_FLOOR));
            return number(leadingLeft + leadingRight + scale, scale);
        }
        if (operator == BinaryOperator.DIVIDE) {
            // The dividend's scale gains six, capped at twelve, and never loses what it had:
            // NUMBER(38,12) / 0.5 is NUMBER(38,12), NUMBER(30,20) / 3 NUMBER(30,20), NUMBER(10,2) / 0.5
            // NUMBER(17,8) (live-verified).
            final int scale = Math.max(left.getScale(),
                Math.min(left.getScale() + DIVISION_SCALE_GAIN, DIVISION_SCALE_CAP));
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
        // A string beside an UNTYPED operand — a bare NULL — is a width nothing bounds, and live
        // spells that as the 128MB unknown length: SYSTEM$TYPEOF(NULL || 'x') is VARCHAR(134217728).
        if (left instanceof StringType && right == null || right instanceof StringType && left == null) {
            return new StringType("VARCHAR", MAX_LENGTH);
        }
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof StringType && right instanceof StringType) {
            return new StringType("VARCHAR", Math.min(((StringType) left).getMaxLength()
                + ((StringType) right).getMaxLength(), MAX_LENGTH));
        }
        if (left instanceof BinaryType && right instanceof BinaryType) {
            // A binary pair adds its widths up to BINARY's 64MB maximum and is unsized past it:
            // BINARY(8388608) || BINARY(5) is BINARY(8388613) and two 8MB binaries BINARY(16777216)
            // (live-verified). An unsized operand leaves nothing to add, and the pair is unsized itself.
            long sized = 0;
            boolean unsizedOperand = false;
            for (final BinaryType operand : new BinaryType[] {(BinaryType) left, (BinaryType) right}) {
                if (operand.getWidthSpelling() == BinaryWidthSpelling.DECLARED) {
                    sized += operand.getMaxLength();
                } else {
                    unsizedOperand = true;
                }
            }
            return BinaryType.concatenation(sized, unsizedOperand);
        }
        if (left instanceof BinaryType || right instanceof BinaryType) {
            return null;
        }
        if (left instanceof StringType && convertsToFullWidthText(right)
                || right instanceof StringType && convertsToFullWidthText(left)) {
            return new StringType("VARCHAR", MAX_LENGTH);
        }
        // A VARIANT beside a number converts both to text and answers the same full width: live declares
        // 0 || PARSE_JSON('1') and 1.5::FLOAT || PARSE_JSON('1') VARCHAR(134217728).
        if (left instanceof VariantType && right instanceof NumericType
                || left instanceof NumericType && right instanceof VariantType) {
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
