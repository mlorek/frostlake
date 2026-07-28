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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code LEFT(str, n)} / {@code RIGHT(str, n)} — leading/trailing substrings. The interesting part is
 * PARSING, not semantics: LEFT and RIGHT are also join keywords, so the function form must stay
 * callable everywhere an expression is legal — including in the same query as a {@code LEFT JOIN}
 * and inside procedural assignment expressions.
 */
public class LeftRightFunctionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void leftAndRightReturnTheEdgeSubstrings() {
        assertEquals("hel", String.valueOf(scalar("SELECT LEFT('hello', 3)")));
        assertEquals("llo", String.valueOf(scalar("SELECT RIGHT('hello', 3)")));
        assertEquals("hello", String.valueOf(scalar("SELECT LEFT('hello', 99)")));
        assertEquals("", String.valueOf(scalar("SELECT RIGHT('hello', 0)")));
        assertNull(scalar("SELECT LEFT(NULL, 3)"));
    }

    @Test
    public void theFunctionFormCoexistsWithJoinKeywords() {
        engine.execute("CREATE TABLE t (a VARCHAR)");
        engine.execute("CREATE TABLE u (a VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('x1')");
        engine.execute("INSERT INTO u VALUES ('x1')");
        assertEquals("x", String.valueOf(scalar(
            "SELECT LEFT(t.a, 1) FROM t LEFT OUTER JOIN u ON t.a = u.a")));
        assertEquals(1, engine.executeQuery(
            "SELECT 1 WHERE LEFT('abc:2.3:a', 8) = 'abc:2.3:'").getRowCount());
    }

    @Test
    public void worksInProceduralAssignments() {
        engine.execute("""
            CREATE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE x VARCHAR;
            BEGIN
              x := LEFT('hello', 3) || RIGHT('hello', 2);
              RETURN x;
            END $$""");
        assertEquals("hello".substring(0, 3) + "lo",
            String.valueOf(engine.execute("CALL p()").getResultSets().get(0).getRows().get(0).getValue(0)));
    }
}
