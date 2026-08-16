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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare name inside a subquery binds to the innermost query block whose FROM carries it; only a name no
 * inner relation carries reaches the outer row. So in {@code SELECT id FROM fz WHERE (SELECT b FROM fz WHERE
 * id = 5)} the inner {@code id} is the inner FZ's, for every outer row. The same holds in every clause and at
 * every depth: a scalar subquery, EXISTS and IN, a join or a derived table inside the subquery, QUALIFY and
 * ORDER BY, a LATERAL source, and a subquery in UPDATE or DELETE. Every cell is live-verified.
 */
public class SubqueryNameScopingTest extends BaseDatabaseTest {

    /** Each outer id with the value the subquery gave it, as {@code id:x,…}. */
    private static final String ROWS =
        "SELECT LISTAGG(id || ':' || COALESCE(x::VARCHAR, 'null'), ',') WITHIN GROUP (ORDER BY id) FROM (%s)";
    /** The outer ids a predicate kept, as {@code id,…}. */
    private static final String KEPT = "SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz WHERE %s";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE TABLE h (k INT, w INT)");
        engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
    }

    private String answer(final String sql) {
        try {
            return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String[] rows(final String subquery, final String expected) {
        return new String[] {String.format(ROWS, "SELECT id, " + subquery + " AS x FROM fz"), expected};
    }

    private static String[] kept(final String predicate, final String expected) {
        return new String[] {String.format(KEPT, predicate), expected};
    }

    private void assertAnswers(final String[][] cells) {
        final List<String> wrong = new ArrayList<String>();
        for (final String[] cell : cells) {
            final String got = answer(cell[0]);
            if (!cell[1].equals(got)) {
                wrong.add(cell[0] + "\n    expected: " + cell[1] + "\n    got:      " + got);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    /** The inner relation shadows the outer one, in a predicate and in a projected subquery alike. */
    @Test
    public void aBareNameBindsToTheInnermostRelationCarryingIt() {
        assertAnswers(new String[][] {
            kept("(SELECT b FROM fz WHERE id = 5)", "5,7"),
            rows("(SELECT v FROM g WHERE id = 5)", "5:50,7:50"),
            rows("(SELECT COUNT(*) FROM g WHERE id = id)", "5:2,7:2"),
            rows("(SELECT MAX(v) FROM g WHERE id < 6)", "5:50,7:50"),
            rows("(SELECT v FROM g JOIN h ON g.id = h.k WHERE id = 5)", "5:50,7:50"),
            rows("(SELECT (SELECT v FROM g WHERE id = 6) FROM h WHERE k = 5)", "5:60,7:60"),
            rows("(SELECT v FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1)", "5:50,7:50"),
            rows("(SELECT v FROM g ORDER BY id DESC LIMIT 1)", "5:60,7:60"),
            kept("b = (SELECT b FROM fz f2 WHERE id = 7)", "7"),
            {"SELECT (SELECT v FROM g WHERE id = 5)", "50"},
            {"SELECT id, (SELECT b FROM fz WHERE id = fz.id) AS x FROM fz ORDER BY id",
                "Single-row subquery returns more than one row."},
        });
    }

    /** EXISTS and IN bind the same way, and a qualified name reaches the relation it names. */
    @Test
    public void existsAndInBindTheSameWay() {
        assertAnswers(new String[][] {
            kept("EXISTS (SELECT 1 FROM g WHERE id = 6)", "5,7"),
            kept("NOT EXISTS (SELECT 1 FROM g WHERE id = 7)", "5,7"),
            kept("id IN (SELECT id FROM g)", "5"),
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM fz o"
                + " WHERE EXISTS (SELECT 1 FROM g i WHERE i.id = o.id)", "5"},
            kept("EXISTS (SELECT 1 FROM g WHERE g.id = fz.id)", "5"),
            kept("fz.id IN (SELECT g.id FROM g WHERE g.v > fz.id)", "5"),
            kept("(SELECT MAX(v) FROM g WHERE g.id = fz.id) = 50", "5"),
            rows("(SELECT MAX(v) FROM g WHERE id < fz.id)", "5:null,7:60"),
            rows("(SELECT COUNT(*) FROM g WHERE g.id = fz.id)", "5:1,7:0"),
            rows("(SELECT ANY_VALUE(v) FROM g WHERE g.id = fz.id)", "5:50,7:null"),
        });
    }

    /** A LATERAL source binds its bare names to its own relation too. */
    @Test
    public void aLateralSourceBindsTheSameWay() {
        assertAnswers(new String[][] {
            {"SELECT LISTAGG(fz.id || ':' || t.v, ',') WITHIN GROUP (ORDER BY fz.id)"
                + " FROM fz, LATERAL (SELECT v FROM g WHERE id = 5) t", "5:50,7:50"},
            {"SELECT LISTAGG(fz.id || ':' || t.v, ',') WITHIN GROUP (ORDER BY fz.id)"
                + " FROM fz, LATERAL (SELECT v FROM g WHERE g.id = fz.id) t", "5:50"},
        });
    }

    /** A subquery in UPDATE or DELETE binds its bare names to its own relation. */
    @Test
    public void aSubqueryInDmlBindsTheSameWay() {
        engine.execute("UPDATE fz SET b = (SELECT v > 0 FROM g WHERE id = 5)");
        assertEquals("2", answer("SELECT COUNT_IF(b) FROM fz"));
        engine.execute("DELETE FROM fz WHERE (SELECT v FROM g WHERE id = 6) = 60");
        assertEquals("0", answer("SELECT COUNT(*) FROM fz"));
    }
}
