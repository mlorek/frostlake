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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a subquery holding a query's own aggregate may stand. An aggregate written inside a subquery — however
 * deep — over the columns of a query around it alone is that query's aggregate, and the subquery holding it
 * stands in that query's select list or HAVING and nowhere else: in its WHERE, a join's ON, its GROUP BY or its
 * ORDER BY, and where an ORDER BY or GROUP BY key names a select item holding it, the statement is refused while
 * it compiles, once its names have resolved and whatever rows it would read. The refusal echoes the first such
 * call as the plan holds it. The WHERE of an UPDATE and of a DELETE is held to the same. Every row is
 * live-verified.
 */
public class CorrelatedAggregatePlaceTest extends BaseDatabaseTest {

    private static final String TAIL = "] can only appear in having or select clause";

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] PLACES = {
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE (SELECT MAX(fz.id) FROM g WHERE g.id = 5) = 5 ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id IN (SELECT MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE NOT EXISTS (SELECT COUNT(fz.s) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [COUNT(FZ.S)] can only appear in having "
                + "or select clause"},
        {"SELECT id FROM fz ORDER BY (SELECT MAX(fz.id) FROM g WHERE g.id = 5)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT COUNT(*) FROM fz GROUP BY (SELECT MAX(fz.id) FROM g WHERE g.id = 5)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz GROUP BY id ORDER BY 1",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT (SELECT SUM(fz.id) FROM g) FROM fz GROUP BY b ORDER BY 1",
            "REFUSED Subquery containing correlated aggregate function [SUM(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT MAX(fz.id))) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g g1 WHERE EXISTS (SELECT MAX(g1.id) FROM fz f2)) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(G1.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) FROM fz WHERE id IN (SELECT MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY 2",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY 2",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY e",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY e, id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY id, 2",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE FALSE AND EXISTS (SELECT MAX(fz.id) FROM g)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g) LIMIT 0",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id > 100 AND EXISTS (SELECT MAX(fz.id) FROM g)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(b) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.B)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id + 1) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID + 1)] can only appear in "
                + "having or select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT max( fz.id ) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz f WHERE EXISTS (SELECT MAX(f.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(F.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT COUNT(DISTINCT fz.s) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [COUNT(DISTINCT FZ.S)] can only appear in "
                + "having or select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MIN(fz.id), MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MIN(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 + MAX(fz.id) * 2 FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.s, ',') FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [LISTAGG(FZ.S, ',')] can only appear in "
                + "having or select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g HAVING MAX(fz.id) > 1) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = MAX(fz.id)) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g ORDER BY MAX(fz.id)) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id = 'x' AND EXISTS (SELECT MAX(fz.id) FROM g)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM (SELECT * FROM fz) d WHERE EXISTS (SELECT MAX(d.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(D.ID)] can only appear in having or "
                + "select clause"},
        {"WITH c AS (SELECT * FROM fz) SELECT id FROM c WHERE EXISTS (SELECT MAX(c.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(C.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz ORDER BY EXISTS (SELECT MAX(fz.id) FROM g), id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT COUNT(*) FROM fz GROUP BY EXISTS (SELECT MAX(fz.id) FROM g)",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT fz.id FROM fz JOIN g ON g.id = fz.id AND EXISTS (SELECT MAX(fz.id) FROM g g2) ORDER BY 1",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id IN (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id = ANY (SELECT MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id > (SELECT COUNT(fz.id) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [COUNT(FZ.ID)] can only appear in having "
                + "or select clause"},
        {"SELECT id FROM fz WHERE CASE WHEN EXISTS (SELECT MAX(fz.id) FROM g) THEN TRUE ELSE FALSE END ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE EXISTS (SELECT MAX(fz.id))) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.v > (SELECT MAX(g.id) FROM fz f2)) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(G.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, (SELECT COUNT(*) FROM g WHERE EXISTS (SELECT MAX(g.id) FROM fz f2 WHERE f2.id = fz.id)) "
            + "FROM fz ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(G.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT EXISTS (SELECT MAX(fz.id) FROM g) AS e, COUNT(*) FROM fz GROUP BY 1",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT EXISTS (SELECT MAX(fz.id) FROM g) AS e, COUNT(*) FROM fz GROUP BY e",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT EXISTS (SELECT MAX(fz.id) FROM g) AS e, COUNT(*) FROM fz GROUP BY ALL",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz GROUP BY id ORDER BY 2, 1",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz GROUP BY id ORDER BY e, id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT DISTINCT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY 2, 1",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT LISTAGG(fz.s, ',') WITHIN GROUP (ORDER BY fz.s) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [LISTAGG(FZ.S, ',')] can only appear in "
                + "having or select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(id + 1) FROM (SELECT 1 AS z)) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID + 1)] can only appear in "
                + "having or select clause"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT SUM(fz.id) + MAX(g.v) FROM g) ORDER BY id",
            "REFUSED Subquery containing correlated aggregate function [SUM(FZ.ID)] can only appear in having or "
                + "select clause"},
        {"SELECT COUNT(*) FROM (SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g))",
            "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or "
                + "select clause"},
    };

    private static final String[][] NAMES_FIRST = {
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g) AND nosuch = 1",
            "REFUSED invalid identifier 'NOSUCH'"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.nosuch) FROM g)", "REFUSED invalid identifier 'FZ.NOSUCH'"},
        {"SELECT nosuch FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g)", "REFUSED invalid identifier 'NOSUCH'"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g WHERE g.nosuch = 1)",
            "REFUSED invalid identifier 'G.NOSUCH'"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fo (id INT, k VARCHAR)");
        engine.execute("INSERT INTO fo VALUES (5, 'a'), (7, 'b'), (9, 'd'), (NULL, 'a'), (NULL, 'b')");
        engine.execute("CREATE OR REPLACE TABLE gn (id INT, k VARCHAR)");
        engine.execute("INSERT INTO gn VALUES (NULL, 'a'), (5, 'a'), (6, 'c'), (NULL, 'd')");
        engine.execute("CREATE OR REPLACE TABLE fn (id INT, n INT)");
        engine.execute("INSERT INTO fn VALUES (0, NULL), (2, 1), (5, NULL)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
    }

    /** A statement's answer as one text: a bar between cells, " ;; " between rows; a refusal as REFUSED and its words. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            boolean firstRow = true;
            while (rs.next()) {
                out.append(firstRow ? "" : " ;; ");
                firstRow = false;
                for (int c = 0; c < rs.getColumnCount(); c++) {
                    out.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** Each statement answers the given text, or is refused in the given words. */
    private void assertAnswers(final String[][] cases) {
        for (final String[] one : cases) {
            final String answered = answer(one[0]);
            if (one[1].startsWith("REFUSED ")) {
                assertTrue(answered.startsWith("REFUSED ") && answered.contains(one[1].substring("REFUSED ".length())),
                    one[0] + " -> " + answered);
            } else {
                assertEquals(one[1], answered, one[0]);
            }
        }
    }

    @Test
    public void aSubqueryHoldingTheQuerysAggregateStandsInItsSelectListOrHaving() {
        assertAnswers(PLACES);
    }

    @Test
    public void aNameThatDoesNotResolveIsRefusedAhead() {
        assertAnswers(NAMES_FIRST);
    }

    @Test
    public void aRewriteAndAViewAreHeldToTheSame() {
        engine.execute("CREATE OR REPLACE TABLE t5 (id INT, e BOOLEAN)");
        engine.execute("INSERT INTO t5 VALUES (5, TRUE), (7, TRUE)");
        assertAnswers(new String[][] {
            {"DELETE FROM t5 WHERE EXISTS (SELECT MAX(t5.id) FROM g)",
                "REFUSED Subquery containing correlated aggregate function [MAX(T5.ID)" + TAIL},
            {"UPDATE t5 SET e = FALSE WHERE EXISTS (SELECT MAX(t5.id) FROM g)",
                "REFUSED Subquery containing correlated aggregate function [MAX(T5.ID)" + TAIL},
            {"CREATE OR REPLACE VIEW v6 AS SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g)",
                "REFUSED Subquery containing correlated aggregate function [MAX(FZ.ID)" + TAIL},
            {"SELECT * FROM t5 ORDER BY id", "5|true ;; 7|true"},
        });
    }
}
