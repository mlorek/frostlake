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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QueryHistoryTracker}: recording, id lookup, the filter views (user / database / type /
 * status), limited listing, clearing, and max-size eviction.
 */
public class QueryHistoryTrackerTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(QueryHistoryTrackerTest.class);

    @Test
    public void trackerRecordsAndFiltersExecutedQueries() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "reads the embedded QueryHistoryTracker (engine.getExecutor()) and expects the statements it "
            + "just ran to be its entire contents; a live session's statements never reach that tracker");
        final QueryHistoryTracker tracker = engine.getExecutor().getQueryHistoryTracker();
        tracker.clear();

        engine.executeQuery("SELECT 1 AS probe");
        engine.execute("CREATE TABLE qh_t (i INTEGER)");
        engine.execute("INSERT INTO qh_t VALUES (7)");

        final List<QueryHistory> all = tracker.getAllHistory();
        assertTrue(all.size() >= 3, "three statements must be recorded, got " + all.size());

        assertEquals(2, tracker.getHistory(2).size());

        final QueryHistory sample = all.get(0);
        assertNotNull(sample.getQueryId());
        assertNotNull(sample.getStatus());

        final QueryHistory byId = tracker.getQueryById(sample.getQueryId());
        assertNotNull(byId);
        assertEquals(sample.getQueryText(), byId.getQueryText());
        assertNull(tracker.getQueryById("no-such-query-id"));

        boolean foundByUser = false;
        for (final QueryHistory h : tracker.getHistoryByUser(sample.getUser())) {
            if (sample.getQueryId().equals(h.getQueryId())) {
                foundByUser = true;
            }
        }
        assertTrue(foundByUser, "user filter must include the sample entry");

        boolean foundByDatabase = false;
        for (final QueryHistory h : tracker.getHistoryByDatabase(sample.getDatabase())) {
            if (sample.getQueryId().equals(h.getQueryId())) {
                foundByDatabase = true;
            }
        }
        assertTrue(foundByDatabase, "database filter must include the sample entry");

        boolean foundByStatus = false;
        for (final QueryHistory h : tracker.getHistoryByStatus(sample.getStatus())) {
            if (sample.getQueryId().equals(h.getQueryId())) {
                foundByStatus = true;
            }
        }
        assertTrue(foundByStatus, "status filter must include the sample entry");

        tracker.clear();
        assertEquals(0, tracker.getAllHistory().size());
        logger.info("Tracker filters verified over {} recorded entries", all.size());
    }

    @Test
    public void trackerEvictsBeyondMaxSize() {
        final QueryHistoryTracker small = new QueryHistoryTracker(2);
        small.addQuery(new QueryHistory("SELECT 1", "DB1", "S1", "WH", "U1", "R1"));
        small.addQuery(new QueryHistory("SELECT 2", "DB1", "S1", "WH", "U1", "R1"));
        small.addQuery(new QueryHistory("SELECT 3", "DB1", "S1", "WH", "U1", "R1"));
        assertEquals(2, small.getAllHistory().size(), "tracker must evict past its max size");
    }
}
