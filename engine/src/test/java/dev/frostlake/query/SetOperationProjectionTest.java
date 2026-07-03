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
 * Set operations must combine each operand's PROJECTED SELECT list (not the raw base rows), and INTERSECT
 * must bind tighter than UNION / EXCEPT (Snowflake operator precedence).
 */
public class SetOperationProjectionTest extends BaseDatabaseTest {

    private void seed() {
        engine.execute("CREATE TABLE sp (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO sp VALUES (1, 99), (2, 88)");
    }

    @Test
    public void unionCombinesProjectedExpressionsNotRawRows() {
        seed();
        // a+1 -> {2,3}, b -> {99,88}; UNION distinct -> {2,3,88,99}
        final ResultSet rs = engine.executeQuery("SELECT a+1 FROM sp UNION SELECT b FROM sp ORDER BY 1");
        assertEquals(4, rs.getRows().size());
        assertEquals(1, rs.getColumns().size());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(88L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
        assertEquals(99L, ((Number) rs.getRows().get(3).getValue(0)).longValue());
    }

    @Test
    public void unionDedupsOnTheProjectedColumnOnly() {
        engine.execute("CREATE TABLE sp2 (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO sp2 VALUES (1, 99), (1, 88), (2, 77)");
        // Projecting only 'a', the differing 'b' must not defeat dedup -> {1,2}
        final ResultSet rs = engine.executeQuery("SELECT a FROM sp2 UNION SELECT a FROM sp2 ORDER BY 1");
        assertEquals(2, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void intersectBindsTighterThanUnion() {
        // set1 UNION (set2 INTERSECT set2) == {1} UNION {2} == {1,2}, NOT (1 UNION 2) INTERSECT 2 == {2}
        final ResultSet rs = engine.executeQuery("SELECT 1 UNION SELECT 2 INTERSECT SELECT 2 ORDER BY 1");
        assertEquals(2, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void exceptCombinesProjectedColumns() {
        seed();
        // a -> {1,2}; EXCEPT {2} -> {1}
        final ResultSet rs = engine.executeQuery("SELECT a FROM sp EXCEPT SELECT 2");
        assertEquals(1, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void mixedNumericTypesAcrossBranchesSortWithoutError() {
        engine.execute("CREATE TABLE mn (a INTEGER)");
        engine.execute("INSERT INTO mn VALUES (1), (2)");
        // 'a' is an integer (Long) in one branch, 1.5 is a decimal (BigDecimal) in the other — the combined
        // column is mixed-typed and must still sort (not ClassCastException).
        final ResultSet rs = engine.executeQuery("SELECT a FROM mn UNION SELECT 1.5 ORDER BY 1");
        assertEquals(3, rs.getRows().size());
        assertEquals(1.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 1e-9);
        assertEquals(1.5, ((Number) rs.getRows().get(1).getValue(0)).doubleValue(), 1e-9);
        assertEquals(2.0, ((Number) rs.getRows().get(2).getValue(0)).doubleValue(), 1e-9);
    }

    @Test
    public void fetchAppliesToTheCombinedSetOperationResult() {
        engine.execute("CREATE TABLE sp3 (a INTEGER)");
        engine.execute("INSERT INTO sp3 VALUES (1), (2), (3)");
        final ResultSet rs = engine.executeQuery(
            "SELECT a FROM sp3 UNION SELECT a FROM sp3 ORDER BY a FETCH FIRST 2 ROWS ONLY");
        assertEquals(2, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }
}
