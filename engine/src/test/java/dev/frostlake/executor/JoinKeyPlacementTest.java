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
package dev.frostlake.executor;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An equality between a column of each side keys a join's hash path wherever the column sits: in a relation an
 * earlier join merged into the left side, written bare or qualified, and on either side of a self-join, each alias
 * reading its own copy of the table. A bare USING key does not key a later join, since it reads the first non-NULL
 * of its copies rather than one column. The answers are live's; how the join ran is the embedded engine's own plan.
 */
public class JoinKeyPlacementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE a AS SELECT SEQ4() AS k, SEQ4() AS ak FROM TABLE(GENERATOR(ROWCOUNT => 20))");
        engine.execute("CREATE TABLE b AS SELECT SEQ4() AS bk, SEQ4() AS j FROM TABLE(GENERATOR(ROWCOUNT => 20))");
        engine.execute("CREATE TABLE c AS SELECT SEQ4() AS cj, SEQ4() AS cz FROM TABLE(GENERATOR(ROWCOUNT => 20))");
        engine.execute("CREATE TABLE t AS SELECT SEQ4() AS id, SEQ4() - 1 AS parent"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 20))");
    }

    /** The count a query answers, checking on the embedded engine that its plan holds {@code keyed}. */
    private String count(final String sql, final String keyed) {
        final String count = String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
        if (!isLiveSnowflake()) {
            final String plan = engine.getExecutor().describeLastSelectPlan();
            assertTrue(plan.contains(keyed), plan);
        }
        return count;
    }

    @Test
    public void aColumnOfAnEarlierJoinKeysTheNext() {
        assertEquals("20", count("SELECT COUNT(*) FROM a JOIN b ON a.k = b.bk JOIN c ON b.j = c.cj",
            "INNER JOIN[joined x C ON b.j = c.cj | HASH J=CJ]"));
        assertEquals("20", count("SELECT COUNT(*) FROM a JOIN b ON a.k = b.bk JOIN c ON j = cj",
            "INNER JOIN[joined x C ON j = cj | HASH J=CJ]"));
        assertEquals("20", count("SELECT COUNT(*) FROM a, b, c WHERE a.k = b.bk AND b.j = c.cj",
            "CROSS JOIN[joined x C | HASH J=CJ]"));
        assertEquals("20", count("SELECT COUNT(*) FROM a, b, c WHERE a.k = b.bk AND j = cj",
            "CROSS JOIN[joined x C | HASH J=CJ]"));
    }

    @Test
    public void eachSideOfASelfJoinKeysByItsAlias() {
        assertEquals("19", count("SELECT COUNT(*) FROM t x JOIN t y ON x.id = y.parent",
            "INNER JOIN[T x T ON x.id = y.parent | HASH ID=PARENT]"));
        assertEquals("19", count("SELECT COUNT(*) FROM t x, t y WHERE x.id = y.parent",
            "CROSS JOIN[T x T | HASH ID=PARENT]"));
        assertEquals("19", count("SELECT COUNT(*) FROM t x JOIN t y ON y.parent = x.id",
            "INNER JOIN[T x T ON y.parent = x.id | HASH ID=PARENT]"));
    }

    @Test
    public void aBareUsingKeyDoesNotKeyALaterJoin() {
        engine.execute("CREATE TABLE ua (k INT)");
        engine.execute("INSERT INTO ua VALUES (1), (2)");
        engine.execute("CREATE TABLE ub (k INT)");
        engine.execute("INSERT INTO ub VALUES (2), (3)");
        engine.execute("CREATE TABLE uc (ck INT)");
        engine.execute("INSERT INTO uc VALUES (1), (2), (3)");
        assertEquals("2", count("SELECT COUNT(*) FROM ua RIGHT JOIN ub USING (k) JOIN uc ON k = uc.ck",
            "INNER JOIN[joined x UC ON k = uc.ck]"));
        assertEquals("3", count("SELECT COUNT(*) FROM ua FULL JOIN ub USING (k) JOIN uc ON k = uc.ck",
            "INNER JOIN[joined x UC ON k = uc.ck]"));
    }
}
