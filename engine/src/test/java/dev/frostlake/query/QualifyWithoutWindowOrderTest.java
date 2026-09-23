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

/**
 * What a QUALIFY no window function serves is refused AFTER, and what it is refused BEFORE (live-verified).
 *
 * <p>After the query has compiled: a select item's argument types, its arity and its constant arguments — a
 * DISTINCT list's, a FROM-less list's and a grouped list's too — the HAVING's, and whatever a subquery's
 * compilation refuses, its own QUALIFY included. Before any row is read, so a value an aggregate cannot compute
 * never speaks first, and before ROUND's rounding mode, which live judges later wherever the call stands.
 */
public class QualifyWithoutWindowOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), d DATE)");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'x', '2024-01-01'), (2, 2.5, 'y', '2024-01-02')");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String noWindow(final int position) {
        return "SQL compilation error: error line 1 at position " + position
            + "|found QUALIFY clause but no window function.";
    }

    private static String upperArity(final int position) {
        return "SQL compilation error: error line 1 at position " + position
            + "|too many arguments for function [UPPER(1, 2)] expected 1, got 2";
    }

    /** A select item's argument rules come first. */
    @Test
    public void aSelectItemsArgumentsAreJudgedFirst() {
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N'",
            answer("SELECT RANDOM(n) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'RANDOM': (BOOLEAN)",
            answer("SELECT RANDOM(TRUE) FROM rt QUALIFY 1 = 1"));
        assertEquals(upperArity(7), answer("SELECT UPPER(1, 2) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'ABS': (DATE)",
            answer("SELECT ABS(d) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for function [LPAD(RT.G)], expected 2, got 1",
            answer("SELECT LPAD(g) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 9|Invalid argument types for function '+': (NUMBER(38,0), OBJECT)",
            answer("SELECT n + OBJECT_CONSTRUCT('a', 1) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(TRUE)] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(TRUE) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: ['nosuchpart'] is not a valid date/time component for function DATEADD.",
            answer("SELECT DATEADD('nosuchpart', 1, d) FROM rt QUALIFY 1 = 1"));
    }

    /** Whatever the list looks like: aliased, beside a column, DISTINCT, FROM-less, and whatever QUALIFY reads. */
    @Test
    public void everyListShapeIsJudgedFirst() {
        assertEquals(upperArity(7), answer("SELECT UPPER(1, 2) AS u FROM rt QUALIFY u = 1"));
        assertEquals(upperArity(10), answer("SELECT n, UPPER(1, 2) FROM rt QUALIFY TRUE"));
        assertEquals(upperArity(16), answer("SELECT DISTINCT UPPER(1, 2) FROM rt QUALIFY 1 = 1"));
        assertEquals(upperArity(7), answer("SELECT UPPER(1, 2) QUALIFY 1 = 1"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RT.N'",
            answer("SELECT RANDOM(n) FROM rt QUALIFY n = 1"));
    }

    /** A subquery's compilation refusal comes first, wherever the subquery stands and whatever it refuses. */
    @Test
    public void aSubquerysRefusalComesFirst() {
        assertEquals("SQL compilation error: error line 1 at position 15|too many arguments for function [UPPER(1, 2)] expected 1, got 2",
            answer("SELECT (SELECT UPPER(1, 2)) FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 37|too many arguments for function [LOWER(1, 2)] expected 1, got 2",
            answer("SELECT n FROM rt QUALIFY 1 = (SELECT LOWER(1, 2))"));
        assertEquals(noWindow(17), answer("SELECT (SELECT 1 QUALIFY TRUE) FROM rt QUALIFY 1 = 1"));
    }

    /** An aggregate query: the grouped list is judged over no rows, so no value it computes speaks first. */
    @Test
    public void anAggregateQueryIsRefusedBeforeItsValues() {
        assertEquals(noWindow(22), answer("SELECT SUM(g) FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(33), answer("SELECT SUM(g) FROM rt GROUP BY n QUALIFY 1 = 1"));
        assertEquals("SQL compilation error:|[RT.N] is not a valid group by expression",
            answer("SELECT SUM(g), n FROM rt QUALIFY 1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 7|'RT.F' in select clause is neither an aggregate nor in the group by clause.",
            answer("SELECT f, SUM(n) FROM rt GROUP BY n QUALIFY 1 = 1"));
        assertEquals(upperArity(29), answer("SELECT SUM(n) FROM rt HAVING UPPER(1, 2) = 1 QUALIFY 1 = 1"));
        assertEquals(upperArity(7), answer("SELECT UPPER(1, 2), COUNT(*) FROM rt QUALIFY 1 = 1"));
    }

    /** ROUND's rounding mode is judged after the QUALIFY, in the select list, WHERE and ORDER BY alike. */
    @Test
    public void roundsModeWaitsForTheQualify() {
        assertEquals(noWindow(37), answer("SELECT ROUND(n, 0, 'nosuch') FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(43), answer("SELECT ROUND(f, 0, 'HALF_TO_EVEN') FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(30), answer("SELECT ROUND(n, 1, 1) FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(32), answer("SELECT ROUND(1.5, 0, n) FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(42), answer("SELECT n FROM rt WHERE ROUND(n, 1, 1) = 1 QUALIFY 1 = 1"));
        assertEquals(noWindow(17), answer("SELECT n FROM rt QUALIFY 1 = 1 ORDER BY ROUND(n, 1, 1)"));
    }

    /** What is only known per row still waits for the QUALIFY. */
    @Test
    public void rowTimeFaultsStillWait() {
        assertEquals(noWindow(19), answer("SELECT 1/0 FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(32), answer("SELECT CAST('x' AS INT) FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(29), answer("SELECT ARRAY_SIZE(n) FROM rt QUALIFY 1 = 1"));
        assertEquals(noWindow(27), answer("SELECT ROUND(f, n) FROM rt QUALIFY 1 = 1"));
    }
}
