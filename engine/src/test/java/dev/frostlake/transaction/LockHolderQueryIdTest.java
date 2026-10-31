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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statement a table lock is named by, in another session's refusal and in SHOW LOCKS: the latest of the holder's
 * statements that changed a row of the table — an INSERT among them, a statement that changed no row and one that
 * wrote another table not — and, for a write a procedure makes, the procedure's statement rather than its CALL;
 * the lock keeps the time its first statement took it. An INSERT into an empty table takes no lock. A block's
 * handler reads the refusal as 627 / 57014 (all live-verified).
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class LockHolderQueryIdTest extends BaseDatabaseTest {

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext sessionA;
    private SessionContext sessionB;
    private Connection connA;
    private Connection connB;

    @BeforeEach
    public void openTwoSessions() {
        if (isLiveSnowflake()) {
            connA = LiveSnowflake.open();
            connB = LiveSnowflake.open();
        } else {
            concurrent = new ConcurrentDatabaseEngine();
            sessionA = concurrent.createSession();
            sessionB = concurrent.createSession();
            say("A", "CREATE DATABASE IF NOT EXISTS test_db");
            say("A", "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say("A", "USE SCHEMA test_db.test_schema");
        say("B", "USE SCHEMA test_db.test_schema");
        say("A", "CREATE OR REPLACE TABLE lh (id INT, v INT)");
        say("A", "INSERT INTO lh VALUES (1, 1), (2, 2)");
        say("A", "CREATE OR REPLACE TABLE lh2 (id INT, v INT)");
        say("A", "INSERT INTO lh2 VALUES (1, 1)");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP PROCEDURE IF EXISTS lh_proc()");
        say("A", "DROP TABLE IF EXISTS lh");
        say("A", "DROP TABLE IF EXISTS lh2");
        say("A", "DROP TABLE IF EXISTS lh3");
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
        } else {
            concurrent.shutdown();
        }
    }

    /**
     * Runs a statement in one session.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and
     *         the refusal
     */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = ("A".equals(side) ? connA : connB).createStatement()) {
                    if (!statement.execute(sql)) {
                        return "count " + statement.getUpdateCount();
                    }
                    final java.sql.ResultSet rows = statement.getResultSet();
                    final ResultSetMetaData columns = rows.getMetaData();
                    final StringBuilder out = new StringBuilder();
                    while (rows.next()) {
                        out.append(out.length() == 0 ? "" : " ;; ");
                        for (int i = 1; i <= columns.getColumnCount(); i++) {
                            out.append(i == 1 ? "" : "|").append(rows.getString(i));
                        }
                    }
                    return out.toString();
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql,
                "A".equals(side) ? sessionA : sessionB);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** The holder's statement a lock refusal names, or the answer when it is none. */
    private static String holderOf(final String answer) {
        final String marker = "Statement '";
        final int at = answer.indexOf(marker);
        return at < 0 ? answer : answer.substring(at + marker.length(), answer.indexOf('\'', at + marker.length()));
    }

    /** Session A's SHOW LOCKS row on a table, as its acquired_on and its query_id. */
    private String lockOn(final String table) {
        say("A", "SHOW LOCKS");
        return say("A", "SELECT \"acquired_on\"::VARCHAR, \"query_id\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"resource\" LIKE '%." + table + "'");
    }

    @Test
    public void theLockNamesTheLatestStatementThatChangedARowOfTheTable() {
        say("A", "BEGIN");
        say("A", "UPDATE lh SET v = 100 WHERE id = 1");
        final String first = say("A", "SELECT LAST_QUERY_ID()");
        final String acquired = lockOn("LH").split("\\|")[0];
        say("A", "UPDATE lh SET v = 200 WHERE id = 2");
        final String second = say("A", "SELECT LAST_QUERY_ID()");
        // A statement that changes no row of the table, and one that writes another, leave the name as it is.
        say("A", "UPDATE lh SET v = 0 WHERE id = 99");
        say("A", "UPDATE lh2 SET v = 5 WHERE id = 1");
        final String other = say("A", "SELECT LAST_QUERY_ID()");
        assertTrue(!first.equals(second), first);
        assertEquals(second, holderOf(say("B", "UPDATE lh SET v = 7 WHERE id = 1")));
        assertEquals(second, holderOf(say("B", "DELETE FROM lh WHERE id = 2")));
        assertEquals(acquired + "|" + second, lockOn("LH"));
        assertEquals(other, lockOn("LH2").split("\\|")[1]);
    }

    @Test
    public void anInsertHoldsItsTableNamedByTheLatestWriter() {
        say("A", "BEGIN");
        say("A", "INSERT INTO lh2 VALUES (3, 3)");
        final String inserted = say("A", "SELECT LAST_QUERY_ID()");
        assertEquals(inserted, lockOn("LH2").split("\\|")[1]);
        assertEquals(inserted, holderOf(say("B", "UPDATE lh2 SET v = 8 WHERE id = 1")));
        say("A", "INSERT INTO lh2 VALUES (4, 4)");
        final String insertedAgain = say("A", "SELECT LAST_QUERY_ID()");
        assertEquals(insertedAgain, holderOf(say("B", "UPDATE lh2 SET v = 8 WHERE id = 1")));
        say("A", "UPDATE lh2 SET v = 9 WHERE id = 1");
        final String updated = say("A", "SELECT LAST_QUERY_ID()");
        assertEquals(updated, lockOn("LH2").split("\\|")[1]);
        assertEquals(updated, holderOf(say("B", "UPDATE lh2 SET v = 8 WHERE id = 1")));
    }

    @Test
    public void anInsertIntoAnEmptyTableTakesNoLock() {
        say("A", "CREATE OR REPLACE TABLE lh3 (id INT)");
        say("A", "BEGIN");
        say("A", "INSERT INTO lh3 VALUES (1)");
        assertEquals("", lockOn("LH3"));
        assertEquals("1", say("A", "SELECT COUNT(*) FROM lh3"));
        assertEquals("0", say("B", "SELECT COUNT(*) FROM lh3"));
    }

    @Test
    public void aProceduresStatementNamesTheLockItsWriteTakes() {
        say("A", "CREATE OR REPLACE PROCEDURE lh_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN"
            + " UPDATE lh SET v = 5 WHERE id = 1; RETURN LAST_QUERY_ID(); END $$");
        say("A", "BEGIN");
        final String update = say("A", "CALL lh_proc()");
        final String call = say("A", "SELECT LAST_QUERY_ID()");
        assertTrue(!update.equals(call), update);
        assertEquals(update, holderOf(say("B", "UPDATE lh SET v = 7 WHERE id = 1")));
        assertEquals(update, lockOn("LH").split("\\|")[1]);
    }

    @Test
    public void aHandlerReadsTheLockRefusalAs627() {
        say("A", "BEGIN");
        say("A", "UPDATE lh SET v = 10 WHERE id = 1");
        assertEquals("627|57014", say("B", "EXECUTE IMMEDIATE $$ BEGIN UPDATE lh SET v = 0 WHERE id = 2;"
            + " RETURN 'done'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' || SQLSTATE; END $$"));
    }
}
