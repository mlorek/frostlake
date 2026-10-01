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

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;

import java.util.List;

/**
 * The argument families the interval functions take — DATEADD with TIMEADD and TIMESTAMPADD, DATEDIFF
 * with TIMEDIFF and TIMESTAMPDIFF — judged while the statement COMPILES, so an empty table refuses
 * exactly as a full one does. A refusal names the function the call is PLANNED as, lists the two value
 * arguments only, and is anchored at the call (live-verified):
 *
 * <pre>
 *   DATEADD(day, TRUE, d)       'DATE_ADDDAYSTODATE': (BOOLEAN, DATE)
 *   DATEADD(hour, TRUE, d)      'DATE_ADDHOURSTOTIMESTAMP': (BOOLEAN, TIMESTAMP_NTZ(9))   the DATE promoted
 *   DATEADD(second, TRUE, tm)   'DATE_ADDSECONDSTOTIME': (BOOLEAN, TIME(9))
 *   DATEADD(day, TRUE, g)       'DATE_ADDDAYSTOTIMESTAMP': (BOOLEAN, VARCHAR(10))          a text listed as text
 *   DATEADD(day, 1, i)          'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), NUMBER(38,0))
 *   DATEDIFF(day, i, d)         'DATE_DIFFDATEINDAYS': (NUMBER(38,0), DATE)
 *   DATEDIFF(day, ts, i)        'DATE_DIFFTIMESTAMPINDAYS': (TIMESTAMP_NTZ(9), NUMBER(38,0))
 *   DATEDIFF(hour, g, tm)       'DATEDIFF': (VARCHAR(4), VARCHAR(10), TIME(9))            a TIME beside anything else
 * </pre>
 *
 * <p>A shift takes a NUMBER, a FLOAT, a text or a VARIANT as its amount, and a DATE, a TIME, a
 * TIMESTAMP, a text or a VARIANT as its target; a difference takes the target families on both sides.
 * BOOLEAN, ARRAY, OBJECT, MAP, BINARY and GEOGRAPHY are refused wherever they stand, a NUMBER or a FLOAT
 * everywhere but a shift's amount, and a temporal value as an amount. The plan kind is DATE for a
 * whole-day shift of a DATE and for a difference with a DATE on either side, TIME for a shift of a TIME,
 * and TIMESTAMP otherwise.
 *
 * <p>A TIME is only ever measured against a TIME. Beside any other family — a text literal and a
 * VARIANT included — the difference is refused under the name it was WRITTEN with, the unit listed as
 * the text it is spelled in: {@code DATEDIFF(hours, g, tm)} lists VARCHAR(5), {@code DATEDIFF(h, g, tm)}
 * VARCHAR(1). An untyped NULL in either value position makes the call NULL and refuses nothing, and a
 * family not listed here is left to the channels that judge it.
 */
final class IntervalCallArguments {

    private IntervalCallArguments() {
    }

    /**
     * The refusal a shift's arguments earn, or null when they compile.
     *
     * @param unit       the shift's unit
     * @param amount     the amount's static type, or null when it has none
     * @param amountText the amount's type as a refusal lists it
     * @param target     the target's static type, or null when it has none
     * @param targetText the target's type as a refusal lists it
     * @return the refusal's sentence, or null
     */
    static String shiftRefusal(final IntervalUnit unit, final DataType amount, final String amountText,
                               final DataType target, final String targetText) {
        if (amount == null || target == null) {
            return null;
        }
        final boolean amountRefused = isNeverInterval(amount) || amount instanceof DateTimeType;
        final boolean targetRefused = isNeverInterval(target) || target instanceof NumericType;
        if (!amountRefused && !targetRefused) {
            return null;
        }
        final String kind;
        String listedTarget = targetText;
        if (isDate(target) && unit.isWholeDay()) {
            kind = "DATE";
        } else if (isDate(target)) {
            kind = "TIMESTAMP";
            listedTarget = "TIMESTAMP_NTZ(9)";
        } else if (isTime(target)) {
            kind = "TIME";
        } else {
            kind = "TIMESTAMP";
        }
        return "Invalid argument types for function 'DATE_ADD" + shiftUnitName(unit) + "TO" + kind + "': ("
            + amountText + ", " + listedTarget + ")";
    }

    /**
     * The refusal a difference's arguments earn, or null when they compile.
     *
     * @param funcName   the call's name as written, upper-cased
     * @param unitText   the unit as it is spelled
     * @param unit       the unit it names
     * @param from       the first value's static type, or null when it has none
     * @param fromText   the first value's type as a refusal lists it
     * @param to         the second value's static type, or null when it has none
     * @param toText     the second value's type as a refusal lists it
     * @return the refusal's sentence, or null
     */
    static String differenceRefusal(final String funcName, final String unitText, final IntervalUnit unit,
                                    final DataType from, final String fromText, final DataType to,
                                    final String toText) {
        if (from == null || to == null) {
            return null;
        }
        if (isTime(from) != isTime(to)) {
            return "Invalid argument types for function '" + funcName + "': (VARCHAR(" + unitText.length()
                + "), " + fromText + ", " + toText + ")";
        }
        if (!isNeverInterval(from) && !(from instanceof NumericType)
                && !isNeverInterval(to) && !(to instanceof NumericType)) {
            return null;
        }
        final String kind = isDate(from) || isDate(to) ? "DATE" : "TIMESTAMP";
        return "Invalid argument types for function 'DATE_DIFF" + kind + "IN" + unit.name() + "S': ("
            + fromText + ", " + toText + ")";
    }

    /**
     * The refusal a call of an internal shift or difference name earns for its types, or null when they compile:
     * a shift's amount must be a number, a text or a VARIANT, and every value must move to the function's kind —
     * a DATE or a timestamp to DATE and TIMESTAMP, a TIME or a timestamp to TIME, a text or a VARIANT to any. A
     * refusal names the function and the two types as the call gives them:
     *
     * <pre>
     *   DATE_ADDDAYSTODATE(TRUE, d)       'DATE_ADDDAYSTODATE': (BOOLEAN, DATE)
     *   DATE_ADDDAYSTODATE(1, tm)         'DATE_ADDDAYSTODATE': (NUMBER(1,0), TIME(9))
     *   DATE_ADDHOURSTOTIME(1, d)         'DATE_ADDHOURSTOTIME': (NUMBER(1,0), DATE)
     *   DATE_DIFFDATEINDAYS(1, d)         'DATE_DIFFDATEINDAYS': (NUMBER(1,0), DATE)
     * </pre>
     *
     * @param name the function's name
     * @param kind DATE, TIMESTAMP or TIME
     * @param shift whether the first value is a shift's amount rather than a difference's operand
     * @param first the first value's static type, or null when it has none
     * @param firstText the first value's type as a refusal lists it
     * @param second the second value's static type, or null when it has none
     * @param secondText the second value's type as a refusal lists it
     * @return the refusal's sentence, or null
     */
    static String plannedRefusal(final String name, final String kind, final boolean shift, final DataType first,
                                 final String firstText, final DataType second, final String secondText) {
        if (first == null || second == null) {
            return null;
        }
        final boolean firstRefused = shift ? isNeverInterval(first) || first instanceof DateTimeType
            : !movesTo(kind, first);
        if (!firstRefused && movesTo(kind, second)) {
            return null;
        }
        return "Invalid argument types for function '" + name + "': (" + firstText + ", " + secondText + ")";
    }

    /**
     * The refusal a TIME earns where an internal name moves its values to a TIMESTAMP — not an argument-type
     * sentence but a cast one: {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]}.
     *
     * @param kind the function's kind
     * @param types the values' static types, any of them null
     * @param texts the values' types as a refusal lists them
     * @return the sentence, or null when no TIME is moved to a TIMESTAMP
     */
    static String plannedIncompatibility(final String kind, final List<DataType> types, final List<String> texts) {
        if (!"TIMESTAMP".equals(kind)) {
            return null;
        }
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) != null && isTime(types.get(i))) {
                return "incompatible types: [" + texts.get(i) + "] and [TIMESTAMP_NTZ(9)]";
            }
        }
        return null;
    }

    /** Whether a value of this type moves to the kind. */
    private static boolean movesTo(final String kind, final DataType type) {
        if (isNeverInterval(type) || type instanceof NumericType) {
            return false;
        }
        if (isTime(type)) {
            return "TIME".equals(kind);
        }
        if (isDate(type)) {
            return !"TIME".equals(kind);
        }
        return true;
    }

    /**
     * A unit as a planned shift names it: in the plural, and MILLIS, MICROS and NANOS for the three
     * smallest — where a difference spells all three out in full.
     *
     * @param unit the unit
     * @return its name inside {@code DATE_ADD…TO…}
     */
    static String shiftUnitName(final IntervalUnit unit) {
        switch (unit) {
            case MILLISECOND:
                return "MILLIS";
            case MICROSECOND:
                return "MICROS";
            case NANOSECOND:
                return "NANOS";
            default:
                return unit.name() + "S";
        }
    }

    /**
     * A family no interval function reads in any position. An interval value is one of them: DATEADD(second, 1,
     * ts - ts2) is 'DATE_ADDSECONDSTOTIMESTAMP': (NUMBER(1,0), INTERVAL DAY(9) TO SECOND(9)) (live-verified).
     */
    private static boolean isNeverInterval(final DataType type) {
        return type instanceof BooleanType || type instanceof ArrayType || type instanceof ObjectType
            || type instanceof MapType || type instanceof BinaryType || type instanceof GeographyType
            || IntervalCasts.isIntervalType(type);
    }

    private static boolean isDate(final DataType type) {
        return type instanceof DateTimeType && "DATE".equals(SqlTypeNames.canonical(type));
    }

    /** A TIME, not a TIMESTAMP — the canonical spellings of both begin with the same four letters. */
    private static boolean isTime(final DataType type) {
        return type instanceof DateTimeType && SqlTypeNames.canonical(type).startsWith("TIME(");
    }
}
