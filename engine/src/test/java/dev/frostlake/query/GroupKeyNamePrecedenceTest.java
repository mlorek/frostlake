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
 * With two faults, a GROUP BY key's unknown name, and then a positional key past the select list, is reported
 * right after the select list's and the WHERE's names and ahead of everything else (live-verified): the other
 * clauses' names, an unknown function, an argument count, an aggregate or a window out of place, and any
 * subquery. A plus sign is an operator, so {@code GROUP BY +2} groups by a constant rather than a position.
 */
public class GroupKeyNamePrecedenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2), (3, 4)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String badName(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "|invalid identifier '" + name + "'";
    }

    private static String badPosition(final String written) {
        return "SQL compilation error:|[" + written + "] is not a valid group by expression";
    }

    /** A key's name comes before any subquery's, wherever the subquery stands. */
    @Test
    public void aKeysNameComesBeforeASubquerysName() {
        assertEquals(badName(64, "NOSUCH2"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) GROUP BY nosuch2"));
        assertEquals(badName(52, "NOSUCH2"), answer("SELECT (SELECT nosuch1 FROM FULL_T) FROM T GROUP BY nosuch2"));
        assertEquals(badName(67, "NOSUCH2"), answer("SELECT a FROM T WHERE EXISTS (SELECT nosuch1 FROM FULL_T) GROUP BY nosuch2"));
        assertEquals(badName(72, "NOSUCH2"), answer("SELECT COUNT(*) FROM T WHERE a IN (SELECT nosuch1 FROM FULL_T) GROUP BY nosuch2"));
        assertEquals(badName(37, "NOSUCH2"), answer("SELECT a FROM T WHERE a = 1 GROUP BY nosuch2 HAVING a = (SELECT nosuch1 FROM FULL_T)"));
        assertEquals(badName(76, "NOSUCH2"),
            answer("SELECT a FROM T WHERE a IN (SELECT a FROM FULL_T GROUP BY nosuch1) GROUP BY nosuch2"));
    }

    /** Every key shape: a later key, a ROLLUP member, a grouping set's member. */
    @Test
    public void everyKeyShapeIsJudged() {
        assertEquals(badName(67, "NOSUCH2"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) GROUP BY a, nosuch2"));
        assertEquals(badName(71, "NOSUCH2"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) GROUP BY ROLLUP(nosuch2)"));
        assertEquals(badName(80, "NOSUCH2"),
            answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) GROUP BY GROUPING SETS ((nosuch2))"));
    }

    /** It comes after the select list's and the WHERE's names, and before the other clauses' names. */
    @Test
    public void itSitsBetweenTheClauses() {
        assertEquals(badName(7, "NOSUCH1"), answer("SELECT nosuch1 FROM T GROUP BY nosuch2"));
        assertEquals(badName(22, "NOSUCH1"), answer("SELECT a FROM T WHERE nosuch1 = 1 GROUP BY nosuch2"));
        assertEquals(badName(25, "NOSUCH2"), answer("SELECT a FROM T GROUP BY nosuch2 HAVING nosuch1 = 1"));
        assertEquals(badName(25, "NOSUCH2"), answer("SELECT a FROM T GROUP BY nosuch2 ORDER BY nosuch1"));
        assertEquals(badName(25, "NOSUCH2"), answer("SELECT a FROM T GROUP BY nosuch2 QUALIFY nosuch1 = 1"));
        assertEquals("SQL compilation error: error line 1 at position 40|aggregate function alias 'S' cannot be used in the WHERE clause",
            answer("SELECT a AS x, SUM(b) AS s FROM T WHERE s = 1 GROUP BY nosuch2"));
    }

    /** And before what judges a meaning: an unknown function, an arity, an aggregate or a window out of place. */
    @Test
    public void itComesBeforeMeanings() {
        assertEquals(badName(35, "NOSUCH2"), answer("SELECT nosuchfn(a) FROM T GROUP BY nosuch2"));
        assertEquals(badName(47, "NOSUCH2"), answer("SELECT a FROM T WHERE nosuchfn(a) = 1 GROUP BY nosuch2"));
        assertEquals(badName(25, "NOSUCH2"), answer("SELECT a FROM T GROUP BY nosuch2 ORDER BY nosuchfn(a)"));
        assertEquals(badName(49, "NOSUCH2"), answer("SELECT a FROM T WHERE UPPER(1, 2) = 'x' GROUP BY nosuch2"));
        assertEquals(badName(35, "NOSUCH2"), answer("SELECT UPPER(1, 2) FROM T GROUP BY nosuch2"));
        assertEquals(badName(42, "NOSUCH2"), answer("SELECT a FROM T WHERE SUM(a) > 1 GROUP BY nosuch2"));
        assertEquals(badName(66, "NOSUCH2"), answer("SELECT a FROM T WHERE ROW_NUMBER() OVER (ORDER BY a) = 1 GROUP BY nosuch2"));
        assertEquals(badName(25, "NOSUCH2"), answer("SELECT a FROM T GROUP BY nosuch2 ORDER BY 9"));
    }

    /** A position past the select list follows the keys' names and precedes the rest. */
    @Test
    public void aPositionPastTheListFollowsTheNames() {
        assertEquals(badName(28, "NOSUCH2"), answer("SELECT a FROM T GROUP BY 9, nosuch2"));
        assertEquals(badName(22, "NOSUCH1"), answer("SELECT a FROM T WHERE nosuch1 = 1 GROUP BY 9"));
        assertEquals(badPosition("9"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1 FROM FULL_T) GROUP BY 9"));
        assertEquals(badPosition("9"), answer("SELECT a FROM T GROUP BY 9 ORDER BY nosuch1"));
        assertEquals(badPosition("9"), answer("SELECT nosuchfn(a) FROM T GROUP BY 9"));
        assertEquals(badPosition("9"), answer("SELECT a FROM T GROUP BY 9 HAVING nosuch1 = 1"));
        assertEquals(badPosition("9"), answer("SELECT a FROM T WHERE UPPER(1, 2) = 'x' GROUP BY 9"));
        assertEquals(badPosition("9"), answer("SELECT UPPER(1, 2) FROM T GROUP BY 9"));
        assertEquals(badPosition("9"), answer("SELECT a FROM T WHERE ROW_NUMBER() OVER (ORDER BY a) = 1 GROUP BY 9"));
        assertEquals(badPosition("-1"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1) GROUP BY -1"));
        assertEquals(badPosition("9"), answer("SELECT a, COUNT(*) FROM T WHERE a = (SELECT nosuch1) GROUP BY GROUPING SETS ((9))"));
    }

    /** A plus sign makes an expression: the key is a constant, not a position. */
    @Test
    public void aPlusSignIsNoPosition() {
        assertEquals("SQL compilation error: error line 1 at position 7|'T.A' in select clause is neither an aggregate nor in the group by clause.",
            answer("SELECT a FROM T GROUP BY +2"));
        assertEquals("SQL compilation error: error line 1 at position 7|'FULL_T.A' in select clause is neither an aggregate nor in the group by clause.",
            answer("SELECT a FROM FULL_T GROUP BY +1"));
        assertEquals(badName(34, "NOSUCH1"), answer("SELECT a FROM T WHERE a = (SELECT nosuch1) GROUP BY +2"));
        assertEquals("ACCEPTED 2", answer("SELECT a FROM FULL_T ORDER BY +2"));
    }
}
