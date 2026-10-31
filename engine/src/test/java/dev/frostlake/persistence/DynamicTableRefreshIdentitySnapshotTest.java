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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream on a dynamic table keeps the identities of the rows it last saw across an engine clone and a checkpoint
 * restore, so a refresh after the copy still reads an updated row as one update: a DELETE and an INSERT with
 * METADATA$ISUPDATE TRUE.
 */
public class DynamicTableRefreshIdentitySnapshotTest {

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
        dir = Files.createTempDirectory("dynamic_table_identity_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        copies.add(restored);
        return copies;
    }

    private static String changes(final DatabaseEngine engine) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT a, METADATA$ACTION, METADATA$ISUPDATE FROM dti.s.sd "
                + "ORDER BY 1, 2").getRows()) {
            text.append(text.length() > 0 ? " ;; " : "").append(row.getValue(0)).append('|').append(row.getValue(1))
                .append('|').append(row.getValue(2));
        }
        return text.toString();
    }

    @Test
    public void aStreamKeepsItsRowsIdentitiesAcrossACopy() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE dti");
        original.execute("CREATE SCHEMA dti.s");
        original.execute("USE SCHEMA dti.s");
        original.execute("CREATE TABLE b (a INT)");
        original.execute("INSERT INTO b VALUES (1), (2), (3)");
        original.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 hour' WAREHOUSE = compute_wh AS SELECT a FROM b");
        original.execute("CREATE STREAM sd ON DYNAMIC TABLE dt");
        for (final DatabaseEngine copy : copiesOf(original)) {
            copy.execute("USE SCHEMA dti.s");
            copy.execute("UPDATE b SET a = 10 WHERE a = 1");
            copy.execute("ALTER DYNAMIC TABLE dt REFRESH");
            assertEquals("1|DELETE|true ;; 10|INSERT|true", changes(copy));
        }
    }
}
