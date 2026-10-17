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

import org.junit.jupiter.api.Test;

/**
 * A date or a timestamp moved by an interval, or by DATEADD over a timestamp, keeps the days its column holds moved
 * with it, which a year read from it is tagged by: {@code YEAR(t + INTERVAL '7 years')} over the years 100 to 120 is
 * [SB1] and {@code '8 years'} [SB2]. An interval written before a date is refused by the operator's argument types
 * while the statement compiles (live-verified).
 */
public class IntervalDayRangeTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aMovedTimestampKeepsItsDays() {
        engine.execute("CREATE OR REPLACE TABLE td (t6 TIMESTAMP_NTZ, d6 DATE, tl TIMESTAMP_LTZ)");
        engine.execute("INSERT INTO td VALUES ('0100-01-01 10:00:00', '0100-01-01', '0100-01-01 10:00:00'), "
            + "('0120-06-01 23:30:00', '0120-06-01', '0120-06-01 23:30:00')");
        engine.execute("CREATE OR REPLACE TABLE ti (i INT)");
        engine.execute("INSERT INTO ti VALUES (3), (300)");
        engine.execute("CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(5)) LIMIT 1", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1 day')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 - INTERVAL '1 day')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1 hour')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1 year')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1000 years')) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(d6 + INTERVAL '1 day')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(hour, 1, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(day, 1, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(DAYOFYEAR(t6 + INTERVAL '1 day')) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(tl + INTERVAL '1 day')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1 day 2 hours')) FROM td LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '2'."},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '30 minutes')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
        });
    }

    @Test
    public void everyMoveIsReadThrough() {
        engine.execute("CREATE OR REPLACE TABLE td (t6 TIMESTAMP_NTZ, d6 DATE)");
        engine.execute("INSERT INTO td VALUES ('0100-01-01 10:00:00', '0100-01-01'), ('0120-06-01 23:30:00', "
            + "'0120-06-01')");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 7, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 8, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '7 years')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '8 years')) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(month, 1, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 + INTERVAL '1 month')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(t6 - INTERVAL '1 year')) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(day, -1, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 7, d6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 8, d6))) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(d6 + INTERVAL '8 years')) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(DAYOFYEAR(DATEADD(day, 1, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(minute, 30, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(hour, -10, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(TIMESTAMPADD(year, 8, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(year, 7, t6) + INTERVAL '1 year')) FROM td LIMIT 1",
                "NUMBER(4,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(INTERVAL '1 year' + t6)) FROM td LIMIT 1",
                "REFUSED SQL compilation error: error line 1 at position 44 | Invalid argument types for function "
                + "'+': (INTERVAL, TIMESTAMP_NTZ(9))"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(month, 90, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(YEAR(DATEADD(month, 96, t6))) FROM td LIMIT 1", "NUMBER(4,0)[SB2]"},
        });
    }
}
