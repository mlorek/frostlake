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
 * A correlated set operation under EXISTS or IN is judged operand by operand: a UNION of operands that read the
 * outer row alike is answered, one whose operands read it differently — another outer column, another comparison,
 * sides swapped, another relation of the outer row, no outer name at all — is refused, an operand the statistics
 * prove empty drops out, INTERSECT, EXCEPT and MINUS keep their first operand's correlation only, a second operand
 * the statistics prove empty aside, and a LIMIT is refused. Every cell is live-verified.
 */
public class CorrelatedSetOperationMembershipTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
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

    private static String exists(final String body) {
        return "SELECT id FROM fz WHERE EXISTS (" + body + ") ORDER BY id";
    }

    @Test
    public void operandsThatReadTheOuterRowAlikeAreAnswered() {
        assertCells(new String[][] {
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL SELECT 1 WHERE fz.id = 7"), "5 | 7"},
            {exists("SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE id = 7"), "5 | 7"},
            {exists("SELECT 1 WHERE id = 5 UNION SELECT 1 WHERE id = 7"), "5 | 7"},
            {exists("(SELECT 1 WHERE id = 5) UNION ALL (SELECT 1 WHERE id = 7)"), "5 | 7"},
            {exists("SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE id = 7 UNION ALL SELECT 1 WHERE id = 9"), "5 | 7"},
            {exists("SELECT 1 WHERE id = 5 UNION ALL SELECT 1 FROM (SELECT 1) WHERE id = 7"), "5 | 7"},
            {exists("SELECT 1 WHERE id = 5 GROUP BY 1 UNION ALL SELECT 1 WHERE id = 7"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.s = 'a' UNION ALL SELECT 1 WHERE fz.s = 'b'"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.s LIKE 'a%' UNION ALL SELECT 1 WHERE fz.s LIKE 'b%'"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.id = 5 AND fz.s = 'a' UNION ALL SELECT 1 WHERE fz.s = 'b' AND fz.id = 7"),
                "5 | 7"},
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL SELECT 1 WHERE fz.id = 6 + 1"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL SELECT 1 FROM g WHERE fz.id = 7"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.id = 7 UNION ALL SELECT 1 FROM g WHERE fz.id = g.id"), "5 | 7"},
            {exists("SELECT 1 WHERE 7 = fz.id UNION ALL SELECT 1 FROM g WHERE g.id = fz.id"), "5 | 7"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.v - 43 = fz.id"), "5 | 7"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM (SELECT 1 AS k) WHERE k = fz.id"),
                "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g JOIN g g2 ON g2.id = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL (SELECT 1 FROM g WHERE g.v = fz.id "
                + "UNION ALL SELECT 1 FROM g WHERE g.id + 2 = fz.id)"), "5 | 7"},
            {exists("SELECT fz.id UNION ALL SELECT 2"), "5 | 7"},
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL BY NAME SELECT 1 WHERE fz.id = 7"), "5 | 7"},
            {"SELECT id FROM fz WHERE NOT EXISTS (SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE id = 6) ORDER BY id",
                "7"},
            {"SELECT id, EXISTS (SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE id = 6) FROM fz ORDER BY id",
                "5, true | 7, false"},
            {"SELECT id FROM fz WHERE id IN (SELECT 5 WHERE s = 'a' UNION SELECT 7 WHERE s = 'b') ORDER BY id",
                "5 | 7"},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT 5 WHERE s = 'a' UNION ALL SELECT 6 WHERE s = 'b') ORDER BY id",
                "7"},
            {"SELECT id, id IN (SELECT 5 WHERE s = 'a' UNION ALL SELECT 7 WHERE s = 'x') FROM fz ORDER BY id",
                "5, true | 7, false"},
        });
    }

    @Test
    public void anOperandTheStatisticsProveEmptyDropsOut() {
        assertCells(new String[][] {
            {exists("SELECT 1 WHERE id = 5 UNION ALL SELECT 1 FROM g WHERE FALSE"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 WHERE FALSE"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = 99 UNION ALL SELECT 1 WHERE id = 7"), "7"},
            {exists("SELECT id WHERE id > 5 UNION ALL SELECT 1 WHERE FALSE"), "7"},
            {exists("SELECT 1 WHERE fz.s LIKE 'b%' UNION ALL SELECT 1 WHERE FALSE"), "7"},
            {exists("SELECT 1 WHERE id IN (5, 7) UNION ALL SELECT 1 WHERE FALSE"), "5 | 7"},
            // g.v holds 50 and 60, fz.id 5 and 7: the second operand never meets a row.
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.v = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION SELECT 1 FROM g WHERE g.v = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE fz.id = g.v"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id GROUP BY g.v UNION ALL SELECT 1 FROM g WHERE g.v = fz.id"),
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT 5 WHERE s = 'a' UNION ALL SELECT 7 WHERE s = 'x') ORDER BY id",
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id "
                + "UNION ALL SELECT g.v FROM g WHERE g.v = fz.id) ORDER BY id", "5"},
            {exists("SELECT 1 FROM g WHERE g.id = 5 UNION ALL SELECT 1 WHERE FALSE"), "5 | 7"},
        });
    }

    @Test
    public void operandsThatReadTheOuterRowDifferentlyAreRefused() {
        assertUnsupported(new String[][] {
            {exists("SELECT 1 WHERE fz.id = 7 UNION ALL SELECT 1 FROM g WHERE g.id = fz.id"), "24"},
            {exists("SELECT fz.id + 1 UNION ALL SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT 1 WHERE id IN (5, 7) UNION ALL SELECT 1 WHERE fz.s LIKE 'a%'"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 WHERE fz.id = 7"), "24"},
            {exists("SELECT 1 WHERE fz.id = 7 UNION ALL SELECT 1 FROM g WHERE g.id = 5"), "24"},
            {exists("SELECT 1 UNION ALL SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL SELECT 1 WHERE 7 = fz.id"), "24"},
            {exists("SELECT 1 WHERE fz.id > 6 UNION ALL SELECT 1 WHERE fz.id > 4"), "24"},
            {exists("SELECT 1 WHERE fz.id IN (5, 6) UNION ALL SELECT 1 WHERE fz.id IN (7)"), "24"},
            {exists("SELECT 1 WHERE fz.id = 5 AND fz.s = 'a' UNION ALL SELECT 1 WHERE fz.id = 7"), "24"},
            {exists("SELECT 1 WHERE fz.id = 5 OR fz.id = 6 UNION ALL SELECT 1 WHERE fz.id = 7"), "24"},
            {exists("SELECT 1 WHERE fz.s = 'a' UNION ALL SELECT 1 WHERE fz.id = 7"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.v = fz.id + 43"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.id < fz.id"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.s = fz.s"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g"), "24"},
            {exists("SELECT 1 WHERE fz.id = 5 UNION ALL SELECT 1 WHERE fz.id = LENGTH(fz.s) + 6"), "24"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.s = fz.s UNION ALL SELECT 7 WHERE fz.s = 'b') "
                + "ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT 5 UNION ALL SELECT 7 WHERE fz.s = 'b') ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id UNION ALL SELECT 7) ORDER BY id",
                "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id UNION SELECT 7) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT id UNION ALL SELECT 7) ORDER BY id", "31"},
            {"SELECT id FROM fz WHERE id IN (SELECT fz.id UNION ALL SELECT 7) ORDER BY id", "31"},
        });
    }

    @Test
    public void anOperandTheSingleSelectRuleRefusesIsRefused() {
        assertUnsupported(new String[][] {
            {exists("SELECT 1 WHERE b UNION ALL SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT COUNT(*) WHERE id = 5 UNION ALL SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE fz.b"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.id + fz.id = 12"), "24"},
            {exists("SELECT MAX(g.v) FROM g WHERE g.id = fz.id UNION ALL SELECT 1 WHERE FALSE"), "24"},
            {exists("SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE id = 7 LIMIT 1"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.id = 7 LIMIT 5"), "24"},
            {"SELECT id FROM fz WHERE id IN (SELECT 5 WHERE s = 'a' UNION ALL SELECT 7 WHERE s = 'b' LIMIT 3) "
                + "ORDER BY id", "31"},
            {"SELECT id, (SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE FALSE) FROM fz ORDER BY id", "12"},
        });
    }

    @Test
    public void operandsReadingTwoRelationsOfTheOuterRowDiffer() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        assertUnsupported(new String[][] {
            {"SELECT fz.id, g.id FROM fz JOIN g ON TRUE WHERE EXISTS (SELECT 1 WHERE fz.id = 5 "
                + "UNION ALL SELECT 1 WHERE g.id = 6) ORDER BY 1, 2", "48"},
            {"SELECT fz.id, g.id FROM fz JOIN g ON TRUE WHERE EXISTS (SELECT 1 WHERE fz.s = 'a' "
                + "UNION ALL SELECT 1 WHERE g.s = 'a') ORDER BY 1, 2", "48"},
            {"SELECT fz.id, f2.id FROM fz JOIN fz f2 ON TRUE WHERE EXISTS (SELECT 1 WHERE fz.id = 5 "
                + "UNION ALL SELECT 1 WHERE f2.id = 7) ORDER BY 1, 2", "53"},
            {"SELECT fz.id, g.id FROM fz JOIN g ON TRUE WHERE fz.id IN (SELECT 5 WHERE fz.s = 'a' "
                + "UNION ALL SELECT 7 WHERE g.s = 'a') ORDER BY 1, 2", "58"},
            {"SELECT q.v, fz.id FROM q JOIN fz ON TRUE WHERE EXISTS (SELECT 1 WHERE v = 1 "
                + "UNION ALL SELECT 1 WHERE id = 7) ORDER BY 1, 2", "47"},
        });
        assertCells(new String[][] {
            {"SELECT q.v, fz.id FROM q JOIN fz ON TRUE WHERE EXISTS (SELECT 1 WHERE v = 1 "
                + "UNION ALL SELECT 1 WHERE q.v = 2) ORDER BY 1, 2", "1, 5 | 1, 7 | 2, 5 | 2, 7"},
            {"SELECT f.id FROM fz f WHERE EXISTS (SELECT 1 WHERE f.id = 5 UNION ALL SELECT 1 WHERE id = 7) ORDER BY 1",
                "5 | 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = 5 UNION ALL SELECT 1 WHERE fz.id = 7) ORDER BY 1",
                "5 | 7"},
            {"SELECT fz.id, f2.id FROM fz JOIN fz f2 ON TRUE WHERE EXISTS (SELECT 1 WHERE f2.id = 5 "
                + "UNION ALL SELECT 1 WHERE f2.id = 7) ORDER BY 1, 2", "5, 5 | 5, 7 | 7, 5 | 7, 7"},
            {"SELECT fz.id, g.id FROM fz JOIN g ON TRUE WHERE EXISTS (SELECT 1 WHERE fz.s = 'a' "
                + "UNION ALL SELECT 1 WHERE fz.s = 'b') ORDER BY 1, 2", "5, 5 | 5, 6 | 7, 5 | 7, 6"},
        });
    }

    @Test
    public void aSecondOperandTheStatisticsProveEmptyDropsOut() {
        engine.execute("CREATE TABLE ge (id INT)");
        assertCells(new String[][] {
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 FROM g WHERE g.v = fz.id"), ""},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 FROM g WHERE g.v = fz.id + 1000"), ""},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 1 FROM g WHERE g.v = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id MINUS SELECT 1 FROM g WHERE g.v = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 1 FROM ge WHERE ge.id = fz.id"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT (SELECT 1 FROM g WHERE g.v = fz.id "
                + "UNION ALL SELECT 1 FROM ge WHERE ge.id = fz.id)"), "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id INTERSECT SELECT g.id FROM g "
                + "WHERE g.v = fz.id) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id EXCEPT SELECT g.id FROM g "
                + "WHERE g.v = fz.id) ORDER BY id", "5"},
            {"SELECT id, EXISTS (SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 1 FROM g WHERE g.v = fz.id) "
                + "FROM fz ORDER BY id", "5, true | 7, false"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 1 FROM g EXCEPT SELECT 2 FROM g"), ""},
        });
        // The first operand is judged still, a first operand proven empty spares nothing, and a UNION reading the
        // outer row is not planned beneath an EXCEPT.
        assertUnsupported(new String[][] {
            {exists("SELECT 1 WHERE fz.b INTERSECT SELECT 1 FROM g WHERE g.v = fz.id"), "24"},
            {exists("SELECT 1 WHERE fz.b EXCEPT SELECT 1 FROM g WHERE g.v = fz.id"), "24"},
            {exists("SELECT 1 FROM g WHERE g.v = fz.id INTERSECT SELECT 1 FROM g WHERE g.id = fz.id"), "24"},
            {exists("SELECT 1 FROM g WHERE g.v = fz.id EXCEPT SELECT 1 FROM g WHERE g.id = fz.id"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.id + 2 = fz.id "
                + "EXCEPT SELECT 1 FROM g WHERE g.v = fz.id"), "24"},
            {exists("(SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.id + 2 = fz.id) "
                + "EXCEPT SELECT 1 FROM g WHERE g.v = fz.id"), "24"},
        });
    }

    @Test
    public void aUnionOperandIsPrunedAsASelectIsPruned() {
        engine.execute("CREATE TABLE ge (id INT)");
        assertCells(new String[][] {
            {exists("SELECT 1 FROM g WHERE g.s = 'x' AND g.id + fz.id = 10 "
                + "UNION ALL SELECT 1 FROM g WHERE g.s = 'y' AND g.id + fz.id = 12"), ""},
            {"SELECT id FROM fz WHERE id + 100 IN (SELECT g.id FROM g WHERE fz.id = fz.id "
                + "UNION ALL SELECT g.id FROM g WHERE g.id = fz.id) ORDER BY id", ""},
        });
        // A GROUP BY over the outer row is judged ahead of the pruning, in an operand as in a select.
        assertUnsupported(new String[][] {
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.v > 1000 GROUP BY fz.id"),
                "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM ge GROUP BY fz.id"), "24"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE fz.id = fz.id "
                + "UNION ALL SELECT g.id FROM g WHERE g.id = fz.id) ORDER BY id", "31"},
        });
    }

    @Test
    public void intersectAndExceptKeepTheirFirstOperandsCorrelationOnly() {
        assertCells(new String[][] {
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 FROM g"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 1 WHERE FALSE"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id EXCEPT SELECT 2 FROM g"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id MINUS SELECT 2 FROM g"), "5"},
            {exists("SELECT 1 WHERE id = 5 EXCEPT SELECT 2"), "5"},
            {exists("SELECT 1 WHERE id = 5 INTERSECT SELECT 1"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 FROM g INTERSECT SELECT 1"), "5"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 WHERE FALSE"), ""},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id INTERSECT SELECT 1 FROM g "
                + "UNION ALL SELECT 1 FROM g WHERE g.v = fz.id"), "5"},
            {exists("SELECT g.id FROM g WHERE g.id = fz.id INTERSECT SELECT 5"), "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id INTERSECT SELECT 5) ORDER BY id",
                "5"},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id EXCEPT SELECT 6) ORDER BY id", "5"},
        });
        assertUnsupported(new String[][] {
            {exists("SELECT 1 WHERE id = 5 INTERSECT SELECT 1 WHERE id = 5"), "24"},
            {exists("SELECT 1 WHERE id = 5 EXCEPT SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT 1 WHERE id = 5 MINUS SELECT 1 WHERE id = 7"), "24"},
            {exists("SELECT 1 FROM g INTERSECT SELECT 1 FROM g WHERE g.id = fz.id"), "24"},
            {exists("SELECT 1 FROM g EXCEPT SELECT 1 FROM g WHERE g.id = fz.id"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL (SELECT 1 FROM g WHERE g.v = fz.id "
                + "INTERSECT SELECT 1 FROM g)"), "24"},
            {exists("SELECT 1 FROM g WHERE g.id = fz.id UNION ALL SELECT 1 FROM g WHERE g.v = fz.id "
                + "INTERSECT SELECT 1 FROM g"), "24"},
            {"SELECT id FROM fz WHERE id IN (SELECT 5 WHERE s = 'a' INTERSECT SELECT 5 WHERE s = 'a') ORDER BY id",
                "31"},
        });
    }
}
