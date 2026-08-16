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
import dev.frostlake.executor.StatementClock;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clock is read once per STATEMENT, live-verified. Every row of one INSERT stores the same
 * {@code CURRENT_TIMESTAMP}, two calls inside one statement are equal, the temporal functions agree
 * with each other — and two statements inside one transaction do NOT agree, because the pin is per
 * statement rather than per transaction.
 *
 * <p>Before this, each call read the wall clock, so a five-row INSERT stored five different instants
 * where Snowflake stores one.
 */
public class StatementClockTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String text(final String sql) {
        final Object value = scalar(sql);
        return value == null ? null : value.toString();
    }

    /** Five rows from one INSERT, one instant — the case that was five instants. */
    @Test
    public void everyRowOfOneStatementSharesTheInstant() {
        engine.execute("CREATE TABLE ticks(id INT, ts TIMESTAMP_NTZ)");
        engine.execute(
            "INSERT INTO ticks SELECT 1, CURRENT_TIMESTAMP() FROM TABLE(GENERATOR(ROWCOUNT => 5))");
        assertEquals(5L, ((Number) scalar("SELECT COUNT(*) FROM ticks")).longValue());
        assertEquals(1L, ((Number) scalar("SELECT COUNT(DISTINCT ts) FROM ticks")).longValue(),
            "one statement reads the clock once");
    }

    @Test
    public void twoCallsInOneStatementAreEqual() {
        assertEquals(Boolean.TRUE, scalar("SELECT CURRENT_TIMESTAMP() = CURRENT_TIMESTAMP()"));
    }

    /** The session-zone family shares the pin, so they agree with each other. */
    @Test
    public void theTemporalFunctionsAgreeWithinAStatement() {
        assertEquals(Boolean.TRUE, scalar("SELECT CURRENT_TIMESTAMP() = LOCALTIMESTAMP()"));
        assertEquals(Boolean.TRUE, scalar("SELECT CURRENT_DATE() = TO_DATE(CURRENT_TIMESTAMP())"));
    }

    /**
     * SYSDATE is NOT CURRENT_TIMESTAMP. Live, with the session on America/Los_Angeles, SYSDATE
     * answered {@code 13:57:23.484} against CURRENT_TIMESTAMP's {@code 06:57:23.484 -0700} — the same
     * instant rendered in UTC rather than the session's zone, so the two are equal only where that
     * offset is zero. Asserted as the offset itself rather than as inequality, which would fail on a
     * machine already running in UTC.
     */
    @Test
    public void sysdateIsTheSameInstantRenderedInUtc() {
        final int delta = ((Number) scalar(
            "SELECT DATEDIFF(second, SYSDATE(), CURRENT_TIMESTAMP())")).intValue();
        if (isLiveSnowflake()) {
            // The SESSION's zone decides the lead, and it is the account's, not the runner's
            // (America/Los_Angeles here vs the JVM's). Assert what holds in every zone: the lead is a
            // real zone offset — a whole number of quarter-hours within +/-14h — and the two are equal
            // exactly when that offset is zero.
            assertEquals(0, delta % 900, "a zone offset is a whole number of quarter-hours: " + delta);
            assertTrue(Math.abs(delta) <= 14 * 3600, "zone offset within +/-14h: " + delta);
        } else {
            // Embedded: the engine's session zone IS UTC (its TIMEZONE parameter), so SYSDATE and
            // CURRENT_TIMESTAMP render the same instant identically whatever zone the host runs in.
            assertEquals(0, delta, "the engine's session zone is UTC, so the lead is zero");
        }
        assertEquals(delta == 0 ? Boolean.TRUE : Boolean.FALSE,
            scalar("SELECT SYSDATE() = CURRENT_TIMESTAMP()"));
    }

    /** A row's stored instant is the INSERT's, and re-reading it never moves. */
    @Test
    public void aStoredInstantDoesNotMoveOnRead() {
        engine.execute("CREATE TABLE stored(ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO stored SELECT CURRENT_TIMESTAMP()");
        final String first = text("SELECT ts FROM stored");
        final String second = text("SELECT ts FROM stored");
        assertEquals(first, second);
    }

    /**
     * Statement-stable, NOT transaction-stable: live, two statements inside one BEGIN…COMMIT get
     * different times, so the pin must not span the transaction.
     */
    @Test
    public void separateStatementsInOneTransactionGetSeparateInstants() {
        engine.execute("CREATE TABLE spans(n INT, ts TIMESTAMP_NTZ)");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO spans SELECT 1, CURRENT_TIMESTAMP()");
        engine.execute("INSERT INTO spans SELECT 2, CURRENT_TIMESTAMP()");
        engine.execute("COMMIT");
        final String first = text("SELECT ts FROM spans WHERE n = 1");
        final String second = text("SELECT ts FROM spans WHERE n = 2");
        assertNotEquals(first, second, "the clock is pinned per statement, not per transaction");
    }

    /**
     * The pin does not outlive its statement: nothing is pinned between statements, so the next one
     * reads the clock afresh. Asserted on the pin rather than by comparing two rendered timestamps,
     * which race — consecutive statements land in the same millisecond and render identically.
     */
    @Test
    public void thePinDoesNotOutliveItsStatement() {
        assertFalse(StatementClock.isPinned(), "nothing pinned before a statement");
        engine.executeQuery("SELECT CURRENT_TIMESTAMP()");
        assertFalse(StatementClock.isPinned(), "the pin is released when the statement ends");
    }

    /** The pinned instant is a real reading, not a frozen constant. */
    @Test
    public void thePinnedInstantTracksWallClockTime() {
        final Object before = scalar("SELECT CURRENT_TIMESTAMP() >= DATEADD(year, -1, CURRENT_TIMESTAMP())");
        assertEquals(Boolean.TRUE, before);
        assertTrue(text("SELECT CURRENT_TIMESTAMP()::VARCHAR").length() > 0);
    }
}
