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
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A permanent table hidden beneath a temporary table of the same name survives every copy of the engine's state with
 * its own rows - an in-memory engine clone and an on-disk checkpoint alike - uncovered there, since a copy has none of
 * the source's sessions and so none of their temporary tables, while the source keeps it hidden until the end of the
 * session uncovers it.
 */
public class HiddenTableSnapshotTest {

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    @Test
    public void everyCopyOfTheStateUncoversTheTable() throws Exception {
        final DatabaseEngine original = shadowingEngine();

        final DatabaseEngine twin = original.cloneInstance();
        assertUncovered(twin);

        dir = Files.createTempDirectory("hidden_table_snapshot_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        assertUncovered(restored);
        assertShadowed(original);
    }

    @Test
    public void theEndOfTheSessionUncoversTheTable() {
        final DatabaseEngine engine = shadowingEngine();
        engine.shutdown();
        assertEquals("1 | 2", column(engine, "SELECT * FROM hidden_db.s.kt ORDER BY 1"));
    }

    /** An engine where a temporary table KT holding 3 hides a permanent KT holding 1 and 2. */
    private static DatabaseEngine shadowingEngine() {
        final DatabaseEngine engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE hidden_db");
        engine.execute("CREATE SCHEMA hidden_db.s");
        engine.execute("CREATE TABLE hidden_db.s.kt (a INT)");
        engine.execute("INSERT INTO hidden_db.s.kt VALUES (1), (2)");
        engine.execute("CREATE TEMPORARY TABLE hidden_db.s.kt (b INT)");
        engine.execute("INSERT INTO hidden_db.s.kt VALUES (3)");
        return engine;
    }

    private static void assertUncovered(final DatabaseEngine engine) {
        assertEquals("1 | 2", column(engine, "SELECT * FROM hidden_db.s.kt ORDER BY 1"));
        assertEquals("BASE TABLE", column(engine, "SELECT table_type FROM hidden_db.information_schema.tables"
            + " WHERE table_schema = 'S' ORDER BY 1"));
    }

    private static void assertShadowed(final DatabaseEngine engine) {
        assertEquals("3", column(engine, "SELECT * FROM hidden_db.s.kt"));
        assertEquals("BASE TABLE | LOCAL TEMPORARY", column(engine, "SELECT table_type FROM hidden_db.information_schema.tables"
            + " WHERE table_schema = 'S' ORDER BY 1"));
        engine.execute("DROP TABLE hidden_db.s.kt");
        assertEquals("1 | 2", column(engine, "SELECT * FROM hidden_db.s.kt ORDER BY 1"));
    }

    private static String column(final DatabaseEngine engine, final String sql) {
        final StringBuilder cells = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            cells.append(cells.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return cells.toString();
    }

    private static void deleteRecursively(final File f) {
        final File[] children = f.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
