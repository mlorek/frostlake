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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time travel reaches back no further than the table's DATA_RETENTION_TIME_IN_DAYS, the days its schema, its database
 * or the account give it when it sets none. With 0 there is no history to read at all: every point in the past is
 * refused in the words of a point beyond the allowed period, while the table itself and its streams read on. The
 * window was bounded only by the count of snapshots the engine keeps, so a table with no retention answered every
 * point as if it had one.
 */
public class TimeTravelRetentionTest {

    private static final String UNAVAILABLE = "Time travel data is not available for table ";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws InterruptedException {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE keep_db");
        engine.execute("USE SCHEMA keep_db.PUBLIC");
        engine.execute("CREATE TABLE none_kept (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE TABLE day_kept (a INT)");
        engine.execute("CREATE STREAM none_kept_changes ON TABLE none_kept");
        engine.execute("INSERT INTO none_kept VALUES (1)");
        engine.execute("INSERT INTO day_kept VALUES (1)");
        engine.execute("SET first_write = LAST_QUERY_ID()");
        Thread.sleep(15);
        engine.execute("SET p = CURRENT_TIMESTAMP()");
        Thread.sleep(15);
        engine.execute("INSERT INTO none_kept VALUES (2)");
        engine.execute("SET second_write = LAST_QUERY_ID()");
        engine.execute("INSERT INTO day_kept VALUES (2)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    @Test
    public void aRetentionOfZeroDaysRefusesEveryPointInThePast() {
        final String[] points = {
            "SELECT * FROM none_kept AT(TIMESTAMP => $p)",
            "SELECT * FROM none_kept AT(OFFSET => -1)",
            "SELECT * FROM none_kept AT(STATEMENT => $second_write)",
            "SELECT * FROM none_kept BEFORE(STATEMENT => $second_write)",
            "SELECT a, METADATA$ACTION FROM none_kept CHANGES(INFORMATION => DEFAULT) AT(TIMESTAMP => $p)",
            "CREATE TABLE none_kept_then CLONE none_kept AT(TIMESTAMP => $p)",
            "CREATE STREAM none_kept_from_p ON TABLE none_kept AT(TIMESTAMP => $p)",
        };
        for (final String point : points) {
            assertTrue(refusal(point).startsWith(UNAVAILABLE + "NONE_KEPT. The requested time is either beyond the"
                + " allowed time travel period"), point);
        }
    }

    @Test
    public void theTableAsItIsAndAPlainCloneStillRead() {
        assertEquals("[1]; [2]", rows("SELECT * FROM none_kept AT(TIMESTAMP => CURRENT_TIMESTAMP()) ORDER BY a"));
        engine.execute("CREATE TABLE none_kept_now CLONE none_kept");
        assertEquals("[1]; [2]", rows("SELECT * FROM none_kept_now ORDER BY a"));
    }

    @Test
    public void theDefaultRetentionStillReadsRecentPoints() {
        assertEquals("[1]", rows("SELECT * FROM day_kept AT(TIMESTAMP => $p)"));
        assertEquals("[1]", rows("SELECT * FROM day_kept AT(STATEMENT => $first_write)"));
    }

    @Test
    public void aStreamOnATableWithNoRetentionStillReadsAndConsumesItsChanges() {
        assertEquals("[1, INSERT]; [2, INSERT]",
            rows("SELECT a, METADATA$ACTION FROM none_kept_changes ORDER BY a"));
        engine.execute("CREATE TABLE sink (a INT)");
        engine.execute("INSERT INTO sink SELECT a FROM none_kept_changes");
        engine.execute("INSERT INTO none_kept VALUES (3)");
        assertEquals("[3, INSERT]", rows("SELECT a, METADATA$ACTION FROM none_kept_changes"));
    }

    @Test
    public void aSchemasRetentionIsInheritedAndATablesOwnOverridesIt() throws InterruptedException {
        engine.execute("CREATE SCHEMA keep_db.none DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE TABLE keep_db.none.t (a INT)");
        engine.execute("INSERT INTO keep_db.none.t VALUES (1)");
        Thread.sleep(15);
        engine.execute("SET q = CURRENT_TIMESTAMP()");
        Thread.sleep(15);

        assertTrue(refusal("SELECT * FROM keep_db.none.t AT(TIMESTAMP => $q)").startsWith(UNAVAILABLE + "T."));
        engine.execute("ALTER TABLE keep_db.none.t SET DATA_RETENTION_TIME_IN_DAYS = 1");
        assertEquals("[1]", rows("SELECT * FROM keep_db.none.t AT(TIMESTAMP => $q)"));
    }
}
