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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Snowflake {@code SELECT *} column-list modifiers: EXCLUDE, RENAME, REPLACE, and ILIKE.
 */
public class SelectStarModifiersTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE sm (aa INTEGER, ab INTEGER, bc INTEGER)");
        engine.execute("INSERT INTO sm VALUES (1, 2, 3)");
    }

    private long v(final ResultSet rs, final int col) {
        return ((Number) rs.getRows().get(0).getValue(col)).longValue();
    }

    @Test
    public void excludeDropsListedColumns() {
        final ResultSet rs = engine.executeQuery("SELECT * EXCLUDE (ab) FROM sm");
        assertEquals(2, rs.getColumns().size());
        assertTrue(rs.getColumns().get(0).getName().equalsIgnoreCase("aa"));
        assertTrue(rs.getColumns().get(1).getName().equalsIgnoreCase("bc"));
        assertEquals(1L, v(rs, 0));
        assertEquals(3L, v(rs, 1));
    }

    @Test
    public void excludeAcceptsASingleColumnWithoutParens() {
        final ResultSet rs = engine.executeQuery("SELECT * EXCLUDE ab FROM sm");
        assertEquals(2, rs.getColumns().size());
    }

    @Test
    public void renameChangesTheOutputColumnNameNotTheValue() {
        final ResultSet rs = engine.executeQuery("SELECT * RENAME (aa AS x) FROM sm");
        assertEquals(3, rs.getColumns().size());
        assertTrue(rs.getColumns().get(0).getName().equalsIgnoreCase("x"));
        assertEquals(1L, v(rs, 0));   // value unchanged
    }

    @Test
    public void replaceSwapsTheValueKeepingTheName() {
        final ResultSet rs = engine.executeQuery("SELECT * REPLACE (aa + 100 AS aa) FROM sm");
        assertEquals(3, rs.getColumns().size());
        assertTrue(rs.getColumns().get(0).getName().equalsIgnoreCase("aa"));
        assertEquals(101L, v(rs, 0));
    }

    @Test
    public void ilikeKeepsOnlyMatchingColumns() {
        final ResultSet rs = engine.executeQuery("SELECT * ILIKE 'a%' FROM sm");
        assertEquals(2, rs.getColumns().size());   // aa, ab (not bc)
        assertTrue(rs.getColumns().get(0).getName().equalsIgnoreCase("aa"));
        assertTrue(rs.getColumns().get(1).getName().equalsIgnoreCase("ab"));
    }

    @Test
    public void plainStarIsUnaffected() {
        final ResultSet rs = engine.executeQuery("SELECT * FROM sm");
        assertEquals(3, rs.getColumns().size());
    }
}
