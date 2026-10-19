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
 * An aggregate written inside a subquery over the columns of the query around it alone is that query's own
 * aggregate, not the subquery's. In the select list of an EXISTS it is a value the rows are not counted by: the
 * EXISTS answers as if a constant stood there, so {@code EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id)}
 * is TRUE where a row of g matches and FALSE elsewhere, and {@code EXISTS (SELECT MAX(fz.id) FROM g WHERE FALSE)}
 * is FALSE where an aggregate of its own would answer one row. Where the value itself is read — under DISTINCT,
 * by a HAVING, as a scalar subquery's result or an IN's item — the subquery is refused. An aggregate of the
 * EXISTS's own that reads the outer row inside its argument is refused too, unless the select groups without it.
 * Every row is live-verified.
 */
public class OuterAggregateExistsTest extends BaseDatabaseTest {

    /** A statement, and its answer or the words it is refused in. */
    private static final String[][] EXISTS = {
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE FALSE) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) + 1 FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT COUNT(fz.s) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g GROUP BY g.s) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id)) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, NOT EXISTS (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id FROM fz QUALIFY EXISTS (SELECT MAX(fz.id) FROM g)",
            "REFUSED found QUALIFY clause but no window function."},
        {"SELECT MAX(id) FROM fz HAVING EXISTS (SELECT MAX(fz.id) FROM g)", "7"},
        {"SELECT f1.id, (SELECT COUNT(*) FROM fz f2 WHERE EXISTS (SELECT MAX(f1.id) FROM g WHERE g.id = "
            + "f2.id)) FROM fz f1 ORDER BY 1",
            "5|1 ;; 7|1"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) OR FALSE FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, CASE WHEN EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) THEN 'y' ELSE 'n' END "
            + "FROM fz ORDER BY id",
            "5|y ;; 7|n"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id), g.v FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id), MAX(g.v) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING MAX(fz.id) > 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT 1 FROM g WHERE g.v > MAX(fz.id)) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT 1 FROM g HAVING MAX(fz.id) > 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g ORDER BY 1) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g LIMIT 1) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) OVER () FROM g) FROM fz ORDER BY id",
            "REFUSED Window function [MAX(FZ.ID) OVER ()] contains a correlation."},
        {"SELECT id, EXISTS (SELECT MAX(b) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g UNION ALL SELECT 1 FROM g WHERE FALSE) FROM fz ORDER BY id",
            "5|true ;; 7|true"},
        {"SELECT s, MAX(id) FROM fz GROUP BY s HAVING EXISTS (SELECT MAX(fz.id) FROM g) ORDER BY s", "a|5 ;; b|7"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) FROM fz GROUP BY id ORDER BY id", "5|true ;; 7|true"},
        {"SELECT s, EXISTS (SELECT MAX(fz.id) FROM g) FROM fz GROUP BY s ORDER BY s", "a|true ;; b|true"},
        {"SELECT s, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = MAX(fz.id)) FROM fz GROUP BY s ORDER BY s",
            "a|true ;; b|false"},
        {"SELECT s FROM fz GROUP BY s HAVING EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) ORDER BY s",
            "REFUSED [FZ.ID] is not a valid group by expression"},
        {"SELECT COUNT(*), EXISTS (SELECT MAX(fz.id) FROM g) FROM fz", "2|true"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id + g.v) FROM g) ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 24"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) OVER () FROM g) ORDER BY id",
            "REFUSED Window function [MAX(FZ.ID) OVER ()] contains a correlation."},
        {"SELECT id FROM fz QUALIFY EXISTS (SELECT MAX(fz.id) FROM g) ORDER BY id",
            "REFUSED found QUALIFY clause but no window function."},
        {"SELECT id, (SELECT COUNT(*) FROM g WHERE EXISTS (SELECT MAX(fz.id))) FROM fz ORDER BY id", "5|2 ;; 7|2"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz WHERE id > 5 ORDER BY id", "7|false"},
        {"SELECT id, EXISTS (SELECT LISTAGG(fz.s, ',') FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MIN_BY(fz.id, fz.s) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT COUNT(DISTINCT fz.s) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id AND g.v > 0) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT DISTINCT MAX(fz.id) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) AS m FROM g HAVING m > 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING COUNT(*) > 5) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING COUNT(*) = 2) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING MAX(fz.id) > 6) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g ORDER BY MAX(g.v)) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g LIMIT 0) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id), COUNT(*) FROM g WHERE FALSE) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE FALSE GROUP BY g.s) FROM fz ORDER BY id",
            "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g GROUP BY fz.s) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = 7) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AND id > 5 FROM fz ORDER BY id", "5|false ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.s = fz.s) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g g1 JOIN g g2 ON g1.id = g2.id WHERE g1.id = fz.id) FROM "
            + "fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE "
            + "FALSE) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id LIMIT 1) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id > fz.id) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM (SELECT * FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) FROM fz WHERE FALSE", ""},
        {"SELECT id, NOT EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|false ;; 7|true"},
        {"SELECT fz.id, EXISTS (SELECT MAX(fz.id + f2.id) FROM g) FROM fz JOIN fz f2 ON f2.id = fz.id ORDER BY 1",
            "5|true ;; 7|true"},
        {"SELECT fz.id, EXISTS (SELECT MAX(f2.id) FROM g WHERE g.id = fz.id) FROM fz JOIN fz f2 ON f2.id = "
            + "fz.id ORDER BY 1",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(d.id) FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz) d ORDER BY id",
            "5|true ;; 7|false"},
        {"WITH c AS (SELECT * FROM fz) SELECT id, EXISTS (SELECT MAX(c.id) FROM g WHERE g.id = c.id) FROM c "
            + "ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT x, EXISTS (SELECT MAX(t.x) FROM g WHERE g.id = t.x) FROM (VALUES (5), (7)) t(x) ORDER BY x",
            "5|true ;; 7|false"},
        {"SELECT id, CASE WHEN id > 5 THEN EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) END FROM fz "
            + "ORDER BY id",
            "5|null ;; 7|false"},
        {"SELECT id, IFF(EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id), 1, 0) FROM fz ORDER BY id",
            "5|1 ;; 7|0"},
        {"SELECT SUM(IFF(EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id), 1, 0)) FROM fz", "1"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id + g.v) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id + g.v) FROM g GROUP BY g.s) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id + g.v) FROM g HAVING COUNT(*) > 0) FROM fz ORDER BY id",
            "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT 1 FROM g HAVING MAX(fz.id + g.v) > 0) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id GROUP BY g.s) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) AS m FROM g ORDER BY m) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT TOP 1 MAX(fz.id) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY g.id) = 1) FROM fz "
            + "ORDER BY id",
            "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id), g.v FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING TRUE) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g HAVING FALSE) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.v > 100) FROM fz ORDER BY id", "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT AVG(fz.id) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT COUNT_IF(fz.b) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT ARRAY_AGG(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT LISTAGG(fz.s, ',') WITHIN GROUP (ORDER BY fz.s) FROM g WHERE g.id = "
            + "fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) + MIN(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(id) FROM (SELECT 1 AS z) WHERE z = id - 4) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g UNION ALL SELECT MAX(fz.id) FROM g WHERE FALSE) FROM fz "
            + "ORDER BY id",
            "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE FALSE UNION ALL SELECT MIN(fz.id) FROM g WHERE "
            + "FALSE) FROM fz ORDER BY id",
            "5|false ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id UNION ALL SELECT MIN(fz.id) FROM g "
            + "WHERE g.v = fz.id) FROM fz ORDER BY id",
            "5|true ;; 7|false"},
        {"SELECT id, EXISTS ((SELECT MAX(fz.id) FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id", "5|true ;; 7|false"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id IN (fz.id, 6)) FROM fz ORDER BY id",
            "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id + fz.id = 10) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT s FROM fz GROUP BY s HAVING EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = MAX(fz.id)) ORDER BY s",
            "a"},
        {"SELECT s FROM fz GROUP BY s HAVING EXISTS (SELECT MAX(fz.id) FROM g) ORDER BY s", "a ;; b"},
        {"SELECT COUNT(*) FROM fz HAVING EXISTS (SELECT MAX(fz.id) FROM g)", "2"},
        {"SELECT COUNT(*) FROM fz HAVING EXISTS (SELECT MAX(fz.id) FROM g WHERE FALSE)", ""},
        {"SELECT * FROM (SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) AS e FROM fz) ORDER BY id",
            "5|true ;; 7|false"},
        {"WITH c AS (SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) AS e FROM fz) SELECT * "
            + "FROM c WHERE e ORDER BY id",
            "5|true"},
    };

    private static final String[][] VALUES_READ = {
        {"SELECT id, (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT SUM(fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT COUNT(fz.s) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT COUNT(fz.id) + COUNT(*) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g LIMIT 1) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MIN(fz.id) FROM g WHERE g.id = 6) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT AVG(fz.id) FROM g WHERE g.id = 6) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id + g.v) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id)) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g GROUP BY g.s ORDER BY 1 LIMIT 1) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT LISTAGG(fz.s) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT ARRAY_AGG(fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT COUNT(DISTINCT fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g WHERE g.id = 5), (SELECT MAX(g.v) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT s, MAX(id), (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz GROUP BY s ORDER BY s",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 20"},
        {"SELECT s, (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz GROUP BY s ORDER BY s",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
        {"SELECT (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 8"},
        {"SELECT MAX(id), (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 17"},
        {"SELECT id, (SELECT MAX(fz.id) FROM g WHERE g.v > (SELECT MIN(fz.id) FROM g)) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT id, (SELECT MAX(g.v) + SUM(fz.id) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 12"},
        {"SELECT (SELECT MAX(fz.id) FROM g WHERE g.id = 5) FROM fz GROUP BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 8"},
        {"SELECT id, id IN (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 18"},
        {"SELECT id, id IN (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 18"},
        {"SELECT id, id NOT IN (SELECT MAX(fz.id) FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 22"},
        {"SELECT id FROM fz WHERE id IN (SELECT MAX(fz.id + g.v) FROM g) ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 31"},
        {"SELECT id, id IN (SELECT MAX(fz.id + g.v) - 60 FROM g) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 18"},
    };

    private static final String[][] OWN_AGGREGATES = {
        {"SELECT id, id = (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|true ;; 7|null"},
        {"SELECT id, (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|5 ;; 7|null"},
        {"SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|1 ;; 7|0"},
        {"SELECT id, EXISTS (SELECT MAX(g.id) FROM g WHERE g.id = 99) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT MAX(id) FROM g) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g ORDER BY MAX(g.v)) ORDER BY id", "5 ;; 7"},
        {"SELECT id, EXISTS (SELECT 1 FROM g ORDER BY MAX(g.v)) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT 1 FROM g WHERE FALSE ORDER BY MAX(g.v)) FROM fz ORDER BY id", "5|true ;; 7|true"},
        {"SELECT id, EXISTS (SELECT SUM(g.v * fz.id) FROM g WHERE g.id = 5) FROM fz ORDER BY id",
            "REFUSED Unsupported subquery type cannot be evaluated at line 1, position 11"},
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
    public void anExistsCountsItsRowsWithoutTheOuterQuerysAggregate() {
        assertAnswers(EXISTS);
    }

    @Test
    public void aSubqueryReadForTheAggregatesValueIsRefused() {
        assertAnswers(VALUES_READ);
    }

    @Test
    public void anAggregateOfTheSubquerysOwnStaysItsOwn() {
        assertAnswers(OWN_AGGREGATES);
    }

    @Test
    public void aStatementKeepingTheRowsKeepsTheAnswer() {
        engine.execute("CREATE OR REPLACE TABLE t6 AS SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) AS e"
            + " FROM fz");
        engine.execute("CREATE OR REPLACE VIEW v7 AS SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) AS e"
            + " FROM fz");
        engine.execute("CREATE OR REPLACE TABLE t5 (id INT, e BOOLEAN)");
        engine.execute("INSERT INTO t5 VALUES (5, FALSE), (7, TRUE)");
        engine.execute("UPDATE t5 SET e = EXISTS (SELECT MAX(t5.id) FROM g WHERE g.id = t5.id)");
        engine.execute("INSERT INTO t5 SELECT id + 10, EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id) FROM fz");
        assertAnswers(new String[][] {
            {"SELECT * FROM t6 ORDER BY id", "5|true ;; 7|false"},
            {"SELECT * FROM v7 ORDER BY id", "5|true ;; 7|false"},
            {"SELECT * FROM t5 ORDER BY id", "5|true ;; 7|false ;; 15|true ;; 17|false"},
        });
    }
}
