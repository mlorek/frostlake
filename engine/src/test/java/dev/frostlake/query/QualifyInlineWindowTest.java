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
 * QUALIFY with a window function written INLINE in the predicate (not referenced through a SELECT
 * alias), e.g. {@code QUALIFY ROW_NUMBER() OVER (PARTITION BY g ORDER BY v DESC) = 1}. The engine
 * precomputes each inline window function over all rows and substitutes it before filtering.
 */
public class QualifyInlineWindowTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    private void seed() {
        engine.execute("CREATE TABLE w (id INTEGER, grp VARCHAR, val INTEGER)");
        engine.execute("INSERT INTO w VALUES (1,'a',10),(2,'a',20),(3,'a',30),(4,'b',5),(5,'b',15)");
    }

    // Top row per group, window function inline in QUALIFY (no SELECT alias).
    @Test
    public void inlineRowNumberEqualsOne() {
        seed();
        final ResultSet rs = run(
            "SELECT id FROM w QUALIFY ROW_NUMBER() OVER (PARTITION BY grp ORDER BY val DESC) = 1");
        assertEquals(2, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    // A range predicate over an inline window function: top two rows per group.
    @Test
    public void inlineRowNumberTopTwo() {
        seed();
        final ResultSet rs = run(
            "SELECT id FROM w QUALIFY ROW_NUMBER() OVER (PARTITION BY grp ORDER BY val DESC) <= 2");
        assertEquals(4, rs.getRowCount());
    }

    // The existing alias-based QUALIFY form keeps working (regression guard).
    @Test
    public void aliasedRowNumberStillWorks() {
        seed();
        final ResultSet rs = run(
            "SELECT id, ROW_NUMBER() OVER (PARTITION BY grp ORDER BY val DESC) AS rn FROM w QUALIFY rn = 1");
        assertEquals(2, rs.getRowCount());
    }

    // Inline window function combined with an ordinary predicate via AND.
    @Test
    public void inlineWindowWithAnd() {
        seed();
        final ResultSet rs = run(
            "SELECT id FROM w QUALIFY ROW_NUMBER() OVER (PARTITION BY grp ORDER BY val DESC) = 1 AND grp = 'a'");
        assertEquals(1, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
