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
 * A qualified reference reads only the relation its qualifier names: a USING or NATURAL join's key answers
 * through a relation that carries it, never through one that does not — {@code b.y} over
 * {@code a JOIN b USING (x) JOIN c USING (y)}, with {@code b} lacking {@code y}, is {@code invalid identifier
 * 'B.Y'} while the statement compiles, at the reference, in every clause.
 */
public class UsingKeyQualifiedReferenceTest extends JoinScopeTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ta (x INT, y INT)");
        engine.execute("CREATE TABLE tb (x INT)");
        engine.execute("CREATE TABLE tc (y INT)");
        engine.execute("INSERT INTO ta VALUES (1, 2)");
        engine.execute("INSERT INTO tb VALUES (1)");
        engine.execute("INSERT INTO tc VALUES (2)");
    }

    private void assertInvalid(final String sql, final int position, final String name) {
        assertRefused(sql, "error line 1 at position " + position, "invalid identifier '" + name + "'");
    }

    @Test
    public void aRelationLackingTheKeyDoesNotAnswerForIt() {
        assertInvalid("SELECT b.y FROM (SELECT 1 x, 2 y) a JOIN (SELECT 1 x) b USING (x) JOIN (SELECT 2 y) c USING (y)",
            7, "B.Y");
        assertInvalid("SELECT b.y FROM (SELECT 1 x, 2 y) a JOIN (SELECT 1 x) b USING (x) JOIN (SELECT 2 y) c USING (y)"
            + " WHERE FALSE", 7, "B.Y");
        assertInvalid("SELECT b.y FROM (SELECT 1 x, 2 y) a JOIN (SELECT 1 x) b ON a.x = b.x JOIN (SELECT 2 y) c USING (y)",
            7, "B.Y");
        assertInvalid("SELECT a.x, b.y, c.y FROM (SELECT 1 x, 2 y) a JOIN (SELECT 1 x) b USING (x)"
            + " JOIN (SELECT 2 y) c USING (y)", 12, "B.Y");
        assertInvalid("SELECT tb.y FROM ta JOIN tb USING (x) JOIN tc USING (y)", 7, "TB.Y");
        assertInvalid("SELECT tc.x FROM ta JOIN tb USING (x) JOIN tc USING (y)", 7, "TC.X");
        assertInvalid("SELECT tb.y FROM ta NATURAL JOIN tb NATURAL JOIN tc", 7, "TB.Y");
        assertInvalid("SELECT tb.y FROM ta LEFT JOIN tb USING (x) LEFT JOIN tc USING (y)", 7, "TB.Y");
        assertInvalid("SELECT tb.y FROM ta JOIN tb USING (x) NATURAL JOIN tc", 7, "TB.Y");
        assertInvalid("SELECT tb.y FROM (ta JOIN tb USING (x)) JOIN tc USING (y)", 7, "TB.Y");
        assertInvalid("SELECT tb.y FROM tc JOIN (ta JOIN tb USING (x)) USING (y)", 7, "TB.Y");
        assertInvalid("SELECT q.y FROM ta JOIN tb q USING (x) JOIN tc USING (y)", 7, "Q.Y");
        assertInvalid("SELECT tb.y IS NULL FROM ta JOIN tb USING (x) JOIN tc USING (y)", 7, "TB.Y");
        assertInvalid("SELECT COUNT(tb.y) FROM ta JOIN tb USING (x) JOIN tc USING (y)", 13, "TB.Y");
    }

    @Test
    public void everyClauseRefusesItAtTheReference() {
        assertInvalid("SELECT ta.x FROM ta JOIN tb USING (x) JOIN tc USING (y) WHERE tb.y = 2", 62, "TB.Y");
        assertInvalid("SELECT ta.x FROM ta JOIN tb USING (x) JOIN tc USING (y) ORDER BY tb.y", 65, "TB.Y");
        assertInvalid("SELECT COUNT(*) FROM ta JOIN tb USING (x) JOIN tc USING (y) GROUP BY tb.y", 69, "TB.Y");
        assertInvalid("SELECT MAX(ta.x) FROM ta JOIN tb USING (x) JOIN tc USING (y) HAVING MAX(tb.y) = 2", 72, "TB.Y");
        assertInvalid("SELECT * FROM ta JOIN tb USING (x) JOIN tc USING (y) WHERE tb.y = 2", 59, "TB.Y");
        assertInvalid("SELECT ta.x FROM ta JOIN tb USING (x) JOIN tc USING (y)"
            + " WHERE EXISTS (SELECT 1 FROM tc z WHERE z.y = tb.y)", 101, "TB.Y");
    }

    @Test
    public void aRelationCarryingTheColumnStillAnswers() {
        assertEquals("1", rows("SELECT tb.x FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("2", rows("SELECT tc.y FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("2", rows("SELECT ta.y FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("1|1|1", rows("SELECT ta.x, tb.x, x FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("1|1|2|2", rows("SELECT ta.x, tb.x, tc.y, ta.y FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("2|2|2", rows("SELECT a.y, c.y, y FROM (SELECT 1 x, 2 y) a JOIN (SELECT 1 x) b USING (x)"
            + " JOIN (SELECT 2 y) c USING (y)"));
        assertEquals("1", rows("SELECT tb.* FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("{\"X\":1}", rows("SELECT TO_JSON(OBJECT_CONSTRUCT(tb.*)) FROM ta JOIN tb USING (x) JOIN tc USING (y)"));
        assertEquals("{\"X\":1}", rows("SELECT TO_JSON(OBJECT_CONSTRUCT(tb.*)) FROM ta JOIN tb ON ta.x = tb.x"));
    }
}
