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


package dev.frostlake.persistence;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CALL whose procedure runs EXECUTE TASK is part of the caller's transaction: what the procedure wrote there goes
 * with the caller's ROLLBACK, and a restart does not bring it back, while the task's own run, which committed by
 * itself, stays.
 */
public class WalCallExecuteTaskTest {
    private Path dir;

    /** The engines a test leaves running as a process that ended would, shut down once the test is over. */
    private final List<DatabaseEngine> crashed = new ArrayList<DatabaseEngine>();

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_temporary_");
    }

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : crashed) {
            engine.shutdown();
        }
        deleteRecursively(dir.toFile());
    }

    /** An engine the test leaves running when it restarts, as a process that ended would leave its log. */
    private DatabaseEngine crashingEngine() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        crashed.add(engine);
        return engine;
    }

    /** Remove a test's data directory whole: the engine keeps its stages in a directory inside it. */
    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** A statement's answer on one line: rows as "a, b | c, d", or "REFUSED " and the refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aRolledBackCallStaysRolledBackAfterARestart() {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String before;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE WAREHOUSE wh");
            engine.execute("CREATE TABLE runs (n VARCHAR)");
            engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '60 MINUTE' AS INSERT INTO runs VALUES ('task')");
            engine.execute("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS $$ BEGIN "
                + "INSERT INTO runs VALUES ('proc'); EXECUTE TASK tk; RETURN 1; END $$");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO runs VALUES ('before-call')");
            engine.execute("CALL p()");
            engine.execute("SELECT SYSTEM$WAIT(1)");
            engine.execute("ROLLBACK");
            before = answer(engine, check);
        } finally {
            engine.shutdown();
        }
        assertEquals("task, 1", before);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(before, answer(recovered, check));
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aRolledBackCallInASessionOfTheConcurrentEngine() {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        final String before;
        try {
            final SessionContext session = engine.createSession();
            for (final String sql : new String[] {"CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE WAREHOUSE wh",
                "CREATE TABLE runs (n VARCHAR)",
                "CREATE TASK tk WAREHOUSE = wh SCHEDULE = '60 MINUTE' AS INSERT INTO runs VALUES ('task')",
                "CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS $$ BEGIN INSERT INTO runs VALUES ('proc'); "
                    + "EXECUTE TASK tk; RETURN 1; END $$",
                "BEGIN", "INSERT INTO runs VALUES ('before-call')", "CALL p()", "SELECT SYSTEM$WAIT(1)", "ROLLBACK"}) {
                engine.executeQuery(sql, session);
            }
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(check, session).getRows()) {
                rows.append(rows.length() > 0 ? " | " : "").append(row.getValue(0)).append(", ")
                    .append(row.getValue(1));
            }
            before = rows.toString();
        } finally {
            engine.shutdown();
        }
        assertEquals("task, 1", before);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(before, answer(recovered, check));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aCallWhoseTransactionNeverEndedIsNotReplayed() {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        createObjects(engine);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO runs VALUES ('before-call')");
        engine.execute("CALL p()");
        // The process ends here, its transaction open: no COMMIT, no ROLLBACK, no shutdown.
        assertEquals("task, 1", recovered(check));
    }

    /**
     * Makes every checkpoint fail, as a crash between a transaction's record and the checkpoint its run owes leaves
     * the log: the snapshot has no directory to go to, so the log keeps every record. Called once the engine has
     * started: a file in a data directory that has no stages of its own yet reads as a former run's data, which takes
     * a copy of the shared stage root.
     */
    private void failCheckpoints() throws IOException {
        Files.createFile(dir.resolve("wal-checkpoints"));
    }

    /** Creates the table, the task and two procedures that write and then run the task. */
    private static void createObjects(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE WAREHOUSE wh");
        engine.execute("CREATE TABLE runs (n VARCHAR)");
        engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '60 MINUTE' AS INSERT INTO runs VALUES ('task')");
        engine.execute("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS $$ BEGIN INSERT INTO runs VALUES ('proc'); "
            + "EXECUTE TASK tk; RETURN 1; END $$");
        engine.execute("CREATE PROCEDURE q() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN "
            + "INSERT INTO runs VALUES ('branch'); EXECUTE TASK tk; END IF; RETURN 1; END $$");
    }

    /** What the engine recovering from the log answers, with no replay failing. */
    private String recovered(final String check) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            return answer(recovered, check);
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aCommittedCallReplaysWithoutRunningItsTaskAgain() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        failCheckpoints();
        createObjects(engine);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO runs VALUES ('before-call')");
        engine.execute("CALL p()");
        engine.execute("COMMIT");
        assertEquals("before-call, 1 | proc, 1 | task, 1", answer(engine, check));
        assertEquals("before-call, 1 | proc, 1 | task, 1", recovered(check));
    }

    @Test
    public void aRolledBackCallLeavesOnlyItsTaskRunInTheLog() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        failCheckpoints();
        createObjects(engine);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO runs VALUES ('before-call')");
        engine.execute("CALL p()");
        engine.execute("ROLLBACK");
        assertEquals("task, 1", answer(engine, check));
        assertEquals("task, 1", recovered(check));
    }

    @Test
    public void aCallOutsideATransactionReplaysWithoutRunningItsTaskAgain() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        failCheckpoints();
        createObjects(engine);
        engine.execute("CALL p()");
        assertEquals("proc, 1 | task, 1", answer(engine, check));
        assertEquals("proc, 1 | task, 1", recovered(check));
    }

    @Test
    public void aTaskRunInABranchOfAnUnfinishedTransactionIsReplayedAlone() {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        createObjects(engine);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO runs VALUES ('before-call')");
        engine.execute("CALL q()");
        // The process ends here, its transaction open.
        assertEquals("task, 1", recovered(check));
    }
}
