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

import org.junit.jupiter.api.Test;

/**
 * A correlated subquery in a DELETE's or an UPDATE's WHERE is judged as the statement compiles, before any row is
 * read, as a query's is: over a target of no row, of one and of two. A membership selecting its own aggregate beside
 * the outer row over a pruned scan is refused over each; a scalar subquery filtered by the outer row is read row by row
 * over a target of one row at most and refused over two; a membership the plan answers stays answered. An EXPLAIN of
 * the statement is refused alike, at the subquery's place after the EXPLAIN's words. Every answer is live-verified.
 */
public class DmlCorrelatedSubqueryPlanTest extends MeasuredCellsTestSupport {

    private void prepare() {
        answer("CREATE OR REPLACE TABLE t0 (id INT, y INT)");
        answer("CREATE OR REPLACE TABLE t1 (id INT, y INT)");
        answer("CREATE OR REPLACE TABLE t2 (id INT, y INT)");
        answer("CREATE OR REPLACE TABLE u55 (id INT, v INT)");
        answer("INSERT INTO t1 VALUES (1, 0)");
        answer("INSERT INTO t2 VALUES (1, 0), (2, 0)");
        answer("INSERT INTO u55 VALUES (1, 10), (2, 20)");
    }

    @Test
    public void anAggregateBesideTheOuterRowIsRefusedOverEveryTarget() {
        prepare();
        for (final String t : new String[] {"t0", "t1", "t2"}) {
            assertAnswer(unsupportedAt(31),
                "SELECT id FROM " + t + " WHERE id IN (SELECT MAX(v) + " + t + ".id FROM u55 WHERE FALSE)");
            assertAnswer(unsupportedAt(28),
                "DELETE FROM " + t + " WHERE id IN (SELECT MAX(v) + " + t + ".id FROM u55 WHERE FALSE)");
            assertAnswer(unsupportedAt(41),
                "EXPLAIN UPDATE " + t + " SET y = 1 WHERE id IN (SELECT MAX(v) + " + t + ".id FROM u55 WHERE FALSE)");
        }
    }

    @Test
    public void aMembershipThePlanAnswersStaysAnswered() {
        prepare();
        assertAnswer("", "SELECT id FROM t0 WHERE id IN (SELECT v + t0.id FROM u55)");
        assertAnswer("0", "DELETE FROM t0 WHERE id IN (SELECT v + t0.id FROM u55)");
        assertAnswer("", "SELECT id FROM t1 WHERE id IN (SELECT v + t1.id FROM u55)");
        assertAnswer("0", "DELETE FROM t1 WHERE id IN (SELECT v + t1.id FROM u55)");
        assertAnswer("", "SELECT id FROM t2 WHERE id IN (SELECT v + t2.id FROM u55)");
        assertAnswer("0", "DELETE FROM t2 WHERE id IN (SELECT v + t2.id FROM u55)");
    }

    @Test
    public void aScalarSubqueryIsReadRowByRowOverOneRowAtMost() {
        prepare();
        assertAnswer("", "SELECT id FROM t0 WHERE id = (SELECT v FROM u55 WHERE u55.id = t0.id)");
        assertAnswer("0", "DELETE FROM t0 WHERE id = (SELECT v FROM u55 WHERE u55.id = t0.id)");
        assertAnswer("", "SELECT id FROM t1 WHERE id = (SELECT v FROM u55 WHERE u55.id = t1.id)");
        assertAnswer("0", "DELETE FROM t1 WHERE id = (SELECT v FROM u55 WHERE u55.id = t1.id)");
        assertAnswer(unsupportedAt(30), "SELECT id FROM t2 WHERE id = (SELECT v FROM u55 WHERE u55.id = t2.id)");
        assertAnswer(unsupportedAt(27), "DELETE FROM t2 WHERE id = (SELECT v FROM u55 WHERE u55.id = t2.id)");
        assertAnswer(unsupportedAt(40),
            "EXPLAIN UPDATE t2 SET y = 1 WHERE id = (SELECT v FROM u55 WHERE u55.id = t2.id)");
    }
}
