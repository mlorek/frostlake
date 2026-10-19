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

package dev.frostlake.stage;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table's stage belongs to the table. Its files stayed under the table's old name when the table was renamed or
 * moved, or its schema or database renamed, and a new table taking that name deleted them. Now they go where the
 * table goes, a table that takes the old name starts with an empty stage, and a swap trades the stages as it trades
 * the rows. A named internal stage follows its schema's and its database's renames the same way.
 */
public class TableStageFollowsItsTableTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    private Path internalRoot;
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() throws IOException {
        internalRoot = Files.createTempDirectory("table_stage_follows_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, internalRoot.toString());
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("COPY INTO @%t/f FROM (SELECT 1 AS a)" + CSV);
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
        deleteRecursively(internalRoot.toFile());
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

    /** Every row's first cell, a bar between rows. */
    private String answer(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aRenamedTableTakesItsStage() {
        engine.execute("ALTER TABLE t RENAME TO t2");
        assertEquals("f", answer("LIST @%t2"));
        assertEquals("1", answer("SELECT $1 FROM @%t2/f"));
        // A table that takes the old name starts empty, and its CREATE leaves the renamed table's files alone.
        engine.execute("CREATE TABLE t (a INT)");
        assertEquals("", answer("LIST @%t"));
        assertEquals("f", answer("LIST @%t2"));
    }

    @Test
    public void aTableMovedToAnotherSchemaOrDatabaseTakesItsStage() {
        engine.execute("CREATE SCHEMA d.s2");
        engine.execute("ALTER TABLE d.PUBLIC.t RENAME TO d.s2.t");
        assertEquals("f", answer("LIST @d.s2.%t"));
        engine.execute("CREATE DATABASE e");
        engine.execute("ALTER TABLE d.s2.t RENAME TO e.PUBLIC.moved");
        assertEquals("f", answer("LIST @e.PUBLIC.%moved"));
        assertEquals("1", answer("SELECT $1 FROM @e.PUBLIC.%moved/f"));
    }

    @Test
    public void aSwapTradesTheStages() {
        engine.execute("CREATE TABLE u (a INT)");
        engine.execute("COPY INTO @%u/g FROM (SELECT 2 AS a)" + CSV);
        engine.execute("ALTER TABLE t SWAP WITH u");
        assertEquals("g", answer("LIST @%t"));
        assertEquals("f", answer("LIST @%u"));
        assertEquals("1", answer("SELECT $1 FROM @%u/f"));
    }

    @Test
    public void aRenamedSchemaOrDatabaseTakesItsStages() {
        engine.execute("CREATE SCHEMA d.s");
        engine.execute("CREATE TABLE d.s.x (a INT)");
        engine.execute("COPY INTO @d.s.%x/h FROM (SELECT 3 AS a)" + CSV);
        engine.execute("CREATE STAGE d.s.st");
        engine.execute("COPY INTO @d.s.st/k FROM (SELECT 4 AS a)" + CSV);
        engine.execute("ALTER SCHEMA d.s RENAME TO d.s2");
        assertEquals("h", answer("LIST @d.s2.%x"));
        assertEquals("st/k", answer("LIST @d.s2.st"));
        engine.execute("ALTER DATABASE d RENAME TO d3");
        assertEquals("f", answer("LIST @d3.PUBLIC.%t"));
        assertEquals("h", answer("LIST @d3.s2.%x"));
        assertEquals("4", answer("SELECT $1 FROM @d3.s2.st/k"));
    }

    @Test
    public void aDroppedTablesFilesDoNotReachTheTableRenamedToItsName() {
        engine.execute("CREATE TABLE w (a INT)");
        engine.execute("COPY INTO @%w/old FROM (SELECT 9 AS a)" + CSV);
        engine.execute("DROP TABLE w");
        engine.execute("ALTER TABLE t RENAME TO w");
        assertEquals("f", answer("LIST @%w"));
    }
}
