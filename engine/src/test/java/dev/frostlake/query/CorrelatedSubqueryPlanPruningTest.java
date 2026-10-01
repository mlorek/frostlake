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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A correlated subquery live cannot evaluate is refused when the statement is planned, whether or not a row
 * reaches it — under a WHERE, a LIMIT, a QUALIFY or a join that leaves no row — unless the planner prunes the
 * scan first: a FALSE or NULL literal conjunct, a conjunct the table's statistics prove false of every row, or
 * tables of at most one row. A WHERE, HAVING or QUALIFY whose own constant conjunct folds to FALSE drops the
 * subqueries it holds, a conditional's branches stay with the row that reaches them, and the refusal waits for
 * the statement's other compile-time refusals. Every cell is live-verified.
 */
public class CorrelatedSubqueryPlanPruningTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED =
        "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position ";
    private static final String S = "(SELECT v FROM g WHERE g.id = fz.id)";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE em (id INT, v INT)");
        engine.execute("CREATE OR REPLACE TABLE one (id INT)");
        engine.execute("INSERT INTO one VALUES (5)");
    }

    /** Every row, its cells joined by a colon, the rows by a bar; or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                if (out.length() > 0) {
                    out.append('|');
                }
                for (int i = 0; i < row.getValues().size(); i++) {
                    if (i > 0) {
                        out.append(':');
                    }
                    out.append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aFilterThatKeepsNoRowDoesNotSpareTheSubquery() {
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE 1 = 0"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE id = 6"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE id IN (6)"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE NOT TRUE"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE id > 6 AND id < 7"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE (SELECT FALSE)"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE 1 = 0 OR 2 = 0"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE id > 0 AND 1 = 0"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE b AND NOT b"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " + 0 AS x FROM fz WHERE 1 = 0"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz WHERE 1 = 0 GROUP BY id"));
    }

    @Test
    public void aLimitQualifyOrJoinThatKeepsNoRowDoesNotSpareTheSubquery() {
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz LIMIT 0"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz LIMIT 1 OFFSET 5"));
        assertEquals(UNSUPPORTED + "12",
            answer("SELECT id, " + S + " AS x FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY id) > 5"));
        assertEquals(UNSUPPORTED + "15", answer("SELECT fz.id, " + S + " AS x FROM fz JOIN em ON TRUE"));
        assertEquals(UNSUPPORTED + "15", answer("SELECT fz.id, " + S + " AS x FROM fz JOIN g ON FALSE"));
        assertEquals(UNSUPPORTED + "15", answer("SELECT fz.id, " + S + " AS x FROM em JOIN fz ON TRUE"));
        assertEquals(UNSUPPORTED + "15", answer("SELECT fz.id, " + S + " AS x FROM fz, em"));
    }

    @Test
    public void aScanThePlannerPrunesSparesTheSubquery() {
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE FALSE"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE NULL"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE (FALSE)"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id > 0 AND FALSE"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id > 100"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id < 0 OR id > 100"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id > 100 OR 1 = 0"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE NOT (id > 0)"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id IS NULL"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id BETWEEN 8 AND 9"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE ABS(id) > 100"));
        assertEquals("", answer("SELECT id, " + S + " AS x FROM fz WHERE id > (SELECT 100)"));
        assertEquals("", answer("SELECT fz.id, " + S + " AS x FROM fz JOIN g ON TRUE WHERE fz.id > 100"));
        assertEquals("", answer("SELECT fz.id, " + S + " AS x FROM fz JOIN g ON fz.id > 100"));
        assertEquals("", answer("SELECT id, (SELECT v FROM g WHERE g.id = em.id) AS x FROM em"));
        assertEquals("", answer("SELECT id, (SELECT v FROM g WHERE g.id = one.id) AS x FROM one WHERE 1 = 0"));
    }

    @Test
    public void aWhereSubqueryIsJudgedUnlessTheWhereFoldsOrIsPruned() {
        assertEquals(UNSUPPORTED + "36", answer("SELECT id FROM fz WHERE id = 6 AND " + S + " = 50"));
        assertEquals(UNSUPPORTED + "35",
            answer("SELECT id FROM fz WHERE id = 6 AND EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10)"));
        assertEquals(UNSUPPORTED + "34", answer("SELECT id FROM fz WHERE FALSE OR " + S + " = 50"));
        assertEquals(UNSUPPORTED + "34", answer("SELECT id FROM fz WHERE 1 = 0 OR " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz WHERE FALSE AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz WHERE NULL AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz WHERE " + S + " = 50 AND NULL"));
        assertEquals("", answer("SELECT id FROM fz WHERE 1 = 0 AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz WHERE " + S + " = 50 AND id > 100"));
        assertEquals("",
            answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10) AND id > 100"));
        assertEquals("", answer("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10) LIMIT 0"));
    }

    @Test
    public void aHavingOrQualifyDropsTheSubqueriesItsFalseConjunctFolds() {
        assertEquals("", answer("SELECT id FROM fz GROUP BY id HAVING FALSE AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz GROUP BY id HAVING 1 = 0 AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz GROUP BY id HAVING id > 100 AND " + S + " = 50"));
        assertEquals("", answer("SELECT id FROM fz QUALIFY FALSE AND ROW_NUMBER() OVER (ORDER BY id) = " + S));
        assertEquals(UNSUPPORTED + "47", answer("SELECT id FROM fz GROUP BY id HAVING NULL AND " + S + " = 50"));
        assertEquals(UNSUPPORTED + "47", answer("SELECT id FROM fz GROUP BY id HAVING FALSE OR " + S + " = 50"));
    }

    @Test
    public void theRefusalIsPositionedInEveryClause() {
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz GROUP BY id"));
        assertEquals(UNSUPPORTED + "12", answer("SELECT id, " + S + " AS x FROM fz GROUP BY id HAVING FALSE"));
        assertEquals(UNSUPPORTED + "12",
            answer("SELECT id, " + S + " AS x, ROW_NUMBER() OVER (ORDER BY id) FROM fz"));
        assertEquals(UNSUPPORTED + "28", answer("SELECT id FROM fz ORDER BY " + S + " LIMIT 0"));
        assertEquals(UNSUPPORTED + "38", answer("SELECT id FROM fz GROUP BY id HAVING " + S + " = 50"));
    }

    @Test
    public void theRefusalWaitsForTheStatementsOtherCompileTimeRefusals() {
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(3)] for predicate ['abc']",
            answer("SELECT id, " + S + " AS x FROM fz GROUP BY id HAVING 'abc'"));
        assertEquals("SQL compilation error: error line 1 at position 80|Invalid argument types for function 'SUM': (BOOLEAN)",
            answer("SELECT " + S + " AS x, id FROM fz GROUP BY id HAVING SUM(b) > 0"));
        assertEquals("SQL compilation error: error line 1 at position 51|"
                + "Unsupported: Scalar subquery with multi-column SELECT clause.",
            answer("SELECT " + S + " AS x, (SELECT 1, 2) FROM fz"));
    }

    @Test
    public void aConditionalBranchStaysWithTheRowThatReachesIt() {
        assertEquals("5:1|7:1", answer("SELECT id, IFF(id > 0, 1, " + S + ") AS x FROM fz ORDER BY id"));
        assertEquals("5:1|7:1",
            answer("SELECT id, CASE WHEN id > 0 THEN 1 ELSE " + S + " END AS x FROM fz ORDER BY id"));
        assertEquals("5:1|7:1", answer("SELECT id, IFF(TRUE, 1, " + S + ") AS x FROM fz ORDER BY id"));
        assertEquals("5:1|7:1", answer("SELECT id, COALESCE(1, " + S + ") AS x FROM fz ORDER BY id"));
        assertEquals("5:5|7:7", answer("SELECT id, NVL(id, " + S + ") AS x FROM fz ORDER BY id"));
        assertEquals("", answer("SELECT id, IFF(id > 0, 1, " + S + ") AS x FROM fz WHERE 1 = 0"));
        assertEquals(UNSUPPORTED + "27", answer("SELECT id, IFF(id > 6, 1, " + S + ") AS x FROM fz"));
    }
}
