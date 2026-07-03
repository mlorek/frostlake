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

public class SpreadOperatorTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, dept VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering')");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Marketing')");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testQualifiedStar() {
        // SELECT t.* FROM table AS t
        ResultSet rs = engine.executeQuery("SELECT e.* FROM employees e ORDER BY e.id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(3, rs.getColumns().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1).toString());
        assertEquals("Engineering", rs.getRows().get(0).getValue(2).toString());
    }

    @Test
    public void testSpreadOperator() {
        // SELECT e.** FROM table AS e — same as e.*
        ResultSet rs = engine.executeQuery("SELECT e.** FROM employees e ORDER BY e.id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(3, rs.getColumns().size());
        assertEquals("Alice", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void testSpreadOperatorWithAdditionalColumn() {
        // SELECT e.**, extra_expr alongside spread
        ResultSet rs = engine.executeQuery(
            "SELECT e.**, UPPER(e.name) AS upper_name FROM employees e ORDER BY e.id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(4, rs.getColumns().size()); // id, name, dept, upper_name
        assertEquals("ALICE", rs.getRows().get(0).getValue(3).toString());
    }

    @Test
    public void testQualifiedStarJoin() {
        engine.execute("CREATE TABLE depts (dept VARCHAR, budget INTEGER)");
        engine.execute("INSERT INTO depts VALUES ('Engineering', 500000)");
        engine.execute("INSERT INTO depts VALUES ('Marketing', 200000)");

        ResultSet rs = engine.executeQuery(
            "SELECT e.*, d.budget FROM employees e JOIN depts d ON e.dept = d.dept ORDER BY e.id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertEquals(4, rs.getColumns().size()); // id, name, dept, budget
        assertEquals(500000L, ((Number) rs.getRows().get(0).getValue(3)).longValue());
    }
}
