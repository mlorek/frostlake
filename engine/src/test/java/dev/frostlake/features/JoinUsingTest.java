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

public class JoinUsingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept_id INTEGER)");
        engine.execute("CREATE TABLE departments (dept_id INTEGER, dept_name VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 10)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 20)");
        engine.execute("INSERT INTO employees VALUES (3, 'Carol', 10)");
        engine.execute("INSERT INTO departments VALUES (10, 'Engineering')");
        engine.execute("INSERT INTO departments VALUES (20, 'Marketing')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testInnerJoinUsing() {
        ResultSet rs = engine.executeQuery(
            "SELECT name, dept_name FROM employees JOIN departments USING (dept_id) ORDER BY name");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Engineering", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void testLeftJoinUsing() {
        engine.execute("INSERT INTO employees VALUES (4, 'Dave', 99)");
        ResultSet rs = engine.executeQuery(
            "SELECT name, dept_name FROM employees LEFT JOIN departments USING (dept_id) ORDER BY name");
        assertNotNull(rs);
        assertEquals(4, rs.getRowCount());
        // Dave has no matching dept — dept_name should be null
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("Dave".equals(rs.getRows().get(i).getValue(0).toString())) {
                assertNull(rs.getRows().get(i).getValue(1), "Unmatched row should have null dept_name");
            }
        }
    }

    @Test
    public void testJoinUsingMultipleColumns() {
        engine.execute("CREATE TABLE t1 (a INTEGER, b INTEGER, val VARCHAR)");
        engine.execute("CREATE TABLE t2 (a INTEGER, b INTEGER, info VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES (1, 2, 'x')");
        engine.execute("INSERT INTO t1 VALUES (1, 3, 'y')");
        engine.execute("INSERT INTO t2 VALUES (1, 2, 'matched')");
        ResultSet rs = engine.executeQuery(
            "SELECT val, info FROM t1 JOIN t2 USING (a, b)");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(0).toString());
        assertEquals("matched", rs.getRows().get(0).getValue(1).toString());
    }
}
