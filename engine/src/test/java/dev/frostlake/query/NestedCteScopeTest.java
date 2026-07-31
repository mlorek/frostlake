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

/**
 * A nested WITH inside a CTE body sees the ENCLOSING scope's CTEs (Snowflake scoping):
 * {@code WITH outputs AS (...), x AS (WITH cleaned AS (... FROM outputs) ...)}. Executing the inner
 * definitions with an empty CTE map made every such reference fail with "Table does not exist" —
 * swallowed by loader error handlers, leaving fact tables silently underpopulated. When a nested
 * WITH reuses an outer CTE's name, the OUTER definition wins (live-Snowflake behavior).
 */
public class NestedCteScopeTest extends BaseDatabaseTest {

    @BeforeEach
    public void setUpData() {
        engine.execute("CREATE TABLE src (id INT, payload VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'x;y'), (2, 'z')");
    }

    @Test
    public void nestedWithSeesParentSiblingCte() {
        final ResultSet rs = engine.executeQuery("""
            WITH outputs AS (
                SELECT id, SPLIT(payload, ';') AS parts FROM src
            ),
            records AS (
                WITH cleaned AS (
                    SELECT id, value::VARCHAR AS part FROM outputs, TABLE(FLATTEN(parts))
                )
                SELECT id, part FROM cleaned
            )
            SELECT id, part FROM records ORDER BY id, part""");
        assertEquals(3, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(1));
        assertEquals("z", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void nestedWithUnderInsertSeesParentSiblingCte() {
        engine.execute("CREATE TABLE sink (id INT, part VARCHAR)");
        engine.execute("""
            INSERT INTO sink (id, part)
            WITH outputs AS (
                SELECT id, SPLIT(payload, ';') AS parts FROM src
            ),
            records AS (
                WITH cleaned AS (
                    SELECT id, value::VARCHAR AS part FROM outputs, TABLE(FLATTEN(parts))
                )
                SELECT id, part FROM cleaned
            )
            SELECT id, part FROM records""");
        assertEquals(3L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM sink")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void tripleNestingResolvesAcrossTwoLevels() {
        final ResultSet rs = engine.executeQuery("""
            WITH lvl1 AS (SELECT id FROM src),
            wrap AS (
                WITH lvl2 AS (SELECT id FROM lvl1),
                deeper AS (
                    WITH lvl3 AS (SELECT id FROM lvl2)
                    SELECT id FROM lvl3
                )
                SELECT id FROM deeper
            )
            SELECT COUNT(*) FROM wrap""");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void outerCteWinsOverSameNamedInnerCte() {
        // Live-Snowflake behavior: when a nested WITH redefines an outer CTE's name, references
        // resolve to the OUTER definition.
        final ResultSet rs = engine.executeQuery("""
            WITH t AS (SELECT 'outer' AS v),
            u AS (
                WITH t AS (SELECT 'inner' AS v)
                SELECT v FROM t
            )
            SELECT v FROM u""");
        assertEquals("outer", rs.getRows().get(0).getValue(0));
    }
}
