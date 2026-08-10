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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionVariablesTest extends BaseDatabaseTest {

    @Test
    public void testSetAndShowVariable() {
        engine.execute("SET my_var = 42");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        assertNotNull(rs);
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_VAR".equalsIgnoreCase(rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString())) {
                found = true;
                assertEquals("42", rs.getRows().get(i).getValue(rs.getColumnIndex("value")).toString());
            }
        }
        assertTrue(found, "SET variable should appear in SHOW VARIABLES");
    }

    @Test
    public void testSetStringVariable() {
        engine.execute("SET greeting = 'hello'");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("GREETING".equalsIgnoreCase(rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString())) {
                found = true;
                assertEquals("hello", rs.getRows().get(i).getValue(rs.getColumnIndex("value")).toString());
            }
        }
        assertTrue(found);
    }

    @Test
    public void testSetMultipleVariables() {
        engine.execute("SET (x, y, z) = (1, 2, 'three')");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        boolean foundX = false;
        boolean foundY = false;
        boolean foundZ = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            final String name = rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString().toUpperCase();
            final String val = rs.getRows().get(i).getValue(rs.getColumnIndex("value")) != null ? rs.getRows().get(i).getValue(rs.getColumnIndex("value")).toString() : null;
            if ("X".equals(name)) {
                foundX = true;
                assertEquals("1", val);
            }
            if ("Y".equals(name)) {
                foundY = true;
                assertEquals("2", val);
            }
            if ("Z".equals(name)) {
                foundZ = true;
                assertEquals("three", val);
            }
        }
        assertTrue(foundX && foundY && foundZ, "All three variables should be set");
    }

    @Test
    public void testUnsetVariable() {
        engine.execute("SET to_remove = 99");
        engine.execute("UNSET to_remove");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        for (int i = 0; i < rs.getRowCount(); i++) {
            assertFalse("TO_REMOVE".equalsIgnoreCase(rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString()),
                "Unset variable should not appear in SHOW VARIABLES");
        }
    }

    @Test
    public void testUnsetMultipleVariables() {
        engine.execute("SET (a, b, c) = (1, 2, 3)");
        engine.execute("UNSET (a, b)");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        boolean foundA = false;
        boolean foundB = false;
        boolean foundC = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            final String name = rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString().toUpperCase();
            if ("A".equals(name)) foundA = true;
            if ("B".equals(name)) foundB = true;
            if ("C".equals(name)) foundC = true;
        }
        assertFalse(foundA, "A should be unset");
        assertFalse(foundB, "B should be unset");
        assertTrue(foundC, "C should still be set");
    }

    @Test
    public void testSelectSessionVar() {
        engine.execute("SET my_val = 42");
        final ResultSet rs = engine.executeQuery("SELECT $my_val");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        final Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val);
        assertEquals(42L, ((Number) val).longValue());
    }

    @Test
    public void testSelectSessionVarString() {
        engine.execute("SET greeting = 'hello'");
        final ResultSet rs = engine.executeQuery("SELECT $greeting");
        assertNotNull(rs);
        assertEquals("hello", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testSessionVarInWhereClause() {
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1)");
        engine.execute("INSERT INTO nums VALUES (5)");
        engine.execute("INSERT INTO nums VALUES (10)");
        engine.execute("SET threshold = 5");
        final ResultSet rs = engine.executeQuery("SELECT n FROM nums WHERE n >= $threshold");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void testSessionVarInExpression() {
        engine.execute("SET base = 100");
        final ResultSet rs = engine.executeQuery("SELECT $base + 50");
        assertNotNull(rs);
        assertEquals(150L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testOverwriteVariable() {
        engine.execute("SET counter = 10");
        engine.execute("SET counter = 20");
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("COUNTER".equalsIgnoreCase(rs.getRows().get(i).getValue(0).toString())) {
                assertEquals("20", rs.getRows().get(i).getValue(1).toString());
            }
        }
    }
}
