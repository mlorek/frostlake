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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DML statements report their affected-row count Snowflake-style: a single-row result set whose
 * column is "number of rows inserted" / "updated" / "deleted" (MERGE reports one column per
 * action). {@code DatabaseEngine.executeUpdate} surfaces the count directly. Previously every
 * count was hardcoded 0 (the engine computed the number, then discarded it).
 */
public class AffectedRowCountTest extends BaseDatabaseTest {

    private void seed() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c')");
    }

    @Test
    public void insertValuesReportsCount() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR)");
        assertEquals(3, engine.executeUpdate("INSERT INTO t VALUES (1,'a'), (2,'b'), (3,'c')"));
    }

    @Test
    public void insertResultSetIsSnowflakeShaped() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        final ResultSet rs = engine.executeQuery("INSERT INTO t VALUES (1), (2)");
        assertEquals(1, rs.getRowCount());
        assertEquals("number of rows inserted", rs.getColumns().get(0).getName());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void insertSelectReportsCount() {
        seed();
        engine.execute("CREATE TABLE t2 (id INTEGER, v VARCHAR)");
        assertEquals(3, engine.executeUpdate("INSERT INTO t2 SELECT id, v FROM t"));
    }

    @Test
    public void updateReportsCount() {
        seed();
        assertEquals(2, engine.executeUpdate("UPDATE t SET v = 'x' WHERE id <= 2"));
    }

    @Test
    public void updateFromReportsCount() {
        seed();
        engine.execute("CREATE TABLE src (id INTEGER, nv VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'n1'), (2, 'n2')");
        assertEquals(2, engine.executeUpdate(
            "UPDATE t SET v = src.nv FROM src WHERE t.id = src.id"));
    }

    @Test
    public void deleteReportsCount() {
        seed();
        assertEquals(1, engine.executeUpdate("DELETE FROM t WHERE id = 2"));
    }

    @Test
    public void deleteUsingReportsCount() {
        seed();
        engine.execute("CREATE TABLE gone (id INTEGER)");
        engine.execute("INSERT INTO gone VALUES (1), (3)");
        assertEquals(2, engine.executeUpdate(
            "DELETE FROM t USING gone WHERE t.id = gone.id"));
    }

    @Test
    public void mergeReportsPerActionCounts() {
        seed();
        engine.execute("CREATE TABLE src (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'updated'), (9, 'inserted')");
        final ResultSet rs = engine.executeQuery(
            "MERGE INTO t USING src ON t.id = src.id"
            + " WHEN MATCHED THEN UPDATE SET v = src.v"
            + " WHEN NOT MATCHED THEN INSERT VALUES (src.id, src.v)");
        assertEquals(1, rs.getRowCount());
        assertEquals("number of rows inserted", rs.getColumns().get(0).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("number of rows updated", rs.getColumns().get(1).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void mergeDeleteReportsDeletedCount() {
        seed();
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("INSERT INTO src VALUES (2)");
        final ResultSet rs = engine.executeQuery(
            "MERGE INTO t USING src ON t.id = src.id WHEN MATCHED THEN DELETE");
        assertEquals("number of rows deleted", rs.getColumns().get(2).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
    }

    @Test
    public void ddlReportsZero() {
        assertEquals(0, engine.executeUpdate("CREATE TABLE ddl_only (id INTEGER)"));
    }

    @Test
    public void updateMatchingNothingReportsZero() {
        seed();
        assertEquals(0, engine.executeUpdate("UPDATE t SET v = 'x' WHERE id = 999"));
    }
}
