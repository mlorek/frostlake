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
import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.TransactionOutcome;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A TRUNCATE forgets the table's COPY load history as it empties the rows, so it does so as DML does: for its own
 * transaction at once, so a COPY after it there loads the files again; for everyone once it commits; and not at all
 * when it rolls back, which keeps the rows and leaves the files skipped. A commit whose writes do not apply ends what
 * its statements left for its end as a rollback does. The live account allows no COPY INTO, so the rule is held by
 * the embedded engine.
 */
public class TruncateLoadHistoryInTransactionTest {

    private static final String NONE = "Copy executed with 0 files processed.";

    private ConcurrentDatabaseEngine engine;
    private SessionContext a;
    private SessionContext b;
    private Path dir;

    @BeforeEach
    public void setUp() throws Exception {
        engine = new ConcurrentDatabaseEngine();
        a = engine.createSession();
        b = engine.createSession();
        dir = Files.createTempDirectory("truncate_history_");
        final Path file = dir.resolve("ok.csv");
        Files.writeString(file, "1,a\n2,b\n");
        for (final String sql : new String[] {
            "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE STAGE st",
            "PUT file://" + file.toAbsolutePath() + " @st AUTO_COMPRESS=FALSE", "CREATE TABLE u (id INT, v STRING)",
        }) {
            run(a, sql);
        }
        run(b, "USE SCHEMA d.public");
        assertEquals("st/ok.csv", run(a, "COPY INTO u FROM @st"));
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
    public void aRolledBackTruncateKeepsTheLoadHistory() {
        run(a, "BEGIN");
        run(a, "TRUNCATE TABLE u");
        run(a, "ROLLBACK");
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
        assertEquals(NONE, run(a, "COPY INTO u FROM @st"));
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
        assertEquals("1", run(a, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
    }

    @Test
    public void aTruncateForgetsTheHistoryForItsTransactionAtOnceAndForEveryoneAtCommit() {
        run(a, "BEGIN");
        run(a, "TRUNCATE TABLE u");
        assertEquals("st/ok.csv", run(a, "COPY INTO u FROM @st"));
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
        run(a, "COMMIT");
        assertEquals("2", run(b, "SELECT COUNT(*) FROM u"));
        assertEquals(NONE, run(b, "COPY INTO u FROM @st"));
        assertEquals("1", run(b, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.LOAD_HISTORY"));
    }

    @Test
    public void anAutocommitTruncateForgetsTheHistory() {
        run(a, "TRUNCATE TABLE u");
        assertEquals("st/ok.csv", run(b, "COPY INTO u FROM @st"));
        assertEquals("2", run(a, "SELECT COUNT(*) FROM u"));
    }

    @Test
    public void aCommitWhoseWritesDoNotApplyEndsItsOutcomesAsARollback() {
        final DatabaseEngine plain = new DatabaseEngine();
        try {
            plain.execute("CREATE DATABASE f");
            plain.execute("CREATE TABLE f.public.w (id INT)");
            final TransactionManager manager = plain.getTransactionManager();
            final Transaction transaction = manager.beginTransaction();
            transaction.getWriteSet().recordInsert("F.PUBLIC.W", new Row(1, 2, 3));
            final boolean[] ended = new boolean[2];
            transaction.onOutcome(new TransactionOutcome() {
                @Override
                public void committed() {
                    ended[0] = true;
                }

                @Override
                public void rolledBack() {
                    ended[1] = true;
                }
            });
            assertTrue(plain.getStorageEngine().hasTable("F.PUBLIC.W"));
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    manager.commit();
                }
            });
            assertFalse(ended[0]);
            assertTrue(ended[1]);
        } finally {
            plain.shutdown();
        }
    }
}
