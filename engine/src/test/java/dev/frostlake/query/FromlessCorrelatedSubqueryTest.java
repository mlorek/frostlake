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
 * A FROM-less subquery reads the outer row by a bare name in its WHERE, as it does in its items: under EXISTS, NOT
 * EXISTS or IN, beside a qualified name, through an OR or a nested subquery, over a table, a derived table, a CTE or
 * a subquery's own relation, and in an UPDATE or DELETE. The names its own items publish still come first, the items
 * of a FROM-less query around it are still no outer row, a name nothing answers is still an invalid identifier, and
 * a shape the account cannot plan is still refused as an unsupported subquery. Every cell is live-verified.
 */
public class FromlessCorrelatedSubqueryTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
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

    @Test
    public void aBareOuterNameInTheWhereReadsTheOuterRow() {
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM q WHERE EXISTS (SELECT 1 WHERE v = 1)", "1"},
            {"SELECT COUNT(*) FROM q WHERE EXISTS (SELECT 1 WHERE q.v = 1)", "1"},
            {"SELECT COUNT(*) FROM q WHERE NOT EXISTS (SELECT 1 WHERE v = 1)", "1"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v = 1 AND v < 5) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE NOT EXISTS (SELECT 1 WHERE v = 1) ORDER BY v", "2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v IN (1, 3)) ORDER BY v", "1"},
            {"SELECT v FROM q q1 WHERE EXISTS (SELECT 1 WHERE q1.v = 2) ORDER BY v", "2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v = (SELECT MAX(v) FROM q)) ORDER BY v", "2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v = 1) OR v = 2 ORDER BY v", "1 | 2"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = 5 AND s = 'a') ORDER BY id", "5"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = 5 GROUP BY 1)", "5"},
            {"SELECT v FROM q WHERE v IN (SELECT 1 WHERE v = 1) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE 1 = (SELECT COUNT(*) WHERE v = 1) ORDER BY v", "1"},
            {"SELECT v, EXISTS (SELECT 1 WHERE v = 1) FROM q ORDER BY v", "1, true | 2, false"},
        });
    }

    @Test
    public void everyKindOfOuterQueryLendsItsRow() {
        assertCells(new String[][] {
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 AS w WHERE w = v) ORDER BY v", "1"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 WHERE v = 50 AND id = 5)) ORDER BY id",
                "5 | 7"},
            {"SELECT v FROM (SELECT 1 AS v) WHERE EXISTS (SELECT 1 WHERE v = 1)", "1"},
            {"WITH c AS (SELECT v FROM q) SELECT v FROM c WHERE EXISTS (SELECT 1 WHERE v = 2)", "2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE \"V\" = 1) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE V = 1) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE EXISTS (SELECT \"V\" WHERE \"V\" = 1) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE v = 1 ORDER BY 1) ORDER BY v", "1"},
        });
        engine.execute("UPDATE fz SET s = 'u' WHERE EXISTS (SELECT 1 WHERE id = 5)");
        assertEquals("5, u | 7, b", rows("SELECT id, s FROM fz ORDER BY id"));
        engine.execute("DELETE FROM q WHERE EXISTS (SELECT 1 WHERE v = 1)");
        assertEquals("2", rows("SELECT v FROM q ORDER BY v"));
    }

    @Test
    public void theItemsNamesStillComeFirst() {
        assertCells(new String[][] {
            {"SELECT v FROM q WHERE EXISTS (SELECT v WHERE v > 1) ORDER BY v", "2"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT id WHERE id = 5) ORDER BY id", "5"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1) ORDER BY v", "1 | 2"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE FALSE) ORDER BY v", ""},
        });
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE nosuch = 1)",
            "error line 1 at position 47\ninvalid identifier 'NOSUCH'");
        assertRefused("SELECT 1 AS a WHERE EXISTS (SELECT 1 WHERE a = 1)",
            "error line 1 at position 43\ninvalid identifier 'A'");
        assertRefused("SELECT 1 AS a WHERE a IN (SELECT 1 WHERE a = 1)",
            "error line 1 at position 41\ninvalid identifier 'A'");
    }

    @Test
    public void aShapeTheAccountCannotPlanIsStillRefused() {
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE b) ORDER BY id",
            "Unsupported subquery type cannot be evaluated at line 1, position 24");
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE fz.b AND fz.s = 'a') ORDER BY id",
            "Unsupported subquery type cannot be evaluated at line 1, position 24");
        assertRefused("SELECT id FROM fz WHERE NOT EXISTS (SELECT 1 WHERE b) ORDER BY id",
            "Unsupported subquery type cannot be evaluated at line 1, position 28");
        assertRefused("SELECT id, EXISTS (SELECT 1 WHERE b) FROM fz ORDER BY id",
            "Unsupported subquery type cannot be evaluated at line 1, position 11");
        assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE id = 5 LIMIT 1) ORDER BY id",
            "Unsupported subquery type cannot be evaluated at line 1, position 24");
        assertRefused("SELECT v, (SELECT 1 WHERE v = 1) FROM q ORDER BY v",
            "Unsupported subquery type cannot be evaluated at line 1, position 11");
    }
}
