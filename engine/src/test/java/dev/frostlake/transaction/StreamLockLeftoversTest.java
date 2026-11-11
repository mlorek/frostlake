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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Three sessions over one stream, the cases a stream's lock leaves (live-verified): a holder that read the stream twice
 * is named by its first read; SHOW LOCKS lists a session waiting for the stream with the type STREAM and the status
 * WAITING, beside the holder's HOLDING, and the waiter goes on once the holder's transaction ends; a request that
 * begins a transaction and reads the held stream in one text waits as a statement of its own does, refused inside
 * the multi-statement wrapper; and a stream replaced while another transaction holds the old one is free.
 *
 * <p>Live, the sessions are three connections; embedded, three sessions of one concurrent engine.
 */
public class StreamLockLeftoversTest extends BaseDatabaseTest {

    private static final String LOCKED = "REFUSED Statement '<FIRST>' has locked stream 'S5' in transaction <TXN> and "
        + "this lock has not yet been released.|Your statement '<QID>' was aborted because waiting for this lock is "
        + "limited to 2 seconds.";

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext sessionA;
    private SessionContext sessionB;
    private SessionContext sessionC;
    private Connection connA;
    private Connection connB;
    private Connection connC;

    @BeforeEach
    public void openSessions() {
        if (isLiveSnowflake()) {
            connA = LiveSnowflake.open();
            connB = LiveSnowflake.open();
            connC = LiveSnowflake.open();
        } else {
            concurrent = new ConcurrentDatabaseEngine();
            sessionA = concurrent.createSession();
            sessionB = concurrent.createSession();
            sessionC = concurrent.createSession();
            say("A", "CREATE DATABASE IF NOT EXISTS test_db");
            say("A", "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say("A", "USE SCHEMA test_db.test_schema");
        say("B", "USE SCHEMA test_db.test_schema");
        say("C", "USE SCHEMA test_db.test_schema");
        say("A", "CREATE OR REPLACE TABLE t5 (id INT)");
        say("A", "CREATE OR REPLACE STREAM s5 ON TABLE t5");
        say("A", "INSERT INTO t5 VALUES (1)");
        say("A", "CREATE OR REPLACE TABLE sink5 (id INT)");
        say("A", "ALTER SESSION SET LOCK_TIMEOUT = 2");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 2");
    }

    @AfterEach
    public void closeSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        say("A", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("B", "ALTER SESSION UNSET LOCK_TIMEOUT");
        say("A", "ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        say("A", "DROP STREAM IF EXISTS s5");
        say("A", "DROP TABLE IF EXISTS t5");
        say("A", "DROP TABLE IF EXISTS sink5");
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
            connC.close();
        } else {
            concurrent.shutdown();
        }
    }

    /**
     * Runs a statement in one session.
     *
     * @return its rows as {@code a|b ;; c|d}, the count a statement that answers no rows reports, or {@code REFUSED}
     *         and the refusal, its lines joined by {@code |}
     */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                final Connection connection = "A".equals(side) ? connA : "B".equals(side) ? connB : connC;
                final Statement statement = connection.createStatement();
                try {
                    if (!statement.execute(sql)) {
                        return String.valueOf(statement.getUpdateCount());
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
                } finally {
                    statement.close();
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql,
                "A".equals(side) ? sessionA : "B".equals(side) ? sessionB : sessionC);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The last query ID a session ran. */
    private String lastQuery(final String side) {
        return say(side, "SELECT LAST_QUERY_ID()");
    }

    /** A text with the named query ID marked and every other query and transaction ID masked. */
    private static String masked(final String text, final String first) {
        return text.replace(first, "<FIRST>")
            .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
            .replaceAll("\\b\\d{19}\\b", "<TXN>");
    }

    @Test
    public void aHolderThatReadTheStreamTwiceIsNamedByItsFirstRead() {
        say("A", "BEGIN");
        say("A", "SELECT * FROM s5");
        final String first = lastQuery("A");
        say("A", "SELECT COUNT(*) FROM s5");
        assertEquals(LOCKED, masked(say("B", "INSERT INTO sink5 SELECT id FROM s5"), first));
        say("A", "ROLLBACK");
        say("A", "BEGIN");
        say("A", "SELECT * FROM s5");
        final String read = lastQuery("A");
        say("A", "INSERT INTO sink5 SELECT id FROM s5");
        assertEquals(LOCKED, masked(say("B", "INSERT INTO sink5 SELECT id FROM s5"), read));
    }

    @Test
    public void showLocksListsTheWaiterUntilTheHolderEnds() throws InterruptedException {
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        say("A", "BEGIN");
        say("A", "SELECT * FROM s5");
        final String[] waited = new String[1];
        final Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                waited[0] = say("B", "INSERT INTO sink5 SELECT id FROM s5");
            }
        });
        waiter.start();
        String locks = "";
        for (int i = 0; i < 100 && !locks.contains("WAITING"); i++) {
            Thread.sleep(100);
            say("C", "SHOW LOCKS");
            locks = say("C", "SELECT \"resource\", \"type\", \"status\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "WHERE \"resource\" = 'TEST_DB.TEST_SCHEMA.S5' ORDER BY 3");
        }
        assertEquals("TEST_DB.TEST_SCHEMA.S5|STREAM|HOLDING ;; TEST_DB.TEST_SCHEMA.S5|STREAM|WAITING", locks);
        say("A", "ROLLBACK");
        waiter.join();
        assertEquals("1", waited[0]);
    }

    @Test
    public void aRequestThatBeginsAndReadsWaitsInsideTheWrapper() {
        say("B", "BEGIN");
        say("B", "SELECT * FROM s5");
        say("A", "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        assertEquals("REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on "
            + "statement \"SELECT COUNT(*) FROM s5\" (at line 1, position 7).|Statement '<QID>' has locked stream "
            + "'S5' in transaction <TXN> and this lock has not yet been released.|Your statement '<QID>' was aborted "
            + "because waiting for this lock is limited to 2 seconds. in SYSTEM$MULTISTMT at '    throw `Execution of "
            + "multiple statements failed on statement {0} (at line {1}, position {2}).`"
            + ".replace('{1}', LINES[i])' position 4|stackstrace: |SYSTEM$MULTISTMT line: 10",
            masked(say("A", "BEGIN; SELECT COUNT(*) FROM s5"), "<none>"));
        assertEquals("true", say("A", "SELECT CURRENT_TRANSACTION() IS NOT NULL").toLowerCase(Locale.ROOT));
    }

    @Test
    public void aStreamReplacedWhileTheOldOneIsHeldIsFree() {
        say("A", "BEGIN");
        say("A", "SELECT * FROM s5");
        say("B", "CREATE OR REPLACE STREAM s5 ON TABLE t5");
        assertEquals("0", say("B", "INSERT INTO sink5 SELECT id FROM s5"));
        assertEquals("0", say("B", "SELECT COUNT(*) FROM s5"));
        say("B", "BEGIN");
        assertEquals("0", say("B", "SELECT COUNT(*) FROM s5"));
        say("B", "ROLLBACK");
        assertEquals("0", say("A", "SELECT COUNT(*) FROM s5"));
    }
}
