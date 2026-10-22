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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines told after a time-zone phrase a type cannot take: a cast's complete type followed by WITH or WITHOUT,
 * a TIMESTAMP whose words break off, a column's type followed by WITH and no column clause; and a COMMENT target
 * of two words told at its second word alone (all live-verified).
 */
public class TimeZonePhraseFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void timeZonePhrases() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'WITHOUT'. | "
                + "syntax error line 1 at position 68 unexpected 'ZONE'.",
            "SELECT SYSTEM$TYPEOF(CAST('2020-01-01' AS TIMESTAMP(3) WITHOUT TIME ZONE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'WITH'. | syntax "
                + "error line 1 at position 65 unexpected 'ZONE'.",
            "SELECT SYSTEM$TYPEOF(CAST('2020-01-01' AS TIMESTAMP(3) WITH TIME ZONE))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'WITH'. | syntax "
                + "error line 1 at position 47 unexpected ')'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP WITH ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'WITHOUT'. | "
                + "syntax error line 1 at position 50 unexpected 'TIME'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP_NTZ WITHOUT TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'WITH'. | syntax "
                + "error line 1 at position 43 unexpected 'ZONE'.",
            "SELECT CAST('2020-01-01' AS TIME WITH TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TABLES'.",
            "COMMENT ON ICEBERG TABLES ice_k IS 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TABLES'.",
            "COMMENT ON DYNAMIC TABLES dk IS 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'VIEWS'.",
            "COMMENT ON MATERIALIZED VIEWS mk IS 'x'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'WITH'. | syntax "
                + "error line 1 at position 52 unexpected 'TIME'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP(3) WITH LOCAL TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'WITH'. | syntax "
                + "error line 1 at position 52 unexpected 'ZONE'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP_LTZ WITH TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'WITH'. | syntax "
                + "error line 1 at position 43 unexpected 'ZONE'.",
            "SELECT CAST('2020-01-01' AS DATE WITH TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'ZONE'. | syntax "
                + "error line 1 at position 38 unexpected 'WITHOUT'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP WITHOUT ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ')'. | syntax "
                + "error line 1 at position 38 unexpected 'WITH'.",
            "SELECT CAST('2020-01-01' AS TIMESTAMP WITH TIME)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'WITH'.",
            "SELECT '2020-01-01'::TIMESTAMP(3) WITH TIME ZONE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'TIME'.",
            "CREATE OR REPLACE TABLE t2039 (c TIMESTAMP(3) WITH TIME ZONE)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'ZONE'.",
            "CREATE OR REPLACE TABLE t2039 (c TIMESTAMP WITH ZONE)");
    }
}
