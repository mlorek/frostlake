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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a correlated membership stands decides how it is judged. A HAVING holding no aggregate, EXISTS or scalar
 * subquery of its own is moved to the WHERE, so a positive IN conjunct of it is a semi-join that drops a GROUP BY
 * removing duplicates, as in a WHERE. And a QUALIFY with no window function is reported ahead of any correlated
 * subquery live cannot evaluate, whichever clause holds that. Every cell is live-verified.
 */
public class CorrelatedHavingQualifyPlacementTest extends BaseDatabaseTest {

    private static final String UNSUPPORTED = "Unsupported subquery type cannot be evaluated at line 1, position ";

    private static final String NO_WINDOW = "found QUALIFY clause but no window function.";

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
    }

    private void assertEmpty(final String[] statements) {
        for (final String sql : statements) {
            assertEquals(0, engine.executeQuery(sql).getRowCount(), sql);
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
    public void aHavingMovedToTheWhereKeepsItsSemiJoin() {
        assertEmpty(new String[] {
            "SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v",
            "SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v) AND v > 0 ORDER BY v",
            "SELECT v FROM q GROUP BY v HAVING (v IN (SELECT g.v FROM g GROUP BY g.v, q.v)) ORDER BY v",
            "SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v)"
                + " AND v IN (SELECT g.id FROM g) ORDER BY v",
            "SELECT v, COUNT(*) FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v",
            "SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g WHERE g.id > q.v GROUP BY g.v, q.v) ORDER BY v",
        });
    }

    @Test
    public void aHavingLeftInPlaceRefusesTheGroupingOverTheOuterRow() {
        final String[][] cells = {
            {"SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v) AND COUNT(*) > 0"
                + " ORDER BY v", "40"},
            {"SELECT v FROM q GROUP BY v HAVING MAX(v) IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v", "45"},
            {"SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v)"
                + " AND (SELECT COUNT(*) FROM g) > 0 ORDER BY v", "40"},
            {"SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v)"
                + " AND EXISTS (SELECT 1 FROM g WHERE g.id > 0) ORDER BY v", "40"},
            {"SELECT v FROM q GROUP BY v HAVING v NOT IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v", "44"},
            {"SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v FROM g GROUP BY g.v, q.v) OR v = 2 ORDER BY v", "40"},
            {"SELECT v FROM q GROUP BY v HAVING v IN (SELECT g.v - 49 FROM g GROUP BY g.v, q.v) ORDER BY v", "40"},
        };
        for (final String[] cell : cells) {
            assertRefused(cell[0], UNSUPPORTED + cell[1]);
        }
    }

    @Test
    public void aQualifyWithoutWindowIsReportedFirst() {
        assertRefused("SELECT v FROM q QUALIFY v IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v", NO_WINDOW);
        assertRefused("SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g GROUP BY q.v) QUALIFY v > 0 ORDER BY v",
            NO_WINDOW);
        assertRefused("SELECT v, EXISTS (SELECT 1 FROM g WHERE g.id = q.v + 4 LIMIT 1) FROM q QUALIFY v > 0 ORDER BY v",
            NO_WINDOW);
        assertRefused("SELECT v FROM (SELECT * FROM q) d WHERE EXISTS (SELECT 1 FROM g GROUP BY d.v) QUALIFY v > 0"
            + " ORDER BY v", NO_WINDOW);
        // The position is the QUALIFY keyword's.
        assertRefused("SELECT v FROM q QUALIFY v IN (SELECT g.v FROM g GROUP BY g.v, q.v) ORDER BY v",
            "error line 1 at position 16");
        // A name inside the subquery still speaks first, and with a window present the correlation does.
        assertRefused("SELECT v FROM q WHERE EXISTS (SELECT nope FROM g) QUALIFY v > 0 ORDER BY v",
            "invalid identifier 'NOPE'");
        assertRefused("SELECT v FROM q QUALIFY v IN (SELECT g.v FROM g GROUP BY g.v, q.v)"
            + " AND ROW_NUMBER() OVER (ORDER BY v) > 0 ORDER BY v", UNSUPPORTED + "30");
        assertRefused("SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE g.id = q.v + 4 LIMIT 1)"
            + " QUALIFY v > 0 AND COUNT(*) OVER () > 0 ORDER BY v", UNSUPPORTED + "22");
    }
}
