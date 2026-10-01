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

package dev.frostlake;

import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account claim that keeps a second {@code SF_LIVE} run off an account another run holds. Each test runs the
 * claim's own statements against a claim table in the test's database, over a JDBC session of its own: Frostlake's
 * driver embedded, the account's driver with {@code SF_LIVE=1}, so the protocol's SQL is checked on both. Two
 * claimants stand for two runs; the tests move a heartbeat back in time rather than wait out the window.
 */
public class LiveAccountClaimTest extends BaseDatabaseTest {

    private static final long WINDOW = LiveAccountClaim.STALE_AFTER_SECONDS;
    private static final String TABLE = "TEST_DB.TEST_SCHEMA.ACCOUNT_CLAIM";
    /** The refusal a second run reads while "holder a" holds the claim; each {@code #} is a number of seconds. */
    private static final String REFUSAL = "The live account is claimed by another SF_LIVE run, so this run refuses"
        + " to start: two runs on one account drop each other's objects. Holder: holder a (run run-a), claimed # s"
        + " ago, last heartbeat # s ago. If that run has died, its claim goes stale # s from now; to free it sooner,"
        + " run UPDATE " + TABLE + " SET RUN_ID = NULL WHERE RUN_ID = 'run-a'.";
    /** What run-a's heartbeat answers once its claim table, or the database holding it, is dropped. */
    private static final String GONE = "This run (run-a) no longer holds the live account's claim: the claim table "
        + TABLE + " no longer exists, and the account may hold another run by now. This run stops using the account.";

    private Connection connection;

    @Override
    protected void setupTest() {
        connection = isLiveSnowflake() ? LiveSnowflake.open() : new DirectConnection(engine);
    }

    @Override
    protected void teardownTest() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (final SQLException ignored) {
            // Best-effort close only.
        }
    }

    private LiveAccountClaim claimant(final String runId, final String holder) throws SQLException {
        final LiveAccountClaim claim = new LiveAccountClaim(connection, "TEST_DB", "TEST_SCHEMA", runId, holder,
            WINDOW);
        claim.prepare();
        return claim;
    }

    /** The claim row's cell, as text. */
    private String claimCell(final String column) {
        final List<Row> rows = engine.executeQuery("SELECT " + column + " FROM " + TABLE).getRows();
        assertEquals(1, rows.size(), "the claim table holds one row");
        final Object value = rows.get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** Moves the holder's heartbeat back by {@code seconds}, as if it had not beaten for that long. */
    private void ageHeartbeat(final long seconds) {
        engine.execute("UPDATE " + TABLE + " SET HEARTBEAT_AT = HEARTBEAT_AT - " + seconds);
    }

    /** Asserts {@code actual} reads as {@code expected}, where each {@code #} in it stands for a whole number. */
    private static void assertSentence(final String expected, final String actual) {
        final String[] parts = expected.split("#", -1);
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            regex.append(i == 0 ? "" : "\\d+").append(Pattern.quote(parts[i]));
        }
        assertTrue(actual != null && actual.matches(regex.toString()), "expected [" + expected + "] but was ["
            + actual + "]");
    }

    @Test
    public void theFirstClaimantHoldsTheAccount() throws SQLException {
        assertNull(claimant("run-a", "holder a").acquire());
        assertEquals("run-a", claimCell("RUN_ID"));
        assertEquals("holder a", claimCell("HOLDER"));
    }

    @Test
    public void aSecondClaimantIsRefusedWithTheHolderNamed() throws SQLException {
        assertNull(claimant("run-a", "holder a").acquire());
        assertSentence(REFUSAL, claimant("run-b", "holder b").acquire());
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void theRefusalsUpdateFreesTheClaimByHand() throws SQLException {
        assertNull(claimant("run-a", "holder a").acquire());
        final String refusal = claimant("run-b", "holder b").acquire();
        engine.execute(refusal.substring(refusal.indexOf("UPDATE "), refusal.length() - 1));
        assertNull(claimCell("RUN_ID"));
        assertNull(claimant("run-b", "holder b").acquire());
        assertEquals("run-b", claimCell("RUN_ID"));
    }

    @Test
    public void aRunTakesItsOwnClaimAgain() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        assertNull(a.acquire());
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void preparingAgainKeepsTheOneRow() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        claimant("run-b", "holder b");
        a.prepare();
        assertEquals("1", claimCell("COUNT(*)"));
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void onlyTheHoldersHeartbeatCounts() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        assertNull(a.heartbeat());
        assertSentence("This run (run-b) no longer holds the live account's claim: holder a (run run-a), claimed # s"
            + " ago, last heartbeat # s ago. This run stops using the account.",
            claimant("run-b", "holder b").heartbeat());
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void aClaimYoungerThanTheWindowIsNotStale() throws SQLException {
        assertNull(claimant("run-a", "holder a").acquire());
        ageHeartbeat(WINDOW - 120);
        assertSentence(REFUSAL, claimant("run-b", "holder b").acquire());
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void aStaleClaimIsTakenOverAndItsOldHolderLearnsIt() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        ageHeartbeat(WINDOW + 60);
        assertNull(claimant("run-b", "holder b").acquire());
        assertEquals("run-b", claimCell("RUN_ID"));
        assertEquals("holder b", claimCell("HOLDER"));
        assertSentence("This run (run-a) no longer holds the live account's claim: holder b (run run-b), claimed # s"
            + " ago, last heartbeat # s ago. This run stops using the account.", a.heartbeat());
        assertFalse(a.release());
        assertEquals("run-b", claimCell("RUN_ID"));
    }

    @Test
    public void onlyTheHolderReleasesTheClaim() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        final LiveAccountClaim b = claimant("run-b", "holder b");
        assertNull(a.acquire());
        assertFalse(b.release());
        assertEquals("run-a", claimCell("RUN_ID"));
        assertTrue(a.release());
        assertNull(claimCell("RUN_ID"));
        assertNull(b.acquire());
        assertEquals("run-b", claimCell("RUN_ID"));
    }

    @Test
    public void aReleasedClaimTellsItsOldHolder() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        assertTrue(a.release());
        assertEquals("This run (run-a) no longer holds the live account's claim: it was released, and the account may"
            + " hold another run by now. This run stops using the account.", a.heartbeat());
    }

    @Test
    public void aDroppedClaimTableIsALostClaim() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        engine.execute("DROP TABLE " + TABLE);
        assertEquals(GONE, a.heartbeat());
    }

    @Test
    public void aDroppedClaimDatabaseIsALostClaim() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        engine.execute("DROP DATABASE TEST_DB");
        assertEquals(GONE, a.heartbeat());
    }

    @Test
    public void aHeartbeatOnABrokenSessionFailsWithoutLosingTheClaim() throws SQLException {
        final LiveAccountClaim a = claimant("run-a", "holder a");
        assertNull(a.acquire());
        connection.close();
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                a.heartbeat();
            }
        });
        assertEquals("run-a", claimCell("RUN_ID"));
    }

    @Test
    public void theTeardownKeepsTheHarnessDatabases() {
        assertTrue(LiveAccountObjects.isHarnessObject("DATABASE", LiveAccountClaim.DATABASE));
        assertTrue(LiveAccountObjects.isHarnessObject("DATABASE", "frostlake_live_claim"));
        assertTrue(LiveAccountObjects.isHarnessObject("DATABASE", "TEST_DB"));
        assertFalse(LiveAccountObjects.isHarnessObject("DATABASE", "OTHER_DB"));
        assertFalse(LiveAccountObjects.isHarnessObject("ROLE", LiveAccountClaim.DATABASE));
    }
}
