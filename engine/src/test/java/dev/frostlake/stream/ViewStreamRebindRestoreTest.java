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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A view stream re-bound to the table that replaced its view's own, while it kept the changes it had not read, is
 * listed on the new table after a checkpoint's restore and a clone as it was before: the listing is part of the
 * stream's state.
 */
public class ViewStreamRebindRestoreTest extends BaseDatabaseTest {

    private final List<DatabaseEngine> copies = new ArrayList<>();
    private Path dir;

    @AfterEach
    public void dropTheCopies() {
        for (final DatabaseEngine copy : copies) {
            copy.shutdown();
        }
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    /** The streams of PQ as SHOW STREAMS lists them: name, base tables, stale. */
    private static String listing(final DatabaseEngine on) {
        on.execute("SHOW STREAMS IN SCHEMA test_db.pq");
        final ResultSet rs = on.executeQuery("SELECT \"name\" || '|' || \"base_tables\" || '|' || \"stale\" FROM "
            + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getRows().size(); i++) {
            out.append(i == 0 ? "" : " ;; ").append(rs.getRows().get(i).getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aStreamThatKeptItsChangesIsListedOnTheNewTableAfterARestore() throws Exception {
        Assumptions.assumeFalse(isLiveSnowflake(), "a restore is the engine's own");
        engine.execute("CREATE OR REPLACE SCHEMA pq");
        engine.execute("CREATE TABLE pq.raw2 (id INT)");
        engine.execute("CREATE VIEW pq.v2 AS SELECT id FROM pq.raw2");
        engine.execute("CREATE STREAM pq.s3 ON VIEW pq.v2");
        engine.execute("INSERT INTO pq.raw2 VALUES (1)");
        engine.execute("DROP TABLE pq.raw2");
        engine.execute("CREATE TABLE pq.raw2 (id INT)");
        engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('pq.s3')");
        assertEquals("S3|TEST_DB.PQ.RAW2|false", listing(engine));
        final DatabaseEngine clone = engine.cloneInstance();
        copies.add(clone);
        dir = Files.createTempDirectory("rebind_restore_");
        engine.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        copies.add(restored);
        restored.restoreStateFrom(dir);
        assertEquals("S3|TEST_DB.PQ.RAW2|false", listing(clone));
        assertEquals("S3|TEST_DB.PQ.RAW2|false", listing(restored));
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
