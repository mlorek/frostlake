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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A query that reads the outer row through a common table expression is judged as the query it passes on: a
 * definition of one row read alone, or a query the select merely projects, is answered — a SQL function's body called
 * over a column and a correlated scalar subquery alike — while a filter, a grouping or an ordering of the outer value
 * is refused, as two such definitions read together are (live-verified).
 */
public class SqlFunctionCteBodyTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|"
        + "Unsupported subquery type cannot be evaluated inside Function object: ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ca (id VARCHAR, n INT)");
        engine.execute("INSERT INTO ca VALUES ('a1', 1), ('a2', 2)");
        engine.execute("CREATE TABLE cb (id VARCHAR, w INT)");
        engine.execute("INSERT INTO cb VALUES ('a1', 10), ('a1', 11), ('a2', 20)");
        engine.execute("CREATE FUNCTION f_cte(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v) SELECT v FROM d $$");
        engine.execute("CREATE FUNCTION f_cte_two(p VARCHAR, q INT) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v, q AS w) SELECT v || w FROM d $$");
        engine.execute("CREATE FUNCTION f_cte_nested(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v), e AS (SELECT v || 'x' AS v2 FROM d) SELECT v2 FROM e $$");
        engine.execute("CREATE FUNCTION f_cte_cols(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d (x) AS (SELECT p) SELECT x FROM d $$");
        engine.execute("CREATE FUNCTION f_cte_count(p VARCHAR) RETURNS INT AS "
            + "$$ WITH d AS (SELECT COUNT(*) AS c FROM cb WHERE id = p) SELECT c FROM d $$");
        engine.execute("CREATE FUNCTION f_cte_max(p VARCHAR) RETURNS INT AS "
            + "$$ WITH d AS (SELECT MAX(w) AS m FROM cb WHERE id = p) SELECT m + 1 FROM d $$");
        engine.execute("CREATE FUNCTION f_derived_count(p VARCHAR) RETURNS INT AS "
            + "$$ SELECT c FROM (SELECT COUNT(*) AS c FROM cb WHERE id = p) $$");
        engine.execute("CREATE FUNCTION f_cte_where(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v) SELECT v FROM d WHERE v > 'a' $$");
        engine.execute("CREATE FUNCTION f_cte_group(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v) SELECT v FROM d GROUP BY v $$");
        engine.execute("CREATE FUNCTION f_cte_pair(p VARCHAR) RETURNS VARCHAR AS "
            + "$$ WITH d AS (SELECT p AS v), e AS (SELECT 'k' AS k) SELECT v || k FROM d, e $$");
    }

    @Test
    public void aBodyReadingItsParameterThroughADefinitionIsAnswered() {
        assertCells(engine, new String[][] {
            {"SELECT id, f_cte(id) FROM ca ORDER BY id", "a1, a1 | a2, a2"},
            {"SELECT id, f_cte(id || 'x') FROM ca ORDER BY id", "a1, a1x | a2, a2x"},
            {"SELECT id, f_cte_two(id, n) FROM ca ORDER BY id", "a1, a11 | a2, a22"},
            {"SELECT id, f_cte_nested(id) FROM ca ORDER BY id", "a1, a1x | a2, a2x"},
            {"SELECT id, f_cte_cols(id) FROM ca ORDER BY id", "a1, a1 | a2, a2"},
            {"SELECT id FROM ca WHERE f_cte(id) = 'a1'", "a1"},
            {"SELECT f_cte(id) FROM ca WHERE 1 = 0", "no row"},
        });
    }

    @Test
    public void aBodyPassingOnAnAggregateIsAnswered() {
        assertCells(engine, new String[][] {
            {"SELECT id, f_cte_count(id) FROM ca ORDER BY id", "a1, 2 | a2, 1"},
            {"SELECT id, f_cte_max(id) FROM ca ORDER BY id", "a1, 12 | a2, 21"},
            {"SELECT id, f_derived_count(id) FROM ca ORDER BY id", "a1, 2 | a2, 1"},
            {"SELECT id, (WITH d AS (SELECT COUNT(*) AS c FROM cb WHERE cb.id = ca.id) SELECT c FROM d) FROM ca "
                + "ORDER BY id", "a1, 2 | a2, 1"},
            {"SELECT id, (WITH d AS (SELECT ca.id AS v) SELECT v FROM d) FROM ca ORDER BY id", "a1, a1 | a2, a2"},
        });
    }

    @Test
    public void aFilterGroupingOrPairOfTheOuterValueIsRefused() {
        assertCells(engine, new String[][] {
            {"SELECT id, f_cte_where(id) FROM ca ORDER BY id", REFUSED + "F_CTE_WHERE"},
            {"SELECT id, f_cte_group(id) FROM ca ORDER BY id", REFUSED + "F_CTE_GROUP"},
            {"SELECT id, f_cte_pair(id) FROM ca ORDER BY id", REFUSED + "F_CTE_PAIR"},
            {"SELECT f_cte_where('a1'), f_cte_pair('a1')", "a1, a1k"},
        });
    }
}
