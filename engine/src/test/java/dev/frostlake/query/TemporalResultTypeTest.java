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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the temporal functions DECLARE. The registry names one nominal TIMESTAMP_NTZ for the whole
 * family; live answers from the ARGUMENT, and only DATEADD over a DATE consults the unit as well:
 *
 * <pre>
 *   DATEADD(day, 1, d)      DATE            a whole-day unit leaves a DATE a DATE
 *   DATEADD(hour, 1, d)     TIMESTAMP_NTZ   a sub-day one promotes it
 *   DATEADD(day, 1, tl)     TIMESTAMP_LTZ   a timestamp keeps its FLAVOUR, whatever the unit
 *   DATE_TRUNC(hour, d)     DATE            truncation never changes the family
 *   LAST_DAY(tl)            DATE            these two always answer a DATE
 *   DATEDIFF(hour, …)       NUMBER(9,0)     the UNIT sizes a difference: 9 digits …
 *   DATEDIFF(minute, …)     NUMBER(18,0)    … 18 from MINUTE down …
 *   DATEDIFF(millisecond,…) NUMBER(38,0)    … and 38 below a second
 * </pre>
 *
 * <p>The step between HOUR and MINUTE is the part worth keeping: it is not where "sub-day units are
 * wider" would put it.
 */
public class TemporalResultTypeTest extends BaseDatabaseTest {

    private static final String NTZ = "TIMESTAMP_NTZ(9)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tr (d DATE, tm TIME, ts TIMESTAMP_NTZ,"
            + " tl TIMESTAMP_LTZ, tz TIMESTAMP_TZ, i INT)");
        engine.execute("INSERT INTO tr SELECT '2020-01-01', '10:00:00', '2020-01-01 10:00:00',"
            + " '2020-01-01 10:00:00', '2020-01-01 10:00:00', 1");
    }

    /** The declared type of the expression's result column, temporal precision included. */
    private String typeOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM tr");
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type instanceof DateTimeType) {
            return type.getName() + "(" + ((DateTimeType) type).getPrecision() + ")";
        }
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            final NumericType numeric = (NumericType) type;
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        return type == null ? "null" : type.getName();
    }

    /** Over a DATE the UNIT decides: a whole-day one keeps the DATE, a sub-day one promotes it. */
    @Test
    public void theUnitDecidesWhatADateBecomes() {
        assertEquals("DATE(0)", typeOf("DATEADD(day, 1, d)"));
        assertEquals("DATE(0)", typeOf("DATEADD(year, 1, d)"));
        assertEquals("DATE(0)", typeOf("DATEADD(month, 1, d)"));
        assertEquals("DATE(0)", typeOf("DATEADD(week, 1, d)"));
        assertEquals("DATE(0)", typeOf("DATEADD(quarter, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(hour, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(minute, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(second, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(millisecond, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(microsecond, 1, d)"));
        assertEquals(NTZ, typeOf("DATEADD(nanosecond, 1, d)"));
    }

    /** A quoted unit, a non-constant amount and the two aliases all follow the same rule. */
    @Test
    public void theSpellingOfTheCallChangesNothing() {
        assertEquals("DATE(0)", typeOf("DATEADD('day', 1, d)"));
        assertEquals("DATE(0)", typeOf("DATEADD(day, i, d)"));
        assertEquals("DATE(0)", typeOf("TIMEADD(day, 1, d)"));
        assertEquals("DATE(0)", typeOf("TIMESTAMPADD(day, 1, d)"));
        assertEquals(NTZ, typeOf("TIMEADD(hour, 1, d)"));
        assertEquals(NTZ, typeOf("TIMESTAMPADD(hour, 1, d)"));
    }

    /** Anything already carrying a time keeps its own type, whatever the unit. */
    @Test
    public void aTimestampKeepsItsFlavour() {
        assertEquals(NTZ, typeOf("DATEADD(day, 1, ts)"));
        assertEquals(NTZ, typeOf("DATEADD(hour, 1, ts)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("DATEADD(day, 1, tl)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("DATEADD(hour, 1, tl)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("DATEADD(day, 1, tz)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("DATEADD(hour, 1, tz)"));
        assertEquals("TIME(9)", typeOf("DATEADD(hour, 1, tm)"));
        assertEquals("TIME(9)", typeOf("DATEADD(second, 1, tm)"));
    }

    /** Truncation never changes the family — not even an hour truncation of a DATE. */
    @Test
    public void truncationKeepsTheFamily() {
        assertEquals("DATE(0)", typeOf("DATE_TRUNC(month, d)"));
        assertEquals("DATE(0)", typeOf("DATE_TRUNC(hour, d)"));
        assertEquals("DATE(0)", typeOf("DATE_TRUNC('month', d)"));
        assertEquals("DATE(0)", typeOf("DATE_TRUNC(week, d)"));
        assertEquals(NTZ, typeOf("DATE_TRUNC(day, ts)"));
        assertEquals(NTZ, typeOf("DATE_TRUNC(hour, ts)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("DATE_TRUNC(month, tl)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("DATE_TRUNC(quarter, tl)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("DATE_TRUNC(day, tz)"));
        assertEquals("DATE(0)", typeOf("TRUNC(d, 'month')"));
        assertEquals("DATE(0)", typeOf("TRUNC(d, 'day')"));
        assertEquals(NTZ, typeOf("TRUNC(ts, 'month')"));
    }

    /** LAST_DAY and NEXT_DAY answer a DATE whatever they are handed; ADD_MONTHS keeps the family. */
    @Test
    public void theDayFindersAlwaysAnswerADate() {
        assertEquals("DATE(0)", typeOf("LAST_DAY(d)"));
        assertEquals("DATE(0)", typeOf("LAST_DAY(ts)"));
        assertEquals("DATE(0)", typeOf("LAST_DAY(tl)"));
        assertEquals("DATE(0)", typeOf("LAST_DAY(tz)"));
        assertEquals("DATE(0)", typeOf("LAST_DAY(d, 'month')"));
        assertEquals("DATE(0)", typeOf("NEXT_DAY(d, 'Monday')"));
        assertEquals("DATE(0)", typeOf("NEXT_DAY(ts, 'Monday')"));
        assertEquals("DATE(0)", typeOf("NEXT_DAY(tl, 'Monday')"));
        assertEquals("DATE(0)", typeOf("ADD_MONTHS(d, 1)"));
        assertEquals(NTZ, typeOf("ADD_MONTHS(ts, 1)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("ADD_MONTHS(tl, 1)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("ADD_MONTHS(tz, 1)"));
    }

    /** A DIFFERENCE is sized by its unit, and the step sits between HOUR and MINUTE. */
    @Test
    public void theUnitSizesADifference() {
        assertEquals("NUMBER(9,0)", typeOf("DATEDIFF(day, d, d)"));
        assertEquals("NUMBER(9,0)", typeOf("DATEDIFF(week, d, ts)"));
        assertEquals("NUMBER(9,0)", typeOf("DATEDIFF(year, d, ts)"));
        assertEquals("NUMBER(9,0)", typeOf("DATEDIFF(hour, ts, ts)"));
        assertEquals("NUMBER(18,0)", typeOf("DATEDIFF(minute, d, ts)"));
        assertEquals("NUMBER(18,0)", typeOf("DATEDIFF(second, d, ts)"));
        assertEquals("NUMBER(38,0)", typeOf("DATEDIFF(millisecond, d, ts)"));
        assertEquals("NUMBER(38,0)", typeOf("DATEDIFF(microsecond, d, ts)"));
    }

    /** A conversion declares the flavour ITS OWN NAME asks for, not the one behind it. */
    @Test
    public void aConversionDeclaresTheFlavourItsNameAsksFor() {
        assertEquals("DATE(0)", typeOf("TO_DATE(ts)"));
        assertEquals("DATE(0)", typeOf("TO_DATE(d)"));
        assertEquals("TIME(9)", typeOf("TO_TIME(ts)"));
        assertEquals("TIME(9)", typeOf("TO_TIME(tm)"));
        assertEquals(NTZ, typeOf("TO_TIMESTAMP(d)"));
        assertEquals(NTZ, typeOf("TO_TIMESTAMP_NTZ(d)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("TO_TIMESTAMP_LTZ(d)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("TO_TIMESTAMP_TZ(d)"));
    }

    /** The same holds for the part constructors, which share one construction and four names. */
    @Test
    public void thePartConstructorsDeclareTheirOwnFlavours() {
        assertEquals("DATE(0)", typeOf("DATE_FROM_PARTS(2020, 1, 1)"));
        assertEquals("TIME(9)", typeOf("TIME_FROM_PARTS(1, 2, 3)"));
        assertEquals(NTZ, typeOf("TIMESTAMP_FROM_PARTS(2020, 1, 1, 1, 2, 3)"));
        assertEquals(NTZ, typeOf("TIMESTAMP_NTZ_FROM_PARTS(2020, 1, 1, 1, 2, 3)"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("TIMESTAMP_LTZ_FROM_PARTS(2020, 1, 1, 1, 2, 3)"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("TIMESTAMP_TZ_FROM_PARTS(2020, 1, 1, 1, 2, 3)"));
    }

    /** And the clock functions, where CURRENT_TIMESTAMP is the zoned one and SYSDATE is not. */
    @Test
    public void theClockFunctionsDifferFromOneAnother() {
        assertEquals("DATE(0)", typeOf("CURRENT_DATE"));
        assertEquals("TIME(9)", typeOf("CURRENT_TIME"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("CURRENT_TIMESTAMP"));
        assertEquals(NTZ, typeOf("SYSDATE()"));
    }

    /** The base columns are untouched — the rules must not reach past the functions. */
    @Test
    public void theBaseColumnsAreUntouched() {
        assertEquals("DATE(0)", typeOf("d"));
        assertEquals("TIME(9)", typeOf("tm"));
        assertEquals(NTZ, typeOf("ts"));
        assertEquals("TIMESTAMP_LTZ(9)", typeOf("tl"));
        assertEquals("TIMESTAMP_TZ(9)", typeOf("tz"));
    }
}
