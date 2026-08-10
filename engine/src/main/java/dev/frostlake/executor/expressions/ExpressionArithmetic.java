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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonNulls;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.XmlVariants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import tools.jackson.databind.JsonNode;

/**
 * Pure arithmetic, temporal and comparison helpers extracted from {@link ExpressionEvaluatorVisitor}:
 * numeric/date add/subtract/multiply/divide/negate, three-valued truthiness, value equality/ordering and
 * INTERVAL application. Field-free — all inputs arrive as arguments.
 */
final class ExpressionArithmetic {

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
            return null;
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
     * The two operands as doubles when at least one of them is a VARIANT holding a number (or numeric
     * text), else null. Snowflake arithmetic over VARIANT operands produces FLOAT (live-verified:
     * {@code PARSE_JSON('1') + 1} → 2.0 FLOAT, {@code TRANSFORM([1,2,3], x -> x + 1)} → doubles).
     */
    private static Number[] coerceVariantOperands(final Object left, final Object right) {
        if (!(left instanceof VariantValue) && !(right instanceof VariantValue)) {
            return null;
        }
        final Number leftNumber = left instanceof VariantValue
            ? variantAsNumber((VariantValue) left) : asNumber(left);
        final Number rightNumber = right instanceof VariantValue
            ? variantAsNumber((VariantValue) right) : asNumber(right);
        if (leftNumber == null || rightNumber == null) {
            return null;
        }
        return new Number[] {Double.valueOf(leftNumber.doubleValue()), Double.valueOf(rightNumber.doubleValue())};
    }

    private static Number variantAsNumber(final VariantValue variant) {
        final JsonNode node = variant.node();
        if (node != null && node.isNumber()) {
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

    static Object add(final Object left, final Object right) {
        return add(left, right, null);
    }

    static Object add(final Object left, final Object right, final SourcePosition at) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() + ((Number) right).longValue();
            }
            // A FLOAT operand makes the result FLOAT (Snowflake's type propagation).
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() + ((Number) right).doubleValue();
            }
            // Otherwise, use BigDecimal for precision
            final BigDecimal result = new BigDecimal(left.toString()).add(new BigDecimal(right.toString()));
            return result;
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
                return addDays(temporal, ((Number) other).longValue());
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
        throw new RuntimeException("Cannot add: " + left + " + " + right);
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
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() - ((Number) right).longValue();
            }
            // A FLOAT operand makes the result FLOAT (Snowflake's type propagation).
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() - ((Number) right).doubleValue();
            }
            // Otherwise, use BigDecimal for precision
            return new BigDecimal(left.toString()).subtract(new BigDecimal(right.toString()));
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
                return addDays(left, -((Number) right).longValue());
            }
            final LocalDateTime rightTemporal = asTemporal(right);
            if (rightTemporal != null) {
                // DATE - DATE → whole days between. TIMESTAMP - TIMESTAMP is an INTERVAL in Snowflake; we
                // approximate it as the whole-day difference (use DATEDIFF for finer units).
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
        throw new RuntimeException("Cannot subtract: " + left + " - " + right);
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
        Object result = applyIntervalPart(temporal, interval, sign);
        for (IntervalValue part = interval.getRest(); part != null; part = part.getRest()) {
            result = applyIntervalPart(result, part, sign);
        }
        return result;
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
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() * ((Number) right).longValue();
            }
            // A FLOAT operand makes the result FLOAT (Snowflake's type propagation).
            if (isFloatType(left) || isFloatType(right)) {
                return ((Number) left).doubleValue() * ((Number) right).doubleValue();
            }
            // Otherwise, use BigDecimal for precision
            return new BigDecimal(left.toString()).multiply(new BigDecimal(right.toString()));
        }
        // Retry with VARIANT / numeric-VARCHAR operands coerced to FLOAT (Snowflake's implicit conversion).
        final Number[] multiplyVariant = coerceVariantOperands(left, right);
        if (multiplyVariant != null) {
            return multiply(multiplyVariant[0], multiplyVariant[1]);
        }
        final Number[] multiplyCoerced = coerceTextOperands(left, right);
        if (multiplyCoerced != null) {
            return multiply(multiplyCoerced[0], multiplyCoerced[1]);
        }
        throw new RuntimeException("Cannot multiply: " + left + " * " + right);
    }

    static Object divide(final Object left, final Object right) {
        if (isJsonNullOperand(left) || isJsonNullOperand(right)) {
            return null;
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
            return SharedFunctionHelpers.divideWithSnowflakeScale(
                new BigDecimal(left.toString()), new BigDecimal(right.toString()));
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
        throw new RuntimeException("Cannot divide: " + left + " / " + right);
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
            // Otherwise use BigDecimal.remainder (also dividend-signed) for precision.
            return new BigDecimal(left.toString()).remainder(new BigDecimal(right.toString()));
        }
        throw new RuntimeException("Cannot compute modulo: " + left + " % " + right);
    }

    private static boolean isIntegerType(final Object value) {
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
    }

    static boolean equals(final Object left, final Object right) {
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
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        final Integer booleanNumeric = booleanVsNumber(left, right);
        if (booleanNumeric != null) {
            return booleanNumeric == 0;
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
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        final Integer booleanNumeric = booleanVsNumber(left, right);
        if (booleanNumeric != null) {
            return booleanNumeric;
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
        return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString()));
    }

    /** The variant's numeric content, or null when it does not hold a JSON number. */
    private static Number numericVariant(final VariantValue variant) {
        final JsonNode node = variant.node();
        return node != null && node.isNumber() ? node.decimalValue() : null;
    }

    /**
     * A semi-structured value compared against a VARCHAR compares the variant's DISPLAY TEXT
     * (live-verified): a variant STRING unwraps to its content ({@code PARSE_JSON('"abc"') = 'abc'}
     * is TRUE and {@code = '"abc"'} is FALSE), while an object/array/number/boolean compares as its
     * JSON text. Variant-vs-variant comparisons are not handled here — canonical-text equality
     * (the toString fallback) already matches Snowflake's typed behavior for those. Returns null
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
    private static Integer temporalVsString(final Object left, final Object right) {
        try {
            if (left instanceof LocalDateTime && right instanceof CharSequence) {
                return ((LocalDateTime) left).compareTo(SharedFunctionHelpers.toLocalDateTime(right.toString()));
            }
            if (right instanceof LocalDateTime && left instanceof CharSequence) {
                return SharedFunctionHelpers.toLocalDateTime(left.toString()).compareTo((LocalDateTime) right);
            }
            if (left instanceof LocalDate && right instanceof CharSequence) {
                return ((LocalDate) left).compareTo(SharedFunctionHelpers.toLocalDate(right.toString()));
            }
            if (right instanceof LocalDate && left instanceof CharSequence) {
                return SharedFunctionHelpers.toLocalDate(left.toString()).compareTo((LocalDate) right);
            }
            if (left instanceof LocalTime && right instanceof CharSequence) {
                return ((LocalTime) left).compareTo(SharedFunctionHelpers.toLocalTime(right.toString()));
            }
            if (right instanceof LocalTime && left instanceof CharSequence) {
                return SharedFunctionHelpers.toLocalTime(left.toString()).compareTo((LocalTime) right);
            }
        } catch (final RuntimeException notATemporalString) {
            return null;
        }
        return null;
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
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
    }

    static Object negate(final Number value) {
        if (value instanceof Long) {
            return -(Long) value;
        }
        return new BigDecimal(value.toString()).negate();
    }

    /** Three-valued single comparison for quantified (ALL/ANY) evaluation: a NULL on either side is
     *  UNKNOWN (null), never a match or a mismatch. */
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
