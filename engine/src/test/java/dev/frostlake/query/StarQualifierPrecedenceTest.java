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
 * A star whose qualifier names no relation of the FROM clause is refused as soon as the relations resolve,
 * ahead of every other rule of its query (live-verified): the names of any clause, an unknown function, an
 * arity, the grouping, an ordinal, a join condition, a table function's argument and a subquery's names. A
 * braced star and a qualified qualifier are refused the same way; an alias hides its table's name and a quoted
 * name keeps its case. What resolves with the relations still comes first.
 */
public class StarQualifierPrecedenceTest extends BaseDatabaseTest {

    private static final String NO_NOSUCH = "SQL compilation error:|Object 'NOSUCH' does not exist or not authorized.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (5, 50)");
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

    /** Ahead of every clause's names. */
    @Test
    public void itComesBeforeEveryClausesNames() {
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.*, nosuchcol FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM (SELECT 1 AS a) WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ ORDER BY nosuchcol"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ GROUP BY nosuchcol"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ HAVING nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ QUALIFY nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ QUALIFY ROW_NUMBER() OVER (ORDER BY nosuchcol) = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT (SELECT nosuchcol FROM FZ), nosuch.* FROM FZ"));
    }

    /** Ahead of a join's condition and a table function's arguments, which are read with the sources. */
    @Test
    public void itComesBeforeWhatIsReadWithTheSources() {
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ JOIN FZ f2 ON nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ JOIN G ON G.nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ, LATERAL FLATTEN(INPUT => nosuchcol)"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.*, nosuchcol FROM FZ, LATERAL FLATTEN(INPUT => [1])"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ, TABLE(FLATTEN(input => [nosuchcol]))"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.*, nosuchcol FROM FZ, TABLE(FLATTEN(input => [1]))"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM IDENTIFIER('FZ') WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM (VALUES (1)) WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ SAMPLE (50) WHERE nosuchcol = 1"));
    }

    /** Ahead of what judges a meaning: functions, arities, grouping, ordinals. */
    @Test
    public void itComesBeforeMeanings() {
        assertEquals(NO_NOSUCH, answer("SELECT nosuchfn(1), nosuch.* FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.*, nosuchfn(id) FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ WHERE UPPER(1, 2) = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.*, SUM(id) FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ ORDER BY 9"));
    }

    /** Every star shape: braced, with a modifier, qualified, an alias hiding the name, a quoted name. */
    @Test
    public void everyStarShapeIsJudged() {
        assertEquals(NO_NOSUCH, answer("SELECT {nosuch.*} FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT {nosuch.*}, nosuchcol FROM FZ"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* EXCLUDE id FROM FZ WHERE nosuchcol = 1"));
        assertEquals("SQL compilation error:|Object 'FZ' does not exist or not authorized.",
            answer("SELECT fz.*, nosuchcol FROM FZ f"));
        assertEquals("SQL compilation error:|Object 'FZ' does not exist or not authorized.",
            answer("SELECT fz.*, nosuchcol FROM FZ x JOIN G y ON TRUE"));
        assertEquals("SQL compilation error:|Object '\"fz\"' does not exist or not authorized.",
            answer("SELECT \"fz\".*, nosuchcol FROM FZ"));
        assertEquals("SQL compilation error:|Object 'F' does not exist or not authorized.",
            answer("SELECT f.*, nosuchcol FROM FZ \"f\""));
        assertEquals("SQL compilation error:|Object 'C' does not exist or not authorized.",
            answer("SELECT c.*, nosuchcol FROM (WITH c AS (SELECT 1 AS a) SELECT * FROM c)"));
        assertEquals("SQL compilation error:|Object 'PUBLIC.NOSUCH' does not exist or not authorized.",
            answer("SELECT PUBLIC.NOSUCH.*, nosuchcol FROM FZ"));
        assertEquals("SQL compilation error:|Object 'NOSUCHDB.PUBLIC.FZ' does not exist or not authorized.",
            answer("SELECT NOSUCHDB.PUBLIC.FZ.*, nosuchcol FROM FZ"));
    }

    /** A star that names its relation lets the other rules speak as before. */
    @Test
    public void aStarThatNamesItsRelationIsNoFault() {
        assertEquals(badName(12, "NOSUCHCOL"), answer("SELECT f.*, nosuchcol FROM FZ f"));
        assertEquals(badName(15, "NOSUCHCOL"), answer("SELECT \"FZ\".*, nosuchcol FROM fz"));
        assertEquals(badName(14, "NOSUCHCOL"), answer("SELECT \"f\".*, nosuchcol FROM FZ \"f\""));
        assertEquals(badName(15, "NOSUCHCOL"), answer("SELECT {fz.*}, nosuchcol FROM FZ"));
        assertEquals(badName(18, "NOSUCHCOL"), answer("SELECT flatten.*, nosuchcol FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals(badName(20, "NOSUCHCOL"), answer("SELECT generator.*, nosuchcol FROM TABLE(GENERATOR(ROWCOUNT => 1))"));
        assertEquals(badName(12, "NOSUCHCOL"), answer("SELECT v.*, nosuchcol FROM (SELECT 1 AS a) v"));
        assertEquals(badName(12, "NOSUCHCOL"), answer("SELECT x.*, nosuchcol FROM FZ x JOIN G y ON TRUE"));
        assertEquals(badName(38, "NOSUCHCOL"), answer("WITH c AS (SELECT 1 AS a) SELECT c.*, nosuchcol FROM c"));
    }

    /** What resolves with the relations still comes first, and a nested query waits for its own turn. */
    @Test
    public void whatResolvesWithTheRelationsComesFirst() {
        assertEquals(hinted("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized."),
            answer("SELECT nosuch.* FROM nosuchtable"));
        assertEquals(hinted("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized."),
            answer("SELECT nosuch.* FROM FZ JOIN nosuchtable ON TRUE"));
        assertEquals(hinted("SQL compilation error:|Object 'NOSUCHTABLE' does not exist or not authorized."),
            answer("SELECT (SELECT 1 FROM nosuchtable), nosuch.* FROM FZ"));
        assertEquals("SQL compilation error:|duplicate alias 'X'", answer("SELECT nosuch.* FROM FZ x, G x"));
        assertEquals("SQL compilation error:|Invalid identifier NOSUCHCOL", answer("SELECT nosuch.* FROM FZ JOIN G USING (nosuchcol)"));
        assertEquals(badName(41, "NOSUCHCOL"), answer("SELECT nosuch.* FROM FZ, LATERAL (SELECT nosuchcol)"));
        assertEquals(badName(29, "NOSUCHCOL"), answer("SELECT nosuch.* FROM (SELECT nosuchcol FROM FZ)"));
        assertEquals("SQL compilation error: error line 1 at position 25|Window frame requires an ORDER BY clause.",
            answer("SELECT nosuch.*, SUM(id) OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM FZ"));
        assertEquals(badName(7, "NOSUCHCOL"), answer("SELECT nosuchcol, (SELECT nosuch.* FROM G) FROM FZ"));
        assertEquals("SQL compilation error:|Object 'NOSUCH2' does not exist or not authorized.",
            answer("SELECT (SELECT nosuch1.* FROM G), nosuch2.* FROM FZ"));
        assertEquals(NO_NOSUCH, answer("WITH c AS (SELECT nosuch.* FROM G) SELECT nosuchcol FROM c"));
        assertEquals(badName(7, "NOSUCHCOL"), answer("SELECT nosuchcol FROM FZ UNION ALL SELECT nosuch.* FROM G"));
        assertEquals("SQL compilation error:|Object 'NOSUCH1' does not exist or not authorized.",
            answer("SELECT nosuch1.*, nosuch2.* FROM FZ"));
    }

    /**
     * A PIVOT or UNPIVOT source is named by its ALIAS, and by its source's name when it has none — and an
     * unknown qualifier over one is refused before the clause's names, as over any other relation.
     */
    @Test
    public void aPivotSourceIsNamedLikeAnyOtherRelation() {
        assertEquals(NO_NOSUCH,
            answer("SELECT nosuch.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE)) WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE))"));
        assertEquals(NO_NOSUCH, answer("SELECT nosuch.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE)) AS p"));
        assertEquals("ACCEPTED 1", answer("SELECT p.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE)) AS p"),
            "the alias names the pivot");
        assertEquals("ACCEPTED 1", answer("SELECT fz.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE))"),
            "and with no alias, its source's name does");
        assertEquals("SQL compilation error:|Object 'FZ' does not exist or not authorized.",
            answer("SELECT fz.* FROM FZ PIVOT(SUM(id) FOR b IN (TRUE)) AS p"),
            "which the alias then hides");
        assertEquals(NO_NOSUCH,
            answer("SELECT nosuch.* FROM (SELECT 1 a, 2 b) UNPIVOT(v FOR k IN (a, b))"));
        assertEquals("ACCEPTED 2",
            answer("SELECT u.* FROM (SELECT 1 a, 2 b) UNPIVOT(v FOR k IN (a, b)) AS u"));
    }

    /** The statements around a query: INSERT, CTAS, a view, a subquery and a set operation's second branch. */
    @Test
    public void itHoldsInEveryStatement() {
        assertEquals(NO_NOSUCH, answer("INSERT INTO G SELECT nosuch.* FROM FZ WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("CREATE TABLE ctas AS SELECT nosuch.* FROM FZ WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("CREATE VIEW vv AS SELECT nosuch.* FROM FZ WHERE nosuchcol = 1"));
        assertEquals(NO_NOSUCH, answer("SELECT * FROM FZ WHERE id IN (SELECT nosuch.* FROM G WHERE nosuchcol = 1)"));
        assertEquals(NO_NOSUCH, answer("SELECT id FROM FZ WHERE id = 1 UNION ALL SELECT nosuch.* FROM G WHERE nosuchcol = 1"));
    }
}
