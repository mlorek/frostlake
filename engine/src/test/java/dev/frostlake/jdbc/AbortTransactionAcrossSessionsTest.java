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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$ABORT_TRANSACTION ends a transaction of ANOTHER session as it ends the caller's own (live-verified): the
 * function answers {@code Aborted transaction id: <id>}, the transaction's writes are gone and its lock released at
 * once, and SHOW TRANSACTIONS no longer lists it. The session that held it is told at its next statement — every
 * one is refused until it issues a COMMIT, which fails naming the transaction, or a ROLLBACK — and goes on after
 * that, with the aborted transaction as its last.
 */
public class AbortTransactionAcrossSessionsTest extends BaseJdbcTest {

    private static final String IGNORED = "Your transaction was aborted. All statements will be ignored until you"
        + " issue a COMMIT or a ROLLBACK.";

    /** Another session on the same account or engine as the test's own connection. */
    private Connection another() throws SQLException {
        return LiveSnowflake.enabled() ? LiveSnowflake.open() : new DirectConnection(sharedEngine);
    }

    private static String cell(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    private String refusal(final Statement on, final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                on.execute(sql);
            }
        }, sql).getMessage();
    }

    /** Whether SHOW TRANSACTIONS IN ACCOUNT lists a transaction of the id. */
    private static boolean listed(final Statement on, final String id) throws SQLException {
        try (ResultSet rs = on.executeQuery("SHOW TRANSACTIONS IN ACCOUNT")) {
            while (rs.next()) {
                if (id.equals(rs.getString(1))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Test
    public void theSessionThatHeldItIsRefusedUntilItsCommitFails() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_tx (k INT, v INT)");
        statement.execute("INSERT INTO test_db.public.abort_tx VALUES (1, 1)");
        try (Connection other = another(); Statement on = other.createStatement()) {
            on.execute("BEGIN NAME held");
            on.execute("UPDATE test_db.public.abort_tx SET v = 2 WHERE k = 1");
            final String id = cell(on, "SELECT CURRENT_TRANSACTION()");
            assertTrue(listed(statement, id));
            assertEquals("Aborted transaction id: " + id,
                cell(statement, "SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")"));
            // Its write is gone and its lock released: the row is this session's to take at once.
            assertEquals(1, statement.executeUpdate("UPDATE test_db.public.abort_tx SET v = 3 WHERE k = 1"));
            assertEquals("3", cell(statement, "SELECT v FROM test_db.public.abort_tx"));
            assertFalse(listed(statement, id));
            assertTrue(refusal(on, "SELECT 1").contains(IGNORED));
            assertTrue(refusal(on, "SELECT v FROM test_db.public.abort_tx").contains(IGNORED));
            assertTrue(refusal(on, "BEGIN").contains(IGNORED));
            assertTrue(refusal(on, "COMMIT").contains(
                "COMMIT failed. Your transaction 'HELD', id '" + id + "', was already aborted."));
            // The failed COMMIT acknowledged it: the session goes on, the transaction its last.
            assertEquals("1", cell(on, "SELECT 1"));
            assertEquals(id, cell(on, "SELECT LAST_TRANSACTION()"));
            assertEquals("3", cell(on, "SELECT v FROM test_db.public.abort_tx"));
            // Nothing holds the id any more.
            assertEquals("Could not abort txn: " + id,
                cell(statement, "SELECT SYSTEM$ABORT_TRANSACTION('" + id + "')"));
        }
    }

    @Test
    public void aRollbackAcknowledgesItAndTheSessionGoesOn() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_tx (k INT, v INT)");
        statement.execute("INSERT INTO test_db.public.abort_tx VALUES (1, 1)");
        try (Connection other = another(); Statement on = other.createStatement()) {
            on.execute("BEGIN");
            on.execute("INSERT INTO test_db.public.abort_tx VALUES (2, 2)");
            final String id = cell(on, "SELECT CURRENT_TRANSACTION()");
            assertEquals("Aborted transaction id: " + id,
                cell(statement, "CALL SYSTEM$ABORT_TRANSACTION(" + id + ")"));
            assertTrue(refusal(on, "SELECT COUNT(*) FROM test_db.public.abort_tx").contains(IGNORED));
            on.execute("ROLLBACK");
            assertEquals("1", cell(on, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
            assertEquals(id, cell(on, "SELECT LAST_TRANSACTION()"));
            // A transaction it opens afterwards is a new one, and commits.
            on.execute("BEGIN");
            on.execute("INSERT INTO test_db.public.abort_tx VALUES (3, 3)");
            on.execute("COMMIT");
            assertEquals("2", cell(statement, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
        }
    }

    @Test
    public void aTransactionOfThisSessionIsEndedFromAnother() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_tx (k INT, v INT)");
        statement.execute("BEGIN");
        try {
            statement.execute("INSERT INTO test_db.public.abort_tx VALUES (1, 1)");
            final String id = cell(statement, "SELECT CURRENT_TRANSACTION()");
            try (Connection other = another(); Statement on = other.createStatement()) {
                assertEquals("Aborted transaction id: " + id,
                    cell(on, "SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")"));
                // The session that aborted another's transaction holds none itself, and runs on.
                assertEquals("0", cell(on, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
            }
            assertTrue(refusal(statement, "SELECT 1").contains(IGNORED));
            assertTrue(refusal(statement, "SHOW TRANSACTIONS").contains(IGNORED));
        } finally {
            statement.execute("ROLLBACK");
        }
        assertEquals("0", cell(statement, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
    }

    @Test
    public void aTransactionOpenedWithAutocommitOffIsEndedAndTheConnectionsCommitFails() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_tx (k INT, v INT)");
        try (Connection other = another(); Statement on = other.createStatement()) {
            other.setAutoCommit(false);
            on.execute("INSERT INTO test_db.public.abort_tx VALUES (1, 1)");
            final String id = cell(on, "SELECT CURRENT_TRANSACTION()");
            assertEquals("Aborted transaction id: " + id,
                cell(statement, "SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")"));
            final SQLException failed = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    other.commit();
                }
            });
            assertTrue(failed.getMessage().contains("id '" + id + "', was already aborted."), failed.getMessage());
            // Acknowledged: the next write opens a new transaction, which a rollback ends.
            on.execute("INSERT INTO test_db.public.abort_tx VALUES (2, 2)");
            other.rollback();
            assertEquals("0", cell(statement, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
        }
    }

    @Test
    public void aSessionThatEndsHoldingAnAbortedTransactionLeavesNothingBehind() throws SQLException {
        statement.execute("CREATE TABLE test_db.public.abort_tx (k INT, v INT)");
        final String id;
        try (Connection other = another(); Statement on = other.createStatement()) {
            on.execute("BEGIN");
            on.execute("INSERT INTO test_db.public.abort_tx VALUES (1, 1)");
            id = cell(on, "SELECT CURRENT_TRANSACTION()");
            assertEquals("Aborted transaction id: " + id,
                cell(statement, "SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")"));
        }
        assertFalse(listed(statement, id));
        assertEquals("Could not abort txn: " + id, cell(statement, "SELECT SYSTEM$ABORT_TRANSACTION(" + id + ")"));
        assertEquals("0", cell(statement, "SELECT COUNT(*) FROM test_db.public.abort_tx"));
    }
}
