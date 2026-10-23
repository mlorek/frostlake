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
 * SYSTEM$TYPEOF of a difference of two dates is NUMBER(9,0)[SB4], the tag of its declared width, whatever the
 * dates hold and with a NULL date beside the other; arithmetic over it widens as over a NUMBER(9,0)
 * (all live-verified).
 */
public class DateDifferenceTypeOfTest extends MeasuredCellsTestSupport {

    @Test
    public void dateDifferences() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(NULL::DATE - DATE '2024-01-01')");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(DATE '2024-01-01' - NULL::DATE)");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(DATE '2024-03-01' - DATE '2024-01-01')");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(DATE '9999-12-31' - DATE '0001-01-01')");
        assertAnswer("Table DD55 successfully created.", "CREATE OR REPLACE TABLE dd55 (d1 DATE, d2 DATE)");
        assertAnswer("1", "INSERT INTO dd55 VALUES ('2024-01-01', '2024-03-01')");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(d2 - d1) FROM dd55");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(d2 - DATE '2024-01-01') FROM dd55");
        assertAnswer("NUMBER(9,0)[SB4]", "SELECT SYSTEM$TYPEOF(DATEDIFF(day, d1, d2)) FROM dd55");
        assertAnswer("DATE[SB4]", "SELECT SYSTEM$TYPEOF(d2 - 1) FROM dd55");
        assertAnswer("60|NUMBER(10,0)[SB8]", "SELECT d2 - d1, SYSTEM$TYPEOF(d2 - d1 + 1) FROM dd55");
        assertAnswer("NUMBER(10,0)[SB8]", "SELECT SYSTEM$TYPEOF((d2 - d1) * 2) FROM dd55");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
