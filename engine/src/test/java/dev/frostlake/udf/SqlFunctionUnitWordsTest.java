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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function's body is judged for its unit words at CREATE as a query is: LAST_DAY reads YEAR, QUARTER, MONTH
 * and WEEK, so LAST_DAY(d, hour) and LAST_DAY(d, day) are refused at CREATE, and a unit written as a quoted
 * identifier, DATEADD("day", 1, d), is read as the unit (all live-verified).
 */
public class SqlFunctionUnitWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodysUnitWordsAreJudgedAtCreate() {
        assertAnswer("REFUSED SQL compilation error: ['HOUR'] is not a valid date/time component for function "
                + "LAST_DAY.",
            "CREATE OR REPLACE FUNCTION ld_hour(d DATE) RETURNS DATE AS 'LAST_DAY(d, hour)'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function LD_HOUR.",
            "SELECT ld_hour('2024-02-10'::DATE)");
        assertAnswer("REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function LAST_DAY.",
            "CREATE OR REPLACE FUNCTION ld_day(d DATE) RETURNS DATE AS 'LAST_DAY(d, day)'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function LD_DAY.", "SELECT ld_day('2024-02-10'::DATE)");
        assertAnswer("Function LD_WEEK successfully created.",
            "CREATE OR REPLACE FUNCTION ld_week(d DATE) RETURNS DATE AS 'LAST_DAY(d, week)'");
        assertAnswer("2024-02-11", "SELECT ld_week('2024-02-10'::DATE)");
        assertAnswer("Function LD_Q successfully created.",
            "CREATE OR REPLACE FUNCTION ld_q(d DATE) RETURNS DATE AS 'LAST_DAY(d, quarter)'");
        assertAnswer("2024-03-31", "SELECT ld_q('2024-02-10'::DATE)");
        assertAnswer("REFUSED SQL compilation error: ['HOUR'] is not a valid date/time component for function "
                + "LAST_DAY.",
            "SELECT LAST_DAY('2024-02-10'::DATE, hour)");
        assertAnswer("REFUSED SQL compilation error: ['DAY'] is not a valid date/time component for function LAST_DAY.",
            "SELECT LAST_DAY('2024-02-10'::DATE, day)");
        assertAnswer("REFUSED SQL compilation error: ['NOSUCH'] is not a valid date/time component for function "
                + "LAST_DAY.",
            "SELECT LAST_DAY('2024-02-10'::DATE, nosuch)");
        assertAnswer("REFUSED SQL compilation error: ['NOSUCH'] is not a valid date/time component for function "
                + "LAST_DAY.",
            "CREATE OR REPLACE FUNCTION ld_bad(d DATE) RETURNS DATE AS 'LAST_DAY(d, nosuch)'");
        assertAnswer("Function DQ successfully created.",
            "CREATE OR REPLACE FUNCTION dq(d DATE) RETURNS DATE AS 'DATEADD(\"day\", 1, d)'");
        assertAnswer("2024-01-02", "SELECT dq('2024-01-01'::DATE)");
        assertAnswer("2024-01-02", "SELECT DATEADD(\"day\", 1, '2024-01-01'::DATE)");
        assertAnswer("Function DQ2 successfully created.",
            "CREATE OR REPLACE FUNCTION dq2(d DATE) RETURNS DATE AS 'DATEADD(\"DAY\", 1, d)'");
        assertAnswer("2024-01-02", "SELECT dq2('2024-01-01'::DATE)");
        assertAnswer("2024-01-02", "SELECT DATEADD(\"DAY\", 1, '2024-01-01'::DATE)");
        assertAnswer("Function DQ3 successfully created.",
            "CREATE OR REPLACE FUNCTION dq3(d DATE) RETURNS NUMBER AS 'DATEDIFF(\"day\", d, d)'");
        assertAnswer("0", "SELECT dq3('2024-01-01'::DATE)");
    }
}
