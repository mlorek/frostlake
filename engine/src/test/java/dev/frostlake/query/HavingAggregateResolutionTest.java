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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HAVING must be able to evaluate an aggregate that is NOT a SELECT item — it is computed fresh over each
 * group's rows rather than read back from the already-projected output row. Previously HAVING reused only
 * the SELECT-computed values (keyed by canonical form), so a HAVING aggregate absent from the SELECT (or
 * present only wrapped, e.g. COUNT(*)::VARCHAR while HAVING references bare COUNT(*)) resolved to nothing
 * and filtered out every group.
 */
public class HavingAggregateResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (grp VARCHAR, id INTEGER)");
        // group a: 2 rows, sum 3 | group b: 1 row, sum 10 | group c: 3 rows, sum 12
        engine.execute("INSERT INTO t VALUES ('a',1),('a',2),('b',10),('c',3),('c',4),('c',5)");
    }

    private List<String> col0(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            out.add(String.valueOf(rs.getRows().get(i).getValue(0)));
        }
        return out;
    }

    @Test
    public void havingCountNotInSelectList() {
        assertEquals(List.of("a", "c"),
            col0("SELECT grp FROM t GROUP BY grp HAVING COUNT(*) > 1 ORDER BY grp"));
    }

    @Test
    public void havingDifferentAggregateThanSelect() {
        // SELECT projects COUNT(*); HAVING filters on SUM(id) — a different aggregate not in the SELECT.
        assertEquals(List.of("b", "c"),
            col0("SELECT grp FROM t GROUP BY grp HAVING SUM(id) > 5 ORDER BY grp"));
    }

    @Test
    public void havingBareAggregateWhileSelectWrapsIt() {
        // SELECT has COUNT(*)::VARCHAR; HAVING references bare COUNT(*) — canonical forms differ, so HAVING
        // must recompute it over the group instead of matching the SELECT item.
        final ResultSet rs = engine.executeQuery(
            "SELECT grp, COUNT(*)::VARCHAR AS c FROM t GROUP BY grp HAVING COUNT(*) > 1 ORDER BY grp");
        assertEquals(2, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals("2", rs.getRows().get(0).getValue(1));
        assertEquals("c", rs.getRows().get(1).getValue(0));
        assertEquals("3", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void havingStillResolvesSelectAlias() {
        // Regression guard: HAVING on a SELECT alias (the pre-existing path) keeps working.
        assertEquals(List.of("b", "c"),
            col0("SELECT grp, SUM(id) AS total FROM t GROUP BY grp HAVING total > 5 ORDER BY grp"));
    }

    @Test
    public void havingOnImplicitGroupByAggregateNotInSelect() {
        // No GROUP BY: the whole table is one group. HAVING filters on SUM(id) which is not selected.
        assertEquals(List.of("6"), col0("SELECT COUNT(*) FROM t HAVING SUM(id) > 5"));
        assertEquals(List.of(), col0("SELECT COUNT(*) FROM t HAVING SUM(id) > 100"));
    }

    @Test
    public void havingCombinesGroupColumnAndUnselectedAggregate() {
        // Mixes a group-column predicate (resolved from the projected row) with an unselected aggregate.
        assertEquals(List.of("c"),
            col0("SELECT grp FROM t GROUP BY grp HAVING grp <> 'a' AND COUNT(*) > 1 ORDER BY grp"));
    }
}
