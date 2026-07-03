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

/**
 * NATURAL JOIN — an implicit equi-join on the columns common to both inputs (no ON/USING). Verifies the
 * inner and outer variants, joining on multiple common columns, and the standard-SQL rule that no common
 * columns degrades to a cross join.
 */
public class NaturalJoinTest extends BaseDatabaseTest {

    private long count(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void naturalInnerJoinMatchesOnCommonColumn() {
        engine.execute("CREATE TABLE emp (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol')");
        engine.execute("CREATE TABLE dept (id INTEGER, dname VARCHAR)");
        engine.execute("INSERT INTO dept VALUES (1, 'Eng'), (2, 'Sales'), (4, 'HR')");

        final ResultSet rs = engine.executeQuery(
            "SELECT name, dname FROM emp NATURAL JOIN dept ORDER BY name");
        // Joined on the shared column id: ids 1 and 2 match; 3 and 4 drop out.
        assertEquals(2, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Eng", rs.getRows().get(0).getValue(1).toString());
        assertEquals("Bob", rs.getRows().get(1).getValue(0).toString());
        assertEquals("Sales", rs.getRows().get(1).getValue(1).toString());
    }

    @Test
    public void naturalLeftJoinKeepsUnmatchedLeftRows() {
        engine.execute("CREATE TABLE emp (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol')");
        engine.execute("CREATE TABLE dept (id INTEGER, dname VARCHAR)");
        engine.execute("INSERT INTO dept VALUES (1, 'Eng'), (2, 'Sales')");

        assertEquals(3, count("SELECT COUNT(*) FROM emp NATURAL LEFT JOIN dept"));
        final ResultSet rs = engine.executeQuery(
            "SELECT name, dname FROM emp NATURAL LEFT JOIN dept ORDER BY name");
        // Carol (id 3) has no department → dname is NULL but the row is kept.
        assertEquals("Carol", rs.getRows().get(2).getValue(0).toString());
        assertEquals(null, rs.getRows().get(2).getValue(1));
    }

    @Test
    public void naturalJoinUsesAllCommonColumns() {
        engine.execute("CREATE TABLE a (k1 INTEGER, k2 INTEGER, va VARCHAR)");
        engine.execute("INSERT INTO a VALUES (1, 1, 'A'), (1, 2, 'B')");
        engine.execute("CREATE TABLE b (k1 INTEGER, k2 INTEGER, vb VARCHAR)");
        engine.execute("INSERT INTO b VALUES (1, 1, 'X'), (1, 3, 'Y')");

        final ResultSet rs = engine.executeQuery("SELECT va, vb FROM a NATURAL JOIN b");
        // Both k1 AND k2 must match: only (1,1) does.
        assertEquals(1, rs.getRowCount());
        assertEquals("A", rs.getRows().get(0).getValue(0).toString());
        assertEquals("X", rs.getRows().get(0).getValue(1).toString());
    }

    @Test
    public void naturalJoinWithNoCommonColumnsIsCrossJoin() {
        engine.execute("CREATE TABLE x (a INTEGER)");
        engine.execute("INSERT INTO x VALUES (1), (2)");
        engine.execute("CREATE TABLE y (b INTEGER)");
        engine.execute("INSERT INTO y VALUES (3), (4)");

        // No shared column ⇒ a cross join (2 × 2 = 4 rows), per standard SQL.
        assertEquals(4, count("SELECT COUNT(*) FROM x NATURAL JOIN y"));
    }
}
