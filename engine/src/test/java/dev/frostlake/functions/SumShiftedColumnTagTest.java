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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storage tag of a SUM the plan rewrites over a shifted column, {@code SUM(c + k)}. The plan computes it
 * as the column's SUM and the constant times the column's COUNT, so the tag follows that interval rather
 * than the shifted values' own sum, and a shift by exactly one is read at the rewrite's whole intermediate
 * width. A SUM the plan does not rewrite keeps the ordinary accumulated interval. A derived relation, a CTE or
 * a view the plan merges into the query over it is read as the items it projects, so a SUM over its shifted
 * column is rewritten as the SUM over the shift is. Every cell is live-verified.
 */
public class SumShiftedColumnTagTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE TABLE t (n NUMBER(5,2), n10 NUMBER(10,2), n40 NUMBER(4,0), n53 NUMBER(5,3), n200 NUMBER(20,0), n381 NUMBER(38,1))");
        engine.execute("INSERT INTO t VALUES (1.5, 17.25, 17, 1.125, 17, 1.5), (2.5, 3.5, 3, 2.5, 3, 2.5)");
        engine.execute("CREATE TABLE u (c NUMBER(7,0), s NUMBER(7,2))");
        engine.execute("INSERT INTO u VALUES (9000000, 90000.00), (9100000, 91000.00)");
        engine.execute("CREATE TABLE v (d NUMBER(7,0))");
        engine.execute("INSERT INTO v VALUES (9200000), (9300000)");
    }

    /** Every row's first cell, joined by a bar. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(row.getValue(0));
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void everyColumnScaleUnderEveryShift() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.5)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 2)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.1)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 10)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 100)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1.5)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + -1)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + -0.5)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1000000)) FROM t", "NUMBER(28,2)[SB8] | NUMBER(28,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1::NUMBER(18,0))) FROM t", "NUMBER(38,2)[SB16] | NUMBER(38,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 1)) FROM t", "NUMBER(29,2)[SB16] | NUMBER(29,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 0.5)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 2)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 0.1)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 10)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 100)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 1.5)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + -1)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + -0.5)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 1000000)) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 0.25)) FROM t", "NUMBER(23,2)[SB8] | NUMBER(23,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 1::NUMBER(18,0))) FROM t", "NUMBER(38,2)[SB16] | NUMBER(38,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1)) FROM t", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 0.5)) FROM t", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 2)) FROM t", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 0.1)) FROM t", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 10)) FROM t", "NUMBER(21,0)[SB8] | NUMBER(21,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 100)) FROM t", "NUMBER(22,0)[SB8] | NUMBER(22,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1.5)) FROM t", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + -1)) FROM t", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + -0.5)) FROM t", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1000000)) FROM t", "NUMBER(26,0)[SB8] | NUMBER(26,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 0.25)) FROM t", "NUMBER(19,2)[SB8] | NUMBER(19,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1::NUMBER(18,0))) FROM t", "NUMBER(37,0)[SB16] | NUMBER(37,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 1)) FROM t", "NUMBER(24,3)[SB16] | NUMBER(24,3)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 0.5)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 2)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 0.1)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 10)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 100)) FROM t", "NUMBER(25,3)[SB8] | NUMBER(25,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 1.5)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + -1)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + -0.5)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 1000000)) FROM t", "NUMBER(29,3)[SB8] | NUMBER(29,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 0.25)) FROM t", "NUMBER(24,3)[SB8] | NUMBER(24,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 1::NUMBER(18,0))) FROM t", "NUMBER(38,3)[SB16] | NUMBER(38,3)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 1)) FROM t", "NUMBER(33,0)[SB16] | NUMBER(33,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 0.5)) FROM t", "NUMBER(34,1)[SB8] | NUMBER(34,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 2)) FROM t", "NUMBER(33,0)[SB8] | NUMBER(33,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 0.1)) FROM t", "NUMBER(34,1)[SB8] | NUMBER(34,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 10)) FROM t", "NUMBER(33,0)[SB8] | NUMBER(33,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 100)) FROM t", "NUMBER(33,0)[SB8] | NUMBER(33,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 1.5)) FROM t", "NUMBER(34,1)[SB8] | NUMBER(34,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + -1)) FROM t", "NUMBER(33,0)[SB8] | NUMBER(33,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + -0.5)) FROM t", "NUMBER(34,1)[SB8] | NUMBER(34,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 1000000)) FROM t", "NUMBER(33,0)[SB8] | NUMBER(33,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 0.25)) FROM t", "NUMBER(35,2)[SB8] | NUMBER(35,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n200 + 1::NUMBER(18,0))) FROM t", "NUMBER(37,0)[SB16] | NUMBER(37,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 1)) FROM t", "NUMBER(38,1)[SB16] | NUMBER(38,1)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 0.5)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 2)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 0.1)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 10)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 100)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 1.5)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + -1)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + -0.5)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 1000000)) FROM t", "NUMBER(38,1)[SB8] | NUMBER(38,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 0.25)) FROM t", "NUMBER(38,2)[SB8] | NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n381 + 1::NUMBER(18,0))) FROM t", "NUMBER(38,1)[SB16] | NUMBER(38,1)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(SUM(n + 0.5), 0)) FROM t", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.5) + 0) FROM t", "NUMBER(19,2)[SB8] | NUMBER(19,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(SUM(n40 + 1), 0)) FROM t", "NUMBER(17,0)[SB16] | NUMBER(17,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1) + 0) FROM t", "NUMBER(18,0)[SB16] | NUMBER(18,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1) * 1) FROM t", "NUMBER(19,2)[SB16] | NUMBER(19,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 100) + 0) FROM t", "NUMBER(18,0)[SB8] | NUMBER(18,0)[SB8]"},
        });
    }

    @Test
    public void aWideSumMovesByTheConstantTimesTheCount() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(c)) FROM u", "NUMBER(19,0)[SB8] | NUMBER(19,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 2)) FROM u", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 100000000000000000)) FROM u", "NUMBER(37,0)[SB16] | NUMBER(37,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 1)) FROM u", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 200000)) FROM u", "NUMBER(25,0)[SB8] | NUMBER(25,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(d + 2)) FROM v", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(d)) FROM v", "NUMBER(19,0)[SB16] | NUMBER(19,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c - 2)) FROM u", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 0.5)) FROM u", "NUMBER(21,1)[SB16] | NUMBER(21,1)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c - 200000)) FROM u", "NUMBER(25,0)[SB8] | NUMBER(25,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 1.0)) FROM u", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c - -1)) FROM u", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(-1 + c)) FROM u", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(s + 2)) FROM u", "NUMBER(26,2)[SB8] | NUMBER(26,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(s + 100000000000000)) FROM u", "NUMBER(36,2)[SB8] | NUMBER(36,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + (2 - 1))) FROM u", "NUMBER(21,0)[SB16] | NUMBER(21,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c + 3000000000000000000)) FROM u", "NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(c) + 2 * COUNT(c)) FROM u", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.5)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 0.05)) FROM t", "NUMBER(23,2)[SB8] | NUMBER(23,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.001)) FROM t", "NUMBER(19,3)[SB8] | NUMBER(19,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n40 + 1.5)) FROM t", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n x FROM t)", "NUMBER(17,2)[SB8] | NUMBER(17,2)[SB8]"},
        });
    }

    /** A constant at the column's own scale is rewritten too, nineteen digits past its own precision. */
    @Test
    public void aConstantAtTheColumnsOwnScaleIsRewrittenToo() {
        engine.execute("CREATE TABLE w (n71 NUMBER(7,1))");
        engine.execute("INSERT INTO w VALUES (1.5), (2.5)");
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.05)) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.01)) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n - 0.05)) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(0.05 + n)) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1.25)) FROM t", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 10.05)) FROM t", "NUMBER(23,2)[SB8] | NUMBER(23,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 100.05)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.05::NUMBER(10,2))) FROM t", "NUMBER(29,2)[SB8] | NUMBER(29,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 0.10)) FROM t", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n + 1.00)) FROM t", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n10 + 10.05)) FROM t", "NUMBER(23,2)[SB8] | NUMBER(23,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 0.005)) FROM t", "NUMBER(23,3)[SB8] | NUMBER(23,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n53 + 1.125)) FROM t", "NUMBER(23,3)[SB8] | NUMBER(23,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n71 + 0.5)) FROM w", "NUMBER(21,1)[SB8] | NUMBER(21,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(n71 + 1)) FROM w", "NUMBER(26,1)[SB16] | NUMBER(26,1)[SB16]"},
        });
    }

    /**
     * A SUM over a merged derived relation's column is the SUM over the item it projects: a shifted column there
     * is rewritten through a subquery, a CTE, a view, a filter, a join and a re-aliasing projection, and a column
     * standing for any other expression is no column a shift is read over. A relation the plan keeps whole has
     * columns of its own.
     */
    @Test
    public void aMergedDerivedColumnIsTheShiftItStandsFor() {
        engine.execute("CREATE TABLE ts (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute("INSERT INTO ts VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute("CREATE OR REPLACE FUNCTION inc_p(a NUMBER(5,2)) RETURNS NUMBER(6,2) AS 'a + 1'");
        engine.execute("CREATE OR REPLACE VIEW vs AS SELECT n + 1 x, n40 + 1 y FROM ts");
        final String wide = "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]";
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n - 1 x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT 1 + n x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT inc_p(n) x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x FROM ts)", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 0.5 x FROM ts)", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 0.05 x FROM ts)", "NUMBER(22,2)[SB8] | NUMBER(22,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 1 x FROM ts)", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 2 x FROM ts)", "NUMBER(20,0)[SB8] | NUMBER(20,0)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(DISTINCT x)) FROM (SELECT n + 1 x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(AVG(x)) FROM (SELECT n + 1 x FROM ts)", "NUMBER(24,8)[SB16] | NUMBER(24,8)[SB16]"},
            {"WITH c AS (SELECT n + 1 x FROM ts) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c", wide},
            {"WITH c AS (SELECT n40 + 1 x FROM ts) SELECT SYSTEM$TYPEOF(SUM(x)) FROM c",
                "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)), SYSTEM$TYPEOF(SUM(y)) FROM vs", "NUMBER(24,2)[SB16] | NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(y)) FROM vs", "NUMBER(20,0)[SB16] | NUMBER(20,0)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts WHERE k > 0)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts WHERE k = 1)", "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x FROM ts WHERE k > 0)",
                "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x FROM ts WHERE k = 1)", "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 2 x, k FROM ts) WHERE k = 1", "NUMBER(24,2)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SUM(d.x)) FROM (SELECT n + 2 x, k FROM ts) d JOIN ts ON d.k = ts.k", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(y)) FROM (SELECT x AS y FROM (SELECT n + 1 x FROM ts))", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT x FROM (SELECT n + 1 x FROM ts))", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts) GROUP BY x", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT n x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x + 2)) FROM (SELECT n x FROM ts)", "NUMBER(24,2)[SB8] | NUMBER(24,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT DISTINCT n x FROM ts)", wide},
            {"SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT n x FROM ts LIMIT 5)", wide},
            // Not a shifted column once merged.
            {"SELECT SYSTEM$TYPEOF(SUM(x + 0)) FROM (SELECT n + 1 x FROM ts)", "NUMBER(19,2)[SB8] | NUMBER(19,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x + 1)) FROM (SELECT n * 2 x FROM ts)", "NUMBER(19,2)[SB8] | NUMBER(19,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n * 2 x FROM ts)", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n40 + 1.5 x FROM ts)", "NUMBER(18,1)[SB8] | NUMBER(18,1)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 0.001 x FROM ts)", "NUMBER(19,3)[SB8] | NUMBER(19,3)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + k x FROM ts)", "NUMBER(38,2)[SB8] | NUMBER(38,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT (n + 1) + 1 x FROM ts)", "NUMBER(19,2)[SB8] | NUMBER(19,2)[SB8]"},
            // Kept whole: no shift beneath.
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT MAX(n) + 1 x FROM ts GROUP BY k)",
                "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT DISTINCT n + 1 x FROM ts)", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts LIMIT 5)", "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(SUM(x)) FROM (SELECT n + 1 x FROM ts UNION ALL SELECT n + 1 FROM ts)",
                "NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8] | NUMBER(18,2)[SB8]"},
        });
    }
}
