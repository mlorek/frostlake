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

import static dev.frostlake.query.QueryAnswers.answer;
import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A select item is computed only for a row, or a group, that survives the HAVING: live answers
 * {@code SELECT 1/0 AS x HAVING 1 = 0} and {@code SELECT v, 1/0 FROM t GROUP BY v HAVING v = 5} with no row, and
 * raises the item's fault only where the row is read — by the HAVING itself through an alias, a sort, a dedup, a
 * write, or the result handed out. A fault inside an aggregate's own argument raises while the groups are computed,
 * and a HAVING written of constants alone is settled before any key or aggregate is — one that faults raising where
 * the plan reaches it. A scalar subquery that reads a relation is computed ahead of the rows, so what it raises,
 * its returning several rows included, is raised whatever survives. Every cell is live-verified.
 */
public class HavingItemFaultTest extends BaseDatabaseTest {

    private static final String DIVISION = "Division by zero";
    private static final String SEVERAL_ROWS = "Single-row subquery returns more than one row.";
    private static final String NOT_A_NUMBER = "Numeric value 'x' is not recognized";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE gq (v NUMBER)");
        engine.execute("INSERT INTO gq VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE ge (v NUMBER)");
    }


    @Test
    public void aFromlessItemWaitsForTheHaving() {
        assertCells(engine, new String[][] {
            {"SELECT 1/0 AS x HAVING 1 = 0", "no row"},
            {"SELECT 1/0 HAVING FALSE", "no row"},
            {"SELECT RANDOM(NULL) HAVING 1 = 0", "no row"},
            {"SELECT 1/0 AS x HAVING NULL", "no row"},
            {"SELECT 1/0 HAVING RANDOM() = 1.5", "no row"},
            {"SELECT 1/0 WHERE FALSE HAVING TRUE", "no row"},
            {"SELECT 1/0 AS x HAVING 1 = 1 LIMIT 0", "no row"},
            {"SELECT 1/0 AS a, 5 AS b HAVING b = 6", "no row"},
            {"SELECT 1/0 AS x HAVING 1 = 1", DIVISION},
            {"SELECT 1/0 AS x HAVING x = 1", DIVISION},
            {"SELECT 1/0 AS a HAVING a IS NULL", DIVISION},
            {"SELECT 1/0 AS a, 5 AS b HAVING b = 5", DIVISION},
            {"SELECT RANDOM(NULL) HAVING TRUE", "Invalid parameter value: NULL. Reason: seed must not be NULL"},
        });
    }

    @Test
    public void anAggregatedItemWaitsForTheHavingToo() {
        assertCells(engine, new String[][] {
            {"SELECT 1/0 AS x, COUNT(*) HAVING 1 = 0", "no row"},
            {"SELECT COUNT(*), 1/0 HAVING COUNT(*) = 0", "no row"},
            {"SELECT 1/0 AS x HAVING SUM(1) = 2", "no row"},
            {"SELECT COUNT(*), 1/0 HAVING COUNT(*) = 1", DIVISION},
            {"SELECT 1/0 AS x HAVING MAX(1/0) IS NULL", DIVISION},
            {"SELECT SUM(v)/0 FROM gq HAVING COUNT(*) = 5", "no row"},
            {"SELECT v, 1/0 FROM gq GROUP BY v HAVING v = 5", "no row"},
            {"SELECT COUNT(*), 1/0 FROM gq GROUP BY v HAVING COUNT(*) > 5", "no row"},
            {"SELECT 1/0 FROM gq GROUP BY v HAVING MAX(v) > 5", "no row"},
            {"SELECT DISTINCT v, 1/0 FROM gq GROUP BY v HAVING v = 5", "no row"},
            {"SELECT v, 1/0 AS x FROM gq GROUP BY v ORDER BY v LIMIT 0", "no row"},
            {"SELECT v, 1/(v-2) AS r FROM gq GROUP BY v HAVING r > 0", DIVISION},
        });
    }

    @Test
    public void theGroupsThatSurviveAreComputed() {
        assertCells(engine, new String[][] {
            {"SELECT v, TO_VARCHAR(1/(v-2)) FROM gq GROUP BY v HAVING v <> 2 ORDER BY v", "1, -1.000000 | 3, 1.000000"},
            {"SELECT TO_VARCHAR(MAX(v) / (MAX(v) - 3)) AS q FROM gq GROUP BY v HAVING MAX(v) < 3 ORDER BY q",
                "-0.500000 | -2.000000"},
            {"SELECT v, TO_VARCHAR(1/(v-2)) FROM gq GROUP BY v ORDER BY v LIMIT 1", "1, -1.000000"},
            {"SELECT v, TO_VARCHAR(1/(v-2)) AS r, ROW_NUMBER() OVER (ORDER BY v) AS rn FROM gq GROUP BY v"
                + " QUALIFY rn <> 2 ORDER BY v", "1, -1.000000, 1 | 3, 1.000000, 3"},
            {"SELECT v, 1/(v-2) FROM gq GROUP BY v ORDER BY v", DIVISION},
        });
    }

    @Test
    public void anAggregatesOwnArgumentRaisesAsTheGroupsAreComputed() {
        assertCells(engine, new String[][] {
            {"SELECT SUM(v/0) FROM gq HAVING COUNT(*) = 5", DIVISION},
            {"SELECT MAX(1/0) HAVING COUNT(*) = 5", DIVISION},
            {"SELECT MAX(1/0) HAVING RANDOM() = 1.5", DIVISION},
            {"SELECT v/0 AS k, COUNT(*) FROM gq GROUP BY k HAVING COUNT(*) > 100", DIVISION},
            {"SELECT 1/0 AS x GROUP BY 1 HAVING RANDOM() = 1.5", DIVISION},
        });
    }

    @Test
    public void aConstantHavingIsSettledBeforeAnyGroup() {
        assertCells(engine, new String[][] {
            {"SELECT SUM(v/0) FROM gq HAVING FALSE", "no row"},
            {"SELECT SUM(v/0) FROM gq HAVING 1 = 0", "no row"},
            {"SELECT SUM(v/0) FROM gq HAVING NULL", "no row"},
            {"SELECT SUM(v/0) FROM gq HAVING 'a' = 'b'", "no row"},
            {"SELECT MAX(1/0) HAVING FALSE", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING FALSE", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING NOT TRUE", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING 1 IN (2, 3)", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING 1 = 1 AND FALSE", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING ABS(-1) = 2", "no row"},
            {"SELECT v, SUM(v/0) FROM gq GROUP BY v HAVING FALSE", "no row"},
            {"SELECT 1/0 AS x GROUP BY 1 HAVING 1 = 0", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING TRUE", DIVISION},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING k > 1", DIVISION},
        });
    }

    @Test
    public void aConstantHavingThatFaultsIsRaisedWhereThePlanReachesIt() {
        assertCells(engine, new String[][] {
            {"SELECT v FROM ge GROUP BY v HAVING 1/0 = 1", "no row"},
            {"SELECT v FROM gq WHERE v > 5 GROUP BY v HAVING TO_NUMBER('x') = 1", "no row"},
            {"SELECT v FROM gq WHERE FALSE GROUP BY v HAVING 1/0 = 1", "no row"},
            {"SELECT v, COUNT(*) FROM ge GROUP BY v HAVING TO_NUMBER('x') = 1", "no row"},
            {"SELECT v FROM ge GROUP BY v HAVING TO_DATE('2024-13-45') IS NULL", "no row"},
            {"SELECT COUNT(*) FROM ge GROUP BY ROLLUP (v) HAVING 1/0 = 1", "no row"},
            {"SELECT v/0 AS k FROM gq GROUP BY k HAVING TO_NUMBER('x') = 1", NOT_A_NUMBER},
            {"SELECT v, SUM(v/0) FROM gq GROUP BY v HAVING TO_NUMBER('x') = 1", NOT_A_NUMBER},
            {"SELECT SUM(v/0) FROM gq HAVING TO_NUMBER('x') = 1", DIVISION},
            {"SELECT SUM(TO_NUMBER('y' || v)) FROM gq HAVING TO_NUMBER('x') = 1",
                "Numeric value 'y1' is not recognized"},
            {"SELECT COUNT(*) FROM ge HAVING 1/0 = 1", DIVISION},
            {"SELECT COUNT(*) FROM gq WHERE FALSE HAVING 1/0 = 1", DIVISION},
            {"SELECT COUNT(*), 1/0 FROM gq HAVING TO_NUMBER('x') = 1", NOT_A_NUMBER},
        });
    }

    @Test
    public void aSubqueryThatReadsARelationRaisesAheadOfTheRows() {
        assertCells(engine, new String[][] {
            {"SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v HAVING v = 5", SEVERAL_ROWS},
            {"SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v HAVING FALSE", SEVERAL_ROWS},
            {"SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v HAVING NULL", SEVERAL_ROWS},
            {"SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v LIMIT 0", SEVERAL_ROWS},
            {"SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v HAVING 1/0 = 1", SEVERAL_ROWS},
            {"SELECT v/0 AS k, (SELECT v FROM gq) AS x FROM gq GROUP BY k HAVING FALSE", SEVERAL_ROWS},
            {"SELECT v, (SELECT v FROM gq) AS x FROM ge GROUP BY v HAVING FALSE", SEVERAL_ROWS},
            {"SELECT MAX(v) AS m, (SELECT v FROM gq) AS x FROM gq HAVING m = 5", SEVERAL_ROWS},
            {"SELECT (SELECT v FROM gq) AS x, COUNT(*) FROM gq HAVING TO_NUMBER('x') = 1", SEVERAL_ROWS},
            {"SELECT COUNT(*), (SELECT v FROM gq) AS x FROM gq WHERE FALSE HAVING COUNT(*) = 1", SEVERAL_ROWS},
            {"SELECT (SELECT v FROM gq) AS x HAVING 1 = 0", SEVERAL_ROWS},
            {"SELECT (SELECT v FROM gq) AS x WHERE 1 = 0", SEVERAL_ROWS},
            {"SELECT 1/0 AS y, (SELECT v FROM gq) AS x", SEVERAL_ROWS},
            {"SELECT v, (SELECT 1 UNION ALL SELECT 2) AS x FROM gq GROUP BY v HAVING v = 5", SEVERAL_ROWS},
            {"SELECT v, (SELECT v/0 FROM gq WHERE v = 1) AS x FROM gq GROUP BY v HAVING v = 5", DIVISION},
            {"SELECT v, (SELECT MAX(v)/0 FROM gq GROUP BY v HAVING v = 1) AS x FROM gq GROUP BY v HAVING v = 5",
                DIVISION},
            {"SELECT (SELECT SUM(v/0) FROM gq) AS x HAVING 1 = 0", DIVISION},
        });
    }

    @Test
    public void whatASubqueryFoldsIntoItsItemWaitsForTheRow() {
        assertCells(engine, new String[][] {
            {"SELECT v, (SELECT 1/0) AS x FROM gq GROUP BY v HAVING v = 5", "no row"},
            {"SELECT v, (SELECT MAX(v)/0 FROM gq) AS x FROM gq GROUP BY v HAVING v = 5", "no row"},
            {"SELECT v, (SELECT MAX(v)/0 FROM gq) AS x FROM gq GROUP BY v HAVING FALSE", "no row"},
            {"SELECT (SELECT MAX(v)/0 FROM gq) AS x HAVING 1 = 0", "no row"},
            {"SELECT v, (SELECT v FROM gq WHERE v = 1) + 1/0 AS x FROM gq GROUP BY v HAVING v = 5", "no row"},
            {"SELECT v, CASE WHEN v > 5 THEN (SELECT v FROM gq) END AS x FROM gq GROUP BY v HAVING FALSE", "no row"},
            {"SELECT v/0 AS k, (SELECT MAX(v) FROM gq) AS m FROM gq GROUP BY k HAVING FALSE", "no row"},
            {"SELECT v FROM (SELECT v, (SELECT v FROM gq) AS x FROM gq GROUP BY v) ORDER BY v", "1 | 2 | 3"},
        });
    }

    @Test
    public void theOneGroupOfNoRowsComputesItsItemsToo() {
        assertCells(engine, new String[][] {
            {"SELECT 'x', COUNT(*) FROM ge", "x, 0"},
            {"SELECT 1/0, COUNT(*) FROM ge", DIVISION},
            {"SELECT MAX(v), 1/0 FROM gq WHERE v > 5", DIVISION},
            {"SELECT 1/0 AS x FROM ge HAVING COUNT(*) = 0", DIVISION},
            {"SELECT 1/0 AS x FROM ge HAVING COUNT(*) = 1", "no row"},
            {"SELECT 1/0 FROM gq WHERE v > 5 HAVING MAX(v) IS NULL", DIVISION},
            {"SELECT 1/0 FROM gq WHERE v > 5 HAVING MAX(v) IS NOT NULL", "no row"},
            {"SELECT 1/0 WHERE FALSE HAVING COUNT(*) = 0", DIVISION},
            {"SELECT RANDOM(NULL) AS x FROM gq HAVING COUNT(*) = 0", "no row"},
        });
    }

    @Test
    public void aRelationReadsOnlyTheCellsItNeeds() {
        assertCells(engine, new String[][] {
            {"SELECT TO_VARCHAR(r) FROM (SELECT v, 1/(v-2) AS r FROM gq GROUP BY v) WHERE v <> 2 ORDER BY r",
                "-1.000000 | 1.000000"},
            {"SELECT v FROM (SELECT v, 1/(v-2) AS r FROM gq GROUP BY v) ORDER BY v", "1 | 2 | 3"},
            {"SELECT COUNT(*) FROM (SELECT v, 1/(v-2) AS r FROM gq GROUP BY v)", "3"},
            {"SELECT COUNT(*) FROM (SELECT v, 1/(v-2) AS r FROM gq GROUP BY v) WHERE r > 0", DIVISION},
            {"SELECT m FROM (SELECT MAX(v) AS m, 1/0 AS z FROM gq)", "3"},
            {"SELECT * FROM (SELECT MAX(v) AS m, 1/0 AS z FROM gq) WHERE m > 5", "no row"},
            {"WITH c AS (SELECT MAX(v) AS m, 1/0 AS z FROM gq) SELECT m FROM c", "3"},
            {"SELECT (SELECT MAX(v)/0 FROM gq HAVING COUNT(*) = 5) AS x", "null"},
            {"SELECT (SELECT 1/0 FROM gq HAVING COUNT(*) = 3) AS x", DIVISION},
            {"SELECT v FROM gq WHERE v IN (SELECT MAX(v)/0 FROM gq HAVING COUNT(*) = 5)", "no row"},
            {"SELECT IFF(EXISTS (SELECT 1/0, COUNT(*) FROM gq), 'y', 'n')", "y"},
            {"SELECT v, TO_VARCHAR(1/(v-2)) FROM gq GROUP BY v HAVING v <> 2 UNION ALL SELECT 9, '9' ORDER BY 1",
                "1, -1.000000 | 3, 1.000000 | 9, 9"},
            {"SELECT v, 1/(v-2) FROM gq GROUP BY v UNION ALL SELECT 9, 9 ORDER BY 1", DIVISION},
        });
    }

    @Test
    public void aViewOverAGroupedFaultRaisesOnlyWhereItIsRead() {
        engine.execute("CREATE OR REPLACE VIEW gvw AS SELECT v, 1/(v-2) AS r FROM gq GROUP BY v");
        assertCells(engine, new String[][] {
            {"SELECT v FROM gvw ORDER BY v", "1 | 2 | 3"},
            {"SELECT TO_VARCHAR(r) FROM gvw WHERE v <> 2 ORDER BY r", "-1.000000 | 1.000000"},
            {"SELECT * FROM gvw ORDER BY v", DIVISION},
        });
    }

    @Test
    public void aWriteNamesTheColumnItsFaultIsIn() {
        engine.execute("CREATE OR REPLACE TABLE gt (a NUMBER, b NUMBER(10,2))");
        assertCells(engine, new String[][] {
            {"INSERT INTO gt SELECT v, 1/(v-2) FROM gq GROUP BY v",
                "DML operation to table GT failed on column B with error: Division by zero"},
            {"INSERT INTO gt SELECT MAX(v), 1/0 FROM gq",
                "DML operation to table GT failed on column B with error: Division by zero"},
        });
        engine.execute("INSERT INTO gt SELECT v, 1/(v-2) FROM gq GROUP BY v HAVING v <> 2");
        assertEquals("2", answer(engine, "SELECT COUNT(*) FROM gt"));
        final String ctas = answer(engine, "CREATE OR REPLACE TABLE gt3 AS SELECT v, 1/(v-2) AS r FROM gq GROUP BY v");
        assertTrue(ctas.startsWith("DML operation to table ")
            && ctas.endsWith("GT3 failed on column R with error: Division by zero"), ctas);
        engine.execute("CREATE OR REPLACE TABLE gt4 AS SELECT v, 1/(v-2) AS r FROM gq GROUP BY v HAVING v <> 2");
        assertEquals("2", answer(engine, "SELECT COUNT(*) FROM gt4"));
    }

    @Test
    public void aBlockStatementReadsTheRowsItKeeps() {
        assertCells(engine, new String[][] {
            {"BEGIN SELECT 1/0, COUNT(*) FROM gq WHERE v > 5; RETURN 'ok'; END;",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero"},
            {"BEGIN SELECT 1/0, COUNT(*) FROM gq WHERE v > 5 HAVING COUNT(*) > 0; RETURN 'ok'; END;", "ok"},
            {"BEGIN SELECT 1/0 AS x HAVING 1 = 0; RETURN 'ok'; END;", "ok"},
            {"BEGIN SELECT 1/0 AS x HAVING 1 = 1; RETURN 'ok'; END;",
                "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero"},
            {"BEGIN LET x NUMBER := (SELECT MAX(v)/0 FROM gq HAVING COUNT(*) = 5); RETURN x; END;", "null"},
        });
    }

    @Test
    public void aWhereOrHavingReadingAFaultingAliasRaisesIt() {
        assertCells(engine, new String[][] {
            {"SELECT v/0 AS x FROM gq WHERE x = 5", DIVISION},
            {"SELECT v/0 AS x FROM gq WHERE x IS NOT NULL", DIVISION},
            {"SELECT v/0 AS x, x + 1 AS y FROM gq WHERE y = 5", DIVISION},
            {"SELECT v/(v-2) AS x FROM gq WHERE x > 0", DIVISION},
            {"SELECT TO_VARCHAR(v/(v-2)) AS x FROM gq WHERE v <> 2 AND v/(v-2) > 0", "3.000000"},
            {"SELECT v/0 AS x, v FROM gq WHERE v = 5 AND x = 1", "no row"},
            {"SELECT v/0 AS x FROM gq HAVING x = 5", DIVISION},
            {"SELECT v/0 AS x FROM gq HAVING v = 5", "no row"},
            {"SELECT 1 FROM gq HAVING 1/0 = 1", DIVISION},
        });
    }
}
