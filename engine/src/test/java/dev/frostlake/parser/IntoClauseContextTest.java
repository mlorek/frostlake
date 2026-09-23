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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Where a SELECT … INTO may stand, and the syntax errors around an INTO or INSERT that follows a query
 * (live-verified). Only a statement of a Snowflake Scripting block takes an INTO clause; anywhere else it is
 * refused while the statement compiles — ahead of any name in it — at the query's SELECT, where Frostlake
 * answered. A word refused after a query says nothing more about its own bracketed tail, and a refused INSERT
 * stands alone however its statement goes on.
 */
public class IntoClauseContextTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String notAllowed(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + " INTO clause is not allowed in this context";
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void outsideABlockTheIntoClauseIsRefusedAtItsSelect() {
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO :t"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1, 2 INTO t, u"));
        assertEquals(notAllowed(1, 0), refusal("select 1 into t"));
        assertEquals(notAllowed(1, 2), refusal("  SELECT 1 INTO t"));
        assertEquals(notAllowed(2, 0), refusal("\nSELECT 1 INTO t"));
        assertEquals(notAllowed(1, 0), refusal("/* c */ SELECT 1 INTO t"));
        assertEquals(notAllowed(1, 23), refusal("WITH c AS (SELECT 1 a) SELECT a INTO t FROM c"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 COPY INTO t FROM @s"));
        assertEquals(notAllowed(1, 0), refusal("EXECUTE IMMEDIATE 'SELECT 1 INTO t'"));
    }

    @Test
    public void theRefusalComesBeforeAnyName() {
        assertEquals(notAllowed(1, 0), refusal("SELECT nosuch INTO t FROM t"));
        assertEquals(notAllowed(1, 0), refusal("SELECT 1 INTO t FROM nosuch"));
    }

    @Test
    public void aBlockStatementTakesIt() {
        assertEquals(1L, ((Number) engine.executeQuery(
            "EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN SELECT 1 INTO :x; RETURN x; END;$$").getRows().get(0).getValue(0))
            .longValue());
        assertEquals(2L, ((Number) engine.executeQuery("""
            EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN IF (TRUE) THEN SELECT 2 INTO x; END IF; RETURN x; END;$$""")
            .getRows().get(0).getValue(0)).longValue());
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 21 : " + notAllowed(1, 0),
            refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN EXECUTE IMMEDIATE 'SELECT 1 INTO :x'; RETURN x; END;$$"));
    }

    @Test
    public void aRefusedWordSaysNothingOfItsBracketedTail() {
        assertEquals(syntax(16, "VALUES"), refusal("SELECT 1 INTO t VALUES (1)"));
        assertEquals(syntax(18, "VALUES"), refusal("SELECT 1 x INTO t VALUES (1)"));
        assertEquals(syntax(9, "VALUES"), refusal("SELECT 1 VALUES (1)"));
        assertEquals(syntax(14, "VALUES"), refusal("SELECT 1 AS x VALUES (1)"));
        assertEquals(syntax(16, "x"), refusal("SELECT 1 INTO t x (1)"));
    }

    @Test
    public void aRefusedInsertStandsAlone() {
        assertEquals(syntax(9, "INSERT"), refusal("SELECT 1 INSERT t VALUES (1)"));
        assertEquals(syntax(9, "INSERT"), refusal("SELECT 1 INSERT INTO t VALUES (1 2)"));
        assertEquals(syntax(14, "INSERT"), refusal("SELECT 1 AS x INSERT t VALUES (1)"));
        assertEquals(syntax(13, "INSERT"), refusal("SELECT 'foo' INSERT INTO t VALUES (a b c)"));
    }
}
