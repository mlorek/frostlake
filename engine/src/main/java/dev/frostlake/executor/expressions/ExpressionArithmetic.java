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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Pure arithmetic, temporal and comparison helpers extracted from {@link ExpressionEvaluatorVisitor}:
 * numeric/date add/subtract/multiply/divide/negate, three-valued truthiness, value equality/ordering and
 * INTERVAL application. Field-free — all inputs arrive as arguments.
 */
final class ExpressionArithmetic {

    private ExpressionArithmetic() {
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
        return new Number[] {leftNumber, rightNumber};
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
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() + ((Number) right).longValue();
            }
            // Otherwise, use BigDecimal for precision
            BigDecimal result = new BigDecimal(left.toString()).add(new BigDecimal(right.toString()));
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
                return addDays(temporal, ((Number) other).longValue());
            }
        }
        // Retry with a numeric VARCHAR coerced to a number (Snowflake's implicit conversion).
        final Number[] addCoerced = coerceTextOperands(left, right);
        if (addCoerced != null) {
            return add(addCoerced[0], addCoerced[1]);
        }
        throw new RuntimeException("Cannot add: " + left + " + " + right);
    }

    static Object subtract(final Object left, final Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() - ((Number) right).longValue();
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
        // Retry with a numeric VARCHAR coerced to a number (Snowflake's implicit conversion).
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
     * Apply an INTERVAL to a temporal ({@code sign} = +1 to add, -1 to subtract). A date-only operand
     * keeps its DATE type for a date-only interval (YEAR/MONTH/DAY) but is promoted to a TIMESTAMP when
     * the interval carries a time component (HOUR/MINUTE/SECOND), matching Snowflake.
     */
    private static Object applyInterval(final Object temporal, final IntervalValue interval, final int sign) {
        final long amount = sign * interval.getValueAsLong();
        LocalDateTime dt = asTemporal(temporal);
        boolean timeComponent = false;
        switch (interval.getUnit()) {
            case YEAR: case YEARS:
                dt = dt.plusYears(amount);
                break;
            case MONTH: case MONTHS:
                dt = dt.plusMonths(amount);
                break;
            case DAY: case DAYS:
                dt = dt.plusDays(amount);
                break;
            case HOUR: case HOURS:
                dt = dt.plusHours(amount);
                timeComponent = true;
                break;
            case MINUTE: case MINUTES:
                dt = dt.plusMinutes(amount);
                timeComponent = true;
                break;
            case SECOND: case SECONDS:
                dt = dt.plusSeconds(amount);
                timeComponent = true;
                break;
            default:
                throw new RuntimeException("Unsupported interval unit: " + interval.getUnit());
        }
        return isDateOnly(temporal) && !timeComponent ? dt.toLocalDate() : dt;
    }

    static Object multiply(final Object left, final Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            // If both are integer types, perform integer arithmetic
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).longValue() * ((Number) right).longValue();
            }
            // Otherwise, use BigDecimal for precision
            return new BigDecimal(left.toString()).multiply(new BigDecimal(right.toString()));
        }
        // Retry with a numeric VARCHAR coerced to a number (Snowflake's implicit conversion).
        final Number[] multiplyCoerced = coerceTextOperands(left, right);
        if (multiplyCoerced != null) {
            return multiply(multiplyCoerced[0], multiplyCoerced[1]);
        }
        throw new RuntimeException("Cannot multiply: " + left + " * " + right);
    }

    static Object divide(final Object left, final Object right) {
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof Number && right instanceof Number) {
            double divisor = ((Number) right).doubleValue();
            if (divisor == 0.0) {
                throw new RuntimeException("Division by zero");
            }
            final BigDecimal quotient = SharedFunctionHelpers.divideWithSnowflakeScale(new BigDecimal(left.toString()), new BigDecimal(right.toString()));
            // Integer operands historically produced a double; keep the runtime type, now at Snowflake's
            // six-digit division scale (0.333333, not 0.3333333333333333).
            if (isIntegerType(left) && isIntegerType(right)) {
                return quotient.doubleValue();
            }
            return quotient;
        }
        // Retry with a numeric VARCHAR coerced to a number (Snowflake's implicit conversion).
        final Number[] divideCoerced = coerceTextOperands(left, right);
        if (divideCoerced != null) {
            return divide(divideCoerced[0], divideCoerced[1]);
        }
        throw new RuntimeException("Cannot divide: " + left + " / " + right);
    }

    static Object modulo(final Object left, final Object right) {
        if (left == null || right == null) {
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
        return left.toString().equals(right.toString());
    }

    static int compare(final Object left, final Object right) {
        if (left == null || right == null) {
            return 0;
        }
        if (left instanceof Number && right instanceof Number) {
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
        return left.toString().compareTo(right.toString());
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
     * ({@code '01' = '1'} stays a text comparison). Returns null when the string is not numeric, keeping
     * the caller's text path — Snowflake would raise there; the engine stays lenient as before.
     */
    private static Integer numberVsString(final Object left, final Object right) {
        try {
            if (left instanceof Number && right instanceof CharSequence) {
                return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString().trim()));
            }
            if (right instanceof Number && left instanceof CharSequence) {
                return new BigDecimal(left.toString().trim()).compareTo(new BigDecimal(right.toString()));
            }
        } catch (final NumberFormatException notANumericString) {
            return null;
        }
        return null;
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
