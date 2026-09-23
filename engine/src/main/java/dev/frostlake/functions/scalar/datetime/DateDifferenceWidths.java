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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.util.Locale;

/**
 * How wide a date difference is. DATEDIFF is planned as {@code DATE_DIFF<KIND>IN<UNITS>}, and the width follows
 * both the KIND the operands are moved to and the UNIT counted:
 *
 * <pre>
 *                    YEARS … DAYS   HOURS   MINUTES, SECONDS   MILLISECONDS   MICRO-, NANOSECONDS
 *   DATE             NUMBER(9,0)    (18,0)  (18,0)             (18,0)         (38,0)
 *   TIMESTAMP        NUMBER(9,0)    (9,0)   (18,0)             (38,0)         (38,0)
 *   TIME             —              (9,0)   (9,0)              (9,0)          (18,0)
 * </pre>
 *
 * <p>The kind is DATE for two DATEs or a DATE beside a text or a VARIANT, TIME for two TIMEs, and TIMESTAMP
 * when a timestamp is on either side or both are texts.
 */
public final class DateDifferenceWidths {

    /** The kind the operands of a date difference are moved to. */
    public static final String DATE = "DATE";

    /** The kind the operands of a date difference are moved to. */
    public static final String TIMESTAMP = "TIMESTAMP";

    /** The kind the operands of a date difference are moved to. */
    public static final String TIME = "TIME";

    private DateDifferenceWidths() {
    }

    /**
     * The width of a difference counted in {@code unit} between operands moved to {@code kind}.
     *
     * @param kind DATE, TIMESTAMP or TIME
     * @param unit the unit counted
     * @return the NUMBER it answers
     */
    public static NumericType of(final String kind, final IntervalUnit unit) {
        final boolean micro = unit == IntervalUnit.MICROSECOND || unit == IntervalUnit.NANOSECOND;
        if (TIME.equals(kind)) {
            return micro ? IntegerResultWidths.COUNTER : IntegerResultWidths.POSITION;
        }
        if (micro) {
            return IntegerResultWidths.WIDEST;
        }
        if (unit.isWholeDay()) {
            return IntegerResultWidths.POSITION;
        }
        if (DATE.equals(kind)) {
            return IntegerResultWidths.COUNTER;
        }
        switch (unit) {
            case HOUR:
                return IntegerResultWidths.POSITION;
            case MINUTE:
            case SECOND:
                return IntegerResultWidths.COUNTER;
            default:
                return IntegerResultWidths.WIDEST;
        }
    }

    /**
     * The kind a DATEDIFF moves its two operands to.
     *
     * @param from the first operand's type, or null when it has none
     * @param to the second operand's type, or null when it has none
     * @return DATE, TIMESTAMP or TIME; null when the pair is none of them (a refusal, or an untyped NULL)
     */
    public static String plannedKind(final DataType from, final DataType to) {
        if (from == null || to == null) {
            return null;
        }
        if (isNamed(from, "DATE") && isNamed(to, "DATE")) {
            return DATE;
        }
        if (isNamed(from, "TIME") && isNamed(to, "TIME")) {
            return TIME;
        }
        if ((isTimestamp(from) || isTimestamp(to)) && movableToTimestamp(from) && movableToTimestamp(to)) {
            return TIMESTAMP;
        }
        if (isNamed(from, "DATE") && isTextual(to) || isTextual(from) && isNamed(to, "DATE")) {
            return DATE;
        }
        if (isTextual(from) && isTextual(to)) {
            return TIMESTAMP;
        }
        return null;
    }

    private static boolean isNamed(final DataType type, final String name) {
        return type instanceof DateTimeType && name.equals(type.getName().toUpperCase(Locale.ROOT));
    }

    private static boolean isTimestamp(final DataType type) {
        return type instanceof DateTimeType && type.getName().toUpperCase(Locale.ROOT).startsWith("TIMESTAMP");
    }

    private static boolean isTextual(final DataType type) {
        return type instanceof StringType || type instanceof VariantType;
    }

    private static boolean movableToTimestamp(final DataType type) {
        return isNamed(type, "DATE") || isTimestamp(type) || isTextual(type);
    }
}
