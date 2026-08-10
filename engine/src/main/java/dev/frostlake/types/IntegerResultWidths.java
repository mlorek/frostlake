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

package dev.frostlake.types;

/**
 * The declared PRECISION of the integer-returning functions, which is not one number. A NUMBER column
 * is NUMBER(38,0) and so is anything derived arithmetically from it, but a function that returns a
 * COUNT, a LENGTH, a POSITION or a DATE PART declares only as many digits as its result can occupy —
 * and the account distinguishes five widths, every one of them measured here rather than reasoned:
 *
 * <pre>
 *   2   MONTH DAY DAYOFMONTH DAYOFWEEK DAYOFWEEKISO WEEK WEEKISO QUARTER HOUR MINUTE SECOND
 *   4   YEAR DAYOFYEAR YEAROFWEEK
 *   9   CHARINDEX POSITION ARRAY_SIZE ARRAY_POSITION DATEDIFF TIMESTAMPDIFF
 *   18  LENGTH LEN OCTET_LENGTH REGEXP_INSTR REGEXP_COUNT COUNT ROW_NUMBER RANK DENSE_RANK NTILE
 *   19  BIT_LENGTH HASH
 * </pre>
 *
 * <p>The widths track what the value can BE — two digits for a month, nine for a position in a string,
 * eighteen for a row count, nineteen for a signed 64-bit hash — but do not derive them from that story:
 * DAYOFYEAR is four where DAYOFWEEK is two, and BIT_LENGTH is nineteen where OCTET_LENGTH is eighteen.
 * Measure the function, then add it.
 */
public final class IntegerResultWidths {

    /** A date part that cannot exceed two digits: a month, a day, an hour, a week, a quarter. */
    public static final NumericType DATE_PART_SMALL = new NumericType("NUMBER", 2, 0);

    /** A year, a day of the year, a year-of-week. */
    public static final NumericType YEAR_PART = new NumericType("NUMBER", 4, 0);

    /** A position within a string or array, and the difference between two temporals. */
    public static final NumericType POSITION = new NumericType("NUMBER", 9, 0);

    /** A length, a count, a row number — the widest of the ordinary counters. */
    public static final NumericType COUNTER = new NumericType("NUMBER", 18, 0);

    /** A bit length or a hash, which need the extra digit a signed 64-bit value can reach. */
    public static final NumericType WIDE_COUNTER = new NumericType("NUMBER", 19, 0);

    /** The widest NUMBER, which a difference counted in fractions of a second needs. */
    public static final NumericType WIDEST = new NumericType("NUMBER", 38, 0);


    private IntegerResultWidths() {
    }
}
