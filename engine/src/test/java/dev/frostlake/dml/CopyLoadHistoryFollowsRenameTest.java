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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table's COPY load history follows it through ALTER TABLE … RENAME TO and a move to another schema: the files its
 * own stage carried along are the files it already loaded, and a COPY of the renamed table skips them (live-verified
 * for a rename; COPY is not run on the account by this suite, so this is the engine's own check of that measurement).
 */
public class CopyLoadHistoryFollowsRenameTest {

    @TempDir
    Path dir;

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws IOException {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, Files.createDirectories(dir.resolve("stages"))
            .toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE SCHEMA d.other");
        engine.execute("USE SCHEMA d.PUBLIC");
        final Path file = Files.createDirectories(dir.resolve("local")).resolve("h1.csv");
        Files.write(file, "1,a\n".getBytes(StandardCharsets.UTF_8));
        engine.execute("CREATE TABLE lh (a INT, b VARCHAR)");
        engine.execute("PUT 'file://" + file + "' @%lh AUTO_COMPRESS = FALSE");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String copy(final String table) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("COPY INTO " + table + " FILE_FORMAT = (TYPE = CSV)").getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0));
            if (row.getValues().size() > 1) {
                out.append('|').append(row.getValue(1));
            }
        }
        return out.toString();
    }

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void aRenamedTableSkipsTheFilesItLoaded() {
        assertEquals("h1.csv|LOADED", copy("lh"));
        engine.execute("ALTER TABLE lh RENAME TO lh2");
        assertEquals("Copy executed with 0 files processed.", copy("lh2"));
        assertEquals(1L, count("lh2"));
    }

    @Test
    public void aTableMovedToAnotherSchemaSkipsTheFilesItLoaded() {
        assertEquals("h1.csv|LOADED", copy("lh"));
        engine.execute("ALTER TABLE lh RENAME TO d.other.lh3");
        assertEquals("Copy executed with 0 files processed.", copy("d.other.lh3"));
        assertEquals(1L, count("d.other.lh3"));
    }
}
