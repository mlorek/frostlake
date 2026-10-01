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
 * Two copies of one subquery text are two subqueries, and each is evaluated: {@code (SELECT RANDOM())
 * = (SELECT RANDOM())} is FALSE, and so is the same over UUID_STRING or a sequence's NEXTVAL. The
 * uncorrelated-subquery memo used to answer the second copy from the first because their texts matched.
 *
 * <p>Over ROWS the families part: a RANDOM or UUID_STRING subquery is drawn afresh for every row, while
 * a NEXTVAL or CURRENT_TIMESTAMP one is drawn ONCE for the statement and every row sees that value.
 */
public class VolatileSubqueryEvaluationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE SEQUENCE s1 START = 1 INCREMENT = 1");
        engine.execute("CREATE OR REPLACE TABLE t (n INT)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
    }

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** Each copy of a drawing subquery draws its own value. */
    @Test
    public void twoCopiesOfADrawingSubqueryDiffer() {
        assertEquals("false", answer("SELECT (SELECT RANDOM()) = (SELECT RANDOM()) AS same"));
        assertEquals("false", answer("SELECT (SELECT UUID_STRING()) = (SELECT UUID_STRING()) AS same"));
        assertEquals("false", answer("SELECT (SELECT RANDSTR(8, RANDOM())) = (SELECT RANDSTR(8, RANDOM())) AS same"));
        assertEquals("false", answer("SELECT (SELECT NORMAL(0, 1, RANDOM())) = (SELECT NORMAL(0, 1, RANDOM())) AS same"));
        assertEquals("false", answer("SELECT (SELECT (SELECT RANDOM())) = (SELECT (SELECT RANDOM())) AS same"),
            "nesting changes nothing");
    }

    /** A sequence read is drawn per copy too, even wrapped in an expression. */
    @Test
    public void twoCopiesOfASequenceReadDiffer() {
        assertEquals("false", answer("SELECT (SELECT s1.nextval) = (SELECT s1.nextval) AS same"));
        assertEquals("false", answer("SELECT (SELECT s1.nextval + 0) = (SELECT s1.nextval + 0) AS same"));
    }

    /** A subquery that draws nothing of its own is still answered once, per copy. */
    @Test
    public void aSteadySubqueryIsStillShared() {
        assertEquals("true", answer("SELECT (SELECT 1 + 1) = (SELECT 1 + 1) AS same"));
        assertEquals("true", answer("SELECT (SELECT CURRENT_TIMESTAMP()) = (SELECT CURRENT_TIMESTAMP()) AS same"));
        assertEquals("true", answer("SELECT (SELECT SEQ4()) = (SELECT SEQ4()) AS same"),
            "SEQ4 outside a generator is not a draw");
    }

    /** Over rows, RANDOM and UUID_STRING are drawn per row. */
    @Test
    public void aDrawingSubqueryIsDrawnPerRow() {
        assertEquals("2", answer("SELECT COUNT(DISTINCT x) FROM (SELECT (SELECT RANDOM()) AS x FROM t)"));
        assertEquals("2", answer("SELECT COUNT(DISTINCT x) FROM (SELECT (SELECT UUID_STRING()) AS x FROM t)"));
        assertEquals("2", answer("SELECT COUNT(DISTINCT x) FROM (SELECT (SELECT RANDSTR(8, RANDOM())) AS x FROM t)"));
        assertEquals("2", answer("SELECT COUNT(*) FROM t WHERE (SELECT RANDOM()) <> (SELECT RANDOM())"));
    }

    /** Over rows, a sequence read and the statement clock are drawn once for the whole statement. */
    @Test
    public void aSequenceSubqueryIsDrawnOncePerStatement() {
        assertEquals("1", answer("SELECT COUNT(DISTINCT x) FROM (SELECT (SELECT s1.nextval) AS x FROM t)"));
        assertEquals("1", answer("SELECT COUNT(DISTINCT x) FROM (SELECT (SELECT CURRENT_TIMESTAMP()) AS x FROM t)"));
        assertEquals("1", answer("SELECT COUNT(DISTINCT a || '/' || b) FROM "
            + "(SELECT (SELECT s1.nextval) AS a, (SELECT s1.nextval) AS b FROM t)"),
            "two copies, two values, and both steady over the rows");
    }

    /** Written bare rather than in a subquery, both families draw per row, as they always did. */
    @Test
    public void theBareFormsAreUnchanged() {
        assertEquals("false", answer("SELECT RANDOM() = RANDOM() AS same"));
        assertEquals("false", answer("SELECT s1.nextval = s1.nextval AS same"));
        assertEquals("2", answer("SELECT COUNT(DISTINCT x) FROM (SELECT RANDOM() AS x FROM t)"));
        assertEquals("2", answer("SELECT COUNT(DISTINCT x) FROM (SELECT s1.nextval AS x FROM t)"));
    }
}
