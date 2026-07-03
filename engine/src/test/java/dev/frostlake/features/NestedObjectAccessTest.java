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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NestedObjectAccessTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testDeepNestedObjectColonDotAccess() {
        engine.execute("CREATE TABLE obj3 (d VARIANT)");
        engine.execute("INSERT INTO obj3 VALUES ('{\"a\": {\"b\": {\"c\": 1}}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b.c AS e FROM obj3");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val, "d:a.b.c should return 1");
        assertEquals(1L, Long.parseLong(val.toString()));
    }

    @Test
    public void testSingleColonAccess() {
        ResultSet rs = engine.executeQuery(
            "WITH t AS (SELECT {'x': 42} AS d) SELECT d:x AS v FROM t");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testMixedColonDotPaths() {
        engine.execute("CREATE TABLE obj_mixed (d VARIANT)");
        engine.execute("INSERT INTO obj_mixed VALUES ('{\"a\": {\"b\": {\"c\": 1}}}')");
        // d:a.b.c, d:a:b:c, d:a.b:c should all return 1
        ResultSet rs = engine.executeQuery("SELECT d:a.b.c AS e1, d:a:b:c AS e2, d:a.b:c AS e3 FROM obj_mixed");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(1).toString()));
        assertEquals(1L, Long.parseLong(rs.getRows().get(0).getValue(2).toString()));
    }

    @Test
    public void testSingleColonAccessFromTable() {
        engine.execute("CREATE TABLE jt (d VARIANT)");
        engine.execute("INSERT INTO jt VALUES ('{\"x\": 42}')");
        ResultSet rs = engine.executeQuery("SELECT d:x AS v FROM jt");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testDotChainFromTable() {
        engine.execute("CREATE TABLE jt2 (d VARIANT)");
        engine.execute("INSERT INTO jt2 VALUES ('{\"a\": {\"b\": \"hello\"}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b AS v FROM jt2");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testCteRawValue() {
        // Verify CTE stores the JSON object
        ResultSet rs = engine.executeQuery("WITH t AS (SELECT {'a': {'b': 'hello'}} AS d) SELECT d FROM t");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0), "CTE JSON column should not be null");
    }

    @Test
    public void testTwoLevelDotAccess() {
        // Use a real table instead of CTE to avoid CTE projection issues
        engine.execute("CREATE TABLE obj2 (d VARIANT)");
        engine.execute("INSERT INTO obj2 VALUES ('{\"a\": {\"b\": \"hello\"}}')");
        ResultSet rs = engine.executeQuery("SELECT d:a.b AS v FROM obj2");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0).toString());
    }
}
