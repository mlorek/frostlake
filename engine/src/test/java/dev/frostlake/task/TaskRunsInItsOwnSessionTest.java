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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A task's run, an alert's evaluation and a pipe's load run in a session of their own, apart from whatever
 * session started them. The engine ran them through the caller's session, so they joined its transaction (the
 * caller's ROLLBACK undid a task's writes) and its statement history (they moved LAST_QUERY_ID and filled
 * QUERY_HISTORY_BY_SESSION). The scheduler's thread shared the embedded engine's implicit history, so a scheduled
 * run moved an idle caller's LAST_QUERY_ID as well.
 */
public class TaskRunsInItsOwnSessionTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("own_session_pipe_");
        engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE task_db");
        engine.execute("CREATE SCHEMA task_db.s");
        engine.execute("USE SCHEMA task_db.s");
        engine.execute("CREATE TABLE log (n INT)");
        engine.execute("CREATE TASK tk WAREHOUSE = COMPUTE_WH WHEN 1 = 1 AS INSERT INTO log VALUES (1)");
        engine.execute("CREATE ALERT al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN INSERT INTO log VALUES (2)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        deleteRecursively(stageDir.toFile());
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private String first(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    /** What the statement before the last one returned. */
    private String beforeLast() {
        return first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))");
    }

    private String rowsOf(final int n) {
        return first("SELECT COUNT(*) FROM log WHERE n = " + n);
    }

    private String inSessionHistory(final String text) {
        return first("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())"
            + " WHERE query_text = '" + text + "'");
    }

    @Test
    public void aTasksWritesSurviveTheCallersRollback() {
        engine.execute("BEGIN");
        engine.execute("EXECUTE TASK tk");
        engine.execute("ROLLBACK");
        assertEquals("1", rowsOf(1), "an explicit transaction's ROLLBACK");

        engine.execute("ALTER SESSION SET AUTOCOMMIT = FALSE");
        engine.execute("EXECUTE TASK tk");
        engine.execute("ROLLBACK");
        engine.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");
        assertEquals("2", rowsOf(1), "a session that does not autocommit");
    }

    @Test
    public void aTaskRunLeavesTheCallersLastQueryIdAndHistory() {
        engine.executeQuery("SELECT 'mark' AS c");
        engine.execute("EXECUTE TASK tk");
        assertEquals("mark", beforeLast());
        assertEquals("0", inSessionHistory("INSERT INTO log VALUES (1)"), "the task's body");
        assertEquals("0", inSessionHistory("SELECT (1 = 1)"), "the WHEN check the scheduler composes");
        assertEquals("1", first("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())"
            + " WHERE query_text = 'INSERT INTO log VALUES (1)'"), "the account's history still lists the run");
    }

    @Test
    public void aRunOnTheSchedulersThreadLeavesTheCallersLastQueryId() throws InterruptedException {
        final Task task = engine.getCatalog().getDatabase("TASK_DB").getSchema("S").getTask("TK");
        engine.executeQuery("SELECT 'mark' AS c");
        final RuntimeException[] failed = new RuntimeException[1];
        final Thread scheduler = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.getTaskScheduler().executeTaskNow("TASK_DB.S.TK", task);
                } catch (final RuntimeException e) {
                    failed[0] = e;
                }
            }
        });
        scheduler.start();
        scheduler.join();
        if (failed[0] != null) {
            throw failed[0];
        }
        assertEquals("mark", first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        assertEquals("1", rowsOf(1), "the run happened");
    }

    @Test
    public void anAlertsActionSurvivesTheCallersRollbackAndLeavesItsHistory() {
        engine.executeQuery("SELECT 'mark' AS c");
        engine.execute("EXECUTE ALERT al");
        assertEquals("mark", beforeLast());
        assertEquals("0", inSessionHistory("INSERT INTO log VALUES (2)"));

        engine.execute("BEGIN");
        engine.execute("EXECUTE ALERT al");
        engine.execute("ROLLBACK");
        assertEquals("2", rowsOf(2));
    }

    @Test
    public void aPipesLoadTakesNoQueryIdOfTheCallers() throws IOException {
        Files.writeString(stageDir.resolve("one.csv"), "7\n");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
        engine.execute("CREATE PIPE pp AS COPY INTO log FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
        engine.executeQuery("SELECT 'mark' AS c");
        engine.execute("ALTER PIPE pp REFRESH");
        assertEquals("mark", beforeLast());
        assertEquals("1", rowsOf(7), "the pipe loaded the file");
        assertEquals("0", first("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION())"
            + " WHERE query_text ILIKE 'COPY INTO%'"));
    }
}
