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
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The operand a date-part call reads its part from, judged while the statement compiles — over an empty table as
 * over a full one, in a branch never reached, in a view's body (all live-verified). A value that is no date, time
 * or timestamp is refused by its type, ahead of the part's own name:
 *
 * <pre>
 *   EXTRACT(year FROM 2)     Function EXTRACT does not support NUMBER(1,0) argument type
 *   YEAR(a), DATE_PART(…)    the same sentence, naming EXTRACT: one function reads every part
 *   EXTRACT(xyz FROM 2)      the same: the operand is judged before the part
 *   DATE_TRUNC('day', 2)     Function DATE_TRUNC does not support NUMBER(1,0) argument type
 *   LAST_DAY(2)              Function LAST_DAY does not support NUMBER(1,0) argument type
 *   TIME_SLICE(2, 1, 'HOUR') Function TIME_SLICE does not support NUMBER(1,0) argument type
 * </pre>
 *
 * <p>The refused families are NUMBER, FLOAT, BOOLEAN, ARRAY, OBJECT, MAP, BINARY, GEOGRAPHY and GEOMETRY; a text
 * and a VARIANT are refused in the same sentence where they are judged. A date or a time that lacks the part is
 * refused in the part's name instead, the function naming itself:
 *
 * <pre>
 *   EXTRACT(hour FROM d)     invalid value [hour] for parameter 'EXTRACT date/time part'
 *   HOUR(d)                  invalid value [HOUR] for parameter 'HOUR date/time part'
 *   EXTRACT(year FROM tm)    invalid value [year] for parameter 'EXTRACT date/time part'
 *   DATE_PART(epoch_second, tm)   invalid value [EPOCH_SECOND] for parameter 'DATE_PART date/time part'
 * </pre>
 *
 * <p>A TIME has an hour, a minute, a second and a nanosecond and nothing else, its epoch included; a DATE has
 * every part but those four and the two zone components.
 */
final class DatePartOperands {

    /** The functions that read one part, each named after it. */
    private static final Set<String> ONE_PART = new HashSet<>(Arrays.asList(
        "YEAR", "QUARTER", "MONTH", "WEEK", "WEEKOFYEAR", "WEEKISO", "DAY", "DAYOFMONTH", "DAYOFWEEK",
        "DAYOFWEEKISO", "DAYOFYEAR", "HOUR", "MINUTE", "SECOND", "YEAROFWEEK", "YEAROFWEEKISO"));

    /** The zone components, by every spelling. */
    private static final Set<String> ZONE_PARTS = new HashSet<>(Arrays.asList(
        "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TZH", "TZM"));

    private DatePartOperands() {
    }

    /**
     * Where the operand stands among a call's arguments.
     *
     * @param funcName the call's name, upper-cased
     * @return the operand's index, or -1 when the function is none of these
     */
    static int operandAt(final String funcName) {
        if ("EXTRACT".equals(funcName) || "DATE_PART".equals(funcName) || "DATE_TRUNC".equals(funcName)) {
            return 1;
        }
        return ONE_PART.contains(funcName) || "LAST_DAY".equals(funcName) || "TIME_SLICE".equals(funcName) ? 0 : -1;
    }

    /**
     * Whether the function reads a part of its operand: EXTRACT, DATE_PART, or a function named after one part.
     *
     * @param funcName the call's name, upper-cased
     * @return whether it is one of the part readers
     */
    static boolean readsPart(final String funcName) {
        return "EXTRACT".equals(funcName) || "DATE_PART".equals(funcName) || ONE_PART.contains(funcName);
    }

    /**
     * Whether the function is named after the one part it reads.
     *
     * @param funcName the call's name, upper-cased
     * @return whether it is one of them
     */
    static boolean isOnePart(final String funcName) {
        return ONE_PART.contains(funcName);
    }

    /**
     * The name a refused operand's sentence gives the function: its own for DATE_TRUNC, LAST_DAY and TIME_SLICE,
     * and EXTRACT for every part reader.
     *
     * @param funcName the call's name, upper-cased
     * @return the name in the sentence
     */
    static String refusedAs(final String funcName) {
        return "DATE_TRUNC".equals(funcName) || "LAST_DAY".equals(funcName) || "TIME_SLICE".equals(funcName)
            ? funcName : "EXTRACT";
    }

    /**
     * Whether a static type is of a family no date function reads as a date, a time or a timestamp, and converts
     * from none either.
     *
     * @param type the operand's static type, or null when it has none
     * @return whether the operand is refused by its type
     */
    static boolean isNeverTemporal(final DataType type) {
        return type instanceof NumericType || type instanceof BooleanType || type instanceof ArrayType
            || type instanceof ObjectType || type instanceof MapType || type instanceof BinaryType
            || type instanceof GeographyType || type instanceof GeometryType;
    }

    /**
     * Whether a TIME_SLICE operand is a temporal it does not slice: a TIME, or a timestamp that carries a zone.
     *
     * @param type the operand's static type, or null when it has none
     * @return whether the operand is refused by its type
     */
    static boolean isUnslicedTemporal(final DataType type) {
        if (!(type instanceof DateTimeType)) {
            return false;
        }
        final String name = SqlTypeNames.canonical(type);
        return isTime(type) || name.startsWith("TIMESTAMP_TZ") || name.startsWith("TIMESTAMP_LTZ");
    }

    /**
     * Whether the operand's kind lacks a part: a TIME anything but its hour, minute, second and nanosecond, a
     * DATE those four and the zone components.
     *
     * @param part    the part, in any spelling the part readers know
     * @param operand the operand's static type, or null when it has none
     * @return whether the part is refused for the operand
     */
    static boolean lacksPart(final String part, final DataType operand) {
        if (part == null || !(operand instanceof DateTimeType)) {
            return false;
        }
        final IntervalUnit unit = IntervalUnit.fromSpelling(part);
        final boolean ofTheClock = unit != null && !unit.isWholeDay() || "NSECOND".equalsIgnoreCase(part);
        if (isTime(operand)) {
            return !ofTheClock;
        }
        return isDate(operand) && (ofTheClock || ZONE_PARTS.contains(part.toUpperCase(Locale.ROOT)));
    }

    static boolean isDate(final DataType type) {
        return type instanceof DateTimeType && "DATE".equals(SqlTypeNames.canonical(type));
    }

    /** A TIME, not a TIMESTAMP: the canonical spellings of both begin with the same four letters. */
    static boolean isTime(final DataType type) {
        return type instanceof DateTimeType && SqlTypeNames.canonical(type).startsWith("TIME(");
    }
}
