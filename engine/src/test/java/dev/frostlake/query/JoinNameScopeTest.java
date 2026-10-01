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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The name scopes of a FROM clause: an ON, comma, CROSS or LATERAL join keeps its relations apart, so a
 * bare name two of them carry is {@code ambiguous column name 'X'}; a USING or NATURAL join merges every
 * relation its comma-separated item joined before it with its right side into ONE relation, in which the
 * left-most copy answers. A merged key therefore answers only for the relations it merged — a relation
 * joined after them that carries the same name makes it ambiguous again.
 */
public class JoinNameScopeTest extends JoinScopeTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2)");
        engine.execute("CREATE TABLE k1 (k INT, b INT)");
        engine.execute("CREATE TABLE k2 (j INT, b INT)");
        engine.execute("CREATE TABLE k3 (k INT, e INT)");
        engine.execute("CREATE TABLE k4 (k INT, b INT)");
        engine.execute("CREATE TABLE k5 (j INT, c INT)");
        engine.execute("CREATE TABLE k6 (c INT, d INT)");
        engine.execute("INSERT INTO k1 VALUES (1, 10)");
        engine.execute("INSERT INTO k2 VALUES (1, 20)");
        engine.execute("INSERT INTO k3 VALUES (1, 5)");
        engine.execute("INSERT INTO k4 VALUES (1, 40)");
        engine.execute("INSERT INTO k5 VALUES (1, 50)");
        engine.execute("INSERT INTO k6 VALUES (50, 60)");
    }

    private void assertAmbiguous(final String sql, final String name) {
        assertRefused(sql, "ambiguous column name '" + name + "'");
    }

    @Test
    public void aLaterRelationCarryingTheMergedKeyMakesItAmbiguous() {
        assertAmbiguous("SELECT f.a FROM full_t f JOIN t USING (a) JOIN t t2 ON t2.a = a", "A");
        assertAmbiguous("SELECT f.a FROM full_t f NATURAL JOIN t JOIN t t2 ON t2.a = a", "A");
        assertAmbiguous("SELECT f.a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON t2.a = a", "A");
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON t2.a = f.a", "A");
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a) LEFT JOIN full_t t2 ON t2.a = f.a", "A");
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a) JOIN (SELECT 1 a) t2 ON TRUE", "A");
    }

    @Test
    public void everyClauseRefusesItWhileCompiling() {
        assertAmbiguous("SELECT f.a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON t2.a = f.a WHERE a = 1",
            "A");
        assertAmbiguous("SELECT COUNT(*) FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON TRUE GROUP BY a",
            "A");
        assertAmbiguous("SELECT a AS z FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON TRUE ORDER BY a", "A");
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON TRUE WHERE FALSE", "A");
        assertAmbiguous("SELECT SUM(k) FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE", "K");
        assertAmbiguous("SELECT k1.b FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE HAVING MAX(k) > 0", "K");
        assertAmbiguous("SELECT k1.b FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE QUALIFY ROW_NUMBER() OVER (ORDER BY k) = 1",
            "K");
        // The ON of a later join sees the scopes as they stand there.
        assertAmbiguous("SELECT k FROM k1 JOIN k3 USING (k) JOIN k2 ON b = 10", "B");
        assertAmbiguous("SELECT k FROM k1 JOIN k4 USING (k) JOIN k3 ON k3.k = k", "K");
        // And a subquery's own FROM clause is judged the same way.
        assertAmbiguous("SELECT (SELECT k FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE LIMIT 1)", "K");
    }

    @Test
    public void aNonKeyNameIsJudgedTheSameWay() {
        assertAmbiguous("SELECT b FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON TRUE", "B");
        assertAmbiguous("SELECT b FROM k3 JOIN k1 USING (k) JOIN k2 ON TRUE", "B");
        assertEquals("2", rows("SELECT b FROM full_t f JOIN full_t g USING (a)"));
    }

    @Test
    public void aCommaOrLateralItemIsARelationOfItsOwn() {
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a), full_t t2", "A");
        assertAmbiguous("SELECT a FROM full_t f JOIN full_t g USING (a) CROSS JOIN full_t t2", "A");
        assertAmbiguous("SELECT k FROM k1 JOIN k4 USING (k), LATERAL (SELECT k1.k AS k) l", "K");
        assertAmbiguous("SELECT k FROM k3, k1 JOIN k2 USING (b)", "K");
        assertEquals("1", rows("SELECT k FROM k1 JOIN k3 USING (k), k2"));
        assertEquals("1", rows("SELECT k FROM k1 JOIN k4 USING (k), LATERAL (SELECT k1.k AS z) l"));
    }

    @Test
    public void aLaterItemsParenthesizedGroupStaysOneItemBesideAnEarlierItemsJoins() {
        // The group's own USING join runs before the first item's join, and still merges with the item's
        // later USING join rather than with the first item.
        assertEquals("50", rows("SELECT c FROM k1 JOIN k4 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("50", rows("SELECT c FROM k1 JOIN k4 ON TRUE, (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("50|60", rows("SELECT c, d FROM k1 JOIN k3 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("50", rows("SELECT c FROM k1, (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("1", rows("SELECT j FROM k1 JOIN k4 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 ON TRUE"));
        assertEquals("1", rows("SELECT k FROM k1 JOIN k4 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("1", rows("SELECT j FROM (k1 JOIN k4 USING (k)), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)"));
        assertEquals("1", rows("SELECT k FROM (k2 JOIN k5 USING (j)) JOIN k6 USING (c), k1 JOIN k4 USING (k) JOIN k3 USING (k)"));
        assertEquals("1", rows("SELECT j FROM k1 JOIN k4 USING (k), (k2 JOIN k5 ON TRUE) JOIN k6 USING (c)"));
        // Each item still keeps its own copy of a name the other carries.
        assertAmbiguous("SELECT b FROM k1 JOIN k4 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)", "B");
        assertRefused("SELECT $1, $2, $3, $4 FROM k1 JOIN k4 USING (k), (k2 JOIN k5 USING (j)) JOIN k6 USING (c)",
            "ambiguous column name '$1'");
    }

    @Test
    public void aRelationWithoutTheNameLeavesItResolvable() {
        assertEquals("1", rows("SELECT a FROM full_t f JOIN full_t g USING (a) JOIN (SELECT 1 z) t2 ON TRUE"));
        assertEquals("1", rows("SELECT a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 USING (a)"));
        assertEquals("1", rows("SELECT k FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1", rows("SELECT k FROM k1 FULL JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("5", rows("SELECT e FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE"));
        assertEquals("1|5", rows("SELECT k, e FROM k1 JOIN k3 USING (k) JOIN k2 ON j = k"));
        // ORDER BY reads the output column first.
        assertEquals("1", rows("SELECT f.a FROM full_t f JOIN full_t g USING (a) JOIN full_t t2 ON TRUE ORDER BY a"));
        // A subquery's own relation answers its bare names.
        assertEquals("1", rows("SELECT (SELECT MAX(z.k) FROM k3 z WHERE z.k = k) FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE"));
    }

    @Test
    public void aUsingJoinMergesEverythingItsItemJoinedBeforeIt() {
        assertEquals("10", rows("SELECT b FROM k1 JOIN k2 ON TRUE JOIN k3 USING (k)"));
        assertEquals("1", rows("SELECT j FROM k1 JOIN k2 ON TRUE JOIN k3 USING (k)"));
        assertEquals("1", rows("SELECT k FROM k1 JOIN k2 ON TRUE JOIN k3 USING (k)"));
        assertEquals("10", rows("SELECT b FROM k1 JOIN k4 USING (k) JOIN k3 USING (k)"));
        // A parenthesized group on the right is one relation when a USING join merged it.
        assertEquals("1", rows("SELECT k FROM k2 JOIN (k1 JOIN k4 USING (k)) ON TRUE"));
        assertEquals("1", rows("SELECT k FROM k3 JOIN (k1 JOIN k4 USING (k)) USING (k)"));
        assertAmbiguous("SELECT k FROM k3 JOIN (k1 JOIN k4 USING (k)) ON TRUE", "K");
        assertAmbiguous("SELECT b FROM k2 JOIN (k1 JOIN k4 USING (k)) ON TRUE", "B");
    }

    @Test
    public void aStarProjectsEachColumnItsOwnValue() {
        assertEquals("1|10|40|1|20", rows("SELECT * FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1|10|40|1|5", rows("SELECT * FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE"));
        assertEquals("1|10|40|1|5", rows("SELECT * FROM k1 LEFT JOIN k4 USING (k) JOIN k3 ON TRUE"));
        assertEquals("1|10|40|1|20|1", rows("SELECT *, 1 FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1|10|40|1|20|1",
            rows("SELECT *, ROW_NUMBER() OVER (ORDER BY 1) AS rn FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1|10|40|20", rows("SELECT * EXCLUDE (j) FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1|10|40|1|20", rows("SELECT DISTINCT * FROM k1 JOIN k4 USING (k) JOIN k2 ON TRUE"));
        assertEquals("1|10|40|1|5|1", rows("SELECT *, k3.k FROM k1 JOIN k4 USING (k) JOIN k3 ON TRUE"));
        assertEquals("1|10|1|20|1", rows("SELECT *, 1 FROM k1 JOIN k2 ON TRUE"));
        assertEquals("1|2|1", rows("SELECT *, 1 FROM (SELECT 1 a, 2 a) s"));
    }
}
