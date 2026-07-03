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

    static boolean isTrue(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
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
            // Use double division for integer operands, BigDecimal for others
            if (isIntegerType(left) && isIntegerType(right)) {
                return ((Number) left).doubleValue() / divisor;
            }
            // For BigDecimal operands, use BigDecimal division for precision
            BigDecimal bdDivisor = new BigDecimal(right.toString());
            return new BigDecimal(left.toString()).divide(bdDivisor, 10, BigDecimal.ROUND_HALF_UP);
        }
        throw new RuntimeException("Cannot divide: " + left + " / " + right);
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
        return left.toString().equals(right.toString());
    }

    static int compare(final Object left, final Object right) {
        if (left == null || right == null) {
            return 0;
        }
        if (left instanceof Number && right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        return left.toString().compareTo(right.toString());
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
