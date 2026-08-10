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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SELECT <expr> FROM DUAL} — the legacy one-row pseudo-table. Frostlake has no real DUAL table, so
 * a reference to it yields a single row against which the select-list expressions are evaluated. It is a
 * common idiom carried over from other SQL dialects.
 */
public class FromDualTest extends BaseDatabaseTest {

    @Test
    public void testSelectConstantFromDual() {
        final ResultSet rs = engine.executeQuery("SELECT 42 AS n FROM DUAL");
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectExpressionFromDual() {
        final ResultSet rs = engine.executeQuery("SELECT UPPER('abc') || '_' || (1 + 2) AS v FROM DUAL");
        assertEquals(1, rs.getRowCount());
        assertEquals("ABC_3", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testDualIsCaseInsensitive() {
        final ResultSet rs = engine.executeQuery("SELECT 1 FROM dual");
        assertEquals(1, rs.getRowCount());
    }

    @Test
    public void testDualShapeIsSingleNullColumn() {
        // SELECT * FROM DUAL yields exactly one row and one nullable column named COLUMN1 whose value is NULL.
        final ResultSet rs = engine.executeQuery("SELECT * FROM DUAL");
        assertEquals(1, rs.getColumns().size());
        assertEquals("COLUMN1", rs.getColumns().get(0).getName());
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testDualIsNotAdvertisedByCatalogCommands() {
        // DUAL is synthesized on read and never registered in the catalog, so metadata commands must not
        // list it — otherwise tooling would think a DUAL table exists.
        engine.execute("CREATE TABLE realtab (id INTEGER)");
        assertFalse(catalogListContains("SHOW TABLES", "name", "DUAL"));
        assertTrue(catalogListContains("SHOW TABLES", "name", "REALTAB"));
        assertFalse(catalogListContains("SELECT table_name FROM INFORMATION_SCHEMA.TABLES", "TABLE_NAME", "DUAL"));
        assertTrue(catalogListContains("SELECT table_name FROM INFORMATION_SCHEMA.TABLES", "TABLE_NAME", "REALTAB"));
    }

    @Test
    public void testUserDualTableDoesNotShadowPseudoTable() {
        // A bare unquoted FROM DUAL is ALWAYS the pseudo-table (COLUMN1, one NULL row) — even
        // beside a populated user table named DUAL, which only the QUOTED spelling reaches.
        engine.execute("CREATE TABLE DUAL (x INTEGER)");
        engine.execute("INSERT INTO DUAL VALUES (7), (8)");
        final ResultSet pseudo = engine.executeQuery("SELECT * FROM DUAL");
        assertEquals("COLUMN1", pseudo.getColumns().get(0).getName());
        assertEquals(1, pseudo.getRowCount());
        assertNull(pseudo.getRows().get(0).getValue(0));

        final ResultSet real = engine.executeQuery("SELECT * FROM \"DUAL\" ORDER BY x");
        assertEquals("X", real.getColumns().get(0).getName());
        assertEquals(2, real.getRowCount());
        assertEquals(7L, ((Number) real.getRows().get(0).getValue(0)).longValue());
    }

    private boolean catalogListContains(final String sql, final String column, final String wanted) {
        final ResultSet rs = engine.executeQuery(sql);
        final int idx = rs.getColumnIndex(column);
        for (final Row row : rs.getRows()) {
            if (wanted.equalsIgnoreCase(String.valueOf(row.getValue(idx)))) {
                return true;
            }
        }
        return false;
    }
}
