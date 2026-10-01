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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correlated shapes judged by what they read: a GROUP BY over the outer row is refused under EXISTS and IN — ahead
 * of any pruning, and kept by a positive IN in a WHERE only where it removes duplicates — a comparison with outer
 * names on both sides is refused, as is a negation over the outer row, an IN list is judged by the elements the
 * statistics leave of it, and an outer name the statistics pin to one value — a derived table's literal, a one-row
 * relation's column, a column holding one value of any scalar family — correlates nothing. Every cell is
 * live-verified.
 */
public class CorrelatedShapeRuleGapsTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE q1 (v NUMBER)");
        engine.execute("INSERT INTO q1 VALUES (1)");
        engine.execute("CREATE TABLE h (a INT, c INT, t VARCHAR, u VARCHAR)");
        engine.execute("INSERT INTO h VALUES (1, 2, 'x', 'yy'), (3, 2, 'yyyy', 'z')");
        engine.execute("CREATE TABLE hn (a INT, c INT)");
        engine.execute("INSERT INTO hn VALUES (1, 2), (3, 4)");
        engine.execute("CREATE TABLE ht (a INT, c INT, t VARCHAR, u VARCHAR)");
        engine.execute("INSERT INTO ht VALUES (1, 2, 'x', 'yy'), (3, 4, 'yyyy', 'z')");
        engine.execute("CREATE TABLE ge (id INT)");
    }

    /** Every row, its cells joined by a comma and the rows by a bar, lower-cased. */
    private String rows(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    answer.append(", ");
                }
                answer.append(String.valueOf(row.getValue(i)));
            }
        }
        return answer.toString().toLowerCase();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], rows(cell[0]), cell[0]);
        }
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    private void assertUnsupported(final String[][] cells) {
        for (final String[] cell : cells) {
            assertRefused(cell[0], UNSUPPORTED + cell[1]);
        }
    }

    @Test
    public void aGroupByOverTheOuterRowIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE q.v = 1 GROUP BY q.v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v = 1 GROUP BY v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE q.v = 1 GROUP BY q.v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g GROUP BY q.v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE g.id > q.v GROUP BY g.v, q.v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE g.id > q.v GROUP BY g.v + q.v) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT q.v FROM g WHERE g.id > q.v GROUP BY 1) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT q.v AS w FROM g WHERE g.id > q.v GROUP BY w) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g GROUP BY ROLLUP (g.v, q.v)) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g GROUP BY GROUPING SETS ((g.v), (q.v))) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE EXISTS (SELECT COUNT(*) FROM g GROUP BY q.v HAVING COUNT(*) > 1) ORDER BY v", "22"},
            {"SELECT v FROM q WHERE NOT EXISTS (SELECT 1 FROM g WHERE q.v = 1 GROUP BY q.v) ORDER BY v", "26"},
            {"SELECT v, EXISTS (SELECT 1 FROM g WHERE q.v = 1 GROUP BY q.v) FROM q ORDER BY v", "10"},
            {"SELECT v FROM q WHERE v IN (SELECT 1 FROM g WHERE q.v = 1 GROUP BY q.v) ORDER BY v", "28"},
            {"SELECT v FROM q WHERE v IN (SELECT 1 FROM g GROUP BY q.v) ORDER BY v", "28"},
            {"SELECT v FROM q WHERE v IN (SELECT g.v - 49 FROM g GROUP BY g.v, q.v) ORDER BY v", "28"},
            {"SELECT v FROM q WHERE v IN (SELECT MAX(g.v) - 49 FROM g GROUP BY g.v, q.v) ORDER BY v", "28"},
            {"SELECT v FROM q WHERE v IN (SELECT q.v FROM g GROUP BY q.v) ORDER BY v", "28"},
            // Only a semi-join drops a GROUP BY that removes duplicates: not NOT IN, OR, NOT, a comparison or a
            // select list.
            {"SELECT v FROM q WHERE v NOT IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v", "32"},
            {"SELECT v FROM q WHERE NOT (v IN (SELECT g.v FROM g GROUP BY g.v, q.v)) ORDER BY v", "33"},
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g GROUP BY g.v, q.v) OR v = 2 ORDER BY v", "28"},
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g GROUP BY g.v, q.v) = FALSE ORDER BY v", "28"},
            {"SELECT v, v IN (SELECT g.v FROM g GROUP BY g.v, q.v) FROM q ORDER BY v", "16"},
            {"SELECT v, v NOT IN (SELECT g.v FROM g GROUP BY g.v, q.v) FROM q ORDER BY v", "20"},
            // Refused ahead of every pruning: an empty table, a LIMIT 0 or a WHERE FALSE does not spare it.
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM ge GROUP BY fz.id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g GROUP BY fz.id LIMIT 0) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE FALSE GROUP BY fz.id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v > 1000 GROUP BY fz.id) ORDER BY id", "24"},
        });
    }

    @Test
    public void aGroupByOfTheSubquerysOwnIsAnswered() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = 5 GROUP BY 1) ORDER BY id", "5"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE g.id = q.v GROUP BY g.v) ORDER BY v", ""},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE g.id > q.v GROUP BY g.v) ORDER BY v", "1 | 2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT g.v FROM g WHERE g.id > q.v GROUP BY 1) ORDER BY v", "1 | 2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 AS w FROM g WHERE g.id > q.v GROUP BY w) ORDER BY v", "1 | 2"},
            {"SELECT v FROM q WHERE v IN (SELECT g.v - 49 FROM g WHERE g.id > q.v GROUP BY g.v) ORDER BY v", "1"},
            // An IN drops a GROUP BY that only removes duplicates of its own column, outer key and all.
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v", ""},
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g GROUP BY q.v, g.v) ORDER BY v", ""},
            {"SELECT v FROM q WHERE v IN (SELECT g.v AS w FROM g GROUP BY w, q.v) ORDER BY v", ""},
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g WHERE g.id > q.v GROUP BY g.v, q.v) ORDER BY v", ""},
            {"SELECT v FROM q WHERE v IN (SELECT g.v FROM g GROUP BY g.v, q.v) AND v > 0 ORDER BY v", ""},
            {"SELECT v FROM q WHERE (v IN (SELECT g.v FROM g GROUP BY g.v, q.v)) ORDER BY v", ""},
            {"SELECT v FROM q WHERE v NOT IN (SELECT g.v FROM g WHERE g.id > q.v GROUP BY g.v) ORDER BY v", "1 | 2"},
            // Over a relation of one row the subquery runs row by row.
            {"SELECT v FROM q1 WHERE EXISTS (SELECT 1 FROM g WHERE q1.v = 1 GROUP BY q1.v) ORDER BY v", "1"},
        });
    }

    @Test
    public void aComparisonOfOneOuterColumnWithItselfIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.id = g.id)) ORDER BY id",
                "54"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM q WHERE g.id = g.id)) "
                + "ORDER BY id", "54"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.id = id)) ORDER BY id",
                "54"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.s LIKE g.s)) ORDER BY id",
                "54"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id IN (SELECT 5 WHERE g.id = g.id)) ORDER BY id",
                "63"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE 1 = (SELECT COUNT(*) WHERE g.id = g.id)) "
                + "ORDER BY id", "59"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE fz.id = fz.id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE fz.id = fz.id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE fz.id IN (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", "34"},
            {"SELECT id, EXISTS (SELECT 1 WHERE fz.id = fz.id) FROM fz ORDER BY id", "11"},
            {"SELECT id, (SELECT COUNT(*) FROM g WHERE fz.id = fz.id) FROM fz ORDER BY id", "12"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a = h.a) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a < h.a + 1) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a IN (h.a, 7)) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a BETWEEN h.a AND 5) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 FROM g WHERE g.id > h.a AND h.a = h.a) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a IS DISTINCT FROM h.c) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a IS DISTINCT FROM 1) ORDER BY a", "22"},
        });
    }

    @Test
    public void aNestedExistsReadingTheOutermostRowIsRefusedUnlessItsFilterFoldsAway() {
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.id = fz.id)) "
            + "ORDER BY id", "Unsupported subquery type cannot be evaluated");
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.id = fz.id + 0)) "
            + "ORDER BY id", "Unsupported subquery type cannot be evaluated");
        // g2.v holds 50 and 60, fz.id 5 and 7: the statistics prove the inner filter false.
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE g2.v = fz.id)) "
                + "ORDER BY id", ""},
        });
    }

    @Test
    public void outerNamesOnBothSidesOfAComparisonAreRefused() {
        assertUnsupported(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a < hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a = hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a <> hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a + 1 = hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a * 2 = hn.c + 1) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a + hn.c = hn.a) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a = ABS(hn.c)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a < hn.c OR hn.a = 1) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a BETWEEN 0 AND hn.c) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.a BETWEEN ht.c AND 5) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.a IS NOT DISTINCT FROM ht.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a LIKE hn.c) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t LIKE ht.u) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t RLIKE ht.u) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE hn.a < hn.c AND g.id > 0) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT COUNT(*) FROM g HAVING hn.a < hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g JOIN g g2 ON g.id = g2.id AND hn.a < hn.c) ORDER BY a",
                "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM (SELECT hn.a AS x, hn.c AS y) WHERE x < y) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM (SELECT hn.a AS x) WHERE x < hn.c) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE a IN (SELECT 1 FROM g WHERE hn.a < hn.c) ORDER BY a", "29"},
            {"SELECT a, EXISTS (SELECT 1 WHERE hn.a < hn.c) FROM hn ORDER BY a", "10"},
            {"SELECT a, (SELECT COUNT(*) FROM g WHERE hn.a < hn.c) FROM hn ORDER BY a", "11"},
            {"SELECT a, (SELECT MAX(g.v) FROM g WHERE g.id > hn.a AND hn.a < hn.c) FROM hn ORDER BY a", "11"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a < hn.c)) ORDER BY id",
                "55"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.t = h.u) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.t < h.u) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a = LENGTH(h.u)) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE LENGTH(h.t) > h.a) ORDER BY a", "22"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.t = 'x' || h.u) ORDER BY a", "22"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE fz.id < LENGTH(fz.s) + 10) ORDER BY id", "24"},
            {"SELECT a, (SELECT COUNT(*) FROM g WHERE h.a < LENGTH(h.t)) FROM h ORDER BY a", "11"},
        });
    }

    @Test
    public void outerNamesOnOneSideOfAComparisonAreAnswered() {
        assertCells(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a + hn.c = 3) ORDER BY a", "1"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a - hn.c = -1) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE COALESCE(hn.a, hn.c) = 1) ORDER BY a", "1"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE IFF(hn.a > 1, hn.c, 0) = 4) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a = 1 OR hn.c = 4) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a <> 3) ORDER BY a", "1"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IS NOT DISTINCT FROM 3) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a BETWEEN 1 AND 2) ORDER BY a", "1"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id = hn.a + hn.c) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id BETWEEN hn.a AND hn.c) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id BETWEEN hn.a AND hn.a + 5) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id > hn.a AND g.id < hn.c + 10) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id <> hn.a) ORDER BY a", "1 | 3"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t LIKE 'x%') ORDER BY a", "1"},
            // Settled by the statistics either way: a + v lies in 51..63, c + 100 in 102..104.
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id > hn.a AND hn.a + g.v < hn.c + 100) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a BETWEEN hn.c - 100 AND 5) ORDER BY a", "1 | 3"},
            // The statistics prove the membership's own equality false: a lies in 1..3, g.id in 5..6.
            {"SELECT a FROM hn WHERE a IN (SELECT g.id FROM g WHERE hn.a < hn.c) ORDER BY a", ""},
            {"SELECT a, a IN (SELECT g.id FROM g WHERE hn.a < hn.c) FROM hn ORDER BY a", "1, false | 3, false"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.id < g.v)) ORDER BY id",
                "5 | 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE g.v = 50)) ORDER BY id",
                "5 | 7"},
        });
        assertUnsupported(new String[][] {
            {"SELECT a FROM hn WHERE a + 4 IN (SELECT g.id FROM g WHERE hn.a < hn.c) ORDER BY a", "33"},
        });
    }

    @Test
    public void aComparisonWithAnOuterColumnOfOneValueIsAnswered() {
        // h.c holds 2 on every row, so it reads as the constant 2 and each comparison keeps to one side.
        assertCells(new String[][] {
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a < h.c) ORDER BY a", "1"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 FROM g WHERE h.a < h.c) ORDER BY a", "1"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a + 1 = h.c) ORDER BY a", "1"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a IN (h.c, 3)) ORDER BY a", "3"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a BETWEEN h.c AND 5) ORDER BY a", "3"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a IS NOT DISTINCT FROM h.c) ORDER BY a", ""},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE h.a < ABS(h.c) + 0) ORDER BY a", "1"},
            {"SELECT a FROM h WHERE EXISTS (SELECT 1 WHERE LENGTH(h.t) = 4) ORDER BY a", "3"},
            {"SELECT a, (SELECT COUNT(*) FROM g WHERE h.a < h.c) FROM h ORDER BY a", "1, 2 | 3, 0"},
            {"SELECT a FROM h WHERE a IN (SELECT 1 FROM g WHERE h.a < h.c) ORDER BY a", "1"},
        });
    }

    @Test
    public void anInListIsJudgedByTheElementsTheStatisticsLeave() {
        // hn.a holds 1 and 3, hn.c 2 and 4, g.id 5 and 6, q.v 1 and 2; ht.t holds 'x' and 'yyyy'.
        assertCells(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, 3)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (3, hn.c)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, hn.c + 1, 3)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.a + 1, 3)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (1, 2, hn.a)) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.a, 2)) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, 1)) ORDER BY a", "1"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, NULL)) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (7, 8)) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE 3 IN (hn.a, hn.c)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id IN (hn.a, hn.c)) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE hn.a IN (g.id, 3)) ORDER BY a", "3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM q WHERE hn.a IN (hn.c, q.v)) ORDER BY a", "1"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t IN (ht.u, 'x')) ORDER BY a", "1"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t IN (ht.u, 'y')) ORDER BY a", ""},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.a IN (LENGTH(ht.t), 3)) ORDER BY a", "1 | 3"},
            {"SELECT a, (SELECT COUNT(*) FROM g WHERE hn.a IN (hn.c, 3)) FROM hn ORDER BY a", "1, 0 | 3, 2"},
        });
        assertUnsupported(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, hn.c + 1)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, 7)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, 7, 8)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.c, 0)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.a, 7)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a IN (hn.a, hn.a + 1)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.c IN (hn.a + 1, 5)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE hn.a IN (hn.c, g.id)) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t IN (ht.u, 'zzz')) ORDER BY a", "23"},
            {"SELECT a, (SELECT COUNT(*) FROM g WHERE hn.a IN (hn.c, 7)) FROM hn ORDER BY a", "11"},
        });
    }

    @Test
    public void aNegationOverTheOuterRowIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a NOT IN (3, 4)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a NOT IN (1, 3)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a NOT IN (hn.c, 3)) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE hn.a NOT BETWEEN 1 AND 2) ORDER BY a", "23"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 WHERE NOT (hn.a IN (hn.c, 3))) ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t NOT LIKE 'x%') ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t NOT ILIKE 'x%') ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 WHERE ht.t NOT RLIKE 'x.*') ORDER BY a", "23"},
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 FROM g WHERE g.s NOT LIKE ht.t) ORDER BY a", "23"},
        });
        assertCells(new String[][] {
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id NOT IN (hn.a, 3)) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hn WHERE EXISTS (SELECT 1 FROM g WHERE g.id NOT BETWEEN hn.a AND hn.c) ORDER BY a",
                "1 | 3"},
        });
    }

    @Test
    public void anOuterNameTheStatisticsPinIsAConstant() {
        assertCells(new String[][] {
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT 1 AS a) d", "1"},
            {"SELECT (SELECT 1 WHERE a = 1) FROM (SELECT 1 AS a)", "1"},
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT v AS a FROM q1) d", "1"},
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT MAX(v) AS a FROM q) d", "null"},
            {"WITH d AS (SELECT 1 AS a) SELECT (SELECT 1 WHERE d.a = 1) FROM d", "1"},
            {"SELECT (SELECT 1 WHERE d.a = e.b) FROM (SELECT 1 AS a) d, (SELECT 1 AS b) e", "1"},
            {"SELECT (SELECT 1 WHERE d.a = q1.v) FROM (SELECT 1 AS a) d, q1", "1"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = d.a + 4) FROM (SELECT 1 AS a) d", "50"},
            {"SELECT (SELECT 1 WHERE h.c = 2) FROM h ORDER BY 1", "1 | 1"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = h.c + 3) FROM h ORDER BY 1", "50 | 50"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = d.a) FROM (SELECT c + 3 AS a FROM h) d ORDER BY 1", "50 | 50"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = d.a) FROM (SELECT 5 AS a, v FROM q) d ORDER BY 1", "50 | 50"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = d.a) FROM (SELECT 5 AS a) d JOIN q ON TRUE ORDER BY 1",
                "50 | 50"},
            {"SELECT (SELECT COUNT(*) FROM g WHERE g.id > d.a) FROM (SELECT 5 AS a FROM q) d ORDER BY 1", "1 | 1"},
            {"SELECT d.a FROM (SELECT 5 AS a FROM q) d WHERE EXISTS (SELECT 1 FROM g WHERE g.id = d.a LIMIT 1) "
                + "ORDER BY 1", "5 | 5"},
        });
        assertRefused("SELECT (SELECT g.v FROM g WHERE g.id > d.a) FROM (SELECT 1 AS a) d",
            "Single-row subquery returns more than one row.");
    }

    @Test
    public void aColumnHoldingOneValueOfAnyScalarFamilyIsAConstant() {
        engine.execute("CREATE TABLE hd (a INT, c NUMBER(10,2), f FLOAT, t VARCHAR, d DATE, b BOOLEAN, "
            + "ts TIMESTAMP_NTZ, bi BINARY, vr VARIANT, tz TIMESTAMP_TZ, tm TIME)");
        engine.execute("INSERT INTO hd SELECT 1, 2.5, 1.5, 'x', '2024-01-01', TRUE, '2024-01-01 10:00:00', "
            + "TO_BINARY('AB', 'HEX'), PARSE_JSON('{\"k\": 1}'), '2024-01-01 10:00:00 +01:00', '10:00:00' UNION ALL "
            + "SELECT 3, 2.5, 1.5, 'x', '2024-01-01', TRUE, '2024-01-01 10:00:00', TO_BINARY('AB', 'HEX'), "
            + "PARSE_JSON('{\"k\": 1}'), '2024-01-01 10:00:00 +01:00', '10:00:00'");
        final String[] families = {"c", "f", "t", "d", "b", "ts", "bi", "tz", "tm"};
        for (final String column : families) {
            assertCells(new String[][] {
                {"SELECT a FROM hd WHERE EXISTS (SELECT 1 FROM g GROUP BY hd." + column + ") ORDER BY a", "1 | 3"},
            });
        }
        assertCells(new String[][] {
            {"SELECT (SELECT 1 WHERE hd.t = 'x') FROM hd ORDER BY 1", "1 | 1"},
            {"SELECT a, (SELECT 1 WHERE hd.b) FROM hd ORDER BY 1", "1, 1 | 3, 1"},
            {"SELECT a FROM hd WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.s = hd.t) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hd WHERE EXISTS (SELECT 1 WHERE hd.a < LENGTH(hd.t)) ORDER BY a", ""},
            {"SELECT a FROM hd WHERE EXISTS (SELECT 1 WHERE hd.t = hd.t) ORDER BY a", "1 | 3"},
            {"SELECT a FROM hd WHERE EXISTS (SELECT 1 WHERE hd.d IS DISTINCT FROM '2024-01-02'::DATE) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM hd WHERE EXISTS (SELECT 1 WHERE hd.a < hd.f) ORDER BY a", "1"},
        });
        // A VARIANT holds no statistic, and a column holding a second value or a NULL is no constant.
        engine.execute("CREATE TABLE hdn (a INT, t VARCHAR)");
        engine.execute("INSERT INTO hdn VALUES (1, 'x'), (3, NULL)");
        engine.execute("CREATE TABLE hcs (a INT, t VARCHAR)");
        engine.execute("INSERT INTO hcs VALUES (1, 'x'), (3, 'X')");
        assertUnsupported(new String[][] {
            {"SELECT a FROM hd WHERE EXISTS (SELECT 1 FROM g GROUP BY hd.vr) ORDER BY a", "23"},
            {"SELECT a FROM hdn WHERE EXISTS (SELECT 1 FROM g GROUP BY hdn.t) ORDER BY a", "24"},
            {"SELECT a FROM hcs WHERE EXISTS (SELECT 1 FROM g GROUP BY hcs.t) ORDER BY a", "24"},
        });
    }

    @Test
    public void anOuterNameOfManyValuesStillCorrelates() {
        assertUnsupported(new String[][] {
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT 1 AS a UNION ALL SELECT 2) d ORDER BY 1", "8"},
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT v AS a FROM q WHERE v = 1) d", "8"},
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT v AS a FROM q LIMIT 1) d", "8"},
            {"SELECT (SELECT 1 WHERE d.a = 1) FROM (VALUES (1)) d(a)", "8"},
            {"SELECT (SELECT 1 WHERE d.a = q.v) FROM (SELECT 1 AS a) d, q ORDER BY 1", "8"},
            {"SELECT (SELECT 1 WHERE d.v = 1) FROM (SELECT 1 AS a, v FROM q) d ORDER BY 1", "8"},
            {"SELECT (SELECT g.v FROM g WHERE g.id = d.a) FROM (VALUES (5)) d(a)", "8"},
        });
    }
}
