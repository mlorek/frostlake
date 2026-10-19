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

import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import org.junit.jupiter.api.Test;

/**
 * Over grouped rows an expression may hold a group's aggregate beside a window over aggregates — the share of a
 * total {@code SUM(b) / SUM(SUM(b)) OVER ()}, the change {@code MAX(b) - LAG(MAX(b)) OVER (ORDER BY a)} — each
 * aggregate valued over the row's group, in the select list and in QUALIFY alike (live-verified).
 */
public class AggregateBesideWindowTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 10), (1, 20), (2, 10), (2, 30), (3, 10)");
    }

    /** The column names and types, joined. */
    private String columns(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : result.getColumns()) {
            out.append(out.length() > 0 ? ", " : "").append(column.getName());
        }
        return out.toString();
    }

    @Test
    public void anAggregateTakesItsPartBesideAWindow() {
        assertCells(engine, new String[][] {
            {"SELECT a, SUM(b) / SUM(SUM(b)) OVER () share FROM t GROUP BY a ORDER BY a",
                "1, 0.375000 | 2, 0.500000 | 3, 0.125000"},
            {"SELECT a, COUNT(*) * 100 / SUM(COUNT(*)) OVER () pct FROM t GROUP BY a ORDER BY a",
                "1, 40.000000 | 2, 40.000000 | 3, 20.000000"},
            {"SELECT a, MAX(b) - LAG(MAX(b)) OVER (ORDER BY a) diff FROM t GROUP BY a ORDER BY a",
                "1, null | 2, 10 | 3, -20"},
            {"SELECT a, SUM(b) s, SUM(b) / SUM(SUM(b)) OVER () share FROM t GROUP BY a ORDER BY a",
                "1, 30, 0.375000 | 2, 40, 0.500000 | 3, 10, 0.125000"},
            {"SELECT a, RANK() OVER (ORDER BY SUM(b) DESC) + COUNT(*) r FROM t GROUP BY a ORDER BY a",
                "1, 4 | 2, 3 | 3, 4"},
            {"SELECT a, CASE WHEN SUM(b) > AVG(SUM(b)) OVER () THEN 'hi' ELSE 'lo' END c FROM t GROUP BY a"
                + " ORDER BY a", "1, hi | 2, hi | 3, lo"},
            {"SELECT a, SUM(b) / SUM(SUM(b)) OVER (PARTITION BY a > 1) share FROM t GROUP BY a ORDER BY a",
                "1, 1.000000 | 2, 0.800000 | 3, 0.200000"},
            {"SELECT SUM(b) / SUM(SUM(b)) OVER () share FROM t", "1.000000"},
        });
        assertEquals("A, SHARE", columns("SELECT a, SUM(b) / SUM(SUM(b)) OVER () share FROM t GROUP BY a"));
    }

    @Test
    public void aQualifyWeighsAnAggregateAgainstAWindow() {
        assertCells(engine, new String[][] {
            {"SELECT a, SUM(b) s FROM t GROUP BY a QUALIFY SUM(b) / SUM(SUM(b)) OVER () > 0.3 ORDER BY a",
                "1, 30 | 2, 40"},
        });
    }

    @Test
    public void aColumnOutsideTheGroupIsStillRefused() {
        assertCells(engine, new String[][] {
            {"SELECT a, b + SUM(SUM(b)) OVER () x FROM t GROUP BY a ORDER BY a",
                "SQL compilation error: error line 1 at position 10|'T.B' in select clause is neither an aggregate"
                    + " nor in the group by clause."},
        });
    }
}
