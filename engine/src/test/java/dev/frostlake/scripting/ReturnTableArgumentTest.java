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
package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What {@code RETURN TABLE(…)} takes (live-verified): a RESULTSET by its name, or a call over one name that yields
 * one, and nothing else. A query, a parenthesis, a literal, an operator and an empty pair of parentheses are syntax
 * errors — when the block runs on its own, and at CREATE when it is a procedure's body, so the procedure is never
 * created. A fault at the first token inside is followed by the token after the next name in the statement; a
 * fault after the name is reported twice.
 */
public class ReturnTableArgumentTest extends BaseDatabaseTest {

    private static String body(final String inner) {
        return "CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ BEGIN RETURN TABLE("
            + inner + "); END; $$";
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private static String lines(final String first, final int firstAt, final String second, final int secondAt) {
        return "SQL compilation error:\nsyntax error line 1 at position " + firstAt + " unexpected '" + first + "'."
            + (second == null ? "" : "\nsyntax error line 1 at position " + secondAt + " unexpected '" + second + "'.");
    }

    @Test
    public void aQueryIsRefusedAtCreate() {
        assertEquals(lines("SELECT", 20, ")", 33), refusal(body("SELECT 1 AS a")));
        assertEquals(lines("SELECT", 20, "(", 32), refusal(body("SELECT COUNT(*) FROM rta_t")));
        assertEquals(lines("WITH", 20, "AS", 27), refusal(body("WITH c AS (SELECT 1 a) SELECT * FROM c")));
        assertEquals(lines("(", 20, ")", 34), refusal(body("(SELECT 1 AS a)")));
        assertEquals(lines("(", 20, null, 0), refusal(body("(SELECT (1))")));
        assertEquals(lines("SELECT", 20, null, 0), refusal(body("SELECT (1), (2)")));
        assertEquals(lines("VALUES", 20, null, 0), refusal(body("VALUES (1)")));
        assertEquals("SQL compilation error:\nUnknown function RTA.", refusal("CALL rta()"));
    }

    @Test
    public void aLiteralAnOperatorAndNothingAreRefused() {
        assertEquals(lines("5", 20, null, 0), refusal(body("5")));
        assertEquals(lines("1.5", 20, null, 0), refusal(body("1.5")));
        assertEquals(lines("'x'", 20, null, 0), refusal(body("'x'")));
        assertEquals(lines("NULL", 20, null, 0), refusal(body("NULL")));
        assertEquals(lines("-", 20, null, 0), refusal(body("-1")));
        assertEquals(lines("1", 20, null, 0), refusal(body("1 + 2")));
        assertEquals(lines("[", 20, null, 0), refusal(body("[1]")));
        assertEquals(lines(")", 20, null, 0), refusal(body("")));
        assertEquals(lines(":", 20, ")", 24), refusal(body(":res")));
        assertEquals(lines("NOT", 20, ")", 27), refusal(body("NOT res")));
        assertEquals(lines("(", 20, ")", 24), refusal(body("(res)")));
        assertEquals(lines("TABLE", 20, ")", 29), refusal(body("TABLE(res)")));
    }

    @Test
    public void whatFollowsTheNameIsRefusedTwice() {
        assertEquals(lines("extra", 24, "extra", 24), refusal(body("res extra")));
        assertEquals(lines(".", 21, ".", 21), refusal(body("x.y")));
        assertEquals(lines("::", 23, "::", 23), refusal(body("res::INT")));
        assertEquals(lines("+", 24, "+", 24), refusal(body("res + 1")));
        assertEquals(lines(",", 23, ",", 23), refusal(body("res, res2")));
        assertEquals(lines("WHEN", 25, "WHEN", 25), refusal(body("CASE WHEN TRUE THEN 1 END")));
        assertEquals(lines("1", 24, null, 0), refusal(body("ABS(1)")));
        assertEquals(lines("'res'", 31, null, 0), refusal(body("IDENTIFIER('res')")));
    }

    @Test
    public void theFaultIsPlacedInTheBodyAsWritten() {
        assertEquals("SQL compilation error:\nsyntax error line 3 at position 15 unexpected 'SELECT'.\n"
            + "syntax error line 3 at position 28 unexpected ')'.",
            refusal("CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS"
                + " $$\nBEGIN\n  RETURN TABLE(SELECT 1 AS a);\nEND;\n$$"));
        assertEquals(lines("SELECT", 19, ")", 32),
            refusal("CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS"
                + " 'BEGIN RETURN TABLE(SELECT 1 AS a); END;'"));
        assertEquals(lines("SELECT", 36, ")", 49),
            refusal("CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS"
                + " $$ BEGIN LET x INT := 1; RETURN TABLE(SELECT 1 AS a); END; $$"));
        assertEquals(lines("SELECT", 20, ")", 33),
            refusal("CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS"
                + " $$ BEGIN RETURN TABLE(SELECT 1 AS a); RETURN TABLE(SELECT 2 AS a); END; $$"));
        assertEquals(lines("SELECT", 20, "END", 35),
            refusal("CREATE OR REPLACE PROCEDURE rta() RETURNS TABLE (a INT) LANGUAGE SQL AS"
                + " $$ BEGIN RETURN TABLE(SELECT 1 AS a; END; $$"));
    }

    @Test
    public void aBlockRunOnItsOwnIsRefusedTheSameWay() {
        assertEquals(lines("SELECT", 20, ")", 33),
            refusal("EXECUTE IMMEDIATE $$ BEGIN RETURN TABLE(SELECT 1 AS a); END; $$"));
        assertEquals(lines("5", 20, null, 0), refusal("EXECUTE IMMEDIATE $$ BEGIN RETURN TABLE(5); END; $$"));
    }

    @Test
    public void aNameAndACallOverANameAreTaken() {
        engine.execute("CREATE OR REPLACE PROCEDURE rta_ok() RETURNS TABLE (a INT) LANGUAGE SQL AS"
            + " $$ DECLARE r RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END; $$");
        assertEquals("1", String.valueOf(engine.executeQuery("CALL rta_ok()").getRows().get(0).getValue(0)));
        engine.execute("CREATE OR REPLACE PROCEDURE rta_cur() RETURNS TABLE (a INT) LANGUAGE SQL AS"
            + " $$ DECLARE c1 CURSOR FOR SELECT 7 AS a; BEGIN OPEN c1; RETURN TABLE(RESULTSET_FROM_CURSOR(c1)); END; $$");
        assertEquals("7", String.valueOf(engine.executeQuery("CALL rta_cur()").getRows().get(0).getValue(0)));
        engine.execute(body("\"res\""));
        engine.execute(body("nosuch"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusal("CALL rta()"));
    }
}
