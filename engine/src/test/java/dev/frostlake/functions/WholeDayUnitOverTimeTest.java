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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A TIME carries no date, so a WHOLE-DAY unit has nothing to work on: there is no day to add to a
 * time of day, no months between two of them, nothing to truncate to a year. The account refuses all
 * of those and answers the sub-day units normally; Frostlake accepted them and returned the time back
 * unchanged.
 *
 * <p>THREE sentences, and the differences are measured rather than tidied — the ADD family quotes the
 * unit and names the type WITH its precision, the DIFF and TRUNCATE family does neither, and DATE_PART
 * speaks about its parameter instead:
 *
 * <pre>
 *   DATEADD(day, 1, tm)     ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).
 *   DATEDIFF(day, tm, tm)   [DAY] is not a valid date/time component for function DATEDIFF and type TIME.
 *   DATE_PART(day, tm)      invalid value [DAY] for parameter 'DATE_PART date/time part'
 * </pre>
 *
 * <p>The refusal is decided at PLAN time, which the empty-table case pins: raised while reading a
 * value it would never fire over zero rows, and a view over the statement would be created.
 */
public class WholeDayUnitOverTimeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE dt (tm TIME, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO dt SELECT '10:31:45', '2020-03-04 10:31:45'");
        engine.execute("CREATE OR REPLACE TABLE dtempty (tm TIME, ts TIMESTAMP_NTZ)");
    }

    /** The answer, the refusal's message, or a marker when the query simply returns nothing. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** The ADD family quotes the unit and names the type with its precision. */
    @Test
    public void theAddFamilyRefusesAWholeDayUnit() {
        for (final String unit : new String[] {"day", "week", "month", "year"}) {
            assertEquals("SQL compilation error: ['" + unit.toUpperCase()
                    + "'] is not a valid date/time component for function DATEADD and type TIME(9).",
                outcome("SELECT TO_VARCHAR(DATEADD(" + unit + ", 1, tm)) AS x FROM dt"), unit);
        }
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component"
            + " for function TIMEADD and type TIME(9).",
            outcome("SELECT TO_VARCHAR(TIMEADD(day, 1, tm)) AS x FROM dt"));
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component"
            + " for function TIMESTAMPADD and type TIME(9).",
            outcome("SELECT TO_VARCHAR(TIMESTAMPADD(day, 1, tm)) AS x FROM dt"));
    }

    /** The DIFF family neither quotes the unit nor gives the type a precision. */
    @Test
    public void theDiffFamilyRefusesItDifferently() {
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function DATEDIFF and type TIME.",
            outcome("SELECT TO_VARCHAR(DATEDIFF(day, tm, tm)) AS x FROM dt"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function TIMEDIFF and type TIME.",
            outcome("SELECT TO_VARCHAR(TIMEDIFF(day, tm, tm)) AS x FROM dt"));
    }

    /** And DATE_PART speaks about its parameter rather than the function. */
    @Test
    public void theComponentReaderRefusesItAsAParameterValue() {
        assertEquals("SQL compilation error: invalid value [DAY] for parameter"
            + " 'DATE_PART date/time part'",
            outcome("SELECT TO_VARCHAR(DATE_PART(day, tm)) AS x FROM dt"));
    }

    /** The sub-day units answer, on every one of them. */
    @Test
    public void theSubDayUnitsStillAnswer() {
        assertEquals("11:31:45", outcome("SELECT TO_VARCHAR(DATEADD(hour, 1, tm)) AS x FROM dt"));
        assertEquals("10:32:45", outcome("SELECT TO_VARCHAR(DATEADD(minute, 1, tm)) AS x FROM dt"));
        assertEquals("10:31:46", outcome("SELECT TO_VARCHAR(DATEADD(second, 1, tm)) AS x FROM dt"));
        assertEquals("0", outcome("SELECT TO_VARCHAR(DATEDIFF(hour, tm, tm)) AS x FROM dt"));
        assertEquals("10", outcome("SELECT TO_VARCHAR(DATE_PART(hour, tm)) AS x FROM dt"),
            "a TIME reads its own clock components");
    }

    /** A TIMESTAMP takes every unit, so the check reaches only the TIME. */
    @Test
    public void aTimestampIsUnaffected() {
        assertEquals("2020-03-05 10:31:45.000",
            outcome("SELECT TO_VARCHAR(DATEADD(day, 1, ts)) AS x FROM dt"));
    }

    /**
     * And the point of deciding it at plan time: an EMPTY table refuses just the same. A refusal
     * raised while reading a value would let this through and create a view over it.
     */
    @Test
    public void anEmptyTableRefusesJustTheSame() {
        assertEquals("SQL compilation error: ['DAY'] is not a valid date/time component"
            + " for function DATEADD and type TIME(9).",
            outcome("SELECT TO_VARCHAR(DATEADD(day, 1, tm)) AS x FROM dtempty"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.",
            outcome("SELECT TO_VARCHAR(DATE_TRUNC(day, tm)) AS x FROM dtempty"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function TRUNC and type TIME.",
            outcome("SELECT TO_VARCHAR(TRUNC(tm, 'day')) AS x FROM dtempty"));
        assertEquals("SQL compilation error: [DAY] is not a valid date/time component"
            + " for function DATE_TRUNC and type TIME.",
            outcome("SELECT TO_VARCHAR(DATE_TRUNC(day, tm)) AS x FROM dt WHERE 1 = 0"),
            "no row survives the filter, and it still refuses");
    }
}
