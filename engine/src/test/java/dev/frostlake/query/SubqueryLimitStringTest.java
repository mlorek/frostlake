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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A LIMIT or OFFSET value written as a string other than the empty one is a syntax error of the statement at the
 * literal's own place, wherever the clause stands — a subquery, a derived table, a CTE, a view's body, a block's
 * statement — and ahead of everything else the statement would be refused for: an unresolvable name or relation,
 * and the enclosing query's types. Only the first such value is reported, and a statement that does not parse
 * reports its parse fault instead (all live-verified).
 */
public class SubqueryLimitStringTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String unexpected(final int line, final int position, final String token) {
        return "SQL compilation error:\nsyntax error line " + line + " at position " + position + " unexpected '"
            + token + "'.";
    }

    private String firstCell(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return String.valueOf(result.getRows().get(0).getValue(0));
    }

    @Test
    public void aStringLimitInASubqueryIsRefusedAtItsPlaceInTheStatement() {
        assertEquals(unexpected(1, 22, "'x'"), refusal("SELECT a FROM t LIMIT 'x'"));
        assertEquals(unexpected(1, 49, "'x'"), refusal("SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 30, "'x'"), refusal("SELECT (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 30, "'3'"), refusal("SELECT (SELECT a FROM t LIMIT '3')"));
        assertEquals(unexpected(1, 42, "'x'"), refusal("SELECT a FROM t ORDER BY a LIMIT 1 OFFSET 'x'"));
        assertEquals(unexpected(1, 57, "'x'"),
            refusal("SELECT (SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x') LIMIT 1)"));
        assertEquals(unexpected(4, 6, "'x'"), refusal("SELECT a\nFROM t\nWHERE a = (SELECT a FROM t\nLIMIT 'x')"));
        assertEquals(unexpected(1, 30, "'x'"), refusal("/* c */ SELECT (SELECT a FROM t LIMIT 'x')"));
    }

    @Test
    public void itRanksAheadOfNamesRelationsAndTypes() {
        assertEquals(unexpected(1, 68, "'x'"),
            refusal("SELECT a FROM t WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 88, "'x'"),
            refusal("SELECT a FROM t WHERE 'o' + TRUE = 1 AND a = (SELECT a FROM t ORDER BY a LIMIT 1 OFFSET 'x')"));
        assertEquals(unexpected(1, 54, "'x'"), refusal("SELECT nosuch FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 56, "'x'"), refusal("SELECT a FROM nosuch_t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 49, "'x'"),
            refusal("SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x') ORDER BY nosuchfn(a)"));
        assertEquals(unexpected(1, 51, "'x'"), refusal("SELECT a FROM t QUALIFY a = (SELECT a FROM t LIMIT 'x')"));
    }

    @Test
    public void onlyTheFirstIsReported() {
        assertEquals(unexpected(1, 22, "'x'"), refusal("SELECT a FROM t LIMIT 'x' OFFSET 'y'"));
        assertEquals(unexpected(1, 22, "'x'"), refusal("SELECT a FROM t LIMIT 'x' OFFSET 1"));
        assertEquals(unexpected(1, 31, "'y'"), refusal("SELECT a FROM t LIMIT 1 OFFSET 'y'"));
        assertEquals(unexpected(1, 32, "'y'"), refusal("SELECT a FROM t LIMIT '' OFFSET 'y'"));
        assertEquals(unexpected(1, 30, "'x'"), refusal("SELECT (SELECT a FROM t LIMIT 'x'), (SELECT a FROM t LIMIT 'y')"));
        assertEquals(unexpected(1, 49, "'x'"),
            refusal("SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x') AND b = (SELECT a FROM t LIMIT 'y')"));
    }

    @Test
    public void everyPlaceAQueryStandsIsJudged() {
        assertEquals(unexpected(1, 33, "'x'"), refusal("WITH c AS (SELECT a FROM t LIMIT 'x') SELECT * FROM c"));
        assertEquals(unexpected(1, 37, "'x'"), refusal("SELECT * FROM (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 48, "'x'"), refusal("SELECT a FROM t, LATERAL (SELECT a FROM t LIMIT 'x') l"));
        assertEquals(unexpected(1, 52, "'x'"), refusal("SELECT a FROM t WHERE EXISTS (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 50, "'x'"), refusal("SELECT a FROM t WHERE a IN (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 49, "'x'"), refusal("SELECT a FROM t UNION ALL (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 81, "'x'"),
            refusal("CREATE OR REPLACE VIEW v_lim AS SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 79, "'x'"),
            refusal("CREATE OR REPLACE TABLE t2 AS SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 66, "'x'"),
            refusal("INSERT INTO t SELECT a, b FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 40, "'x'"), refusal("UPDATE t SET a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 47, "'x'"), refusal("DELETE FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
        assertEquals(unexpected(1, 45, "'x'"), refusal("MERGE INTO t USING (SELECT a, b FROM t LIMIT 'x') s ON t.a = s.a "
            + "WHEN MATCHED THEN UPDATE SET t.b = s.b"));
        assertEquals(unexpected(1, 30, "'x'"), refusal("EXECUTE IMMEDIATE 'SELECT (SELECT a FROM t LIMIT ''x'')'"));
    }

    /** A block is one statement: the value is its syntax error before any of it runs, not a statement's failure. */
    @Test
    public void aBlocksStatementIsJudgedWithTheBlock() {
        assertEquals(unexpected(2, 71, "'x'"), refusal("""
            BEGIN
              LET r RESULTSET := (SELECT a FROM t WHERE a = (SELECT a FROM t LIMIT 'x'));
              RETURN 1;
            END"""));
    }

    @Test
    public void theEmptyStringAndNullStayLegal() {
        assertEquals("1", firstCell("SELECT (SELECT a FROM t ORDER BY a LIMIT 1 OFFSET '')"));
        assertEquals("Single-row subquery returns more than one row.",
            refusal("SELECT (SELECT a FROM t ORDER BY a LIMIT '')"));
        assertEquals("Single-row subquery returns more than one row.",
            refusal("SELECT (SELECT a FROM t ORDER BY a LIMIT NULL)"));
    }

    @Test
    public void aStatementThatDoesNotParseReportsItsParseFault() {
        assertEquals(unexpected(1, 47, "<EOF>"), refusal("SELECT (SELECT a FROM t LIMIT 'x') FROM t WHERE"));
        assertEquals(unexpected(1, 11, "a"), refusal("SELECT a a a FROM t WHERE a = (SELECT a FROM t LIMIT 'x')"));
    }
}
