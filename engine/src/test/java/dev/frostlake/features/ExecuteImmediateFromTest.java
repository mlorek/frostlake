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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EXECUTE IMMEDIATE FROM — run the SQL script stored in a stage file or at a location URL. The referenced
 * file is read and its statements executed, both for a named-stage reference (@stage/file) and a location
 * string ('file://…').
 */
public class ExecuteImmediateFromTest {

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("exec_immediate_from_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE scripts URL='file://" + stageDir + "'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void runsMultiStatementScriptFromStageFile() throws IOException {
        Files.writeString(stageDir.resolve("make.sql"),
            "CREATE TABLE t1 (id INTEGER); INSERT INTO t1 VALUES (1), (2), (3);");

        engine.execute("EXECUTE IMMEDIATE FROM @scripts/make.sql");

        assertEquals(3, count("t1"));
    }

    @Test
    public void runsScriptFromLocationString() throws IOException {
        Files.writeString(stageDir.resolve("make2.sql"),
            "CREATE TABLE t2 (v VARCHAR); INSERT INTO t2 VALUES ('a'), ('b');");

        engine.execute("EXECUTE IMMEDIATE FROM 'file://" + stageDir + "/make2.sql'");

        assertEquals(2, count("t2"));
    }

    @Test
    public void returnsResultOfTheLastStatement() throws IOException {
        Files.writeString(stageDir.resolve("answer.sql"), "SELECT 42 AS answer;");

        final ResultSet rs = engine.executeQuery("EXECUTE IMMEDIATE FROM @scripts/answer.sql");
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void missingScriptFileThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE FROM @scripts/does_not_exist.sql");
            }
        });
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
