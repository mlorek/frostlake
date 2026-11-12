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
 * TIME_SLICE answers in the type of what it slices: "identical to the data type of the input date_or_time_expr (i.e.
 * either TIMESTAMP_NTZ or DATE)" (documented). A DATE's slice computed a DATE but was declared TIMESTAMP_NTZ(9), so a
 * driver, SYSTEM$TYPEOF and a table created from it all read a timestamp.
 */
public class TimeSliceDateTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aDatesSliceIsADate() {
        engine.execute("CREATE OR REPLACE TABLE tsd (d DATE, t TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO tsd VALUES ('2024-02-15', '2024-02-15 10:30:00')");
        assertShape("X:DATE, Y:TIMESTAMP_NTZ", "SELECT TIME_SLICE(d, 1, 'MONTH') AS x, TIME_SLICE(t, 1, 'MONTH') AS y "
            + "FROM tsd");
        assertAnswer("DATE[SB4]|2024-02-01|2024-02-15",
            "SELECT SYSTEM$TYPEOF(TIME_SLICE(d, 1, 'MONTH')), TIME_SLICE(d, 1, 'MONTH')::VARCHAR, "
                + "TIME_SLICE(d, 1, 'DAY')::VARCHAR FROM tsd");
        engine.execute("CREATE OR REPLACE TABLE tsd_c AS SELECT TIME_SLICE(d, 1, 'MONTH') AS x FROM tsd");
        assertShape("X:DATE", "SELECT x FROM tsd_c");
        assertAnswer("2024-02-01", "SELECT x::VARCHAR FROM tsd_c");
        assertShape("X:DATE", "SELECT TIME_SLICE(d, 1, 'MONTH') AS x FROM tsd UNION ALL SELECT d FROM tsd");
    }
}
