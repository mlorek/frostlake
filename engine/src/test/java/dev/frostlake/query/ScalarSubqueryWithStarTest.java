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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScalarSubqueryWithStarTest extends BaseDatabaseTest {

    @Test
    public void testScalarSubqueryWithStarFromDerivedTable() {
        ResultSet result = engine.executeQuery("select (select 1), * from (select 2 as c) as t");

        assertEquals(2, result.getColumnCount(), "Should have 2 columns");
        assertEquals(1, result.getRowCount(), "Should have 1 row");
        assertEquals(1, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals(2, ((Number) result.getRows().get(0).getValue(1)).intValue());
    }

    @Test
    public void testScalarSubqueryWithStarFromTable() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");

        ResultSet result = engine.executeQuery("select (select 100), * from test_table");

        assertEquals(3, result.getColumnCount(), "Should have 3 columns");
        assertEquals(1, result.getRowCount(), "Should have 1 row");
        assertEquals(100, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals(1, ((Number) result.getRows().get(0).getValue(1)).intValue());
        assertEquals("Alice", result.getRows().get(0).getValue(2));
    }

    @Test
    public void testMultipleScalarSubqueriesWithStar() {
        engine.execute("CREATE TABLE test_table (val INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (42)");

        ResultSet result = engine.executeQuery("select (select 1), (select 2), * from test_table");

        assertEquals(3, result.getColumnCount(), "Should have 3 columns");
        assertEquals(1, result.getRowCount(), "Should have 1 row");
        assertEquals(1, ((Number) result.getRows().get(0).getValue(0)).intValue());
        assertEquals(2, ((Number) result.getRows().get(0).getValue(1)).intValue());
        assertEquals(42, ((Number) result.getRows().get(0).getValue(2)).intValue());
    }
}
