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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CREATE TABLE … AS SELECT accepts an explicit column-name list that renames the query's output columns:
 * {@code CREATE TABLE t (name, total) AS SELECT …}. Previously the column list before AS failed to parse.
 */
public class CtasColumnListTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE src (name VARCHAR, amount1 NUMBER, amount2 NUMBER)");
        engine.execute("INSERT INTO src VALUES ('a', 10, 5), ('b', 20, 7)");
    }

    @Test
    public void ctasRenamesColumnsViaList() {
        engine.execute(
            "CREATE TABLE t (nm, summary_amount) AS SELECT name, amount1 + amount2 FROM src ORDER BY name");
        final ResultSet rs = engine.executeQuery("SELECT nm, summary_amount FROM t ORDER BY nm");
        assertEquals(2, rs.getRows().size());
        assertEquals("a", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals(15.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 1e-9);
        assertEquals(27.0, ((Number) rs.getRows().get(1).getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void ctasColumnNamesReflectedInSchema() {
        engine.execute("CREATE TABLE t2 (x, y) AS SELECT name, amount1 FROM src");
        final ResultSet rs = engine.executeQuery("SELECT * FROM t2");
        assertEquals("X", rs.getColumns().get(0).getName().toUpperCase());
        assertEquals("Y", rs.getColumns().get(1).getName().toUpperCase());
    }

    @Test
    public void ctasWithoutColumnListStillWorks() {
        engine.execute("CREATE TABLE t3 AS SELECT name, amount1 FROM src");
        final ResultSet rs = engine.executeQuery("SELECT name, amount1 FROM t3");
        assertEquals(2, rs.getRows().size());
    }
}
