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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files of an engine's internal stages are part of the state a copy of it carries: an engine clone and a
 * checkpoint's restore list and load the files of its named, table and user stages, and UNDROP SCHEMA brings back a
 * dropped schema's stage with its files, as in the engine they copy. Each copy keeps files of its own.
 */
public class EngineCopyStageFilesTest {

    private Path dir;
    private Path csv;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("engine_copy_stages_");
        csv = dir.resolve("staged.csv");
        Files.write(csv, "id,name\n1,a\n2,b\n".getBytes(StandardCharsets.UTF_8));
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

    private static String answers(final DatabaseEngine engine) {
        final String[] queries = {
            "USE SCHEMA d.PUBLIC",
            "LIST @stp",
            "LIST @%tstage",
            "LIST @~/u",
            "UNDROP SCHEMA d.s2",
            "LIST @d.s2.sdrop",
            "COPY INTO tstage FROM @stp FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)",
            "SELECT COUNT(*) FROM tstage",
        };
        final StringBuilder out = new StringBuilder();
        for (final String query : queries) {
            out.append(query).append(" => ").append(answer(engine, query)).append('\n');
        }
        return out.toString();
    }

    /** Stages a file in a named, a table and a user stage, and in a named stage of a schema it then drops. */
    private DatabaseEngine stagedSource() {
        final DatabaseEngine source = new DatabaseEngine();
        final String file = "'file://" + csv + "'";
        source.execute("CREATE DATABASE d");
        source.execute("USE SCHEMA d.PUBLIC");
        source.execute("CREATE STAGE stp");
        source.execute("PUT " + file + " @stp AUTO_COMPRESS = FALSE");
        source.execute("CREATE TABLE tstage (id INT, name VARCHAR)");
        source.execute("PUT " + file + " @%tstage AUTO_COMPRESS = FALSE");
        source.execute("PUT " + file + " @~/u AUTO_COMPRESS = FALSE");
        source.execute("CREATE SCHEMA d.s2");
        source.execute("CREATE STAGE d.s2.sdrop");
        source.execute("PUT " + file + " @d.s2.sdrop AUTO_COMPRESS = FALSE");
        source.execute("DROP SCHEMA d.s2");
        source.execute("USE SCHEMA d.PUBLIC");
        return source;
    }

    /** The copy answers as the source does, and a file staged in it stays out of the source. */
    private void assertCopyHoldsTheFiles(final DatabaseEngine source, final DatabaseEngine copy) throws IOException {
        final String expected = answers(source);
        assertTrue(expected.contains("LIST @stp => stp/staged.csv, 32, "), expected);
        assertTrue(expected.contains("LIST @%tstage => staged.csv, 32, "), expected);
        assertTrue(expected.contains("LIST @~/u => u/staged.csv, 32, "), expected);
        assertTrue(expected.contains("LIST @d.s2.sdrop => sdrop/staged.csv, 32, "), expected);
        assertTrue(expected.contains("SELECT COUNT(*) FROM tstage => 2\n"), expected);
        assertEquals(expected, answers(copy));
        final Path other = dir.resolve("other.csv");
        Files.write(other, "id,name\n3,c\n".getBytes(StandardCharsets.UTF_8));
        copy.execute("PUT 'file://" + other + "' @stp AUTO_COMPRESS = FALSE");
        assertTrue(answer(copy, "LIST @stp").contains("stp/other.csv"), answer(copy, "LIST @stp"));
        assertTrue(!answer(source, "LIST @stp").contains("other.csv"), answer(source, "LIST @stp"));
    }

    @Test
    public void aCloneHoldsTheFilesOfTheStagesItCopies() throws Exception {
        final DatabaseEngine source = stagedSource();
        final DatabaseEngine clone = source.cloneInstance();
        try {
            assertCopyHoldsTheFiles(source, clone);
        } finally {
            clone.shutdown();
            source.shutdown();
        }
    }

    @Test
    public void aCheckpointRestoreHoldsTheFilesOfTheStagesItCopies() throws Exception {
        final DatabaseEngine source = stagedSource();
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        try {
            assertCopyHoldsTheFiles(source, restored);
        } finally {
            restored.shutdown();
            source.shutdown();
        }
    }
}
