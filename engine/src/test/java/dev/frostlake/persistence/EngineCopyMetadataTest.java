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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a copy of an engine keeps of its objects' metadata: the collation of a view's, a materialized view's and a
 * dynamic table's columns, a literal DEFAULT as the value it is, a dynamic table's last refresh — its data_timestamp,
 * and the rows the next refresh tells changes by — and the creation times of the system roles, INFORMATION_SCHEMA and
 * the SNOWFLAKE database. A write-ahead log's replay of a CREATE STAGE with a directory table registers no file the
 * statements after it staged.
 */
public class EngineCopyMetadataTest {

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("engine_copy_metadata_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
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

    private static final String[] QUERIES = {
        "USE SCHEMA d.PUBLIC",
        "DESCRIBE VIEW cv",
        "DESCRIBE MATERIALIZED VIEW mv",
        "DESCRIBE DYNAMIC TABLE dt1",
        "DESCRIBE TABLE dflt",
        "SELECT GET_DDL('TABLE', 'dflt')",
        "SHOW DYNAMIC TABLES LIKE 'DT1' ->> SELECT \"name\", \"data_timestamp\" FROM $1",
        "SHOW ROLES LIKE 'SYSADMIN' ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW SCHEMAS LIKE 'INFORMATION_SCHEMA' IN DATABASE d ->> SELECT \"name\", \"created_on\" FROM $1",
        "SHOW DATABASES LIKE 'SNOWFLAKE' ->> SELECT \"name\", \"created_on\" FROM $1",
    };

    private static String answers(final DatabaseEngine engine) {
        final StringBuilder out = new StringBuilder();
        for (final String query : QUERIES) {
            out.append(query).append(" => ").append(answer(engine, query)).append('\n');
        }
        return out.toString();
    }

    private static DatabaseEngine source() {
        final DatabaseEngine source = new DatabaseEngine();
        final String[] script = {
            "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE WAREHOUSE wh",
            "CREATE TABLE ct (r VARCHAR COLLATE 'en-ci')", "INSERT INTO ct VALUES ('EU'), ('eu'), ('US')",
            "CREATE VIEW cv AS SELECT r FROM ct",
            "CREATE MATERIALIZED VIEW mv AS SELECT r FROM ct",
            "CREATE TABLE dflt (a NUMBER DEFAULT 0, b FLOAT DEFAULT 1.5, g NUMBER(10,2) DEFAULT 1.50, k INT,"
                + " s VARCHAR DEFAULT 'x', t BOOLEAN DEFAULT TRUE)",
            "CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = wh AS SELECT r, COUNT(*) AS c FROM ct"
                + " GROUP BY r",
            "INSERT INTO ct VALUES ('FR')",
        };
        for (final String sql : script) {
            final String answered = answer(source, sql);
            assertTrue(!answered.startsWith("REFUSED"), sql + " => " + answered);
        }
        return source;
    }

    /** The copy answers as the source does, and its next refresh inserts the one new group. */
    private static void assertCopyKeepsTheMetadata(final DatabaseEngine source, final DatabaseEngine copy) {
        final String expected = answers(source);
        assertTrue(expected.contains("DESCRIBE VIEW cv => R, VARCHAR(16777216) COLLATE 'en-ci', "), expected);
        assertTrue(expected.contains("DESCRIBE TABLE dflt => A, NUMBER(38,0), COLUMN, Y, 0, "), expected);
        assertEquals(expected, answers(copy));
        final String refreshed = answer(copy, "ALTER DYNAMIC TABLE dt1 REFRESH");
        assertTrue(refreshed.contains("{\"insertedRows\":1,"), refreshed);
        assertEquals("EU, 2 | FR, 1 | US, 1", answer(copy, "SELECT * FROM dt1 ORDER BY 1"));
    }

    @Test
    public void aCloneKeepsTheMetadata() throws Exception {
        final DatabaseEngine source = source();
        final DatabaseEngine clone = source.cloneInstance();
        try {
            assertCopyKeepsTheMetadata(source, clone);
        } finally {
            clone.shutdown();
            source.shutdown();
        }
    }

    @Test
    public void aCheckpointRestoreKeepsTheMetadata() throws Exception {
        final DatabaseEngine source = source();
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        try {
            assertCopyKeepsTheMetadata(source, restored);
        } finally {
            restored.shutdown();
            source.shutdown();
        }
    }

    @Test
    public void aReplayedDirectoryStageRegistersNoFileStagedAfterIt() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        cfg.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, dir.resolve("stages").toString());
        final String directory = "SELECT RELATIVE_PATH FROM DIRECTORY(@d.PUBLIC.sd)";
        final DatabaseEngine first = new DatabaseEngine(cfg);
        try {
            first.execute("CREATE DATABASE d");
            first.execute("CREATE STAGE d.PUBLIC.sd DIRECTORY = (ENABLE = TRUE)");
            first.execute("COPY INTO @d.PUBLIC.sd/out/ FROM (SELECT 1 AS a) FILE_FORMAT = (TYPE = CSV)");
            assertEquals("", answer(first, directory));
            assertTrue(answer(first, "LIST @d.PUBLIC.sd").startsWith("sd/out/data_0_0_0.csv.gz"));
        } finally {
            first.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(cfg);
        try {
            assertEquals("", answer(recovered, directory));
            assertTrue(answer(recovered, "LIST @d.PUBLIC.sd").startsWith("sd/out/data_0_0_0.csv.gz"));
        } finally {
            recovered.shutdown();
        }
    }
}
