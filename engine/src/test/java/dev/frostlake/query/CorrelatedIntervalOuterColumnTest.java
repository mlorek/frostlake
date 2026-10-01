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
 * INTERVAL columns of the outer row read by a correlated subquery. The planner keeps statistics for both interval
 * families as for every other scalar family: a column holding one value that is never NULL correlates nothing,
 * while one that varies, or holds a NULL, is refused beside a GROUP BY, an IN item or a comparison with another
 * varying outer name, as any varying outer name is. An aggregate subquery reading the outer row beside a MIN or MAX
 * over an interval argument is refused, as over text, whether or not the relation carries an alias. Every cell is
 * live-verified.
 */
public class CorrelatedIntervalOuterColumnTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE ivc (iv INTERVAL DAY TO SECOND, d2 INTERVAL DAY(2), k NUMBER)");
        engine.execute("INSERT INTO ivc VALUES ('1 01:00:00', '5', 1), ('2 00:00:00', '7', 2)");
        engine.execute("CREATE TABLE ivk (iv INTERVAL DAY TO SECOND, d2 INTERVAL DAY(2), k NUMBER)");
        engine.execute("INSERT INTO ivk VALUES ('1 01:00:00', '5', 1), ('1 01:00:00', '5', 2)");
        engine.execute("CREATE TABLE ivo (iv INTERVAL DAY TO SECOND, d2 INTERVAL DAY(2), k NUMBER)");
        engine.execute("INSERT INTO ivo VALUES ('1 01:00:00', '1', 1), ('6 00:00:00', '5', 2)");
        engine.execute("CREATE TABLE ivn (iv INTERVAL DAY TO SECOND, d2 INTERVAL DAY(2), k NUMBER)");
        engine.execute("INSERT INTO ivn VALUES ('1 01:00:00', '5', 1), (NULL, '5', 2)");
        engine.execute("CREATE TABLE ivy (ym INTERVAL YEAR TO MONTH, k NUMBER)");
        engine.execute("INSERT INTO ivy VALUES ('1-2', 1), ('1-2', 2)");
        engine.execute("CREATE TABLE ivy2 (ym INTERVAL YEAR TO MONTH, k NUMBER)");
        engine.execute("INSERT INTO ivy2 VALUES ('1-2', 1), ('3-4', 2)");
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
    public void anIntervalColumnHoldingOneValueCorrelatesNothing() {
        assertCells(new String[][] {
            {"SELECT k FROM ivk WHERE EXISTS (SELECT 1 FROM g GROUP BY ivk.iv) ORDER BY k", "1 | 2"},
            {"SELECT k FROM ivk WHERE EXISTS (SELECT 1 FROM g GROUP BY ivk.d2) ORDER BY k", "1 | 2"},
            {"SELECT k FROM ivy WHERE EXISTS (SELECT 1 FROM g GROUP BY ivy.ym) ORDER BY k", "1 | 2"},
            {"SELECT k FROM ivk WHERE EXISTS (SELECT 1 FROM g WHERE ivk.iv > ivk.d2) ORDER BY k", ""},
            {"SELECT k FROM ivk WHERE iv IN (SELECT ivk.iv FROM g) ORDER BY k", "1 | 2"},
            {"SELECT k FROM ivy WHERE ym IN (SELECT ivy.ym FROM g) ORDER BY k", "1 | 2"},
            {"SELECT k FROM ivk WHERE iv IN (SELECT ivk.iv + INTERVAL '1' HOUR FROM g) ORDER BY k", ""},
            // A column holding a NULL varies, but a comparison with one that holds one value reads one varying name.
            {"SELECT k FROM ivn WHERE EXISTS (SELECT 1 FROM g WHERE ivn.iv > ivn.d2) ORDER BY k", ""},
        });
    }

    @Test
    public void aVaryingIntervalColumnIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT k FROM ivc WHERE EXISTS (SELECT 1 FROM g GROUP BY ivc.iv) ORDER BY k", "24"},
            {"SELECT k FROM ivy2 WHERE EXISTS (SELECT 1 FROM g GROUP BY ivy2.ym) ORDER BY k", "25"},
            {"SELECT k FROM ivn WHERE EXISTS (SELECT 1 FROM g GROUP BY ivn.iv) ORDER BY k", "24"},
            {"SELECT k FROM ivc WHERE iv IN (SELECT ivc.iv FROM g) ORDER BY k", "31"},
            {"SELECT k FROM ivo WHERE EXISTS (SELECT 1 FROM g WHERE ivo.iv > ivo.d2) ORDER BY k", "24"},
        });
    }

    @Test
    public void anIntervalAggregateBesideAnOuterNameIsRefused() {
        assertUnsupported(new String[][] {
            {"SELECT k, (SELECT MAX(i2.iv) + ivc.iv FROM ivc i2) AS m FROM ivc ORDER BY k", "11"},
            {"SELECT k, (SELECT MIN(i2.iv) + ivc.iv FROM ivc i2) AS m FROM ivc ORDER BY k", "11"},
            {"SELECT k, (SELECT MAX(i2.iv) * ivc.k FROM ivc i2) AS m FROM ivc ORDER BY k", "11"},
            {"SELECT k, (SELECT MAX(y2.ym) + ivy2.ym FROM ivy2 y2) AS m FROM ivy2 ORDER BY k", "11"},
            {"SELECT id, (SELECT MAX(iv) * fz.id FROM ivc) AS m FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT MIN(i.d2) * fz.id FROM ivc AS i) AS m FROM fz ORDER BY id", "12"},
            {"SELECT id, (SELECT MAX(f2.s) || fz.s FROM fz f2) AS m FROM fz ORDER BY id", "12"},
        });
        assertCells(new String[][] {
            {"SELECT k, (SELECT MAX(i2.k) + ivc.k FROM ivc i2) AS m FROM ivc ORDER BY k", "1, 3 | 2, 4"},
            {"SELECT k, (SELECT COUNT(*) + ivc.k FROM ivc i2) AS m FROM ivc ORDER BY k", "1, 3 | 2, 4"},
            {"SELECT id, (SELECT MAX(g2.v) + fz.id FROM g g2) AS m FROM fz ORDER BY id", "5, 65 | 7, 67"},
        });
    }
}
