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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row id is one row's alone for as long as the engine runs: METADATA$ROW_ID is "a unique, immutable row ID"
 * (Snowflake's documentation). Ids come from one sequence for the whole engine, so a table recreated after a drop
 * of it, of its schema or of its database never hands a stream an id the old table's rows held, a rename, a move
 * and a swap leave every row its id, and the drop hooks of the stream manager never disturb a live table's rows.
 *
 * <p>Deliberately NOT on the live surface: it reaches the stream manager's hooks through the engine, and runs
 * its own embedded engine.
 */
public class RowIdsAreNeverReusedTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The row ids a stream reads, in the order of the given column. */
    private List<String> ids(final String stream, final String orderBy) {
        final List<String> ids = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT METADATA$ROW_ID FROM " + stream + " ORDER BY " + orderBy)
                .getRows()) {
            ids.add(String.valueOf(row.getValue(0)));
        }
        return ids;
    }

    /** Creates table {@code t} with a stream {@code s}, inserts two rows and returns their ids. */
    private List<String> twoRows() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO t VALUES (2)");
        final List<String> ids = ids("s", "id");
        assertEquals(2, new HashSet<>(ids).size(), "two rows, two ids");
        return ids;
    }

    private void assertNoneOf(final List<String> earlier, final List<String> later) {
        final Set<String> held = new HashSet<>(earlier);
        for (final String id : later) {
            assertTrue(!held.contains(id), "an id the old table's rows held was handed out again: " + id);
        }
    }

    @Test
    public void aTableRecreatedAfterADropHandsOutNewIds() {
        final List<String> first = twoRows();
        engine.execute("DROP TABLE t");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s2 ON TABLE t");
        engine.execute("INSERT INTO t VALUES (3), (4)");
        assertNoneOf(first, ids("s2", "id"));
    }

    @Test
    public void aTableRecreatedAfterItsSchemaIsDroppedHandsOutNewIds() {
        final List<String> first = twoRows();
        engine.getStreamManager().onSchemaDropped("TEST_DB", "TEST_SCHEMA");
        engine.execute("INSERT INTO t VALUES (3)");
        final List<String> grown = ids("s", "id");
        assertEquals(first, grown.subList(0, 2), "the hook leaves a live table's rows their ids");
        assertEquals(3, new HashSet<>(grown).size());

        engine.execute("DROP SCHEMA test_schema");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (4)");
        assertNoneOf(grown, ids("s", "id"));
    }

    @Test
    public void aTableRecreatedAfterItsDatabaseIsDroppedHandsOutNewIds() {
        final List<String> first = twoRows();
        engine.getStreamManager().onDatabaseDropped("TEST_DB");
        engine.execute("INSERT INTO t VALUES (3)");
        final List<String> grown = ids("s", "id");
        assertEquals(first, grown.subList(0, 2), "the hook leaves a live table's rows their ids");

        engine.execute("DROP DATABASE test_db");
        engine.execute("CREATE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (4)");
        assertNoneOf(grown, ids("s", "id"));
    }

    @Test
    public void aRenameAMoveAndASwapLeaveEveryRowItsId() {
        final List<String> first = twoRows();
        engine.execute("CREATE TABLE consumed AS SELECT id FROM s");
        engine.execute("ALTER TABLE t RENAME TO t2");
        engine.execute("UPDATE t2 SET id = 10 WHERE id = 1");
        assertEquals("[" + first.get(0) + ", " + first.get(0) + "]", ids("s", "METADATA$ACTION").toString(),
            "the renamed table's row is updated under the id it was inserted with");

        engine.execute("CREATE TABLE other (id INT)");
        engine.execute("CREATE STREAM so ON TABLE other");
        engine.execute("INSERT INTO other VALUES (7)");
        final List<String> others = ids("so", "id");
        assertNoneOf(first, others);
        engine.execute("ALTER TABLE t2 SWAP WITH other");
        engine.execute("INSERT INTO other VALUES (8)");
        engine.execute("INSERT INTO t2 VALUES (9)");
        final Set<String> all = new HashSet<>(first);
        all.addAll(others);
        final List<String> afterSwap = new ArrayList<>();
        afterSwap.addAll(ids("s", "id"));
        afterSwap.addAll(ids("so", "id"));
        final Set<String> fresh = new HashSet<>(afterSwap);
        fresh.removeAll(all);
        assertEquals(2, fresh.size(), "the two rows written after the swap took two ids no row held");
    }
}
