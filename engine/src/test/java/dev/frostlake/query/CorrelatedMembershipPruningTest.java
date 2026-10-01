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
 * A correlated EXISTS or IN whose select the statistics prove empty is pruned before its shape is judged: a WHERE
 * or an inner join's ON its columns' least and greatest values rule out — text compared by its code points, a
 * number by its interval, IS NULL over a column holding none — or an IN subject the statistics prove never equal
 * to the item. Over g.s holding 'a' and 'c', {@code g.s = 'x'} prunes and {@code g.s = 'b'} does not. Every cell
 * is live-verified.
 */
public class CorrelatedMembershipPruningTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE hn (a INT, c INT)");
        engine.execute("INSERT INTO hn VALUES (1, 2), (3, 4)");
        engine.execute("CREATE TABLE ht (a INT, c INT, t VARCHAR, u VARCHAR)");
        engine.execute("INSERT INTO ht VALUES (1, 2, 'x', 'yy'), (3, 4, 'yyyy', 'z')");
        engine.execute("CREATE TABLE hd (a INT, t VARCHAR)");
        engine.execute("INSERT INTO hd VALUES (1, 'x'), (3, 'x')");
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
    public void aTextFilterTheColumnsBoundsRuleOutPrunesTheSubquery() {
        assertCells(new String[][] {
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'x') ORDER BY a", ""},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'A') ORDER BY a", ""},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s < 'a') ORDER BY a", ""},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s > 'c') ORDER BY a", ""},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s IN ('x', 'y')) ORDER BY a", ""},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'x' AND g.v = 50) ORDER BY a", ""},
            {"SELECT a FROM hd WHERE a IN (SELECT hd.a FROM g WHERE g.s = hd.t) ORDER BY a", ""},
            {"SELECT a FROM hd WHERE a IN (SELECT hd.a FROM g WHERE g.s > hd.t) ORDER BY a", ""},
            {"SELECT a FROM ht WHERE a IN (SELECT ht.a FROM g WHERE g.s = ht.t) ORDER BY a", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s = 'x' AND g.id + fz.id = 10) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE 'x' = g.s AND g.id + fz.id = 10) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s BETWEEN 'x' AND 'z' AND g.id + fz.id = 10) "
                + "ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE fz.s > 'zz' AND g.id + fz.id = 10) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s = 'x' AND g.id = fz.id LIMIT 1) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g JOIN q ON g.s = 'x' WHERE g.id + fz.id = 10) ORDER BY id",
                ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s = 'x' AND g.id + fz.id = 10 "
                + "UNION ALL SELECT 1 FROM g WHERE g.id = fz.id) ORDER BY id", "5"},
        });
        assertUnsupported(new String[][] {
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'a') ORDER BY a", "29"},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'b') ORDER BY a", "29"},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s >= 'c') ORDER BY a", "29"},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s LIKE 'x%') ORDER BY a", "29"},
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.s = 'x' OR g.v = 50) ORDER BY a", "29"},
            {"SELECT a FROM hd WHERE a IN (SELECT hd.a FROM g WHERE g.s < hd.t) ORDER BY a", "29"},
            {"SELECT a FROM ht WHERE a IN (SELECT ht.a FROM g WHERE g.s < ht.t) ORDER BY a", "29"},
        });
    }

    @Test
    public void aNumericOrNullFilterTheStatisticsRuleOutPrunesTheSubquery() {
        assertCells(new String[][] {
            {"SELECT a FROM hn WHERE a IN (SELECT hn.a FROM g WHERE g.v > 1000) ORDER BY a", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id AND g.v > 1000) ORDER BY id",
                "5 | 7"},
            {"SELECT id, EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id AND g.v > 1000) FROM fz ORDER BY id",
                "5, true | 7, true"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(g.id) FROM g WHERE g.id > fz.id AND g.v > 1000) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v > 1000 AND fz.id = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v > 1000 AND fz.id = fz.id GROUP BY g.s) "
                + "ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.s IS NULL AND g.id + fz.id = 10) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v IS NULL AND g.id + fz.id = 10) ORDER BY id", ""},
        });
        // An outer join may extend its side with NULLs the column's rows do not hold.
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g LEFT JOIN q ON FALSE WHERE q.v IS NULL "
                + "AND g.id + fz.id = 10) ORDER BY id", "24"},
        });
    }

    @Test
    public void anInSubjectTheStatisticsProveUnequalToTheItemPrunesTheSubquery() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id + 100 IN (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id + 100 NOT IN (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", "5 | 7"},
        });
        assertUnsupported(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE fz.id = fz.id) ORDER BY id", "31"},
        });
    }
}
