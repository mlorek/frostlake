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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ALTER TABLE ADD COLUMN must backfill existing rows so their stored width matches the widened schema —
 * otherwise reading the new column throws IndexOutOfBounds. Per Snowflake, existing rows are populated
 * with the column's DEFAULT literal when present, otherwise NULL.
 */
public class AlterTableAddColumnBackfillTest extends BaseDatabaseTest {

    @Test
    public void addColumnNoDefaultBackfillsExistingRowsWithNull() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("INSERT INTO t VALUES (5)");
        engine.execute("ALTER TABLE t ADD COLUMN a2 NUMBER");

        // Reading the new column on a pre-existing row must not throw and must be NULL.
        final ResultSet rs = engine.executeQuery("SELECT a1, a2 FROM t");
        final Row row = rs.getRows().get(0);
        assertEquals(5L, ((Number) row.getValue(0)).longValue());
        assertNull(row.getValue(1));
    }

    @Test
    public void selectStarReflectsNewColumnWidth() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("INSERT INTO t VALUES (5)");
        engine.execute("ALTER TABLE t ADD COLUMN a2 NUMBER");

        final ResultSet rs = engine.executeQuery("SELECT * FROM t");
        assertEquals(2, rs.getColumns().size());
        assertEquals(2, rs.getRows().get(0).getValues().size());
    }

    @Test
    public void addColumnWithDefaultBackfillsExistingRows() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("INSERT INTO t VALUES (5)");
        engine.execute("ALTER TABLE t ADD COLUMN a2 NUMBER DEFAULT 7");

        final ResultSet rs = engine.executeQuery("SELECT a2 FROM t");
        assertEquals(7L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void rowsInsertedAfterAddColumnStillWork() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("INSERT INTO t VALUES (5)");
        engine.execute("ALTER TABLE t ADD COLUMN a2 NUMBER");
        engine.execute("INSERT INTO t VALUES (6, 60)");

        final ResultSet rs = engine.executeQuery("SELECT a1, a2 FROM t ORDER BY a1");
        final List<Row> rows = rs.getRows();
        assertEquals(2, rows.size());
        assertNull(rows.get(0).getValue(1));                       // pre-existing row backfilled to NULL
        assertEquals(60L, ((Number) rows.get(1).getValue(1)).longValue());  // new row keeps its value
    }

    @Test
    public void addColumnWithDefaultFeedsAggregateOverAllRows() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("ALTER TABLE t ADD COLUMN w NUMBER DEFAULT 10");

        // All three backfilled rows contribute the default → SUM = 30.
        final ResultSet rs = engine.executeQuery("SELECT SUM(w) FROM t");
        assertEquals(30.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 1e-9);
    }

    @Test
    public void addColumnToEmptyTableStillWorks() {
        engine.execute("CREATE TABLE t (a1 NUMBER)");
        engine.execute("ALTER TABLE t ADD COLUMN a2 NUMBER");
        engine.execute("INSERT INTO t VALUES (1, 2)");

        final ResultSet rs = engine.executeQuery("SELECT a1, a2 FROM t");
        final Row row = rs.getRows().get(0);
        assertEquals(1L, ((Number) row.getValue(0)).longValue());
        assertEquals(2L, ((Number) row.getValue(1)).longValue());
    }
}
