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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake time travel: AT(TIMESTAMP), AT(OFFSET), BEFORE, and its refusal surface, all
 * live-verified. A point before the table's creation answers the retention wording ({@code Time
 * travel data is not available for table X. The requested time is either beyond the allowed time
 * travel period or before the object creation time.}); a future point answers {@code Future data
 * is not yet available for table X.}; a positive OFFSET refuses at compile time; and a bare
 * numeric TIMESTAMP argument is read by magnitude (seconds, then milli/micro/nanoseconds) the way
 * TO_TIMESTAMP reads one. Checkpoints are taken in the clock domain the engine under test stamps
 * its history with (the server's epoch on live, the JVM's for embedded snapshots), and 1.1-second
 * sleeps on both sides of a checkpoint guarantee second-boundary crossings.
 */
public class TimeTravelTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(TimeTravelTest.class);

    private static final String RETENTION_REFUSAL_EVENTS =
        "Time travel data is not available for table EVENTS. The requested time is either beyond"
            + " the allowed time travel period or before the object creation time.";

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
    }

    /**
     * A checkpoint in the clock domain the engine under test stamps its history with: the remote
     * server's own epoch on live, this JVM's epoch for embedded snapshots. (The SQL form is not
     * usable embedded on a non-UTC host until the EPOCH extraction honors the LTZ flavor.)
     */
    private long checkpointEpochSecond() {
        if (isLiveSnowflake()) {
            final ResultSet rs = engine.executeQuery(
                "SELECT DATE_PART(EPOCH_SECOND, CURRENT_TIMESTAMP())");
            return ((Number) rs.getRows().get(0).getValue(0)).longValue();
        }
        return System.currentTimeMillis() / 1000L;
    }

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    // ── Refusal surface ───────────────────────────────────────────────────────

    @Test
    public void farPastTimestampRefusesWithTheRetentionWording() {
        engine.execute("INSERT INTO events VALUES (1, 'row1')");
        assertEquals(RETENTION_REFUSAL_EVENTS,
            refusal("SELECT * FROM events AT (TIMESTAMP => 1)").getMessage());
    }

    @Test
    public void beforeTimestampZeroRefusesWithTheRetentionWording() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        assertEquals(RETENTION_REFUSAL_EVENTS,
            refusal("SELECT * FROM events BEFORE (TIMESTAMP => 0)").getMessage());
    }

    @Test
    public void offsetBeforeCreationRefusesWithTheRetentionWording() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        assertEquals(RETENTION_REFUSAL_EVENTS,
            refusal("SELECT * FROM events AT (OFFSET => -86400)").getMessage());
    }

    @Test
    public void nearFutureTimestampRefusesAsFutureData() {
        engine.execute("INSERT INTO events VALUES (1, 'a')");
        assertEquals("Future data is not yet available for table EVENTS.",
            refusal("SELECT COUNT(*) FROM events"
                + " AT(TIMESTAMP => DATEADD(hour, 1, CURRENT_TIMESTAMP()))").getMessage());
    }

    @Test
    public void beforeNearFutureRefusesAsFutureData() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        assertEquals("Future data is not yet available for table EVENTS.",
            refusal("SELECT * FROM events"
                + " BEFORE (TIMESTAMP => DATEADD(hour, 1, CURRENT_TIMESTAMP()))").getMessage());
    }

    @Test
    public void largeNumericTimestampReadsAsMillisecondsNotYear9999() {
        // 253402300799 epoch SECONDS would be the year 9999; the magnitude heuristic reads it as
        // a 1978 MILLISECOND instant — in the past, before creation, hence the retention wording.
        engine.execute("INSERT INTO events VALUES (1, 'a')");
        assertEquals(RETENTION_REFUSAL_EVENTS,
            refusal("SELECT * FROM events AT (TIMESTAMP => 253402300799)").getMessage());
    }

    @Test
    public void positiveOffsetRefusesAtCompileTime() {
        assertEquals("SQL compilation error:\nInvalid data type [3600] in AT(OFFSET => 3600)",
            refusal("SELECT * FROM events AT (OFFSET => 3600)").getMessage());
    }

    // ── AT (TIMESTAMP), checkpointed ──────────────────────────────────────────

    @Test
    public void atTimestampIsolatesRowsInTime() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'alpha')");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(1100); // >1 second → the next insert lands in a later second
        engine.execute("INSERT INTO events VALUES (2, 'beta')");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount(), "Should see only 1 row at checkpoint");
        assertEquals("alpha", rs.getRows().get(0).getValue(1).toString());
        logger.info("AT TIMESTAMP isolation: {} rows", rs.getRowCount());
    }

    @Test
    public void atOffsetZeroReturnsCurrent() {
        engine.execute("INSERT INTO events VALUES (1, 'x')");
        final ResultSet rs = engine.executeQuery("SELECT * FROM events AT (OFFSET => 0)");
        assertEquals(1, rs.getRowCount());
    }

    // ── Recovery after DELETE ─────────────────────────────────────────────────

    @Test
    public void timeTravelRecoversRowsDeletedAfterTheCheckpoint() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'keep')");
        engine.execute("INSERT INTO events VALUES (2, 'delete_me')");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(1100);

        engine.execute("DELETE FROM events WHERE id = 2");

        final ResultSet current = engine.executeQuery("SELECT * FROM events");
        assertEquals(1, current.getRowCount());

        final ResultSet historic = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertEquals(2, historic.getRowCount(), "Should recover deleted row via time travel");
        logger.info("Recovered {} rows via time travel", historic.getRowCount());
    }

    // ── Recovery after UPDATE ─────────────────────────────────────────────────

    @Test
    public void timeTravelShowsPreUpdateValues() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'original')");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(2100); // 2.1 seconds to ensure the update is 2+ seconds later

        engine.execute("UPDATE events SET name = 'updated' WHERE id = 1");

        final ResultSet current = engine.executeQuery("SELECT name FROM events WHERE id = 1");
        assertEquals("updated", current.getRows().get(0).getValue(0).toString());

        final ResultSet historic = engine.executeQuery(
            "SELECT id, name FROM events AT (TIMESTAMP => " + checkpoint + ")");
        assertEquals(1, historic.getRowCount(), "Should have 1 row at checkpoint");
        assertEquals("original", historic.getRows().get(0).getValue(1).toString(),
            "Time travel should show pre-update value");
        logger.info("Historic name: {}", historic.getRows().get(0).getValue(1));
    }

    // ── Multiple tables independent ───────────────────────────────────────────

    @Test
    public void timeTravelIsIndependentPerTable() throws Exception {
        engine.execute("CREATE TABLE t1 (v INTEGER)");
        engine.execute("CREATE TABLE t2 (v INTEGER)");

        engine.execute("INSERT INTO t1 VALUES (10)");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(1100);
        engine.execute("INSERT INTO t2 VALUES (20)");

        final ResultSet r1 = engine.executeQuery(
            "SELECT * FROM t1 AT (TIMESTAMP => " + checkpoint + ")");
        final ResultSet r2 = engine.executeQuery(
            "SELECT * FROM t2 AT (TIMESTAMP => " + checkpoint + ")");

        assertEquals(1, r1.getRowCount(), "t1 should have row at checkpoint");
        assertEquals(0, r2.getRowCount(), "t2 should not have row at checkpoint");
    }

    // ── Change detection via snapshot comparison ──────────────────────────────

    @Test
    public void insertsShowUpAgainstAnEarlierSnapshot() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'first_row')");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(1100);
        engine.execute("INSERT INTO events VALUES (2, 'second_row')");

        final ResultSet before = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        final ResultSet after = engine.executeQuery("SELECT * FROM events");

        assertEquals(1, before.getRowCount(), "Snapshot before 2nd insert");
        assertEquals(2, after.getRowCount(), "Current has both rows");
    }

    @Test
    public void deletesShowUpAgainstAnEarlierSnapshot() throws Exception {
        engine.execute("INSERT INTO events VALUES (1, 'a')");
        engine.execute("INSERT INTO events VALUES (2, 'b')");
        Thread.sleep(1100); // cross a second boundary so the checkpoint is strictly after the insert
        final long checkpoint = checkpointEpochSecond();
        Thread.sleep(1100);
        engine.execute("DELETE FROM events WHERE id = 1");

        final ResultSet before = engine.executeQuery(
            "SELECT * FROM events AT (TIMESTAMP => " + checkpoint + ")");
        final ResultSet after = engine.executeQuery("SELECT * FROM events");

        assertEquals(2, before.getRowCount(), "Snapshot before delete");
        assertEquals(1, after.getRowCount(), "Current after delete");
    }

    /** The refusal names the table as canonically stored — a quoted lowercase name echoes as written. */
    @Test
    public void refusalNamesTheTableCanonically() {
        engine.execute("CREATE TABLE future_probe (k INTEGER)");
        engine.execute("INSERT INTO future_probe VALUES (1)");
        final RuntimeException error = refusal("SELECT COUNT(*) FROM future_probe"
            + " AT(TIMESTAMP => DATEADD(hour, 1, CURRENT_TIMESTAMP()))");
        assertTrue(error.getMessage().contains(
            "Future data is not yet available for table FUTURE_PROBE."),
            "unexpected message: " + error.getMessage());
    }
}
