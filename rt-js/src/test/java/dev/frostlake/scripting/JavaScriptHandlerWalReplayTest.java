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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
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
 * A statement a JavaScript procedure issues through {@code snowflake.execute} runs inside the CALL, whose replay runs
 * the procedure and issues it again, so the write-ahead log keeps it with the CALL alone: a restart applies it once,
 * and a ROLLBACK of the CALL's transaction takes it away for good. A task the procedure runs commits by itself and is
 * logged apart, so the CALL's replay does not run it again.
 */
public class JavaScriptHandlerWalReplayTest {

    /** A JavaScript procedure that writes a row and runs the task. */
    private static final String HANDLER = """
        CREATE PROCEDURE jsp() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$
          snowflake.execute({sqlText: "INSERT INTO runs VALUES ('js')"});
          snowflake.execute({sqlText: "EXECUTE TASK tk"});
          return 'ok';
          $$""";

    private Path dir;

    /** The engines a test leaves running as a process that ended would, shut down once the test is over. */
    private final List<DatabaseEngine> crashed = new ArrayList<DatabaseEngine>();

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("wal_js_handler_");
    }

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : crashed) {
            engine.shutdown();
        }
        deleteRecursively(dir.toFile());
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

    /**
     * An engine the test leaves running when it restarts, as a process that ended would leave its log, with the
     * table, the task and the procedure created. Every checkpoint fails from then on, so the log keeps every record:
     * the blocker goes in once the engine has started, as a file in a data directory with no stages of its own yet
     * reads as a former run's data.
     */
    private DatabaseEngine crashingEngine() throws IOException {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        crashed.add(engine);
        Files.createFile(dir.resolve("wal-checkpoints"));
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE WAREHOUSE wh");
        engine.execute("CREATE TABLE runs (n VARCHAR)");
        engine.execute("CREATE TASK tk WAREHOUSE = wh SCHEDULE = '60 MINUTE' AS INSERT INTO runs VALUES ('task')");
        engine.execute(HANDLER);
        return engine;
    }

    /** A query's rows as "a, b | c, d". */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            rows.append(rows.length() > 0 ? " | " : "").append(row.getValue(0)).append(", ").append(row.getValue(1));
        }
        return rows.toString();
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
    public void aProceduresStatementsReplayOnceWithTheirCall() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        engine.execute("CALL jsp()");
        assertEquals("js, 1 | task, 1", answer(engine, check));
        assertEquals("js, 1 | task, 1", recovered(check));
    }

    @Test
    public void aRolledBackCallLeavesOnlyItsTaskRun() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        engine.execute("BEGIN");
        engine.execute("CALL jsp()");
        engine.execute("ROLLBACK");
        assertEquals("task, 1", answer(engine, check));
        assertEquals("task, 1", recovered(check));
    }

    @Test
    public void aCommittedCallReplaysOnce() throws IOException {
        final String check = "SELECT n, COUNT(*) FROM d.PUBLIC.runs GROUP BY n ORDER BY n";
        final DatabaseEngine engine = crashingEngine();
        engine.execute("BEGIN");
        engine.execute("CALL jsp()");
        engine.execute("COMMIT");
        assertEquals("js, 1 | task, 1", answer(engine, check));
        assertEquals("js, 1 | task, 1", recovered(check));
    }
}
