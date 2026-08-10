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

package dev.frostlake.dml;

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
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER PIPE … REFRESH PREFIX / MODIFIED_AFTER narrow the files a pipe's COPY loads: PREFIX keeps only
 * files whose stage-relative path starts with the given prefix; MODIFIED_AFTER keeps only files
 * last-modified strictly after the given ISO-8601 timestamp. Previously both were parsed then ignored
 * (REFRESH always re-ran the full COPY). Uses a {@code file://} stage backed by a temp directory.
 */
public class AlterPipeRefreshFilterTest {

    private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant NEW = Instant.parse("2025-01-01T00:00:00Z");

    private DatabaseEngine engine;
    private Path stageDir;

    @BeforeEach
    public void setUp() throws IOException {
        stageDir = Files.createTempDirectory("pipe_refresh_test_");
        engine = new DatabaseEngine();
        // The tests point stages at local file:// directories - opt in to the affordance the
        // default config refuses (a real account refuses those URLs).
        engine.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
        engine.execute("CREATE STAGE data_stage URL='file://" + stageDir + "'");
        engine.execute("CREATE TABLE events (id INTEGER, kind VARCHAR)");
        engine.execute("CREATE PIPE evt_pipe AS COPY INTO events FROM @data_stage FILE_FORMAT = (TYPE = 'CSV')");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        deleteRecursively(stageDir.toFile());
    }

    private void writeStageFile(final String name, final String content, final Instant modified) throws IOException {
        final Path file = stageDir.resolve(name);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(modified));
    }

    private long eventCount() {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM events").getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void prefixLoadsOnlyMatchingFiles() throws IOException {
        writeStageFile("data_1.csv", "1,login\n", NEW);
        writeStageFile("other_1.csv", "2,logout\n", NEW);

        engine.execute("ALTER PIPE evt_pipe REFRESH PREFIX='data'");

        assertEquals(1, eventCount());
        final ResultSet rs = engine.executeQuery("SELECT kind FROM events");
        assertEquals("login", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void modifiedAfterLoadsOnlyNewerFiles() throws IOException {
        writeStageFile("old.csv", "1,login\n", OLD);
        writeStageFile("new.csv", "2,logout\n", NEW);

        engine.execute("ALTER PIPE evt_pipe REFRESH MODIFIED_AFTER='2023-01-01T00:00:00Z'");

        assertEquals(1, eventCount());
        final ResultSet rs = engine.executeQuery("SELECT kind FROM events");
        assertEquals("logout", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void prefixAndModifiedAfterCombine() throws IOException {
        writeStageFile("data_new.csv", "1,keep\n", NEW);    // right prefix + newer  → loaded
        writeStageFile("data_old.csv", "2,skip\n", OLD);    // right prefix but older → skipped
        writeStageFile("misc_new.csv", "3,skip\n", NEW);    // newer but wrong prefix → skipped

        engine.execute("ALTER PIPE evt_pipe REFRESH PREFIX='data' MODIFIED_AFTER='2023-01-01T00:00:00Z'");

        assertEquals(1, eventCount());
        final ResultSet rs = engine.executeQuery("SELECT kind FROM events");
        assertEquals("keep", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void refreshWithoutFiltersLoadsAll() throws IOException {
        writeStageFile("a.csv", "1,login\n", OLD);
        writeStageFile("b.csv", "2,logout\n", NEW);

        engine.execute("ALTER PIPE evt_pipe REFRESH");

        assertEquals(2, eventCount());
    }

    @Test
    public void invalidModifiedAfterTimestampThrows() throws IOException {
        writeStageFile("a.csv", "1,login\n", NEW);

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER PIPE evt_pipe REFRESH MODIFIED_AFTER='not-a-timestamp'");
            }
        });
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
