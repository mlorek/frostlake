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
 * Correlated EXISTS, IN and scalar shapes judged by their aggregates and derived tables: an aggregate over the
 * subquery's own rows filtered by the outer row is refused under EXISTS and, unless its correlation is an
 * equality, under IN; an IN item reading the outer row alone over a FROM is refused; and a derived table reading
 * the outer row in its select list carries it as a value, refused only where it is aggregated, compared with its
 * own outer column or made DISTINCT. Every cell is live-verified.
 */
public class CorrelatedMembershipShapesTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
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

    private void assertUnsupported(final String[][] cells) {
        for (final String[] cell : cells) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(cell[0]);
                }
            }, cell[0]);
            assertTrue(refused.getMessage() != null && refused.getMessage().contains(UNSUPPORTED + cell[1]),
                cell[0] + " should be refused at " + cell[1] + " but read: " + refused.getMessage());
        }
    }

    @Test
    public void anAggregateBodyFilteredByTheOuterRowIsRefusedUnderExists() {
        assertUnsupported(new String[][] {
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "11"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id > fz.id) FROM fz ORDER BY id", "11"},
            {"SELECT id, EXISTS (SELECT COUNT(*) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "11"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id) ORDER BY id", "24"},
            {"SELECT id FROM fz WHERE NOT EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id) ORDER BY id", "28"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COUNT(*) FROM g WHERE g.id > fz.id) ORDER BY id", "24"},
            {"SELECT id, EXISTS (SELECT COUNT(*) WHERE fz.id = 5) FROM fz ORDER BY id", "11"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COUNT(*) WHERE fz.id = 5) ORDER BY id", "24"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g JOIN fz f2 ON f2.id = g.id AND f2.id = fz.id) FROM fz "
                + "ORDER BY id", "11"},
        });
        assertCells(new String[][] {
            {"SELECT id, EXISTS (SELECT 1 FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5, true | 7, false"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g HAVING MAX(v) > fz.id * 10) FROM fz ORDER BY id",
                "5, true | 7, false"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id GROUP BY g.s) FROM fz ORDER BY id",
                "5, true | 7, false"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id GROUP BY g.s) ORDER BY id", "5"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id = 5) FROM fz ORDER BY id", "5, true | 7, true"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id HAVING COUNT(*) > 0) FROM fz ORDER BY id",
                "5, true | 7, false"},
        });
    }

    @Test
    public void anAggregateBodyUnderInIsRefusedUnlessItsCorrelationIsAnEquality() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT COUNT(*) FROM g WHERE g.id = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT COUNT(*) + 4 FROM g WHERE g.id = fz.id) ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE fz.id = 5) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE fz.id = g.id AND g.v > 0) ORDER BY id",
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id OR g.v = fz.id) ORDER BY id",
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g JOIN q ON q.v = g.id AND g.id = fz.id) "
                + "ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id HAVING COUNT(*) > 0) "
                + "ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id GROUP BY g.s) ORDER BY id",
                "5"},
        });
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT COUNT(*) FROM g WHERE g.id > fz.id) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id > fz.id) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.s LIKE fz.s) ORDER BY id", "31"},
        });
    }

    @Test
    public void anInItemReadingTheOuterRowAloneOverAFromIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT fz.id + 0 FROM g) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT x FROM (SELECT fz.id AS x FROM g)) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT x - 1 FROM (SELECT fz.id + 1 AS x)) ORDER BY id", "31"},
        });
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT fz.id) ORDER BY id", "5 | 7"},
            {"SELECT id, id IN (SELECT fz.id) FROM fz ORDER BY id", "5, true | 7, true"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM (SELECT fz.id + 1 AS x) JOIN g ON g.id < x) ORDER BY id",
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g, (SELECT fz.id + 1 AS x) WHERE g.id < x) ORDER BY id",
                "5"},
        });
    }

    @Test
    public void aDerivedTableCarriesTheOuterRowAsAValue() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1)) FROM fz ORDER BY id", "5, 1 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id)) FROM fz ORDER BY id", "5, 1 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x) WHERE x > 6) FROM fz ORDER BY id",
                "5, 0 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x) d WHERE d.x > 6) FROM fz ORDER BY id",
                "5, 0 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT v + fz.id AS x FROM g)) FROM fz ORDER BY id", "5, 2 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x WHERE fz.id > 5)) FROM fz ORDER BY id",
                "5, 0 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x), g) FROM fz ORDER BY id", "5, 2 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM g, (SELECT fz.id + 1 AS x) WHERE g.id < x) FROM fz ORDER BY id",
                "5, 1 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x UNION ALL SELECT 2)) FROM fz ORDER BY id",
                "5, 2 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x) JOIN g ON g.id < x) FROM fz ORDER BY id",
                "5, 1 | 7, 2"},
            {"SELECT id, (SELECT MAX(g.v) FROM (SELECT fz.id + 1 AS x) JOIN g ON g.id < x) FROM fz ORDER BY id",
                "5, 50 | 7, 60"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x FROM g) WHERE x > 6) FROM fz ORDER BY id",
                "5, 0 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x LIMIT 1)) FROM fz ORDER BY id", "5, 1 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x FROM g GROUP BY g.s)) FROM fz ORDER BY id",
                "5, 2 | 7, 2"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT x FROM (SELECT fz.id + 1 AS x))) FROM fz ORDER BY id",
                "5, 1 | 7, 1"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT v + fz.id AS x FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id",
                "5, 1 | 7, 0"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT v + fz.id AS x FROM g WHERE g.id > fz.id)) FROM fz ORDER BY id",
                "5, 1 | 7, 0"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id AS x FROM g WHERE g.id > 5)) FROM fz ORDER BY id",
                "5, 1 | 7, 1"},
            {"SELECT id, (SELECT x FROM (SELECT fz.id + 1 AS x)) FROM fz ORDER BY id", "5, 6 | 7, 8"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT fz.id + 1 AS x)) FROM fz ORDER BY id", "5, true | 7, true"},
            {"SELECT id, EXISTS (SELECT 1 FROM (SELECT fz.id + 1 AS x WHERE fz.id > 5)) FROM fz ORDER BY id",
                "5, false | 7, true"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM (SELECT fz.id + 1 AS x) WHERE x > 6) ORDER BY id", "7"},
        });
    }

    @Test
    public void aDerivedOuterValueAggregatedOrComparedWithItsColumnIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT id, (SELECT MAX(x) FROM (SELECT fz.id + 1 AS x)) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT SUM(x) FROM (SELECT fz.id AS x)) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT COUNT(x) FROM (SELECT fz.id + 1 AS x)) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT MAX(x) FROM (SELECT fz.id + 1 AS x FROM g)) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT MAX(x) FROM (SELECT g.v + fz.id AS x FROM g WHERE g.id = fz.id)) FROM fz ORDER BY id",
                "12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x) WHERE x > fz.id) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT DISTINCT fz.id + 1 AS x FROM g)) FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT v + fz.id AS x FROM g WHERE g.id = fz.id GROUP BY v)) FROM fz "
                + "ORDER BY id", "12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x WHERE fz.id > 5 LIMIT 1)) FROM fz ORDER BY id",
                "12"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT g.v AS x FROM g WHERE g.id = fz.id LIMIT 1)) FROM fz "
                + "ORDER BY id", "12"},
            {"SELECT id, (SELECT x + 1 FROM (SELECT fz.id AS x FROM g) LIMIT 1) FROM fz ORDER BY id", "12"},
            {"SELECT id, EXISTS (SELECT MAX(x) FROM (SELECT fz.id AS x)) FROM fz ORDER BY id", "11"},
        });
    }
}
