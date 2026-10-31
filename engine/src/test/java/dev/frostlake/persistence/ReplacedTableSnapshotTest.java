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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table CREATE OR REPLACE replaced while another session's open transaction had written it keeps, across an engine
 * clone and a checkpoint, what that transaction committed into it after the replace: UNDROP brings the version back
 * with the commit, as it does on the engine the replace ran on.
 */
public class ReplacedTableSnapshotTest {

    private final List<Path> dirs = new ArrayList<>();
    private final List<DatabaseEngine> engines = new ArrayList<>();
    private ConcurrentDatabaseEngine concurrent;

    @AfterEach
    public void cleanup() {
        if (concurrent != null) {
            concurrent.shutdown();
        }
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
        for (final Path dir : dirs) {
            deleteRecursively(dir.toFile());
        }
    }

    @Test
    public void aReplacedTableKeepsTheChangeCommittedIntoItAcrossACloneAndARestore() throws Exception {
        concurrent = new ConcurrentDatabaseEngine();
        final SessionContext writer = concurrent.createSession();
        final SessionContext replacer = concurrent.createSession();
        concurrent.executeQuery("CREATE DATABASE d", writer);
        concurrent.executeQuery("USE SCHEMA d.PUBLIC", writer);
        concurrent.executeQuery("USE SCHEMA d.PUBLIC", replacer);
        concurrent.executeQuery("CREATE TABLE rt (id INT, v INT)", writer);
        concurrent.executeQuery("INSERT INTO rt VALUES (1, 1)", writer);
        concurrent.executeQuery("BEGIN", writer);
        concurrent.executeQuery("INSERT INTO rt VALUES (9, 9)", writer);
        concurrent.executeQuery("CREATE OR REPLACE TABLE rt (id INT, v INT)", replacer);
        concurrent.executeQuery("COMMIT", writer);

        final DatabaseEngine original = concurrent.getEngine();
        final List<DatabaseEngine> copies = new ArrayList<>();
        copies.add(track(original.cloneInstance()));
        final Path dir = Files.createTempDirectory("replaced_table_");
        dirs.add(dir);
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(dir);
        copies.add(restored);
        for (final DatabaseEngine copy : copies) {
            copy.execute("USE SCHEMA d.PUBLIC");
            copy.execute("ALTER TABLE rt RENAME TO rt_new");
            copy.execute("UNDROP TABLE rt");
            assertEquals(List.of("1|1", "9|9"), rows(copy, "SELECT id, v FROM rt ORDER BY id"));
        }
    }

    private DatabaseEngine track(final DatabaseEngine engine) {
        engines.add(engine);
        return engine;
    }

    private static List<String> rows(final DatabaseEngine engine, final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(rs.getValue(0) + "|" + rs.getValue(1));
        }
        return out;
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
