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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The task graph properties and the stream start state survive a save and a reload: a task's configuration,
 * overlap policy, session parameters, finalizer link, run-as user and statement-size floor, a stream's pending
 * initial rows, a dynamic-table stream's image of its table, and the files a stage's directory table registered.
 */
public class TaskStreamClausePersistenceTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_task_stream_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        if (dataDir != null) {
            deleteRecursively(dataDir.toFile());
        }
    }

    private DatabaseEngine open(final boolean create) {
        final DatabaseEngine engine = new DatabaseEngine(config);
        if (create) {
            engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
            engine.execute("USE DATABASE test_db");
            engine.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        } else {
            engine.execute("USE DATABASE test_db");
        }
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    private static Schema schema(final DatabaseEngine engine) {
        return engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
    }

    @Test
    public void taskGraphPropertiesSurviveAReload() {
        final DatabaseEngine first = open(true);
        final String user = String.valueOf(first.executeQuery("SELECT CURRENT_USER()").getRows().get(0).getValue(0));
        first.execute("CREATE TASK root SCHEDULE = '60 MINUTE' CONFIG = '{\"a\": 1}' OVERLAP_POLICY = ALLOW_ALL_OVERLAP "
            + "QUERY_TAG = 'qt' SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'SMALL' EXECUTE AS USER \"" + user
            + "\" AS SELECT 1");
        first.execute("CREATE TASK fin FINALIZE = root AS SELECT 2");
        first.shutdown();

        final DatabaseEngine second = open(false);
        final Task root = schema(second).getTask("ROOT");
        assertEquals("{\"a\": 1}", root.getConfig());
        assertEquals("ALLOW_ALL_OVERLAP", root.getOverlapPolicy());
        assertEquals("qt", root.getSessionParameters().get("QUERY_TAG"));
        assertEquals("SMALL", root.getServerlessTaskMinStatementSize());
        assertEquals(user, root.getExecuteAsUser());
        assertEquals("ROOT", schema(second).getTask("FIN").getFinalizedRootTask());
        second.shutdown();
    }

    @Test
    public void pendingInitialRowsAndARefreshImageSurviveAReload() {
        final DatabaseEngine first = open(true);
        first.execute("CREATE TABLE t (id INT)");
        first.execute("INSERT INTO t VALUES (1), (2)");
        first.execute("CREATE STREAM s ON TABLE t SHOW_INITIAL_ROWS = TRUE");
        first.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = DOWNSTREAM WAREHOUSE = COMPUTE_WH AS SELECT id FROM t");
        first.execute("CREATE STREAM sd ON DYNAMIC TABLE dt");
        first.shutdown();

        final DatabaseEngine second = open(false);
        final Stream initial = schema(second).getStream("S");
        assertNotNull(initial.getInitialRecords());
        assertEquals(2, initial.getInitialRecords().size());
        assertEquals(2, schema(second).getStream("SD").getRefreshImage().size());
        second.execute("INSERT INTO t VALUES (3)");
        second.execute("ALTER DYNAMIC TABLE dt REFRESH");
        assertEquals(1L, ((Number) second.executeQuery("SELECT COUNT(*) FROM sd").getRows().get(0).getValue(0))
            .longValue(), "only the row the refresh added");
        second.shutdown();
    }

    @Test
    public void aStagesRegisteredFilesSurviveAReload() throws IOException {
        final DatabaseEngine first = open(true);
        first.execute("CREATE STAGE st DIRECTORY = (ENABLE = TRUE)");
        first.execute("CREATE STREAM ss ON STAGE st");
        final Path file = Files.createTempDirectory("persist_stage_put_").resolve("f1.csv");
        Files.writeString(file, "1,a\n");
        first.execute("PUT file://" + file.toAbsolutePath() + " @st AUTO_COMPRESS = FALSE");
        first.execute("ALTER STAGE st REFRESH");
        assertEquals(1, schema(first).getStage("ST").getDirectoryRegistry().size());
        first.shutdown();

        final DatabaseEngine second = open(false);
        assertEquals(1, schema(second).getStage("ST").getDirectoryRegistry().size(),
            "the file the directory table registered before the reload");
        second.shutdown();
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
