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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.datetime.DateShiftAmount;
import dev.frostlake.functions.scalar.datetime.ZonedTimestampShift;
import dev.frostlake.values.ApproximateValues;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.HexDoubleText;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantBooleans;
import dev.frostlake.values.VariantJsonNulls;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.XmlVariants;
import dev.frostlake.values.YearMonthInterval;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import tools.jackson.databind.JsonNode;

/**
 * Pure arithmetic, temporal and comparison helpers extracted from {@link ExpressionEvaluatorVisitor}:
 * numeric/date add/subtract/multiply/divide/negate, three-valued truthiness, value equality/ordering and
 * INTERVAL application. Field-free — all inputs arrive as arguments.
 */
public final class ExpressionArithmetic {

    private ExpressionArithmetic() {
    }

    /**
     * Whether an arithmetic operand reads as SQL NULL: either it IS SQL NULL, or it is the VARIANT
     * JSON null, which has no numeric reading. Live-verified with
     * {@code jn = PARSE_JSON('{"b":null}'):b} — {@code jn + 1}, {@code jn - 1} and {@code jn * 2} are
     * all SQL NULL on a real account. Comparisons deliberately do NOT use this: there a JSON null is a
     * value that orders above numbers and strings ({@code jn > 0} is TRUE live, {@code jn = jn} TRUE).
     */
    private static boolean isJsonNullOperand(final Object value) {
        return value == null || VariantJsonNulls.isJsonNull(value);
    }

    /**
     * A value as a number for an arithmetic context, coercing a numeric VARCHAR the way Snowflake does
     * ({@code '3' + 1} is 4, {@code -'3'} is -3 — string concatenation is {@code ||}, never {@code +}).
     * Returns null when the value is neither a number nor a numeric string, so the caller can report its own
     * type error; a non-numeric string is therefore still an error rather than silently zero.
     */
    static Number asNumber(final Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        if (value instanceof CharSequence) {
            final String text = value.toString().trim();
            if (!text.isEmpty()) {
                try {
                    return new BigDecimal(text);
                } catch (final NumberFormatException notNumeric) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * A text as the number it spells where a text converts to FLOAT — beside another text or a FLOAT, and
     * under a sign: a decimal number, or a hexadecimal one ({@code '0x10'} is 16, see {@link HexDoubleText});
     * null for a text that spells neither. Beside a NUMBER a text converts to FIXED instead, which refuses
     * hexadecimal (live-verified). Any other value is {@link #asNumber}'s.
     *
     * @param value the operand
     * @return its number, or null
     */
    static Number floatText(final Object value) {
        final Number decimal = asNumber(value);
        if (decimal != null || !(value instanceof CharSequence)) {
            return decimal;
        }
        return HexDoubleText.withoutExponent(value.toString().trim(), true);
    }

    /**
     * The row-time refusal an arithmetic operator raises over a TEXT operand that reads as no number —
     * live's own sentence, "Numeric value 'x' is not recognized", the LEFT operand named first
     * ({@code 'x' + 'y'} names 'x'), the text echoed trimmed — or the engine's fallback sentence when
     * neither operand is such a text. Over an empty table nothing is refused: the rule is per row.
     */
    private static RuntimeException unreadableOperand(final Object left, final Object right,
                                                      final String fallback) {
        for (final Object operand : new Object[] {left, right}) {
            if (operand instanceof CharSequence && asNumber(operand) == null) {
                return new RuntimeException(
                    NumericRangeRefusal.unreadableText(operand.toString().trim()));
            }
        }
        return new RuntimeException(fallback);
    }

    /**
     * The two operands as numbers when at least one of them is a numeric STRING that needed coercion (so an
     * arithmetic operator can retry), else null. Restricting it to a text operand keeps every other type
     * combination — temporal, interval, variant — on its existing path.
     */
    private static Number[] coerceTextOperands(final Object left, final Object right) {
        if (!(left instanceof CharSequence) && !(right instanceof CharSequence)) {
            return null;
        }
        final Number leftNumber = asNumber(left);
        final Number rightNumber = asNumber(right);
        if (leftNumber == null || rightNumber == null) {
            return hexadecimalAsFloat(left, right);
        }
        // Snowflake's implicit VARCHAR coercion depends on WHAT IT IS MIXED WITH (live-verified):
        //   '3' + 1     -> NUMBER(19,0) 4        a VARCHAR against a NUMBER converts to FIXED-POINT,
        //   '2.5' * 2   -> NUMBER(19,1) 5.0      taking its scale from the text,
        //   '10' - '4'  -> FLOAT 6.0             but VARCHAR against VARCHAR goes to FLOAT.
        // (Unary minus on a VARCHAR is also FLOAT — handled at the negation site.)
        if (left instanceof CharSequence && right instanceof CharSequence) {
            return new Number[] {Double.valueOf(leftNumber.doubleValue()), Double.valueOf(rightNumber.doubleValue())};
        }
        return new Number[] {leftNumber, rightNumber};
    }

    /**
     * Two operands one of which is a hexadecimal text, as doubles, where the text converts to FLOAT: each
     * operand a text or a FLOAT ({@code '0x10' + '0x1'} is 17 and {@code f * '0x2'} over a FLOAT 16 is 32,
     * where {@code '0x10' + 1} is refused); null otherwise.
     */
    private static Number[] hexadecimalAsFloat(final Object left, final Object right) {
        if (!(left instanceof CharSequence || ApproximateValues.isApproximate(left))
                || !(right instanceof CharSequence || ApproximateValues.isApproximate(right))) {
            return null;
        }
        final Number leftNumber = floatText(left);
        final Number rightNumber = floatText(right);
        return leftNumber == null || rightNumber == null ? null
            : new Number[] {Double.valueOf(leftNumber.doubleValue()), Double.valueOf(rightNumber.doubleValue())};
    }

    /**
     * The two operands as doubles when at least one of them is a VARIANT holding a number (or numeric
     * text), else null. Snowflake arithmetic over VARIANT operands produces FLOAT (live-verified:
     * {@code PARSE_JSON('1') + 1} → 2.0 FLOAT, {@code TRANSFORM([1,2,3], x -> x + 1)} → doubles).
     */
    private static Number[] coerceVariantOperands(final Object left, final Object right) {
        if (!(left instanceof VariantValue) && !(right instanceof VariantValue)) {
            return null;
        }
        // A variant member that reads as no number fails its CAST to REAL — an object, an array, a
        // string spelling no number — and a JSON boolean reads 1 / 0 (live: true + 1 is 2).
        final Number leftNumber = left instanceof VariantValue
            ? VariantNumbers.numberOf((VariantValue) left, VariantNumbers.REAL) : asNumber(left);
        final Number rightNumber = right instanceof VariantValue
            ? VariantNumbers.numberOf((VariantValue) right, VariantNumbers.REAL) : asNumber(right);
        if (leftNumber == null || rightNumber == null) {
            return null;
        }
        return new Number[] {Double.valueOf(leftNumber.doubleValue()), Double.valueOf(rightNumber.doubleValue())};
    }

    static Number variantAsNumber(final VariantValue variant) {
        final JsonNode node = variant.node();
        if (node != null && node.isNumber()) {
            // NaN and the infinities have no BigDecimal, so they stay doubles — which also puts the
            // arithmetic on its FLOAT path, where IEEE gives the answers live gives.
            if ((node.isDouble() || node.isFloat()) && !Double.isFinite(node.doubleValue())) {
                return Double.valueOf(node.doubleValue());
            }
            return node.decimalValue();
        }
        if (node != null && node.isTextual()) {
            return asNumber(node.asText());
        }
        return null;
    }

    static boolean isTrue(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        // Snowflake implicitly coerces in boolean position: a VARCHAR via TO_BOOLEAN's text forms
        // (a bare `WHERE is_direct` over a VARCHAR column holding 'true' filters as a predicate —
        // the relationship-loader idiom), a number as zero/non-zero. An unrecognized string is
        // FALSE here (Snowflake raises; the engine stays lenient as elsewhere).
        if (value instanceof String) {
            final String text = ((String) value).trim().toLowerCase();
            return text.equals("true") || text.equals("t") || text.equals("yes")
                || text.equals("y") || text.equals("on") || text.equals("1");
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString()).compareTo(BigDecimal.ZERO) != 0;
        }
        return false;
    }

    /** A non-null operand collapses to its truthiness (via {@link #isTrue}); NULL stays null (UNKNOWN). */
    static Boolean booleanOrNull(final Object value) {
        return value == null ? null : Boolean.valueOf(isTrue(value));
    }

    /**
     * {@link #booleanOrNull} for the LOGICAL OPERATORS — AND, OR and NOT — whose string conversion is
     * STRICT live: the TO_BOOLEAN text forms convert and anything else refuses at row time, naming the
     * text with no compilation prefix.
     *
     * <pre>
     *   'true' AND TRUE      true
     *   'x' AND TRUE         Boolean value 'x' is not recognized
     *   '5' AND TRUE         Boolean value '5' is not recognized   digits are NOT read as numbers here
     *   NOT 'x'              Boolean value 'x' is not recognized
     * </pre>
     *
     * <p>A VARIANT converts as a cast to BOOLEAN converts it (see {@link VariantBooleans}): a JSON boolean is
     * itself, a boolean spelling converts, a JSON null is UNKNOWN, and a number, an array, an object or any other
     * text fails the row — {@code NOT TO_VARIANT(TRUE)} is FALSE and {@code NOT PARSE_JSON('1')} is "Failed to
     * cast variant value 1 to BOOLEAN" (live-verified).
     *
     * <p>Kept SEPARATE from {@link #isTrue} on purpose: the lenient false-for-anything reading is what
     * predicate FILTERING relies on ({@code WHERE is_direct} over text — the relationship-loader
     * idiom), and this strictness belongs to the operators alone.
     */
    static Boolean strictBooleanOrNull(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof VariantValue) {
            return VariantBooleans.convert((VariantValue) value);
        }
        if (value instanceof String) {
            final String text = ((String) value).trim().toLowerCase();
            if (text.equals("true") || text.equals("t") || text.equals("yes") || text.equals("y")
                    || text.equals("on") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equals("false") || text.equals("f") || text.equals("no") || text.equals("n")
                    || text.equals("off") || text.equals("0")) {
                return Boolean.FALSE;
            }
            throw new RuntimeException("Boolean value '" + value + "' is not recognized");
        }
        return Boolean.valueOf(isTrue(value));
    }

    static Object add(final Object left, final Object right) {
        return add(left, right, null);
    }

    static Object add(final Object left, final Object right, final SourcePosition at) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic — WIDENING past a long: live's
            // only ceiling is the 128-bit window below, so a sum past 2^63 must not silently wrap.
            if (isIntegerType(left) && isIntegerType(right)) {
                try {
                    return Math.addExact(((Number) left).longValue(), ((Number) right).longValue());
                } catch (final ArithmeticException pastLong) {
                    // fall through to the exact path
                }
            }
            // A FLOAT operand makes the result FLOAT (Snowflake's type propagation).
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() + ((Number) right).doubleValue();
            }
            // Otherwise, use BigDecimal for precision — inside the exact carrier: each operand must be
            // representable at the common scale, and the raw sum must fit the 128-bit window.
            final BigDecimal l = new BigDecimal(left.toString());
            final BigDecimal r = new BigDecimal(right.toString());
            requireRescalable(l, r, true);
            requireRescalable(r, l, false);
            final BigDecimal result = l.add(r);
            requireRawResultFits(result);
            return result;
        }
        final Object intervalSum = DayTimeIntervalArithmetic.add(left, right);
        if (intervalSum != null) {
            return intervalSum;
        }
        final Object monthsSum = YearMonthIntervalArithmetic.add(left, right);
        if (monthsSum != null) {
            return monthsSum;
        }
        // An INTERVAL is added TO a date or a timestamp, and never the reverse: live refuses
        // `INTERVAL '1 day' + d` by its argument types, at the operator.
        final String temporalAfterInterval = left instanceof IntervalValue ? temporalTypeName(right) : null;
        if (temporalAfterInterval != null) {
            final String detail = "Invalid argument types for function '+': (INTERVAL, " + temporalAfterInterval + ")";
            throw new RuntimeException(at != null
                ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
                : SqlCompilationError.of(detail));
        }
        if (ZonedTimestampShift.isZoned(left) && right instanceof IntervalValue) {
            return applyZonedInterval(left, (IntervalValue) right, 1);
        }
        // Date/time addition (commutative): temporal + integer (days) or temporal + INTERVAL. Normalize so
        // `temporal` is the DATE/TIMESTAMP operand and `other` is the integer/interval being added.
        final Object temporal = asTemporal(left) != null ? left : (asTemporal(right) != null ? right : null);
        if (temporal != null) {
            final Object other = temporal == left ? right : left;
            if (other instanceof IntervalValue) {
                return applyInterval(temporal, (IntervalValue) other, 1);
            }
            if (other instanceof Number) {
                rejectTimestampPlusNumber(temporal, other, "+", at);
                return addDays(temporal, DateShiftAmount.within32Bits(wholeDays((Number) other)));
            }
        }
        // Retry with VARIANT / numeric-VARCHAR operands coerced to FLOAT (Snowflake's implicit conversion).
        final Number[] addVariant = coerceVariantOperands(left, right);
        if (addVariant != null) {
            return add(addVariant[0], addVariant[1]);
        }
        final Number[] addCoerced = coerceTextOperands(left, right);
        if (addCoerced != null) {
            return add(addCoerced[0], addCoerced[1]);
        }
        throw unreadableOperand(left, right, "Cannot add: " + left + " + " + right);
    }

    /**
     * Snowflake allows {@code DATE ± integer} (day arithmetic) but REJECTS it for TIMESTAMP values
     * (live: "Invalid argument types for function '+': (TIMESTAMP_NTZ(9), NUMBER(1,0))"). Only a real
     * TIMESTAMP runtime value rejects — a temporal-looking string stays on the lenient path.
     */
    private static void rejectTimestampPlusNumber(final Object temporal, final Object number,
                                                  final String op, final SourcePosition at) {
        if (!(temporal instanceof LocalDateTime)) {
            return;
        }
        final String numberType;
        if (isIntegerType(number)) {
            final String digits = new BigDecimal(number.toString()).abs().toBigInteger().toString();
            numberType = "NUMBER(" + digits.length() + ",0)";
        } else {
            numberType = "FLOAT";
        }
        // The same sentence, and the same anchor, as the static channel's: Snowflake points at the
        // OPERATOR token. The two refusal routes must agree — this one is reached when the value is
        // computed before its type is (a projection with no FROM), and the static one otherwise.
        final String detail = "Invalid argument types for function '" + op
            + "': (TIMESTAMP_NTZ(9), " + numberType + ")";
        throw new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail));
    }

    /** Whether the value is an approximate (FLOAT) number — Double or Float. */
    private static boolean isFloatType(final Object value) {
        return value instanceof Double || value instanceof Float;
    }

    static Object subtract(final Object left, final Object right) {
        return subtract(left, right, null);
    }

    static Object subtract(final Object left, final Object right, final SourcePosition at) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic — widening past a long, as addition does.
            if (isIntegerType(left) && isIntegerType(right)) {
                try {
                    return Math.subtractExact(((Number) left).longValue(), ((Number) right).longValue());
                } catch (final ArithmeticException pastLong) {
                    // fall through to the exact path
                }
            }
            // A FLOAT operand makes the result FLOAT (Snowflake's type propagation).
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() - ((Number) right).doubleValue();
            }
            // Otherwise, use BigDecimal for precision — with the same carrier checks as addition.
            final BigDecimal l = new BigDecimal(left.toString());
            final BigDecimal r = new BigDecimal(right.toString());
            requireRescalable(l, r, true);
            requireRescalable(r, l, false);
            final BigDecimal result = l.subtract(r);
            requireRawResultFits(result);
            return result;
        }
        // Two timestamps are an interval apart, and an interval moves a timestamp or meets another.
        final Object intervalDifference = DayTimeIntervalArithmetic.subtract(left, right);
        if (intervalDifference != null) {
            return intervalDifference;
        }
        final Object monthsDifference = YearMonthIntervalArithmetic.subtract(left, right);
        if (monthsDifference != null) {
            return monthsDifference;
        }
        if (ZonedTimestampShift.isZoned(left) && right instanceof IntervalValue) {
            return applyZonedInterval(left, (IntervalValue) right, -1);
        }
        // Date/time subtraction (NOT commutative — the left operand must be the DATE/TIMESTAMP):
        // temporal - integer (days), temporal - INTERVAL, or temporal - temporal (difference in days).
        final LocalDateTime leftTemporal = asTemporal(left);
        if (leftTemporal != null) {
            if (right instanceof IntervalValue) {
                return applyInterval(left, (IntervalValue) right, -1);
            }
            if (right instanceof Number) {
                rejectTimestampPlusNumber(left, right, "-", at);
                // The count checked is the one the date moves by: d - 2147483649 is out of range as -2147483649.
                return addDays(left, DateShiftAmount.within32Bits(-wholeDays((Number) right)));
            }
            final LocalDateTime rightTemporal = asTemporal(right);
            if (rightTemporal != null) {
                // DATE - DATE → whole days between; a timestamp pair was answered as an interval above.
                if (isDateOnly(left) && isDateOnly(right)) {
                    return ChronoUnit.DAYS.between(rightTemporal.toLocalDate(), leftTemporal.toLocalDate());
                }
                return ChronoUnit.DAYS.between(rightTemporal, leftTemporal);
            }
        }
        // Retry with VARIANT / numeric-VARCHAR operands coerced to FLOAT (Snowflake's implicit conversion).
        final Number[] subtractVariant = coerceVariantOperands(left, right);
        if (subtractVariant != null) {
            return subtract(subtractVariant[0], subtractVariant[1]);
        }
        final Number[] subtractCoerced = coerceTextOperands(left, right);
        if (subtractCoerced != null) {
            return subtract(subtractCoerced[0], subtractCoerced[1]);
        }
        throw unreadableOperand(left, right, "Cannot subtract: " + left + " - " + right);
    }

    /**
     * A DATE/TIMESTAMP value as a {@link LocalDateTime}, or null if the value is not temporal: accepts a
     * {@link LocalDate}/{@link LocalDateTime} instance or an ISO string ({@code yyyy-MM-dd} or
     * {@code yyyy-MM-dd HH:mm:ss}). A non-date string (e.g. {@code 'hello'}) returns null so ordinary
     * string operands keep failing arithmetic rather than being misread as dates.
     */
    private static LocalDateTime asTemporal(final Object v) {
        if (v instanceof LocalDateTime) {
            return (LocalDateTime) v;
        }
        if (v instanceof LocalDate) {
            return ((LocalDate) v).atStartOfDay();
        }
        if (v instanceof String) {
            final String s = ((String) v).trim();
            try {
                return LocalDateTime.parse(s.replace(' ', 'T'));
            } catch (final RuntimeException ignored) {
                // not a timestamp string — try a date-only string next
            }
            try {
                return LocalDate.parse(s).atStartOfDay();
            } catch (final RuntimeException ignored) {
                // not temporal
            }
        }
        return null;
    }

    /** Whether a temporal value is date-only (a DATE): a {@link LocalDate} or a bare {@code yyyy-MM-dd} string. */
    private static boolean isDateOnly(final Object v) {
        if (v instanceof LocalDate) {
            return true;
        }
        if (v instanceof LocalDateTime) {
            return false;
        }
        if (v instanceof String) {
            try {
                LocalDate.parse(((String) v).trim());
                return true;
            } catch (final RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    /**
     * The whole number of days an amount shifts a DATE by. The amount is converted to a whole NUMBER
     * first, so a fraction ROUNDS, half away from zero: {@code d + 1.5} is two days later,
     * {@code d + 1.4} one, {@code d - 2.5} three days earlier (live-verified).
     */
    static long wholeDays(final Number amount) {
        if (isIntegerType(amount)) {
            return amount.longValue();
        }
        final BigDecimal exact = amount instanceof Double || amount instanceof Float
            ? BigDecimal.valueOf(amount.doubleValue()) : new BigDecimal(amount.toString());
        return DateShiftAmount.rounded(exact);
    }

    /** temporal ± n days. A DATE stays a DATE (returns {@link LocalDate}); a TIMESTAMP stays a TIMESTAMP. */
    private static Object addDays(final Object temporal, final long days) {
        final LocalDateTime dt = asTemporal(temporal).plusDays(days);
        return isDateOnly(temporal) ? dt.toLocalDate() : dt;
    }

    /**
     * Apply an INTERVAL to a temporal ({@code sign} = +1 to add, -1 to subtract). Snowflake's typing,
     * both live-verified: a DAY-or-finer interval promotes a DATE to TIMESTAMP_NTZ
     * ({@code DATE + INTERVAL '5' DAY} renders 2020-01-20 00:00:00.000), while YEAR/MONTH intervals
     * PRESERVE the DATE ({@code DATE + INTERVAL '1' MONTH} stays a DATE).
     */
    private static Object applyInterval(final Object temporal, final IntervalValue interval, final int sign) {
        // Multi-part intervals ('1 day, 2 hours') chain via rest; apply each part in order.
        requireCountable(interval, sign);
        Object result = applyIntervalPart(temporal, interval, sign);
        for (IntervalValue part = interval.getRest(); part != null; part = part.getRest()) {
            requireCountable(part, sign);
            result = applyIntervalPart(result, part, sign);
        }
        return result;
    }

    /**
     * Refuse a quoted-string part whose amount, signed as the operator applies it, its unit cannot count: the
     * account refuses {@code ts - INTERVAL '1e30 hours'} naming the value {@code -1e+30} (see
     * {@link IntervalStringText#requireRepresentable}).
     */
    private static void requireCountable(final IntervalValue part, final int sign) {
        if (!part.isUnitInString()) {
            return;
        }
        final Object amount = part.getValue();
        if (sign > 0 || !(amount instanceof Number)) {
            IntervalStringText.requireRepresentable(amount, part.getUnit());
            return;
        }
        IntervalStringText.requireRepresentable(amount instanceof BigDecimal ? ((BigDecimal) amount).negate()
            : BigDecimal.valueOf(((Number) amount).longValue()).negate(), part.getUnit());
    }

    /**
     * A TIMESTAMP_LTZ or TIMESTAMP_TZ moved by an INTERVAL, one part after another in written order, each as
     * its flavour moves (see {@link ZonedTimestampShift}): the result keeps the flavour, so a TIMESTAMP_LTZ
     * plus {@code INTERVAL '1 day'} is still an instant in the session's zone. Live-verified.
     */
    private static Object applyZonedInterval(final Object zoned, final IntervalValue interval, final int sign) {
        requireCountable(interval, sign);
        Object result = ZonedTimestampShift.shift(zoned, interval.getUnit(), sign * interval.getValueAsLong());
        for (IntervalValue part = interval.getRest(); part != null; part = part.getRest()) {
            requireCountable(part, sign);
            result = ZonedTimestampShift.shift(result, part.getUnit(), sign * part.getValueAsLong());
        }
        return result;
    }

    /** How an argument-type refusal names a typed date or timestamp value, or null for any other value. */
    private static String temporalTypeName(final Object value) {
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalDateTime) {
            return "TIMESTAMP_NTZ(9)";
        }
        if (value instanceof OffsetDateTime) {
            return "TIMESTAMP_LTZ(9)";
        }
        if (value instanceof ZonedDateTime) {
            return "TIMESTAMP_TZ(9)";
        }
        return null;
    }

    private static Object applyIntervalPart(final Object temporal, final IntervalValue interval, final int sign) {
        final long amount = sign * interval.getValueAsLong();
        LocalDateTime dt = asTemporal(temporal);
        switch (interval.getUnit()) {
            case YEAR:
                dt = dt.plusYears(amount);
                break;
            case QUARTER:
                dt = dt.plusMonths(amount * 3);
                break;
            case MONTH:
                dt = dt.plusMonths(amount);
                break;
            case WEEK:
                dt = dt.plusWeeks(amount);
                break;
            case DAY:
                dt = dt.plusDays(amount);
                break;
            case HOUR:
                dt = dt.plusHours(amount);
                break;
            case MINUTE:
                dt = dt.plusMinutes(amount);
                break;
            case SECOND:
                dt = dt.plusSeconds(amount);
                break;
            case MILLISECOND:
                dt = dt.plusNanos(amount * 1_000_000L);
                break;
            case MICROSECOND:
                dt = dt.plusNanos(amount * 1_000L);
                break;
            case NANOSECOND:
                dt = dt.plusNanos(amount);
                break;
            default:
                throw new RuntimeException("Unsupported interval unit: " + interval.getUnit());
        }
        // A whole-day unit leaves a DATE a DATE. DAY is the exception whose answer also depends on the
        // SPELLING: `d + INTERVAL '5 days'` stays a DATE, `d + INTERVAL '5' DAY` promotes.
        final boolean preservesDate = interval.getUnit().isWholeDay()
            && (interval.getUnit() != IntervalUnit.DAY || interval.isUnitInString());
        return preservesDate && isDateOnly(temporal) ? dt.toLocalDate() : dt;
    }

    static Object multiply(final Object left, final Object right) {
        return multiplyAtScale(left, right, null);
    }

    /**
     * A product presented at the scale its type DECLARES — {@code min(s1 + s2, max(s1, s2, 12))} on the
     * account — the exact product rounded half up to it and checked against the 128-bit window at THAT
     * scale: a NUMBER(38,35) squared is 1.56250000000000000000000000000000000, where the exact
     * seventy-decimal raw could never fit the carrier. Without a declared scale (a VARIANT or a text
     * operand, a caller that knows none) the exact product stands and is checked as it is. Two
     * integers multiply exactly as longs while they fit; a FLOAT operand makes the product a double.
     */
    static Object multiplyAtScale(final Object left, final Object right, final Integer declaredScale) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        final Object scaledInterval = DayTimeIntervalArithmetic.multiply(left, right);
        if (scaledInterval != null) {
            return scaledInterval;
        }
        final Object scaledMonths = YearMonthIntervalArithmetic.multiply(left, right);
        if (scaledMonths != null) {
            return scaledMonths;
        }
        if (left instanceof Number && right instanceof Number) {
            if (isIntegerType(left) && isIntegerType(right)) {
                try {
                    return Math.multiplyExact(((Number) left).longValue(), ((Number) right).longValue());
                } catch (final ArithmeticException pastLong) {
                    // Past a long: the decimal path below carries it.
                }
            }
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() * ((Number) right).doubleValue();
            }
            final BigDecimal exact = new BigDecimal(left.toString()).multiply(new BigDecimal(right.toString()));
            final BigDecimal product = declaredScale != null && exact.scale() > declaredScale.intValue()
                ? exact.setScale(declaredScale.intValue(), RoundingMode.HALF_UP) : exact;
            requireRawResultFits(product);
            return product;
        }
        final Number[] multiplyVariant = coerceVariantOperands(left, right);
        if (multiplyVariant != null) {
            return multiplyAtScale(multiplyVariant[0], multiplyVariant[1], declaredScale);
        }
        final Number[] multiplyCoerced = coerceTextOperands(left, right);
        if (multiplyCoerced != null) {
            return multiplyAtScale(multiplyCoerced[0], multiplyCoerced[1], declaredScale);
        }
        throw unreadableOperand(left, right, "Cannot multiply: " + left + " * " + right);
    }

    static Object divide(final Object left, final Object right) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        final Object dividedInterval = DayTimeIntervalArithmetic.divide(left, right);
        if (dividedInterval != null) {
            return dividedInterval;
        }
        final Object dividedMonths = YearMonthIntervalArithmetic.divide(left, right);
        if (dividedMonths != null) {
            return dividedMonths;
        }
        if (left instanceof Number && right instanceof Number) {
            final double divisor = ((Number) right).doubleValue();
            if (divisor == 0.0) {
                throw new RuntimeException("Division by zero");
            }
            // A FLOAT operand makes the quotient FLOAT; fixed-point division carries Snowflake's
            // NUMBER result with scale MIN(s1 + 6, 12) — 10/3 is BigDecimal 3.333333, 10/2 is 5.000000.
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() / divisor;
            }
            final BigDecimal quotient = SharedFunctionHelpers.divideWithSnowflakeScale(
                new BigDecimal(left.toString()), new BigDecimal(right.toString()));
            requireQuotientFits(quotient);
            return quotient;
        }
        // Retry with VARIANT / numeric-VARCHAR operands coerced to FLOAT (Snowflake's implicit conversion).
        final Number[] divideVariant = coerceVariantOperands(left, right);
        if (divideVariant != null) {
            return divide(divideVariant[0], divideVariant[1]);
        }
        final Number[] divideCoerced = coerceTextOperands(left, right);
        if (divideCoerced != null) {
            return divide(divideCoerced[0], divideCoerced[1]);
        }
        throw unreadableOperand(left, right, "Cannot divide: " + left + " / " + right);
    }

    static Object modulo(final Object left, final Object right) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            if (((Number) right).doubleValue() == 0.0) {
                throw new RuntimeException("Division by zero");
            }
            // Integer operands → an exact integer remainder. Snowflake's % / MOD take the sign of the
            // dividend (like Java's %), so no adjustment is needed.
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() % ((Number) right).longValue();
            }
            // A FLOAT operand makes the result FLOAT, as it does for the other four operators. Without
            // this the BigDecimal below answered a scale-0 decimal whenever the remainder happened to
            // be whole — 2 % 7.5 read back as 2 where live gives 2.0 — and only that shape showed it,
            // because any remainder with a fraction rendered the same either way.
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() % ((Number) right).doubleValue();
            }
            // Otherwise use BigDecimal.remainder (also dividend-signed) for precision. Only the operand
            // RESCALE can overflow here — the remainder's raw is bounded by the divisor's, which fit.
            return checkedRemainder(new BigDecimal(left.toString()), new BigDecimal(right.toString()));
        }
        // The same VARIANT and numeric-text retries the other four operators make: '5' % 2 is 1 live.
        final Number[] moduloVariant = coerceVariantOperands(left, right);
        if (moduloVariant != null) {
            return modulo(moduloVariant[0], moduloVariant[1]);
        }
        final Number[] moduloCoerced = coerceTextOperands(left, right);
        if (moduloCoerced != null) {
            return modulo(moduloCoerced[0], moduloCoerced[1]);
        }
        throw unreadableOperand(left, right, "Cannot compute modulo: " + left + " % " + right);
    }

    /**
     * An operand of {@code + - %} must be REPRESENTABLE at the operation's common scale: widening a
     * raw by {@code 10^(target - own)} can walk it out of the 128-bit carrier before any arithmetic
     * happens, and live refuses THAT step with the aligned type, the operand's own digits printed
     * plain, and the operand's nullability (live-verified: {@code a + 0.5} over a NUMBER(38,0) of 38
     * nines refuses as {@code (38,1)}, echoing the column's value).
     */
    private static void requireRescalable(final BigDecimal value, final BigDecimal other, final boolean left) {
        final int own = Math.max(value.scale(), 0);
        final int target = Math.max(own, Math.max(other.scale(), 0));
        if (target == own || value.precision() + (target - own) <= 38) {
            return;
        }
        final BigInteger raw = value.setScale(target).unscaledValue();
        if (NumericRangeRefusal.outsideSb16Window(raw)) {
            throw new RawRangeOverflow(left ? RawOverflowKind.RESCALE_LEFT : RawOverflowKind.RESCALE_RIGHT,
                value, target);
        }
    }

    /**
     * The RAW scaled integer of a sum, difference or product must fit the 128-bit window. The refusal
     * reports the carrier view itself — {@code (38,0)&#123;not null&#125;} whatever the operands' scales
     * or nullability — and prints the RAW integer as a double, not the value ({@code e + e} over a
     * scale-1 column refuses at {@code 2e+38}, the raw, where the value is 2e+37; live-verified).
     */
    private static void requireRawResultFits(final BigDecimal result) {
        if (result.precision() <= 38) {
            return;
        }
        final BigInteger raw = result.unscaledValue();
        if (NumericRangeRefusal.outsideSb16Window(raw)) {
            throw new RuntimeException(
                NumericRangeRefusal.typedDouble("SB16", 38, 0, false, new BigDecimal(raw)));
        }
    }

    /**
     * A quotient's raw at the division's derived scale must fit the 128-bit window. Unlike the raw-result
     * shape this reports the DERIVED type and prints the quotient's VALUE — always in the double form,
     * even when its digits alone would fit the carrier (live-verified: {@code a / 0.9} prints
     * {@code 1.11111e+38}).
     */
    private static void requireQuotientFits(final BigDecimal quotient) {
        if (quotient.precision() <= 38) {
            return;
        }
        if (NumericRangeRefusal.outsideSb16Window(quotient.unscaledValue())) {
            throw new RawRangeOverflow(RawOverflowKind.QUOTIENT, quotient, Math.max(quotient.scale(), 0));
        }
    }


    private static boolean isIntegerType(final Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }

    /**
     * SQL value equality, as the {@code =} operator answers it: numbers compare by VALUE whatever
     * their runtime class, a boolean reads against a number, a temporal against its own text, a DATE
     * against a TIMESTAMP by instant, and a VARIANT against either. A string that must read as a
     * number and cannot is an error, not an inequality: {@code 'ab' = 1} raises "Numeric value 'ab'
     * is not recognized".
     *
     * <p>Exposed so the functions that are defined AS a comparison — DECODE today — answer with the
     * operator instead of a hand-rolled test of their own. It is the only member of this class
     * visible outside the package; the rest stay package-private.
     */
    public static boolean equals(final Object left, final Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number && right instanceof Number) {
            // Integral and same-type BigDecimal pairs compare directly — the toString/BigDecimal
            // bridge stays only for Double/Float and mixed pairs, whose semantics it defines.
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() == ((Number) right).longValue();
            }
            if (left instanceof BigDecimal && right instanceof BigDecimal) {
                return ((BigDecimal) left).compareTo((BigDecimal) right) == 0;
            }
            if (NonFiniteDoubles.isNonFinite((Number) left) || NonFiniteDoubles.isNonFinite((Number) right)) {
                return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
            }
            // A double beside ANY number compares as a double — the exact side is converted, as live
            // does: 1234567890123456789::FLOAT = 1234567890123456768 and -0.0::FLOAT = 0.0::FLOAT are
            // both TRUE there. Bridging through the double's shortest decimal made the first FALSE.
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() == ((Number) right).doubleValue();
            }
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        final Integer intervals = intervalOrder(left, right);
        if (intervals != null) {
            return intervals == 0;
        }
        final Integer booleanNumeric = booleanVsNumber(left, right);
        if (booleanNumeric != null) {
            return booleanNumeric == 0;
        }
        final Integer temporalPair = temporalVsTemporal(left, right);
        if (temporalPair != null) {
            return temporalPair == 0;
        }
        final Integer temporal = temporalVsString(left, right);
        if (temporal != null) {
            return temporal == 0;
        }
        final Integer numeric = numberVsString(left, right);
        if (numeric != null) {
            return numeric == 0;
        }
        final Integer variantNumeric = variantVsNumber(left, right);
        if (variantNumeric != null) {
            return variantNumeric == 0;
        }
        final Integer variantPair = variantVsVariant(left, right);
        if (variantPair != null) {
            return variantPair == 0;
        }
        final Integer variantText = variantVsText(left, right);
        if (variantText != null) {
            return variantText == 0;
        }
        final Integer binary = binaryVsOther(left, right);
        if (binary != null) {
            return binary == 0;
        }
        return left.toString().equals(right.toString());
    }

    static int compare(final Object left, final Object right) {
        if (left == null || right == null) {
            return 0;
        }
        if (left instanceof Number && right instanceof Number) {
            // Same fast paths as equals(); bit-identical results for integral types.
            if (isIntegerType(left) && isIntegerType(right)) {
                return Long.compare(((Number) left).longValue(), ((Number) right).longValue());
            }
            if (left instanceof BigDecimal && right instanceof BigDecimal) {
                return ((BigDecimal) left).compareTo((BigDecimal) right);
            }
            if (NonFiniteDoubles.isNonFinite((Number) left) || NonFiniteDoubles.isNonFinite((Number) right)) {
                return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
            }
            if (isFloatType(left) || isFloatType(right)) {
                return ApproximateValues.compare(((Number) left).doubleValue(),
                    ((Number) right).doubleValue());
            }
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        final Integer intervalPair = intervalOrder(left, right);
        if (intervalPair != null) {
            return intervalPair;
        }
        final Integer booleanNumeric = booleanVsNumber(left, right);
        if (booleanNumeric != null) {
            return booleanNumeric;
        }
        final Integer temporalPair = temporalVsTemporal(left, right);
        if (temporalPair != null) {
            return temporalPair;
        }
        final Integer temporal = temporalVsString(left, right);
        if (temporal != null) {
            return temporal;
        }
        final Integer numeric = numberVsString(left, right);
        if (numeric != null) {
            return numeric;
        }
        final Integer variantNumericCompare = variantVsNumber(left, right);
        if (variantNumericCompare != null) {
            return variantNumericCompare;
        }
        final Integer variantPairOrder = variantVsVariant(left, right);
        if (variantPairOrder != null) {
            return variantPairOrder.intValue();
        }
        final Integer variantText = variantVsText(left, right);
        if (variantText != null) {
            return variantText;
        }
        final Integer binary = binaryVsOther(left, right);
        if (binary != null) {
            return binary;
        }
        return left.toString().compareTo(right.toString());
    }

    /**
     * A VARIANT holding a number compared against a plain number (or two numeric variants) compares
     * NUMERICALLY — JSON-text comparison would order 10 before 9. Returns null when the pair is not
     * a numeric variant/number combination, so text and other variant shapes keep their paths.
     */
    private static Integer variantVsNumber(final Object left, final Object right) {
        final boolean leftVariant = left instanceof VariantValue;
        final boolean rightVariant = right instanceof VariantValue;
        if (!leftVariant && !rightVariant) {
            return null;
        }
        final Number leftNumber = leftVariant
            ? numericVariant((VariantValue) left) : (left instanceof Number ? (Number) left : null);
        final Number rightNumber = rightVariant
            ? numericVariant((VariantValue) right) : (right instanceof Number ? (Number) right : null);
        if (leftNumber == null || rightNumber == null) {
            return null;
        }
        if (NonFiniteDoubles.isNonFinite(leftNumber) || NonFiniteDoubles.isNonFinite(rightNumber)) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (isFloatType(leftNumber) || isFloatType(rightNumber)) {
            return ApproximateValues.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString()));
    }

    /** The variant's numeric content, or null when it does not hold a JSON number. */
    private static Number numericVariant(final VariantValue variant) {
        final JsonNode node = variant.node();
        if (node == null || !node.isNumber()) {
            return null;
        }
        if ((node.isDouble() || node.isFloat()) && !Double.isFinite(node.doubleValue())) {
            return Double.valueOf(node.doubleValue());
        }
        return node.decimalValue();
    }

    /**
     * A semi-structured value compared against a VARCHAR compares the variant's DISPLAY TEXT
     * (live-verified): a variant STRING unwraps to its content ({@code PARSE_JSON('"abc"') = 'abc'}
     * is TRUE and {@code = '"abc"'} is FALSE), while an object/array/number/boolean compares as its
     * JSON text. A variant against another variant is {@link #variantVsVariant} instead. Returns null
     * when the pair is not a variant/text combination.
     */
    private static Integer variantVsText(final Object left, final Object right) {
        if (left instanceof VariantValue && right instanceof CharSequence) {
            return variantDisplayText((VariantValue) left).compareTo(right.toString());
        }
        if (left instanceof CharSequence && right instanceof VariantValue) {
            return left.toString().compareTo(variantDisplayText((VariantValue) right));
        }
        return null;
    }

    /**
     * A semi-structured value against ANOTHER one: the comparison is the VARIANT order, which keeps an
     * OBJECT apart from a variant STRING spelling the same thing — live-verified, {@code
     * PARSE_JSON('{"a":1}') = TO_VARIANT('{"a":1}')} is FALSE — while reading numbers by value. The
     * DISPLAY text cannot serve: a variant string presents its content unquoted there, so the two would
     * collide. Returns null when the pair is not two variants.
     */
    private static Integer variantVsVariant(final Object left, final Object right) {
        if (left instanceof VariantValue && right instanceof VariantValue) {
            // The account's order: kind first (BOOLEAN < NUMBER < STRING < OBJECT < ARRAY < null), then
            // within the kind — numbers by value, so 1 = 1.0 and 9 < 10; see VariantOrder.
            return Integer.valueOf(((VariantValue) left).compareTo((VariantValue) right));
        }
        return null;
    }

    /** The text a variant presents to VARCHAR contexts: a string's content, an XML element's compact XML, otherwise the JSON text. */
    private static String variantDisplayText(final VariantValue variant) {
        if (variant.node().isTextual()) {
            return variant.node().asText();
        }
        if (XmlVariants.isXmlElement(variant.node())) {
            return XmlVariants.compactXml(variant.node());
        }
        return variant.text();
    }

    /**
     * A BINARY value compared against another BINARY. Comparing BINARY against a STRING is a
     * compile error in Snowflake (live-verified: "Can not convert parameter ''AB'' of type
     * [VARCHAR(2)] into expected type [BINARY(8388608)]") — there is NO implicit hex conversion in
     * comparisons; use TO_BINARY explicitly. Returns null when neither side is a binary value.
     */
    private static Integer binaryVsOther(final Object left, final Object right) {
        if (left instanceof BinaryValue && right instanceof BinaryValue) {
            return ((BinaryValue) left).compareTo((BinaryValue) right);
        }
        if (left instanceof BinaryValue && right instanceof CharSequence) {
            throw varcharBinaryMismatch(right.toString());
        }
        if (left instanceof CharSequence && right instanceof BinaryValue) {
            throw varcharBinaryMismatch(left.toString());
        }
        return null;
    }

    private static RuntimeException varcharBinaryMismatch(final String text) {
        return new RuntimeException("Can not convert parameter ''" + text + "'' of type [VARCHAR("
            + text.length() + ")] into expected type [BINARY(8388608)]");
    }

    /**
     * A BOOLEAN compared against a NUMBER, per Snowflake's implicit numeric-to-boolean coercion (0 is FALSE,
     * any non-zero number is TRUE) — {@code 1 = TRUE} is TRUE. Returns the comparison result with FALSE
     * ordering before TRUE, or null when the pair is not a boolean-number combination.
     */
    private static Integer booleanVsNumber(final Object left, final Object right) {
        if (left instanceof Boolean && right instanceof Number) {
            return Boolean.compare((Boolean) left, new BigDecimal(right.toString()).signum() != 0);
        }
        if (left instanceof Number && right instanceof Boolean) {
            return Boolean.compare(new BigDecimal(left.toString()).signum() != 0, (Boolean) right);
        }
        return null;
    }

    /**
     * A temporal compared against a string: parse the string to the temporal's own kind and compare by TIME
     * VALUE, as Snowflake's implicit coercion does — {@code ts = '2024-11-26 04:43:38.604'} is TRUE. Comparing
     * the toString texts instead made every such predicate false (T separator, dropped fraction zeros).
     * Returns null when the shapes don't match or the string doesn't parse, so callers keep the text path.
     */
    /**
     * A DATE beside a TIMESTAMP compares by INSTANT, with the date read as its own midnight:
     * {@code DATE '2020-01-01' = TIMESTAMP '2020-01-01 00:00:00'} is TRUE (live-verified), where the
     * two renderings compared as text — "2020-01-01" against "2020-01-01T00:00" — never match. The
     * pair reaches the text fallback otherwise, so this is the only place the promotion happens.
     *
     * <p>Returns null unless the pair really is a DATE beside a TIMESTAMP, leaving every other
     * combination to the helpers that own it.
     */
    private static Integer temporalVsTemporal(final Object left, final Object right) {
        // A TIMESTAMP_LTZ is an INSTANT (it arrives as an OffsetDateTime), so anything compared with one
        // is read as an instant too — a naive operand in the SESSION's zone, which is how live gets
        // `ltz = ntz` TRUE for the same wall-clock digits while the same LTZ against its own UTC digits
        // is FALSE. Comparing the two naively would answer both the other way round.
        if (left instanceof OffsetDateTime || right instanceof OffsetDateTime
                || left instanceof ZonedDateTime || right instanceof ZonedDateTime) {
            final Instant leftInstant = sessionInstant(left);
            final Instant rightInstant = sessionInstant(right);
            return leftInstant == null || rightInstant == null ? null
                : Integer.valueOf(leftInstant.compareTo(rightInstant));
        }
        if (left instanceof LocalDate && right instanceof LocalDateTime) {
            return ((LocalDate) left).atStartOfDay().compareTo((LocalDateTime) right);
        }
        if (left instanceof LocalDateTime && right instanceof LocalDate) {
            return ((LocalDateTime) left).compareTo(((LocalDate) right).atStartOfDay());
        }
        return null;
    }

    /** A temporal as the instant it names, a naive one read in the session's zone; null if not temporal. */
    private static Instant sessionInstant(final Object v) {
        if (v instanceof OffsetDateTime) {
            return ((OffsetDateTime) v).toInstant();
        }
        // A TIMESTAMP_TZ is an instant too — it merely remembers how it was spelled.
        if (v instanceof ZonedDateTime) {
            return ((ZonedDateTime) v).toInstant();
        }
        final LocalDateTime naive = asTemporal(v);
        return naive == null ? null : naive.atZone(SessionZone.current()).toInstant();
    }

    /**
     * A temporal beside a string reads the string AS that temporal, and a string that cannot be read
     * is an ERROR rather than an inequality — exactly as a string that cannot read as a number is.
     * Swallowing the parse failure and answering false made {@code d = 'ab'} quietly false where live
     * raises {@code Date 'ab' is not recognized}, and hid the same refusal behind IN, CASE, DECODE and
     * NULLIF. The sentence names the family of the TEMPORAL side, so the same 'ab' is reported as a
     * Date, a Timestamp or a Time depending on what it was compared against.
     */
    private static Integer temporalVsString(final Object left, final Object right) {
        if (left instanceof LocalDateTime && right instanceof CharSequence) {
            return ((LocalDateTime) left).compareTo(timestampOperand(right));
        }
        if (right instanceof LocalDateTime && left instanceof CharSequence) {
            return timestampOperand(left).compareTo((LocalDateTime) right);
        }
        if (left instanceof LocalDate && right instanceof CharSequence) {
            return ((LocalDate) left).compareTo(dateOperand(right));
        }
        if (right instanceof LocalDate && left instanceof CharSequence) {
            return dateOperand(left).compareTo((LocalDate) right);
        }
        if (left instanceof LocalTime && right instanceof CharSequence) {
            return ((LocalTime) left).compareTo(timeOperand(right));
        }
        if (right instanceof LocalTime && left instanceof CharSequence) {
            return timeOperand(left).compareTo((LocalTime) right);
        }
        return null;
    }

    /** The string operand of a TIMESTAMP comparison, or live's refusal for text that is not one. */
    private static LocalDateTime timestampOperand(final Object text) {
        try {
            return SharedFunctionHelpers.toLocalDateTime(text.toString());
        } catch (final RuntimeException notATimestamp) {
            throw new RuntimeException("Timestamp '" + text + "' is not recognized");
        }
    }

    /** The string operand of a DATE comparison, or live's refusal for text that is not one. */
    private static LocalDate dateOperand(final Object text) {
        try {
            return SharedFunctionHelpers.toLocalDate(text.toString());
        } catch (final RuntimeException notADate) {
            throw new RuntimeException("Date '" + text + "' is not recognized");
        }
    }

    /** The string operand of a TIME comparison, or live's refusal for text that is not one. */
    private static LocalTime timeOperand(final Object text) {
        try {
            return SharedFunctionHelpers.toLocalTime(text.toString());
        } catch (final RuntimeException notATime) {
            throw new RuntimeException("Time '" + text + "' is not recognized");
        }
    }

    /**
     * A number compared against a string: Snowflake implicitly coerces the VARCHAR side to a number —
     * {@code 999001 = '999001'} is TRUE (the idiom appears in loaders whose staging tables re-declare a
     * NUMBER key as VARCHAR and then join back to the numeric original). Two strings never coerce
     * ({@code '01' = '1'} stays a text comparison). A string that is NOT numeric is an ERROR, not a
     * mismatch — live-verified on a real account: {@code SELECT 'abc' = 1} fails "Numeric
     * value 'abc' is not recognized", as do {@code 'abc' &lt;&gt; 1}, {@code 'other' &gt; 0},
     * {@code 'abc' IN (1,2)} and {@code '' = 1}, while {@code '3' &lt; 5} and {@code '1.5' = 1.5} are TRUE.
     */
    private static Integer numberVsString(final Object left, final Object right) {
        final Object text = left instanceof Number && right instanceof CharSequence ? right
            : right instanceof Number && left instanceof CharSequence ? left : null;
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(left.toString().trim()).compareTo(new BigDecimal(right.toString().trim()));
        } catch (final NumberFormatException notANumericString) {
            // Against a FLOAT the text converts to FLOAT, where a hexadecimal number reads too.
            final Object number = text == right ? left : right;
            final Number asFloat = ApproximateValues.isApproximate(number) ? floatText(text) : null;
            if (asFloat != null) {
                return text == right
                    ? ApproximateValues.compare(((Number) left).doubleValue(), asFloat.doubleValue())
                    : ApproximateValues.compare(asFloat.doubleValue(), ((Number) right).doubleValue());
            }
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
    }

    static Object negate(final Number value) {
        if (value instanceof Long) {
            return -(Long) value;
        }
        // A double negates AS a double: that keeps the carrier a FLOAT arrived in, and it keeps the sign
        // of zero — -(0.0::FLOAT) is -0 on the account, which a BigDecimal has no way to hold.
        if (value instanceof Double) {
            return Double.valueOf(-((Double) value).doubleValue());
        }
        if (value instanceof Float) {
            return Float.valueOf(-((Float) value).floatValue());
        }
        // The one exact value whose negation leaves the carrier is -2^127 itself: arithmetic can
        // produce it, and negating it is refused in the raw-result form, where -(a + 1) — a 39-digit
        // value still inside the window — answers (live-verified).
        final BigDecimal negated = new BigDecimal(value.toString()).negate();
        requireRawResultFits(negated);
        return negated;
    }

    /**
     * The exact remainder with the operands' rescale check — the step {@code %} and {@code MOD} share:
     * an operand that cannot be represented at the common scale is refused before any arithmetic,
     * with the aligned type and its own digits (live-verified for the function as for the operator).
     *
     * @param dividend the dividend
     * @param divisor the divisor
     * @return the dividend-signed remainder
     */
    public static BigDecimal checkedRemainder(final BigDecimal dividend, final BigDecimal divisor) {
        requireRescalable(dividend, divisor, true);
        requireRescalable(divisor, dividend, false);
        return dividend.remainder(divisor);
    }

    /** Three-valued single comparison for quantified (ALL/ANY) evaluation: a NULL on either side is
     *  UNKNOWN (null), never a match or a mismatch. */
    /**
     * Two intervals of one family ordered by their SPAN, whatever unit each was written in, and an interval
     * beside a string read in the interval's own fields: live, {@code INTERVAL '1' DAY = INTERVAL '24' HOUR}
     * and {@code INTERVAL '1' YEAR = INTERVAL '12' MONTH} are TRUE, {@code INTERVAL '1' DAY = '1'} is TRUE
     * and {@code INTERVAL '24' HOUR = '1'} FALSE. The two families never meet — the statement is refused
     * while it compiles — so a pair across them is left to the other rules.
     *
     * @param left  one operand
     * @param right the other
     * @return the order, or null when the pair is not an interval comparison
     */
    private static Integer intervalOrder(final Object left, final Object right) {
        if (left instanceof String && isSpanInterval(right)) {
            return spanOrder(IntervalText.parse((String) left, IntervalText.ownQualifier(right)), right);
        }
        if (isSpanInterval(left) && right instanceof String) {
            return spanOrder(left, IntervalText.parse((String) right, IntervalText.ownQualifier(left)));
        }
        return spanOrder(left, right);
    }

    private static boolean isSpanInterval(final Object value) {
        return value instanceof DayTimeInterval || value instanceof YearMonthInterval;
    }

    /** Two spans of one family in order, or null for anything else. */
    private static Integer spanOrder(final Object left, final Object right) {
        if (left instanceof DayTimeInterval && right instanceof DayTimeInterval) {
            return ((DayTimeInterval) left).compareTo((DayTimeInterval) right);
        }
        if (left instanceof YearMonthInterval && right instanceof YearMonthInterval) {
            return ((YearMonthInterval) left).compareTo((YearMonthInterval) right);
        }
        return null;
    }

    static Boolean compareWithOperator(final Object left, final Object right,
                                        final BinaryOperator operator) {
        if (left == null || right == null) {
            return null; // UNKNOWN
        }
        switch (operator) {
            case EQUAL:
                return equals(left, right);
            case NOT_EQUAL:
                return !equals(left, right);
            case LESS_THAN:
                return compare(left, right) < 0;
            case LESS_THAN_OR_EQUAL:
                return compare(left, right) <= 0;
            case GREATER_THAN:
                return compare(left, right) > 0;
            case GREATER_THAN_OR_EQUAL:
                return compare(left, right) >= 0;
            default:
                throw new RuntimeException("Unsupported comparison operator: " + operator);
        }
    }
}
