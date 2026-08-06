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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Snowflake time travel: AT(TIMESTAMP), AT(OFFSET), BEFORE, CHANGES.
 * Time-sensitive tests use 1.1-second sleeps to guarantee second-boundary crossings.
 */
public class TimeTravelTest {

    private static final Logger logger = LoggerFactory.getLogger(TimeTravelTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    // ── AT (TIMESTAMP epoch-seconds) ──────────────────────────────────────────

    @Test
    public void testAtTimestampInTheFutureIsRejected() {
        engine.execute("INSERT INTO events VALUES (1, 'a')");
        engine.execute("INSERT INTO events VALUES (2, 'b')");

        // A point in the future has no data yet and is refused. (A real account refuses this input
        // too; for a time this far out its wording is about the retention window instead, which the
        // engine does not model.)
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM events AT (TIMESTAMP => 253402300799)");
            }
        });
        assertTrue(error.getMessage().contains("Future data is not yet available for table EVENTS."),
            "unexpected message: " + error.getMessage());
    }

    @Test
    public void testAtTimestampBeforeAnyDataReturnsEmpty() {
        engine.execute("INSERT INTO events VALUES (1, 'row1')");
        ResultSet rs = engine.executeQuery("SELECT * FROM events AT (TIMESTAMP => 1)"); // 1970
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testAtTimestampIsolatesRowsInTime() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'alpha')");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L; // epoch seconds after first insert
        Thread.sleep(1100); // >1 second → next inserts land in a later second
        engine.execute("INSERT INTO events VALUES (2, 'beta')");

        ResultSet rs = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount(), "Should see only 1 row at checkpoint");
        assertEquals("alpha", rs.getRows().get(0).getValue(1).toString());
        logger.info("AT TIMESTAMP isolation: {} rows", rs.getRowCount());
    }

    // ── BEFORE ────────────────────────────────────────────────────────────────

    @Test
    public void testBeforeTimestampInTheFutureIsRejected() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        engine.execute("INSERT INTO events VALUES (2, 'y')");
        // BEFORE a future point is refused exactly as AT is — live-verified for both.
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM events BEFORE (TIMESTAMP => 253402300799)");
            }
        });
        assertTrue(error.getMessage().contains("Future data is not yet available for table EVENTS."),
            "unexpected message: " + error.getMessage());
    }

    @Test
    public void testBeforeTimestampZeroReturnsEmpty() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        ResultSet rs = engine.executeQuery("SELECT * FROM events BEFORE (TIMESTAMP => 0)");
        assertEquals(0, rs.getRowCount());
    }

    // ── AT (OFFSET) ───────────────────────────────────────────────────────────

    @Test
    public void testAtOffsetFarPastReturnsEmpty() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        ResultSet rs = engine.executeQuery("SELECT * FROM events AT (OFFSET => -86400)"); // 1 day ago
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testAtOffsetZeroReturnsCurrent() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        ResultSet rs = engine.executeQuery("SELECT * FROM events AT (OFFSET => 0)");
        assertEquals(1, rs.getRowCount());
    }

    // ── Recovery after DELETE ─────────────────────────────────────────────────

    @Test
    public void testTimeTravelRecoveryAfterDelete() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'keep')");
        engine.execute("INSERT INTO events VALUES (2, 'delete_me')");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L;
        Thread.sleep(1100);

        engine.execute("DELETE FROM events WHERE id = 2");

        ResultSet current = engine.executeQuery("SELECT * FROM events");
        assertEquals(1, current.getRowCount());

        ResultSet historic = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertEquals(2, historic.getRowCount(), "Should recover deleted row via time travel");
        logger.info("Recovered {} rows via time travel", historic.getRowCount());
    }

    // ── Recovery after UPDATE ─────────────────────────────────────────────────

    @Test
    public void testTimeTravelRecoveryAfterUpdate() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'original')");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L;
        Thread.sleep(2100); // 2.1 seconds to ensure update is 2+ seconds later

        engine.execute("UPDATE events SET name = 'updated' WHERE id = 1");

        ResultSet current = engine.executeQuery("SELECT name FROM events WHERE id = 1");
        assertEquals("updated", current.getRows().get(0).getValue(0).toString());

        ResultSet historic = engine.executeQuery(
            "SELECT id, name FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertEquals(1, historic.getRowCount(), "Should have 1 row at checkpoint");
        assertEquals("original", historic.getRows().get(0).getValue(1).toString(),
            "Time travel should show pre-update value");
        logger.info("Historic name: {}", historic.getRows().get(0).getValue(1));
    }

    // ── Multiple tables independent ───────────────────────────────────────────

    @Test
    public void testTimeTravelIndependentPerTable() throws Exception {
        engine.execute("CREATE TABLE t1 (v INTEGER)");
        engine.execute("CREATE TABLE t2 (v INTEGER)");

        engine.execute("INSERT INTO t1 VALUES (10)");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L;
        Thread.sleep(1100);
        engine.execute("INSERT INTO t2 VALUES (20)");

        ResultSet r1 = engine.executeQuery("SELECT * FROM t1 AT (TIMESTAMP => " + checkpoint + ")");
        ResultSet r2 = engine.executeQuery("SELECT * FROM t2 AT (TIMESTAMP => " + checkpoint + ")");

        assertEquals(1, r1.getRowCount(), "t1 should have row at checkpoint");
        assertEquals(0, r2.getRowCount(), "t2 should not have row at checkpoint");
    }

    // ── CHANGES clause ────────────────────────────────────────────────────────

    /**
     * CHANGES clause is tested indirectly via snapshot comparison.
     * Direct CHANGES(INFORMATION => ...) syntax has grammar lookahead limitations
     * due to CHANGES being ambiguous in table source position.
     */
    @Test
    public void testChangesViaSnapshotComparison_Inserts() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'first_row')");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L;
        Thread.sleep(1100);
        engine.execute("INSERT INTO events VALUES (2, 'second_row')");

        ResultSet before = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        ResultSet after = engine.executeQuery("SELECT * FROM events");

        assertEquals(1, before.getRowCount(), "Snapshot before 2nd insert");
        assertEquals(2, after.getRowCount(), "Current has both rows");
        assertEquals(1, after.getRowCount() - before.getRowCount(), "Net: 1 insert detected");
    }

    @Test
    public void testChangesViaSnapshotComparison_Deletes() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'a')");
        engine.execute("INSERT INTO events VALUES (2, 'b')");
        Thread.sleep(100);
        long checkpoint = System.currentTimeMillis() / 1000L;
        Thread.sleep(1100);
        engine.execute("DELETE FROM events WHERE id = 1");

        ResultSet before = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        ResultSet after = engine.executeQuery("SELECT * FROM events");

        assertEquals(2, before.getRowCount(), "Snapshot before delete");
        assertEquals(1, after.getRowCount(), "Current after delete");
        assertEquals(1, before.getRowCount() - after.getRowCount(), "Net: 1 delete detected");
    }

    /** A time-travel point in the future has no data yet, and live says so rather than returning rows. */
    @Test
    public void timeTravelIntoTheFutureIsRejected() {
        engine.execute("CREATE TABLE future_probe (k INTEGER)");
        engine.execute("INSERT INTO future_probe VALUES (1)");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM future_probe"
                    + " AT(TIMESTAMP => DATEADD(hour, 1, CURRENT_TIMESTAMP()))");
            }
        });
        assertTrue(error.getMessage().contains(
            "Future data is not yet available for table FUTURE_PROBE."),
            "unexpected message: " + error.getMessage());
    }
}
