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
 * Outer names read through a derived table, a view or a CTE. The planner reads the statistics of the stored column
 * such a column passes through, whatever a WHERE on the way keeps, so a column holding one value — or a literal, or
 * a value computed from such — correlates nothing, and the GROUP BY, aggregate-body, IN-item and comparison rules
 * refuse only a column the statistics show varying. An IS DISTINCT FROM whose operands' intervals never meet folds
 * away. Every cell is live-verified.
 */
public class CorrelatedDerivedOuterRelationTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE hn (a INT, c INT)");
        engine.execute("INSERT INTO hn VALUES (1, 2), (3, 4)");
        engine.execute("CREATE TABLE hd (a INT, c NUMBER(10,2), f FLOAT, t VARCHAR, d DATE)");
        engine.execute("INSERT INTO hd VALUES (1, 2.5, 1.5, 'x', '2024-01-01'), (3, 2.5, 1.5, 'x', '2024-01-01')");
        engine.execute("CREATE TABLE ht (a INT, c INT, t VARCHAR, u VARCHAR)");
        engine.execute("INSERT INTO ht VALUES (1, 2, 'x', 'yy'), (3, 4, 'yyyy', 'z')");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE hz (a INT)");
        engine.execute("INSERT INTO hz VALUES (1), (NULL)");
        engine.execute("CREATE TABLE gz (id INT)");
        engine.execute("INSERT INTO gz VALUES (5), (NULL)");
        engine.execute("CREATE VIEW vhd AS SELECT * FROM hd");
        engine.execute("CREATE VIEW vk AS SELECT a, 'k' AS k FROM hn");
        engine.execute("CREATE VIEW vht AS SELECT * FROM ht");
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
            final String sql = cell[0];
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql);
            assertTrue(refused.getMessage() != null && refused.getMessage().contains(UNSUPPORTED + cell[1]),
                sql + " should be refused at " + cell[1] + " but read: " + refused.getMessage());
        }
    }

    @Test
    public void aDerivedColumnHoldingOneValueCorrelatesNothing() {
        assertCells(new String[][] {
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t) ORDER BY a", "1 | 3"},
            {"SELECT a FROM vhd WHERE EXISTS (SELECT 1 FROM g GROUP BY vhd.t) ORDER BY a", "1 | 3"},
            {"WITH c AS (SELECT * FROM hd) SELECT a FROM c WHERE EXISTS (SELECT 1 FROM g GROUP BY c.t) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.f) ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.d) ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT * FROM (SELECT t, a FROM hd)) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t)"
                + " ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT a, 'x' AS t FROM hn) d WHERE EXISTS (SELECT 1 WHERE d.t = d.t) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM vk WHERE EXISTS (SELECT 1 FROM g GROUP BY vk.k) ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT a, NULL AS t FROM hn) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t) ORDER BY a",
                "1 | 3"},
            // A value computed from such columns, which the statistics here do not follow, is not taken to vary.
            {"SELECT a FROM (SELECT a, UPPER(t) AS t FROM hd) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t)"
                + " ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT a, CURRENT_DATE() AS r FROM hn) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.r)"
                + " ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.s = d.t) ORDER BY a",
                "1 | 3"},
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT 1 WHERE d.a < LENGTH(d.t)) ORDER BY a", ""},
            {"SELECT a, (SELECT 1 WHERE d.t = 'x') FROM (SELECT * FROM hd) d ORDER BY a", "1, 1 | 3, 1"},
            // The stored text column's range prunes the membership first.
            {"SELECT a FROM (SELECT * FROM hd) d WHERE a IN (SELECT d.a FROM g WHERE g.s = d.t) ORDER BY a", ""},
            {"SELECT a FROM (SELECT * FROM hd WHERE a > 0) d WHERE EXISTS (SELECT 1 FROM g WHERE g.s = d.t"
                + " GROUP BY d.t) ORDER BY a", ""},
        });
    }

    @Test
    public void aDerivedColumnPassingAVaryingColumnThroughIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT a FROM (SELECT * FROM hn) d WHERE EXISTS (SELECT 1 WHERE d.a < d.c) ORDER BY a", "41"},
            {"WITH c AS (SELECT * FROM hn) SELECT a FROM c WHERE EXISTS (SELECT 1 WHERE c.a < c.c) ORDER BY a", "51"},
            {"SELECT a FROM (SELECT * FROM hn) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.c) ORDER BY a", "41"},
            {"SELECT a FROM (SELECT a, $1 AS z FROM hn) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.z) ORDER BY a",
                "50"},
            {"SELECT a FROM (SELECT * FROM (SELECT t, a FROM ht)) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t)"
                + " ORDER BY a", "60"},
            {"SELECT a FROM vht WHERE EXISTS (SELECT 1 FROM g GROUP BY vht.t) ORDER BY a", "24"},
            {"SELECT a FROM (SELECT * FROM ht) d WHERE a IN (SELECT d.a + 0 FROM g) ORDER BY a", "47"},
            // A WHERE on the way does not pin the column: the stored column's statistics are read.
            {"SELECT a FROM (SELECT * FROM ht WHERE t = 'x') d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.t)"
                + " ORDER BY a", "55"},
            {"SELECT a FROM (SELECT * FROM hn WHERE a = 1) d WHERE EXISTS (SELECT 1 WHERE d.a < d.c) ORDER BY a",
                "53"},
        });
    }

    @Test
    public void isDistinctFromOverIntervalsThatNeverMeetFoldsAway() {
        assertCells(new String[][] {
            {"SELECT a FROM ht WHERE EXISTS (SELECT 1 FROM g WHERE g.id IS DISTINCT FROM ht.a) ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT * FROM ht) d WHERE EXISTS (SELECT 1 FROM g WHERE g.id IS DISTINCT FROM d.a)"
                + " ORDER BY a", "1 | 3"},
            {"SELECT a FROM (SELECT * FROM hd) d WHERE EXISTS (SELECT 1 FROM g WHERE g.id IS DISTINCT FROM d.c)"
                + " ORDER BY a", "1 | 3"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE fz.id IS DISTINCT FROM 9) ORDER BY id", "5 | 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v IS DISTINCT FROM fz.id) ORDER BY id", "5 | 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v IS NOT DISTINCT FROM fz.id) ORDER BY id", ""},
            // One side may be NULL: still distinct on every row.
            {"SELECT a FROM hz WHERE EXISTS (SELECT 1 FROM g WHERE g.id IS DISTINCT FROM hz.a) ORDER BY a",
                "1 | null"},
        });
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE fz.id IS DISTINCT FROM 6) ORDER BY id", "24"},
            {"SELECT id FROM (SELECT * FROM fz) d WHERE EXISTS (SELECT 1 FROM g WHERE g.id IS DISTINCT FROM d.id)"
                + " ORDER BY id", "42"},
            {"SELECT a FROM hz WHERE EXISTS (SELECT 1 FROM gz WHERE gz.id IS DISTINCT FROM hz.a) ORDER BY a", "23"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s IS DISTINCT FROM fz.s) ORDER BY id", "24"},
        });
    }
}
