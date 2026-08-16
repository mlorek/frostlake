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

package dev.frostlake.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * A STAR argument to an aggregate EXPANDS to the source's columns, and the call is then an ordinary
 * multi-argument one. Frostlake used to blow the STACK on it: the star call is its own parse-tree node,
 * only COUNT was handled, and every other aggregate fell through to the contains-an-aggregate branch,
 * which collected the call as its own nested aggregate and recursed until the thread died.
 *
 * <p>★ A StackOverflowError IS NOT A RuntimeException — nothing catches it, an embedded engine cannot
 * recover the thread, and the HTTP server shares threads across sessions. That is why this is pinned by
 * a test rather than left as a wrong answer.
 *
 * <p>★ THE EXPANSION IS ASSERTED AS A RELATION, never as a hash: Frostlake's HASH is its own algorithm
 * (Snowflake's could not be reverse-engineered), so the VALUES differ between engines while every
 * relation below holds on both. HASH_AGG(*) equals HASH_AGG written out, an EXCLUDE drops exactly the
 * named column, and over a join the star spans both tables in FROM order.
 */
public class AggregateStarArgumentTest extends BaseDatabaseTest {

    private String cellOf(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sa (a INT, b VARCHAR, c NUMBER(5,2))");
        engine.execute("INSERT INTO sa VALUES (1, 'x', 1.50), (2, 'y', 2.50), (3, 'z', 3.50)");
        engine.execute("CREATE TABLE sa2 (d INT)");
        engine.execute("INSERT INTO sa2 VALUES (1), (2)");
    }

    @Test
    public void theStarMeansTheColumnsWrittenOut() {
        assertEquals("true", cellOf("SELECT (SELECT HASH_AGG(*) FROM sa)"
            + " = (SELECT HASH_AGG(a, b, c) FROM sa) AS v"));
    }

    @Test
    public void anExcludeDropsExactlyThatColumn() {
        assertEquals("true", cellOf("SELECT (SELECT HASH_AGG(* EXCLUDE (b)) FROM sa)"
            + " = (SELECT HASH_AGG(a, c) FROM sa) AS v"));
        // …and it is a different value from the unexcluded star, so the modifier is not being ignored.
        assertEquals("false", cellOf("SELECT (SELECT HASH_AGG(* EXCLUDE (b)) FROM sa)"
            + " = (SELECT HASH_AGG(*) FROM sa) AS v"));
    }

    @Test
    public void overAJoinTheStarSpansBothTables() {
        assertEquals("true", cellOf("SELECT (SELECT HASH_AGG(*) FROM sa JOIN sa2 ON sa.a = sa2.d)"
            + " = (SELECT HASH_AGG(sa.a, sa.b, sa.c, sa2.d) FROM sa JOIN sa2 ON sa.a = sa2.d) AS v"));
    }

    @Test
    public void theGroupedFormRunsToo() {
        assertEquals("true", cellOf("SELECT (SELECT MAX(h) FROM (SELECT HASH_AGG(*) AS h FROM sa GROUP BY a) g)"
            + " = (SELECT MAX(h) FROM (SELECT HASH_AGG(a, b, c) AS h FROM sa GROUP BY a) g2) AS v"));
        // The WINDOWED form runs too; its expansion is pinned beside the qualified and filtered stars.
        assertEquals("3", cellOf("SELECT COUNT(h) AS v FROM (SELECT HASH_AGG(*) OVER () AS h FROM sa) w"));
    }

    @Test
    public void countStarIsUntouched() {
        assertEquals("3", cellOf("SELECT COUNT(*) AS v FROM sa"));
        // The scalar star-expanding call still works per row, which is what said the star itself was fine.
        assertEquals("3", cellOf("SELECT COUNT(*) AS v FROM (SELECT HASH(*) AS h FROM sa) r"));
    }
}
