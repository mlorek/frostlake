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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A dynamic table's refresh state comes back from a checkpoint as it was: a refresh after the restart that finds
 * nothing changed tells No new data, and a table that never refreshed is as uninitialized as before.
 */
public class DynamicTableRefreshAcrossRestartTest {

    @TempDir
    Path dir;

    private static String statistics(final DatabaseEngine engine, final String table) {
        final Row row = engine.executeQuery("ALTER DYNAMIC TABLE d.PUBLIC." + table + " REFRESH").getRows().get(0);
        return String.valueOf(row.getValue(1));
    }

    @Test
    public void aRestoredTableRefreshesFromWhereItWas() throws Exception {
        final DatabaseEngine source = new DatabaseEngine();
        source.executeQuery("CREATE DATABASE d");
        source.executeQuery("CREATE TABLE d.PUBLIC.src (a INT)");
        source.executeQuery("INSERT INTO d.PUBLIC.src VALUES (1), (2)");
        source.executeQuery("CREATE DYNAMIC TABLE d.PUBLIC.dt TARGET_LAG = '1 hour' WAREHOUSE = COMPUTE_WH"
            + " AS SELECT a FROM d.PUBLIC.src");
        source.executeQuery("CREATE DYNAMIC TABLE d.PUBLIC.dt_later TARGET_LAG = '1 hour' WAREHOUSE = COMPUTE_WH"
            + " INITIALIZE = ON_SCHEDULE AS SELECT a FROM d.PUBLIC.src");
        source.checkpointStateTo(dir.resolve("state"));
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir.resolve("state"));
        try {
            assertEquals("No new data", statistics(restored, "dt"));
            final String refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    restored.executeQuery("SELECT * FROM d.PUBLIC.dt_later");
                }
            }).getMessage();
            assertEquals("Dynamic Table 'D.PUBLIC.DT_LATER' is not initialized. Please run a manual refresh or wait"
                + " for a scheduled refresh before querying.", refused);
            assertEquals("{\"insertedRows\":2,\"copiedRows\":0,\"deletedRows\":0}", statistics(restored, "dt_later"));
        } finally {
            restored.shutdown();
            source.shutdown();
        }
    }
}
