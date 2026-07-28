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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Writing into an ARRAY column follows TO_ARRAY semantics: an existing array passes through
 * unchanged, any other non-null variant value is wrapped in a one-element array, so the fixture
 * idiom {@code INSERT ... SELECT PARSE_JSON('{...}')} reads back as {@code col[0]}.
 */
public class ArrayColumnWriteCoercionTest extends BaseDatabaseTest {

    @Test
    public void testObjectWrappedIntoOneElementArray() {
        engine.execute("CREATE TABLE trend1 (id NUMBER, arr ARRAY)");
        engine.execute("INSERT INTO trend1 SELECT 1, PARSE_JSON('{\"severity\": 4, \"n\": 100}')");
        final ResultSet rs = engine.executeQuery(
            "SELECT ARRAY_SIZE(arr), arr[0]:severity::NUMBER, arr[0]:n::NUMBER FROM trend1");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(100L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
    }

    @Test
    public void testExistingArrayUnchanged() {
        engine.execute("CREATE TABLE trend2 (arr ARRAY)");
        engine.execute("INSERT INTO trend2 SELECT ARRAY_CONSTRUCT(1, 2, 3)");
        engine.execute("INSERT INTO trend2 SELECT PARSE_JSON('[\"a\", \"b\"]')");
        final ResultSet rs = engine.executeQuery("SELECT ARRAY_SIZE(arr) FROM trend2 ORDER BY 1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testScalarWrapped() {
        engine.execute("CREATE TABLE trend3 (arr ARRAY)");
        engine.execute("INSERT INTO trend3 SELECT TO_VARIANT(7)");
        final ResultSet rs = engine.executeQuery("SELECT ARRAY_SIZE(arr), arr[0]::NUMBER FROM trend3");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(7L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testNullPassesThrough() {
        engine.execute("CREATE TABLE trend4 (arr ARRAY)");
        engine.execute("INSERT INTO trend4 SELECT NULL");
        assertNull(engine.executeQuery("SELECT arr FROM trend4").getRows().get(0).getValue(0));
    }
}
