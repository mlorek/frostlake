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

package dev.frostlake.transaction;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.jdbc.DirectConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request of several statements that opens a transaction with BEGIN and then fails leaves that transaction
 * OPEN in its session — live-verified: CURRENT_TRANSACTION() is still set, the session reads the rows its earlier
 * statements wrote while every other session reads none, SHOW TRANSACTIONS lists the transaction as running, and
 * COMMIT or ROLLBACK ends it. Frostlake used to lose it: the session reported no transaction, its rows were gone,
 * and the transaction stayed in SHOW TRANSACTIONS for the engine's lifetime, owned by nothing.
 *
 * <p>Each transaction is named so SHOW TRANSACTIONS can be read on a shared account, where the same user's other
 * sessions may hold transactions of their own.
 */
public class FailedRequestOpenTransactionTest extends BaseJdbcTest {

    /** A per-run suffix keeping the transaction names apart from any other run on the account. */
    private final String nonce = Long.toString(System.nanoTime(), 36).toUpperCase();

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
    }

    @Override
    protected void teardownTest() throws SQLException {
        statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
    }

    /** A second session on the same account or engine, placed in the test's schema. */
    private Connection observer() throws SQLException {
        final Connection other = isLiveSnowflake() ? LiveSnowflake.open() : new DirectConnection(sharedEngine);
        try (Statement use = other.createStatement()) {
            use.execute("USE SCHEMA test_db.public");
        }
        return other;
    }

    /** The first row's first cell as text, through {@code on}. */
    private static String answer(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    /** Send a request that must fail. */
    private void fails(final String sql) {
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        }, sql);
    }

    /** The state SHOW TRANSACTIONS gives the transaction of this name, or null when it lists none. */
    private static String listedState(final Statement on, final String name) throws SQLException {
        String state = null;
        try (ResultSet rs = on.executeQuery("SHOW TRANSACTIONS")) {
            while (rs.next()) {
                if (name.equals(rs.getString("name"))) {
                    state = rs.getString("state");
                }
            }
        }
        return state;
    }

    @Test
    public void aFailedRequestLeavesItsTransactionOpenForCommit() throws SQLException {
        final String name = "FLTX_KEEP_" + nonce;
        statement.execute("CREATE TABLE ft1 (n INT)");
        fails("BEGIN TRANSACTION NAME " + name + "; INSERT INTO ft1 VALUES (1); INSERT INTO nowhere_xyz VALUES (2)");
        try (Connection other = observer(); Statement watch = other.createStatement()) {
            assertEquals("true", answer(statement, "SELECT CURRENT_TRANSACTION() IS NOT NULL").toLowerCase());
            assertEquals("1", answer(statement, "SELECT COUNT(*) FROM ft1"), "the session reads its own row");
            assertEquals("0", answer(watch, "SELECT COUNT(*) FROM ft1"), "no other session does");
            assertEquals("running", listedState(watch, name));
            statement.execute("COMMIT");
            assertEquals("1", answer(watch, "SELECT COUNT(*) FROM ft1"), "COMMIT publishes the row");
            assertNull(listedState(watch, name), "and ends the transaction");
        }
    }

    @Test
    public void aRollbackEndsTheTransactionTheFailedRequestOpened() throws SQLException {
        final String name = "FLTX_UNDO_" + nonce;
        statement.execute("CREATE TABLE ft2 (n INT)");
        fails("BEGIN TRANSACTION NAME " + name + "; INSERT INTO ft2 VALUES (1); INSERT INTO nowhere_xyz VALUES (2)");
        try (Connection other = observer(); Statement watch = other.createStatement()) {
            assertEquals("running", listedState(watch, name));
            statement.execute("ROLLBACK");
            assertNull(listedState(watch, name));
            assertEquals("true", answer(statement, "SELECT CURRENT_TRANSACTION() IS NULL").toLowerCase());
            assertEquals("0", answer(statement, "SELECT COUNT(*) FROM ft2"));
        }
    }

    /** The two-statement form — BEGIN, then the failure — opens and keeps the transaction the same way. */
    @Test
    public void aRequestThatFailsRightAfterItsBeginStillHoldsTheTransaction() throws SQLException {
        final String name = "FLTX_BARE_" + nonce;
        fails("BEGIN TRANSACTION NAME " + name + "; INSERT INTO nowhere_xyz VALUES (1)");
        try (Connection other = observer(); Statement watch = other.createStatement()) {
            assertEquals("true", answer(statement, "SELECT CURRENT_TRANSACTION() IS NOT NULL").toLowerCase());
            assertEquals("running", listedState(watch, name));
            assertEquals("true", answer(watch, "SELECT CURRENT_TRANSACTION() IS NULL").toLowerCase(),
                "the other session is not inside it");
            statement.execute("ROLLBACK");
            assertNull(listedState(watch, name));
        }
    }

    /**
     * Closing the connection ends the transaction it left open. Embedded only: the account ends a closed
     * session's transaction asynchronously and with no bound — one was still listed as running twelve minutes
     * after its connection closed — so there is no live answer to compare with, only the leak to rule out.
     */
    @Test
    public void closingTheConnectionEndsTheTransactionItLeftOpen() throws SQLException {
        if (isLiveSnowflake()) {
            return;
        }
        final String name = "FLTX_GONE_" + nonce;
        statement.execute("CREATE TABLE ft4 (n INT)");
        final Connection doomed = observer();
        try (Statement send = doomed.createStatement()) {
            send.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    send.execute("BEGIN TRANSACTION NAME " + name
                        + "; INSERT INTO ft4 VALUES (1); INSERT INTO nowhere_xyz VALUES (2)");
                }
            });
        }
        assertEquals("running", listedState(statement, name));
        doomed.close();
        assertNull(listedState(statement, name), "the closed connection's transaction is gone");
        assertEquals("0", answer(statement, "SELECT COUNT(*) FROM ft4"), "and its row was never committed");
    }
}
