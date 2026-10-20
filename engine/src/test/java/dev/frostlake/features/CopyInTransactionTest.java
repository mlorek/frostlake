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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO a table is DML, and takes part in the session's transaction as an INSERT does (documented): the rows it
 * loads are seen by other sessions once the transaction commits, and a ROLLBACK takes them back. The load is recorded
 * in the load history, and its files purged, at the commit; a rollback forgets the files it marked as loaded, so the
 * next COPY loads them again. The rows reach a stream on the table as any insert's do. The live account allows no
 * COPY INTO, so the rule is held by the embedded engine.
 */
public class CopyInTransactionTest {

    private ConcurrentDatabaseEngine engine;
    private SessionContext a;
    private SessionContext b;
    private Path dir;

    @BeforeEach
    public void setUp() throws Exception {
        engine = new ConcurrentDatabaseEngine();
        a = engine.createSession();
        b = engine.createSession();
        dir = Files.createTempDirectory("copy_txn_");
        final Path file = dir.resolve("ok.csv");
        Files.writeString(file, "1,a\n2,b\n");
        for (final String sql : new String[] {
            "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE STAGE st",
            "PUT file://" + file.toAbsolutePath() + " @st AUTO_COMPRESS=FALSE", "CREATE TABLE t (id INT, v STRING)",
            "CREATE TABLE u (id INT, v STRING)",
        }) {
            run(a, sql);
        }
        run(b, "USE SCHEMA d.public");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        final File[] children = dir.toFile().listFiles();
        if (children != null) {
            for (final File child : children) {
                child.delete();
            }
        }
        dir.toFile().delete();
    }

    /** A statement's answer: its first cell, or the rows of a listing joined. */
    private String run(final SessionContext session, final String sql) {
        final ExecutionResult result = engine.execute(sql, session);
        assertTrue(result.isSuccess(), sql + ": " + result.getErrorMessage());
        final List<ResultSet> sets = result.getResultSets();
        final ResultSet rs = sets.get(sets.size() - 1);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            out.append(out.length() > 0 ? " ;; " : "").append(rs.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aRolledBackLoadLeavesNoRowNoHistoryAndLoadsAgain() {
        run(a, "BEGIN");
        run(a, "INSERT INTO t VALUES (100, 'x')");
        assertEquals("st/ok.csv", run(a, "COPY INTO u FROM @st"));
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
        assertEquals("0", run(b, "SELECT COUNT(*) FROM u"));
        run(a, "ROLLBACK");
        assertEquals("0", run(a, "SELECT COUNT(*) FROM t"));
        assertEquals("0", run(a, "SELECT COUNT(*) FROM u"));
        assertEquals("0", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("st/ok.csv", run(a, "COPY INTO u FROM @st"));
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
        assertEquals("1", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("Copy executed with 0 files processed.", run(a, "COPY INTO u FROM @st"));
    }

    @Test
    public void aCommittedLoadIsSeenAndRecordedAtTheCommit() {
        run(a, "BEGIN");
        run(a, "COPY INTO u FROM @st");
        assertEquals("0", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("0", run(b, "SELECT COUNT(*) FROM u"));
        run(a, "COMMIT");
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
        assertEquals("1", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
    }

    @Test
    public void aPurgeWaitsForTheCommit() {
        run(a, "BEGIN");
        run(a, "COPY INTO u FROM @st PURGE = TRUE");
        run(a, "ROLLBACK");
        assertEquals("st/ok.csv", run(a, "LIST @st"));
        run(a, "BEGIN");
        run(a, "COPY INTO u FROM @st PURGE = TRUE");
        assertEquals("st/ok.csv", run(a, "LIST @st"));
        run(a, "COMMIT");
        assertEquals("", run(a, "LIST @st"));
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
    }

    @Test
    public void aStreamOnTheTableSeesTheLoadedRows() {
        run(a, "CREATE STREAM us ON TABLE u");
        run(a, "COPY INTO u FROM @st");
        assertEquals("2", run(a, "SELECT COUNT(*) FROM us"));
        assertEquals("INSERT ;; INSERT", run(a, "SELECT METADATA$ACTION FROM us"));
    }

    /** Runs a statement in a session on a thread of its own, its answer or refusal kept in {@code answer[0]}. */
    private Thread runApart(final SessionContext session, final String sql, final String[] answer) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final ExecutionResult result;
                try {
                    result = engine.execute(sql, session);
                } catch (final RuntimeException refused) {
                    answer[0] = "REFUSED " + refused.getMessage();
                    return;
                }
                final ResultSet rs = result.getResultSets().get(result.getResultSets().size() - 1);
                answer[0] = rs.next() ? String.valueOf(rs.getValue(0)) : "";
            }
        });
        thread.start();
        return thread;
    }

    /**
     * FL-444: a file another session's open transaction loaded is not loaded yet. Another session's COPY of it waits
     * for that transaction, as a writer waits for a table's holder, and loads the file once the transaction rolls
     * back, where it answered "0 files processed" and nothing ever loaded the file.
     */
    @Test
    public void anotherSessionsCopyWaitsForAnUncommittedLoadAndLoadsTheFileAfterARollback() throws Exception {
        run(b, "ALTER SESSION SET LOCK_TIMEOUT = 60");
        run(a, "BEGIN");
        assertEquals("st/ok.csv", run(a, "COPY INTO u FROM @st"));
        final String[] answer = new String[1];
        final Thread waiting = runApart(b, "COPY INTO d.public.u FROM @d.public.st", answer);
        Thread.sleep(500L);
        assertTrue(waiting.isAlive(), "the COPY waits for the transaction that loaded the file: " + answer[0]);
        run(a, "ROLLBACK");
        waiting.join(60000L);
        assertEquals("d.public.st/ok.csv", answer[0]);
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
        assertEquals("1", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
        assertEquals("Copy executed with 0 files processed.", run(a, "COPY INTO u FROM @st"));
    }

    /** FL-444: once the transaction that loaded the file commits, the waiting COPY skips it as loaded. */
    @Test
    public void anotherSessionsCopyWaitsForAnUncommittedLoadAndSkipsTheFileAfterACommit() throws Exception {
        run(b, "ALTER SESSION SET LOCK_TIMEOUT = 60");
        run(a, "BEGIN");
        run(a, "COPY INTO u FROM @st");
        final String[] answer = new String[1];
        final Thread waiting = runApart(b, "COPY INTO u FROM @st", answer);
        Thread.sleep(500L);
        assertTrue(waiting.isAlive(), "the COPY waits for the transaction that loaded the file: " + answer[0]);
        run(a, "COMMIT");
        waiting.join(60000L);
        assertEquals("Copy executed with 0 files processed.", answer[0]);
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
    }

    /**
     * FL-444: the wait is bounded by LOCK_TIMEOUT like any lock wait, and refused the same way; the loading
     * transaction's own next COPY counts its file as loaded, and FORCE loads it in another session without waiting.
     */
    @Test
    public void anUncommittedLoadIsTheTransactionsOwnUntilItCommits() {
        run(b, "ALTER SESSION SET LOCK_TIMEOUT = 1");
        run(a, "BEGIN");
        run(a, "COPY INTO u FROM @st");
        assertEquals("Copy executed with 0 files processed.", run(a, "COPY INTO u FROM @st"));
        final ExecutionResult refused;
        try {
            refused = engine.execute("COPY INTO u FROM @st", b);
            throw new AssertionError("the COPY was not refused: " + refused.getResultSets());
        } catch (final RuntimeException expected) {
            assertTrue(expected.getMessage().contains("has locked table 'U' in transaction")
                && expected.getMessage().contains("waiting for this lock is limited to 1 seconds."),
                expected.getMessage());
        }
        assertEquals("st/ok.csv", run(b, "COPY INTO u FROM @st FORCE = TRUE"));
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
        run(a, "ROLLBACK");
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
        // The forced load committed, so the file now counts as loaded for every session.
        assertEquals("Copy executed with 0 files processed.", run(a, "COPY INTO u FROM @st"));
    }
}
