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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * More of what waits for a table another session's open transaction holds (live-verified): an INSERT OVERWRITE that
 * rewrites the table waits as an UPDATE does, where a plain INSERT never waits; a statement of a procedure or of a
 * block that waits past LOCK_TIMEOUT is refused through the block's uncaught STATEMENT_ERROR; and while a statement
 * waits, SHOW LOCKS lists its lock as WAITING, with no acquired_on, beside the holder's HOLDING one.
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class LockWaitSurfacesTest extends BaseDatabaseTest {

    private static final String LOCKED = "Statement '<QID>' has locked table 'LW1' in transaction <TXN> and this lock"
        + " has not yet been released.\nYour statement '<QID>' was aborted because waiting for this lock is limited"
        + " to 1 seconds.";

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
        say("A", "CREATE OR REPLACE TABLE lw1 (id INT, v INT)");
        say("A", "INSERT INTO lw1 VALUES (1, 1), (2, 2)");
        say("A", "CREATE OR REPLACE PROCEDURE lw_upd() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN UPDATE lw1 SET v = 0 WHERE id = 2; RETURN 'done'; END $$");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("A", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "DROP TABLE IF EXISTS lw1");
        say("A", "DROP PROCEDURE IF EXISTS lw_upd()");
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
     *         the refusal with its query and transaction IDs masked
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
            return "REFUSED " + String.valueOf(refused.getMessage())
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    /** Session A's SHOW LOCKS rows of this test's table, each as its status and whether it was acquired, sorted. */
    private List<String> locksOnTable() {
        final List<String> found = new ArrayList<String>();
        for (final String row : say("A", "SHOW LOCKS").split(" ;; ")) {
            final String[] cells = row.split("\\|", -1);
            if (cells.length >= 7 && cells[0].endsWith(".LW1")) {
                found.add(cells[4] + (cells[5] == null || "null".equals(cells[5]) ? " not acquired" : " acquired"));
            }
        }
        Collections.sort(found);
        return found;
    }

    @Test
    public void anInsertOverwriteWaitsAndAPlainInsertDoesNot() {
        say("A", "BEGIN");
        say("A", "UPDATE lw1 SET v = 10 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        assertEquals("REFUSED " + LOCKED, say("B", "INSERT OVERWRITE INTO lw1 SELECT * FROM lw1"));
        assertTrue(!say("B", "INSERT INTO lw1 VALUES (3, 3)").startsWith("REFUSED"));
        say("A", "COMMIT");
        assertEquals("1|10 ;; 2|2 ;; 3|3", say("B", "SELECT id, v FROM lw1 ORDER BY id"));
    }

    @Test
    public void aWaitInsideAProcedureOrABlockIsTheBlocksStatementError() {
        say("A", "BEGIN");
        say("A", "UPDATE lw1 SET v = 10 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        final String inBlock = "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : "
            + LOCKED.replace("\n", " ");
        assertEquals(inBlock, say("B", "CALL lw_upd()").replace("\n", " "));
        assertEquals(inBlock, say("B", "EXECUTE IMMEDIATE $$ BEGIN UPDATE lw1 SET v = 0 WHERE id = 2; "
            + "RETURN 'done'; END $$").replace("\n", " "));
    }

    @Test
    public void showLocksListsAWaitingStatement() throws InterruptedException {
        say("A", "BEGIN");
        say("A", "UPDATE lw1 SET v = 10 WHERE id = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say("B", "UPDATE lw1 SET v = 0 WHERE id = 2");
            }
        });
        waiting.start();
        List<String> locks = locksOnTable();
        for (int i = 0; i < 100 && locks.size() < 2; i++) {
            Thread.sleep(100L);
            locks = locksOnTable();
        }
        assertEquals("[HOLDING acquired, WAITING not acquired]", locks.toString());
        say("A", "COMMIT");
        waiting.join(60000L);
        assertTrue(answered[0] != null && !answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
        assertEquals("[]", locksOnTable().toString());
    }
}
