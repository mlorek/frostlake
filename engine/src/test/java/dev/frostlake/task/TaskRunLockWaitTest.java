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

package dev.frostlake.task;

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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task's run that meets a table another session's open transaction holds waits for it as a session's statement
 * does: SHOW LOCKS lists its statement as WAITING, and once the holder commits the run updates the committed row and
 * succeeds. A run whose task sets a LOCK_TIMEOUT it passes first fails with the lock refusal, ERROR_CODE 000627, and
 * while one run waits another runs and fails beside it (all live-verified).
 *
 * <p>Live, the sessions are connections and EXECUTE TASK returns as the run is scheduled; embedded, they are
 * sessions of one concurrent engine, whose EXECUTE TASK returns as the run ends, so each runs on a thread of its
 * own here.
 */
public class TaskRunLockWaitTest extends BaseDatabaseTest {

    private static final String LOCKED = "Statement '<QID>' has locked table 'TLW' in transaction <TXN> and this lock"
        + " has not yet been released.\nYour statement '<QID>' was aborted because waiting for this lock is ";

    private static final String[] SIDES = {"A", "C", "D", "E"};

    private ConcurrentDatabaseEngine concurrent;
    private final SessionContext[] sessions = new SessionContext[SIDES.length];
    private final Connection[] conns = new Connection[SIDES.length];

    @BeforeEach
    public void openSessions() {
        for (int i = 0; i < SIDES.length; i++) {
            if (isLiveSnowflake()) {
                conns[i] = LiveSnowflake.open();
            } else {
                if (concurrent == null) {
                    concurrent = new ConcurrentDatabaseEngine();
                }
                sessions[i] = concurrent.createSession();
            }
        }
        if (!isLiveSnowflake()) {
            say("C", "CREATE DATABASE IF NOT EXISTS test_db");
            say("C", "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        for (final String side : SIDES) {
            say(side, "USE SCHEMA test_db.test_schema");
        }
        say("C", "CREATE OR REPLACE TABLE tlw (id INT, v INT)");
        say("C", "INSERT INTO tlw VALUES (1, 1), (2, 2)");
    }

    @AfterEach
    public void closeSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("C", "DROP TASK IF EXISTS tlw_wait");
        say("C", "DROP TASK IF EXISTS tlw_short");
        say("C", "DROP TABLE IF EXISTS tlw");
        if (isLiveSnowflake()) {
            for (final Connection conn : conns) {
                conn.close();
            }
        } else {
            concurrent.shutdown();
        }
    }

    private int indexOf(final String side) {
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i].equals(side)) {
                return i;
            }
        }
        throw new IllegalArgumentException(side);
    }

    /**
     * Runs a statement in one session.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and
     *         the refusal, with query and transaction IDs masked throughout
     */
    private String say(final String side, final String sql) {
        final int index = indexOf(side);
        String answer;
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = conns[index].createStatement()) {
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
                    answer = out.toString();
                }
            } else {
                final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql, sessions[index]);
                final StringBuilder out = new StringBuilder();
                while (rows.next()) {
                    out.append(out.length() == 0 ? "" : " ;; ");
                    for (int i = 0; i < rows.getColumnCount(); i++) {
                        out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                    }
                }
                answer = out.toString();
            }
        } catch (final SQLException | RuntimeException refused) {
            answer = "REFUSED " + refused.getMessage();
        }
        return answer.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
            .replaceAll("\\b\\d{19}\\b", "<TXN>");
    }

    /** Runs a statement in one session on a thread of its own. */
    private Thread inBackground(final String side, final String sql) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                say(side, sql);
            }
        }, "task-run-" + side);
        thread.start();
        return thread;
    }

    /** Whether SHOW LOCKS IN ACCOUNT lists a WAITING lock on the table, polled for up to two minutes. */
    private boolean waitingListed() throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        while (System.nanoTime() < deadline) {
            for (final String row : say("C", "SHOW LOCKS IN ACCOUNT").split(" ;; ")) {
                if (row.contains(".TLW|") && row.contains("|WAITING|")) {
                    return true;
                }
            }
            Thread.sleep(250L);
        }
        return false;
    }

    /** A task's latest run once it ended, as its state, error code and error message, polled for two minutes. */
    private String endedRun(final String task) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        String run = "";
        while (System.nanoTime() < deadline) {
            run = say("C", "SELECT state, error_code, error_message FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY())"
                + " WHERE name = '" + task + "' AND database_name = 'TEST_DB' ORDER BY scheduled_time DESC LIMIT 1");
            if (run.startsWith("SUCCEEDED|") || run.startsWith("FAILED|")) {
                return run;
            }
            Thread.sleep(500L);
        }
        return run;
    }

    @Test
    public void aTaskRunWaitsForAHeldTableAndGivesUpAtItsLockTimeout() throws InterruptedException {
        say("C", "CREATE OR REPLACE TASK tlw_wait AS UPDATE tlw SET v = v + 50 WHERE id = 1");
        say("C", "CREATE OR REPLACE TASK tlw_short LOCK_TIMEOUT = 1 AS UPDATE tlw SET v = 66 WHERE id = 2");
        say("A", "BEGIN");
        say("A", "UPDATE tlw SET v = 300 WHERE id = 1");
        say("A", "UPDATE tlw SET v = 400 WHERE id = 2");
        final Thread waiting = inBackground("D", "EXECUTE TASK tlw_wait");
        assertTrue(waitingListed(), "the run never waited for the table");
        final Thread giving = inBackground("E", "EXECUTE TASK tlw_short");
        assertEquals("FAILED|000627|" + LOCKED + "limited to 1 seconds.", endedRun("TLW_SHORT"));
        giving.join(TimeUnit.MINUTES.toMillis(1));
        say("A", "COMMIT");
        assertEquals("SUCCEEDED|null|null", endedRun("TLW_WAIT"));
        waiting.join(TimeUnit.MINUTES.toMillis(1));
        assertEquals("1|350 ;; 2|400", say("C", "SELECT id, v FROM tlw ORDER BY id"));
    }
}
