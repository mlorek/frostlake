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

/**
 * A dynamic table's refresh history goes with a snapshot of the engine's state, and so does where each of its rows
 * lies: after a checkpoint and a restart DYNAMIC_TABLE_REFRESH_HISTORY lists the refreshes the former engine ran, and
 * a refresh that deletes the rows one partition held copies none of the others, as it would have before.
 */
public class DynamicTableRefreshHistoryRestartTest {

    private static final String HISTORY = "SELECT NAME, REFRESH_TRIGGER, REFRESH_ACTION, INPUTS_WITH_CHANGED_DATA "
        + "FROM TABLE(d.INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY(NAME_PREFIX => 'D.PUBLIC.DT')) "
        + "ORDER BY REFRESH_START_TIME";

    private Path dir;

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("dt_refresh_history_");
    }

    @AfterEach
    public void tearDown() {
        deleteRecursively(dir.toFile());
    }

    /** Remove a test's data directory whole: the engine keeps its stages in a directory inside it. */
    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
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

    private static String input(final int added, final int registered, final int removed, final int unregistered) {
        return "[{\"kind\":\"TABLE\",\"name\":\"D.PUBLIC.S\",\"statistics\":{\"numAddedPartitions\":" + added
            + ",\"numRegisteredRows\":" + registered + ",\"numRemovedPartitions\":" + removed
            + ",\"numUnregisteredRows\":" + unregistered + "}}]";
    }

    @Test
    public void aRestartedEngineListsTheRefreshesAndKeepsWhereTheRowsLie() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        final String ran;
        try {
            engine.execute("CREATE DATABASE d");
            engine.execute("USE SCHEMA d.PUBLIC");
            engine.execute("CREATE TABLE s (a INT)");
            engine.execute("INSERT INTO s VALUES (1)");
            final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
            engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 hour' WAREHOUSE = "
                + (wh == null ? "COMPUTE_WH" : String.valueOf(wh)) + " AS SELECT a FROM s");
            engine.execute("INSERT INTO s VALUES (2)");
            engine.execute("ALTER DYNAMIC TABLE dt REFRESH");
            ran = answer(engine, HISTORY);
            assertEquals("DT, CREATION, INCREMENTAL, " + input(1, 1, 0, 0) + " | DT, MANUAL, INCREMENTAL, "
                + input(1, 2, 1, 1), ran);
            engine.checkpoint();
        } finally {
            engine.shutdown();
        }
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertEquals(ran, answer(recovered, HISTORY));
            recovered.execute("DELETE FROM d.PUBLIC.s WHERE a = 1");
            // The creation's partition held the one row deleted: the refresh copies none and adds no partition.
            assertEquals("{\"insertedRows\":0,\"copiedRows\":0,\"deletedRows\":1}",
                answer(recovered, "ALTER DYNAMIC TABLE d.PUBLIC.dt REFRESH ->> SELECT \"statistics\" FROM $1"));
            assertEquals("0, 1", answer(recovered, "SELECT STATISTICS:numAddedPartitions, "
                + "STATISTICS:numRemovedPartitions FROM TABLE(d.INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY("
                + "NAME_PREFIX => 'D.PUBLIC.DT')) ORDER BY REFRESH_START_TIME DESC LIMIT 1"));
        } finally {
            recovered.shutdown();
        }
    }
}
