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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A FLOAT amount of DATEADD, TIMEADD, TIMESTAMPADD and ADD_MONTHS is read as the fixed-point count its unit takes
 * (live-verified): a day or coarser, or an hour, takes a FIXED[SB4](9,0) and anything finer a FIXED[SB8](18,0), so NaN,
 * an infinity and an amount that rounds to a billion, or to 10^18, are refused in the cast's words, the amount printed
 * as C's %g prints it and the argument's nullability spelled in; a VARIANT holding NaN is the VARIANT's cast.
 */
public class DateShiftFloatAmountTest extends MeasuredCellsTestSupport {

    /** Each shift and its answer. */
    @Test
    public void aFloatAmountIsReadAsAFixedCount() {
        engine.execute("CREATE OR REPLACE TABLE dn_t (k INT, f FLOAT, n NUMBER(10,2), d DATE)");
        engine.execute("INSERT INTO dn_t SELECT 1, 'NaN'::FLOAT, 0, '2024-01-01'::DATE UNION ALL SELECT 2, "
            + "'inf'::FLOAT, -2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT DATEADD(day, 'NaN'::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATEADD(day, 'inf'::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf"},
            {"SELECT TIMEADD(minute, '-inf'::FLOAT, '10:00:00'::TIME)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value -inf"},
            {"SELECT TIMESTAMPADD(hour, 'NaN'::FLOAT, '2024-01-01 00:00:00'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATEADD(month, 'NaN'::FLOAT, '2024-01-31'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT ADD_MONTHS('2024-01-31'::DATE, 'NaN'::FLOAT)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT ADD_MONTHS('2024-01-31'::DATE, '-inf'::FLOAT)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value -inf"},
            {"SELECT DATEADD(day, 1.5::FLOAT, '2024-01-01'::DATE), ADD_MONTHS('2024-01-31'::DATE, 1.5::FLOAT)",
                "2024-01-03|2024-03-31"},
            {"SELECT DATEADD(day, 1e300::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+300"},
            {"SELECT DATEADD(day, NULL::FLOAT, '2024-01-01'::DATE), ADD_MONTHS('2024-01-31'::DATE, NULL::FLOAT)",
                "null|null"},
            {"SELECT k, DATEADD(day, f, d) FROM dn_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value nan"},
            {"SELECT k, ADD_MONTHS(d, f) FROM dn_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value nan"},
            {"SELECT DATEADD(day, f, d) FROM dn_t WHERE 1 = 0",
                ""},
        });
    }

    /** Each shift and its answer. */
    @Test
    public void theRefusalPrintsTheAmountAsCDoes() {
        engine.execute("CREATE OR REPLACE TABLE dq_t (k INT, d DATE)");
        engine.execute("INSERT INTO dq_t SELECT 2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT DATEADD(day, 3e9::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09"},
            {"SELECT DATEADD(day, 2147483648.0::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 2.14748e+09"},
            {"SELECT DATEADD(day, 2147483647.6::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 2.14748e+09"},
            {"SELECT DATEADD(day, -1e10::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value -1e+10"},
            {"SELECT DATEADD(minute, 1e19::FLOAT, '2024-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+19"},
            {"SELECT DATEADD(minute, 9.3e18::FLOAT, '2024-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 9.3e+18"},
            {"SELECT DATEADD(second, 1.5e300::FLOAT, '2024-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1.5e+300"},
            {"SELECT DATEADD(day, 123456789012.5::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1.23457e+11"},
            {"SELECT ADD_MONTHS('2024-01-01'::DATE, 3e9::FLOAT)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09"},
            {"SELECT DATEADD(day, 3e9, '2024-01-01'::DATE)",
                "REFUSED Numeric value '3000000000' is out of range"},
            {"SELECT DATEADD(day, 'NaN', '2024-01-01'::DATE)",
                "REFUSED Numeric value 'NaN' is not recognized"},
            {"SELECT DATEADD(day, TO_VARIANT('NaN'::FLOAT), '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED, value NaN"},
            {"SELECT DATEADD(year, 'NaN'::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATEADD(quarter, 'NaN'::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATEADD(week, 'NaN'::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT DATEADD(second, 'NaN'::FLOAT, '2024-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan"},
            {"SELECT TIMEADD(hour, 'inf'::FLOAT, '10:00:00'::TIME)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf"},
            {"SELECT TIMEADD(millisecond, 'inf'::FLOAT, '10:00:00'::TIME)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf"},
            {"SELECT TIMEADD(nanosecond, 'NaN'::FLOAT, '10:00:00'::TIME)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value nan"},
            {"SELECT '2024-01-01'::DATE + 'NaN'::FLOAT",
                "REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for function "
                + "'+': (DATE, FLOAT)"},
        });
    }

    /** Each shift and its answer. */
    @Test
    public void theCountsLimitAndNullabilityAreTheCasts() {
        engine.execute("CREATE OR REPLACE TABLE dl_t (k INT, f FLOAT, fn FLOAT NOT NULL, z INT, d DATE)");
        engine.execute("INSERT INTO dl_t SELECT 1, 'NaN'::FLOAT, 3e9::FLOAT, 0, '2024-01-03'::DATE UNION ALL SELECT "
            + "2, 1.5, 1.5, -2, '2024-01-03'::DATE");
        assertCells(new String[][] {
            {"SELECT DATEADD(day, 1.5e9::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1.5e+09"},
            {"SELECT DATEADD(day, 999999999.4::FLOAT, '2024-01-01'::DATE)",
                "+2739931-01-03"},
            {"SELECT DATEADD(day, 999999999.6::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+09"},
            {"SELECT DATEADD(day, 1000000000::FLOAT, '2024-01-01'::DATE)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+09"},
            {"SELECT DATEADD(day, 999999999, '2024-01-01'::DATE)",
                "+2739931-01-03"},
            {"SELECT DATEADD(hour, 1.5e9::FLOAT, '2024-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1.5e+09"},
            {"SELECT ADD_MONTHS('2024-01-01'::DATE, 1.5e9::FLOAT)",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 1.5e+09"},
            {"SELECT DATEADD(nanosecond, 1.5e18::FLOAT, '2000-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1.5e+18"},
            {"SELECT DATEADD(nanosecond, 9.9e17::FLOAT, '2000-01-01'::TIMESTAMP_NTZ)",
                "2031-05-16T08:00"},
            {"SELECT DATEADD(nanosecond, 999999999999999999.6::FLOAT, '2000-01-01'::TIMESTAMP_NTZ)",
                "REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+18"},
            {"SELECT k, DATEADD(day, fn, d) FROM dl_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09"},
            {"SELECT k, DATEADD(day, f + 1, d) FROM dl_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value nan"},
            {"SELECT k, DATEADD(day, COALESCE(f, 0), d) FROM dl_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"},
            {"SELECT k, ADD_MONTHS(d, fn) FROM dl_t ORDER BY k",
                "REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09"},
        });
    }
}
