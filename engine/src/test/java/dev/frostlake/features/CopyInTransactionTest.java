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
}
