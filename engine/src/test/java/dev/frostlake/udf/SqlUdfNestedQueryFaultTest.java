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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL function's query body that holds a parameter as the row count of a query nested in it, or that names a
 * window its WINDOW clause defines, is refused at CREATE with the syntax error at the body's first word, as a
 * parameter in the outer query's row count is; a literal row count in a nested query and a window written in its
 * call are created. Each refusal's heading and first line are held here (all live-verified).
 */
public class SqlUdfNestedQueryFaultTest extends BaseDatabaseTest {

    private static final String FIRST_LINE = "Compilation of SQL UDF failed: SQL compilation error:"
        + "|syntax error line 1 at position 1 unexpected 'SELECT'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, b INT)");
    }

    /** "created", or the refusal's heading and first line on one line. */
    private String create(final String signature, final String body) {
        try {
            engine.execute("CREATE OR REPLACE FUNCTION " + signature + " AS $$" + body + "$$");
            return "created";
        } catch (final RuntimeException refused) {
            final String refusal = String.valueOf(refused.getMessage()).replace('\n', '|');
            final int second = refusal.indexOf('|', refusal.indexOf('|') + 1);
            return second < 0 ? refusal : refusal.substring(0, second);
        }
    }

    @Test
    public void aParameterAsANestedQuerysRowCountIsRefused() {
        final String[] bodies = {
            "SELECT COUNT(*) FROM (SELECT a FROM t LIMIT n)",
            "SELECT COUNT(*) FROM (SELECT a FROM t LIMIT 1 OFFSET n)",
            "SELECT COUNT(*) FROM (SELECT a FROM t FETCH FIRST n ROWS ONLY)",
            "SELECT (SELECT a FROM t LIMIT n)",
            "SELECT MAX(a) FROM t WHERE a IN (SELECT a FROM t LIMIT n)",
            "SELECT COUNT(*) FROM (SELECT COUNT(*) FROM (SELECT a FROM t LIMIT n))",
        };
        for (final String body : bodies) {
            assertEquals(FIRST_LINE, create("c_lim(n NUMBER) RETURNS NUMBER", body), body);
        }
        assertEquals("created",
            create("c_ok(n NUMBER) RETURNS NUMBER", "SELECT COUNT(*) FROM (SELECT a FROM t LIMIT 2)"));
    }

    @Test
    public void aNamedWindowIsRefused() {
        final String[] bodies = {
            "SELECT SUM(a) OVER w FROM t WINDOW w AS (ORDER BY a)",
            "SELECT a FROM t WINDOW w AS (ORDER BY a)",
            "SELECT COUNT(*) FROM (SELECT SUM(a) OVER w FROM t WINDOW w AS (ORDER BY a))",
            "SELECT MAX(x) FROM (SELECT ROW_NUMBER() OVER w AS x FROM t WINDOW w AS (PARTITION BY b ORDER BY a))",
        };
        for (final String body : bodies) {
            assertEquals(FIRST_LINE, create("c_win() RETURNS NUMBER", body), body);
        }
        assertEquals("created", create("c_ok2() RETURNS NUMBER", "SELECT SUM(a) OVER (ORDER BY a) FROM t"));
    }
}
