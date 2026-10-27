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

package dev.frostlake.features;

import dev.frostlake.metastore.model.DynamicTableRefreshRecord;
import dev.frostlake.metastore.model.RefreshTrigger;
import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/**
 * DYNAMIC_TABLE_REFRESH_HISTORY reads the past day when no DATA_TIMESTAMP bound is given, ends a range now and starts
 * it a day before its end, and lists no refresh whose data timestamp is more than seven days old (documented).
 */
public class DynamicTableRefreshWindowTest extends MeasuredCellsTestSupport {

    /** A refresh of a table of that name recorded as if it ran so long ago. */
    private void ranAgo(final String table, final Duration ago) {
        final Instant start = Instant.now().minus(ago);
        engine.getCatalog().getDynamicTableRefreshHistory().add(new DynamicTableRefreshRecord("TEST_DB",
            "TEST_SCHEMA", table, "1 hour", start, RefreshTrigger.MANUAL, "INCREMENTAL", "q", start, start,
            new long[5], "W", new ArrayList<>(), null, null));
    }

    private static String names(final String bounds) {
        return "SELECT NAME FROM TABLE(INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => "
            + "'TEST_DB.TEST_SCHEMA.W'" + bounds + ")) ORDER BY NAME";
    }

    @Test
    public void aRefreshIsListedForSevenDaysAndByDefaultForOne() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the refreshes are recorded as if they ran days ago");
        ranAgo("W2DAYS", Duration.ofDays(2));
        ranAgo("W8DAYS", Duration.ofDays(8));
        engine.execute("CREATE TABLE wsrc (a INT)");
        engine.execute("INSERT INTO wsrc VALUES (1)");
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        engine.execute("CREATE DYNAMIC TABLE wnow TARGET_LAG = '1 hour' WAREHOUSE = "
            + (wh == null ? "COMPUTE_WH" : String.valueOf(wh)) + " AS SELECT a FROM wsrc");
        assertAnswer("WNOW", names(""));
        assertAnswer("W2DAYS ;; WNOW", names(", DATA_TIMESTAMP_START => DATEADD(DAY, -3, CURRENT_TIMESTAMP())"));
        assertAnswer("W2DAYS", names(", DATA_TIMESTAMP_END => DATEADD(HOUR, -36, CURRENT_TIMESTAMP())"));
        assertAnswer("W2DAYS ;; WNOW", names(", DATA_TIMESTAMP_START => DATEADD(DAY, -30, CURRENT_TIMESTAMP())"));
        assertAnswer("", names(", DATA_TIMESTAMP_START => DATEADD(DAY, -30, CURRENT_TIMESTAMP()), "
            + "DATA_TIMESTAMP_END => DATEADD(DAY, -7, CURRENT_TIMESTAMP())"));
    }
}
