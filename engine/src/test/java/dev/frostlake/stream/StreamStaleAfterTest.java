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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW STREAMS stale_after is the stream's last consumption, or its creation before any, plus the greater of its
 * table's DATA_RETENTION_TIME_IN_DAYS and MAX_DATA_EXTENSION_TIME_IN_DAYS, as the table has them (documented:
 * "calculated by adding the greater value of the DATA_RETENTION_TIME_IN_DAYS or MAX_DATA_EXTENSION_TIME_IN_DAYS
 * parameters setting to the last consumption time of the stream"). It was always the creation plus 14 days.
 * Unmeasured on live.
 */
public class StreamStaleAfterTest extends MeasuredCellsTestSupport {

    private static final String DAYS = """
        SELECT DATEDIFF(day, "created_on", "stale_after") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))""";

    /** The days SHOW STREAMS puts between a stream's creation and its stale_after. */
    private String staleDays(final String stream) {
        engine.execute("SHOW STREAMS LIKE '" + stream + "'");
        return answer(DAYS);
    }

    @Test
    public void theTablesRetentionSetsTheDays() {
        engine.execute("CREATE OR REPLACE TABLE ssa30 (a INT) DATA_RETENTION_TIME_IN_DAYS = 30");
        engine.execute("CREATE OR REPLACE STREAM SSA_S30 ON TABLE ssa30");
        assertEquals("30", staleDays("SSA_S30"));
        engine.execute("CREATE OR REPLACE TABLE ssa1 (a INT) DATA_RETENTION_TIME_IN_DAYS = 1"
            + " MAX_DATA_EXTENSION_TIME_IN_DAYS = 0");
        engine.execute("CREATE OR REPLACE STREAM SSA_S1 ON TABLE ssa1");
        assertEquals("1", staleDays("SSA_S1"));
        engine.execute("CREATE OR REPLACE TABLE ssa5 (a INT) MAX_DATA_EXTENSION_TIME_IN_DAYS = 5");
        engine.execute("CREATE OR REPLACE STREAM SSA_S5 ON TABLE ssa5");
        assertEquals("5", staleDays("SSA_S5"));
        engine.execute("CREATE OR REPLACE TABLE ssa14 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM SSA_S14 ON TABLE ssa14");
        assertEquals("14", staleDays("SSA_S14"));
    }
}
