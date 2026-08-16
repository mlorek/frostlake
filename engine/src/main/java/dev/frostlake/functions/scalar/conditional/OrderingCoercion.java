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

package dev.frostlake.functions.scalar.conditional;

import tools.jackson.databind.JsonNode;

import dev.frostlake.executor.SetOperations;
import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.IncomparableArgumentsException;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * How {@code GREATEST} and {@code LEAST} order arguments that are not already the same family. They
 * do not merely compare — they CONVERT, and the account's answers say which way:
 *
 * <pre>
 *   GREATEST(n, '5')            5          the string joins the number, whichever side it is on
 *   GREATEST('5', n)            5          — still INTEGER, not the first argument's VARCHAR
 *   GREATEST(d, '2027-01-01')   2027-01-01 a date the same way, both orders
 *   GREATEST(bo, n)             TRUE       BOOLEAN wins over NUMBER, whichever side it is on
 *   GREATEST(v, n)              1          a scalar VARIANT unwraps to its value
 *   GREATEST(n, 2.5)            2.5        two numerics compare by VALUE, not by Java class
 *   GREATEST(n, s)              "Numeric value 'abc' is not recognized"   — a ROW-time value error
 * </pre>
 *
 * <p>The string rule is the set operation's rule, and it is literally the same code
 * ({@link SetOperations#coerceStringToLeadingType}) so the two surfaces cannot drift: a string beside
 * another family converts to that family, and a conversion that fails carries the account's own
 * sentence rather than a Java one.
 *
 * <p>What CANNOT be brought together — a BINARY beside anything else — is signalled with
 * {@link IncomparableArgumentsException} instead of being guessed at. The refusal the account gives
 * there names the operand and its DECLARED type, which only the expression tree knows, so the
 * evaluator re-asks the static channel for the wording. Comparing them as text, the way an ORDER BY
 * comparator would, is exactly the leniency this replaced.
 */
public final class OrderingCoercion {

    private OrderingCoercion() {
    }

    /**
     * Every argument converted to the family they will be ordered in — which is also the family of the
     * ANSWER. Live returns the CONVERTED value, not the winning argument as it was written:
     * {@code GREATEST(n, '5')} answers the NUMBER 5, not the string, and {@code GREATEST(n, bo)}
     * answers TRUE rather than the 1 that outranked nothing. Converting first and ordering after is
     * what makes those two agree.
     *
     * @param args the call's arguments, nulls included
     * @return a list of the same size, each value in the common family
     */
    public static List<Object> coerceAll(final List<Object> args) {
        final Object target = orderingTarget(args);
        if (target == null) {
            return args;
        }
        final List<Object> coerced = new ArrayList<>(args.size());
        for (final Object arg : args) {
            coerced.add(arg == null ? null : toTargetFamily(arg, target));
        }
        return coerced;
    }

    /**
     * The value whose family the whole call is ordered in. A BOOLEAN anywhere takes it — live answers
     * both {@code GREATEST(bo, n)} and {@code GREATEST(n, bo)} with TRUE — and otherwise the first
     * argument that is not a string or a scalar VARIANT, since those are the two that convert TOWARD
     * something. All-strings order as strings, and needs no conversion at all.
     */
    private static Object orderingTarget(final List<Object> args) {
        for (final Object arg : args) {
            if (arg instanceof Boolean) {
                return arg;
            }
        }
        // A temporal set is ordered in its WIDEST member's family, not the first one written: live
        // answers GREATEST(d, ts) and GREATEST(ts, d) alike with a timestamp, reading the DATE at
        // midnight. A TIME is deliberately outside this order — live refuses it beside a timestamp.
        Object widestTemporal = null;
        for (final Object arg : args) {
            if (temporalRank(arg) > 0
                    && (widestTemporal == null || temporalRank(arg) > temporalRank(widestTemporal))) {
                widestTemporal = arg;
            }
        }
        if (widestTemporal != null) {
            return widestTemporal;
        }
        for (final Object arg : args) {
            if (arg != null && !(arg instanceof CharSequence) && !isScalarVariant(arg)) {
                return arg;
            }
        }
        return null;
    }

    /**
     * Where a temporal value sits in the widening order — a DATE reads as a timestamp, and a naive
     * timestamp as a zoned one. A TIME is NOT in the order: it answers -1, so it never widens and
     * never accepts a widening, which is what live's refusal beside a timestamp says.
     *
     * @param value the value to rank
     * @return 1 for a DATE, 2 for a naive timestamp, 3 for a zoned one, -1 for anything else
     */
    private static int temporalRank(final Object value) {
        if (value instanceof LocalDate) {
            return 1;
        }
        if (value instanceof LocalDateTime) {
            return 2;
        }
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            // A TIMESTAMP_TZ ranks with the LTZ: both are instants, and only their PRESENTATION differs.
            return 3;
        }
        return -1;
    }

    /**
     * One temporal read in a wider temporal's family — a DATE at midnight, a naive timestamp at the
     * target's own offset.
     *
     * @param value  the narrower temporal
     * @param target the value whose family is being ordered in
     * @return the widened value
     */
    private static Object widenTemporal(final Object value, final Object target) {
        if (target instanceof OffsetDateTime || target instanceof ZonedDateTime) {
            final ZoneOffset offset = target instanceof ZonedDateTime
                ? ((ZonedDateTime) target).getOffset() : ((OffsetDateTime) target).getOffset();
            if (value instanceof LocalDate) {
                return ((LocalDate) value).atStartOfDay().atOffset(offset);
            }
            if (value instanceof LocalDateTime) {
                return ((LocalDateTime) value).atOffset(offset);
            }
            if (value instanceof ZonedDateTime) {
                return ((ZonedDateTime) value).toOffsetDateTime();
            }
            return value;
        }
        if (target instanceof LocalDateTime && value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        return value;
    }

    /** One argument converted into {@code target}'s family, or a signal that it cannot be. */
    private static Object toTargetFamily(final Object arg, final Object target) {
        final Object unwrapped = isScalarVariant(arg) ? unwrapScalarVariant(arg, target) : arg;
        if (target instanceof Boolean) {
            final Boolean flag = asBoolean(unwrapped);
            if (flag == null) {
                throw incomparable(unwrapped, target);
            }
            return flag;
        }
        if (unwrapped instanceof CharSequence) {
            final Object converted = SetOperations.coerceStringToLeadingType(
                target, unwrapped.toString());
            if (converted == null) {
                throw incomparable(unwrapped, target);
            }
            return converted instanceof Number
                ? ValueComparisons.canonicalGroupKeyValue(converted) : converted;
        }
        if (unwrapped instanceof Number && target instanceof Number) {
            return unwrapped;
        }
        if (temporalRank(target) > 0 && temporalRank(unwrapped) > 0) {
            return widenTemporal(unwrapped, target);
        }
        if (unwrapped.getClass().equals(target.getClass())) {
            return unwrapped;
        }
        throw incomparable(unwrapped, target);
    }

    private static boolean isScalarVariant(final Object value) {
        if (!(value instanceof VariantValue)) {
            return false;
        }
        final JsonNode node = ((VariantValue) value).node();
        return node != null && (node.isNumber() || node.isTextual() || node.isBoolean());
    }

    private static IncomparableArgumentsException incomparable(final Object value,
                                                               final Object target) {
        return new IncomparableArgumentsException(value.getClass().getSimpleName()
            + " and " + target.getClass().getSimpleName() + " have no common ordering");
    }

    private static Boolean asBoolean(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return Boolean.valueOf(new BigDecimal(value.toString()).signum() != 0);
        }
        if (value instanceof CharSequence) {
            final Object converted = SetOperations.coerceStringToLeadingType(
                Boolean.TRUE, value.toString());
            return converted instanceof Boolean ? (Boolean) converted : null;
        }
        return null;
    }

    /**
     * A VARIANT holding a SCALAR unwraps to that scalar when its partner is one — {@code GREATEST(v, n)}
     * answers the number. A container VARIANT is left alone: an OBJECT orders as an object, and live
     * returns one from {@code GREATEST(o, o)}.
     */
    private static Object unwrapScalarVariant(final Object value, final Object partner) {
        if (!(value instanceof VariantValue) || partner instanceof VariantValue) {
            return value;
        }
        final JsonNode node = ((VariantValue) value).node();
        if (node == null) {
            return value;
        }
        if (node.isNumber()) {
            return ValueComparisons.canonicalGroupKeyValue(new BigDecimal(node.asText()));
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return Boolean.valueOf(node.asBoolean());
        }
        return value;
    }
}
