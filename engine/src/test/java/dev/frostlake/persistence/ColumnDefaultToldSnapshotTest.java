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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An expression DEFAULT keeps the text the account tells it back as across an engine clone and a checkpoint
 * restore, while INSERT still evaluates the default as written.
 */
public class ColumnDefaultToldSnapshotTest {

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
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

    /** An engine clone and a checkpoint restore of the original. */
    private List<DatabaseEngine> copiesOf(final DatabaseEngine original) throws Exception {
        final List<DatabaseEngine> copies = new ArrayList<>();
        copies.add(original.cloneInstance());
        dir = Files.createTempDirectory("column_default_told_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        copies.add(restored);
        return copies;
    }

    /** DESCRIBE's default cell of each column, in order. */
    private static List<String> defaults(final DatabaseEngine engine, final String table) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE " + table);
        final List<String> cells = new ArrayList<>();
        for (final Row row : described.getRows()) {
            cells.add(String.valueOf(row.getValue(4)));
        }
        return cells;
    }

    @Test
    public void theToldDefaultSurvivesACopy() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE cdt");
        original.execute("CREATE SCHEMA cdt.s");
        original.execute("CREATE TABLE cdt.s.d (c DATE DEFAULT '2020-01-01'::DATE, n INT DEFAULT 1 + 2 * 3, k INT)");
        final List<String> told = new ArrayList<>();
        told.add("CAST('2020-01-01' AS DATE)");
        told.add("1 + (2 * 3)");
        told.add("null");
        assertEquals(told, defaults(original, "cdt.s.d"));
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(told, defaults(copy, "cdt.s.d"));
            copy.execute("INSERT INTO cdt.s.d (k) VALUES (1)");
            final Row inserted = copy.executeQuery("SELECT TO_VARCHAR(c), n FROM cdt.s.d").getRows().get(0);
            assertEquals("2020-01-01", String.valueOf(inserted.getValue(0)));
            assertEquals("7", String.valueOf(inserted.getValue(1)));
        }
    }
}
