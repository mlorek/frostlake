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
import dev.frostlake.storage.ResultSetColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code UNION [ALL] BY NAME} — the set operator that aligns the two branches by column name instead of by
 * position. Columns present in only one branch are filled with NULL, and the result column layout is the
 * first branch's columns followed by any columns unique to later branches.
 */
public class UnionByNameTest extends BaseDatabaseTest {

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn c : rs.getColumns()) {
            names.add(c.getName());
        }
        return names;
    }

    @Test
    public void sameColumnsKeepsRowsAndOrder() {
        final ResultSet rs = engine.executeQuery("SELECT 1 AS a, 2 AS b UNION ALL BY NAME SELECT 3 AS a, 4 AS b");
        assertEquals(List.of("A", "B"), columnNames(rs));
        assertEquals(2, rs.getRowCount());
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(4, ((Number) rs.getRows().get(1).getValue(1)).intValue());
    }

    @Test
    public void reorderedColumnsAreAlignedByName() {
        // Second branch lists b before a; BY NAME must realign to the first branch's (a, b) order.
        final ResultSet rs = engine.executeQuery("SELECT 1 AS a, 2 AS b UNION ALL BY NAME SELECT 4 AS b, 3 AS a");
        assertEquals(List.of("A", "B"), columnNames(rs));
        // Row 1 aligned: a = 3, b = 4 (NOT the positional 4, 3).
        assertEquals(3, ((Number) rs.getRows().get(1).getValue(0)).intValue());
        assertEquals(4, ((Number) rs.getRows().get(1).getValue(1)).intValue());
    }

    @Test
    public void disjointColumnsAreNullFilled() {
        // Right branch has c instead of b; merged layout is (a, b, c) with NULLs where a branch lacks a column.
        final ResultSet rs = engine.executeQuery("SELECT 1 AS a, 2 AS b UNION ALL BY NAME SELECT 3 AS a, 5 AS c");
        assertEquals(List.of("A", "B", "C"), columnNames(rs));
        // (a=1, b=2, c=NULL)
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(1)).intValue());
        assertNull(rs.getRows().get(0).getValue(2));
        // (a=3, b=NULL, c=5)
        assertEquals(3, ((Number) rs.getRows().get(1).getValue(0)).intValue());
        assertNull(rs.getRows().get(1).getValue(1));
        assertEquals(5, ((Number) rs.getRows().get(1).getValue(2)).intValue());
    }

    @Test
    public void distinctVariantDedupesByName() {
        // The two rows are the same once aligned by name (a=1, b=2), so distinct UNION collapses them to one.
        final ResultSet rs = engine.executeQuery("SELECT 1 AS a, 2 AS b UNION BY NAME SELECT 2 AS b, 1 AS a");
        assertEquals(1, rs.getRowCount());
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(1)).intValue());
    }

    @Test
    public void chainMergesAllBranchColumns() {
        final ResultSet rs = engine.executeQuery(
            "SELECT 1 AS a UNION ALL BY NAME SELECT 2 AS b UNION ALL BY NAME SELECT 3 AS c");
        assertEquals(List.of("A", "B", "C"), columnNames(rs));
        assertEquals(3, rs.getRowCount());
    }

    @Test
    public void selectStarAlignsByName() {
        // The real loader pattern: SELECT * from two tables whose columns are declared in a different order.
        engine.execute("CREATE TABLE ubn1 (x INT, y INT)");
        engine.execute("INSERT INTO ubn1 VALUES (1, 2)");
        engine.execute("CREATE TABLE ubn2 (y INT, x INT)");
        engine.execute("INSERT INTO ubn2 VALUES (20, 10)");
        final ResultSet rs = engine.executeQuery("SELECT * FROM ubn1 UNION ALL BY NAME SELECT * FROM ubn2");
        assertEquals(List.of("X", "Y"), columnNames(rs));
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());   // ubn1.x
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(1)).intValue());   // ubn1.y
        assertEquals(10, ((Number) rs.getRows().get(1).getValue(0)).intValue());  // ubn2.x aligned to slot 0
        assertEquals(20, ((Number) rs.getRows().get(1).getValue(1)).intValue());  // ubn2.y aligned to slot 1
    }

    @Test
    public void differingColumnCountsAreAllowed() {
        // Positional UNION rejects mismatched counts; BY NAME allows them (missing side is NULL).
        final ResultSet rs = engine.executeQuery("SELECT 1 AS a, 2 AS b UNION ALL BY NAME SELECT 9 AS a");
        assertEquals(List.of("A", "B"), columnNames(rs));
        assertNull(rs.getRows().get(1).getValue(1));
    }

    @Test
    public void onlyByNameIsAccepted() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 1 AS a UNION BY FOO SELECT 2 AS a");
            }
        });
    }

    @Test
    public void positionalUnionStillWorks() {
        // Regression: a plain positional UNION ALL must be unaffected.
        final ResultSet rs = engine.executeQuery("SELECT 1, 2 UNION ALL SELECT 3, 4");
        assertEquals(2, rs.getRowCount());
        assertEquals(3, ((Number) rs.getRows().get(1).getValue(0)).intValue());
    }
}
