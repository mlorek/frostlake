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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scripting name may be introduced only once per scope: a second DECLARE item or LET of it is refused
 * as "Variable with name 'X' declared twice.", at the second introduction and before any undeclared name
 * is judged. A nested block, branch or loop body is a fresh scope that may introduce the name again. An
 * EXCEPTION collides only with another EXCEPTION. A procedure refuses its DECLARE sections, its
 * parameters included, at CREATE, and a repeated LET only at CALL. Every cell is live-verified.
 */
public class VariableRedeclarationTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aSecondIntroductionInOneScopeIsRefused() {
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  v_foo VARCHAR DEFAULT '';\nBEGIN\n  LET v_foo "
                + ":= 'bar';\n  RETURN v_foo;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'V_FOO' "
                + "declared twice.");
        assertRefused("DECLARE\n  v_foo VARCHAR DEFAULT '';\nBEGIN\n  LET v_foo := 'bar';\n  RETURN "
                + "v_foo;\nEND;",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'V_FOO' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a INT;\nBEGIN\n  RETURN a;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a CURSOR FOR SELECT 1;\nBEGIN\n  "
                + "RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a RESULTSET;\nBEGIN\n  RETURN "
                + "1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  c CURSOR FOR SELECT 1;\nBEGIN\n  LET c := 5;\n "
                + " RETURN c;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'C' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  LET x := 1;\n  LET x := 2;\n  RETURN x;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'X' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  LET x := 1;\n  LET x INT := 2;\n  RETURN "
                + "x;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'X' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  A INT;\nBEGIN\n  RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a DEFAULT 5;\nBEGIN\n  RETURN "
                + "a;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  b INT;\n  a INT;\n  b INT;\nBEGIN\n  "
                + "RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT DEFAULT 1;\nBEGIN\n  LET a := 2;\n  "
                + "RETURN a;\nEXCEPTION\n  WHEN OTHER THEN\n    RETURN -1;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE a INT; a INT; BEGIN RETURN 1; END; $$",
            "SQL compilation error: error line 1 at position 16\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\nBEGIN\n  LET a RESULTSET := (SELECT "
                + "1);\n  RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\nBEGIN\n  LET a CURSOR FOR SELECT 1;\n  "
                + "RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  e1 EXCEPTION (-20001, 'x');\n  e1 EXCEPTION "
                + "(-20002, 'y');\nBEGIN\n  RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'E1' "
                + "declared twice.");
    }

    @Test
    public void itIsJudgedBeforeAnUndeclaredName() {
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\nBEGIN\n  RETURN nosuch;\n  LET a := "
                + "1;\nEND;\n$$",
            "SQL compilation error: error line 6 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a INT;\nBEGIN\n  RETURN "
                + "nosuch;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
    }

    @Test
    public void aNestedScopeMayIntroduceTheNameAgain() {
        assertEquals("2", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT DEFAULT 1;\nBEGIN\n  DECLARE\n    a INT "
                + "DEFAULT 2;\n  BEGIN\n    RETURN a;\n  END;\nEND;\n$$"));
        assertEquals("1", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\n  a EXCEPTION (-20001, 'x');\nBEGIN\n  "
                + "RETURN 1;\nEND;\n$$"));
        assertEquals("5", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  x EXCEPTION (-20001, 'x');\nBEGIN\n  LET x := "
                + "5;\n  RETURN x;\nEND;\n$$"));
        assertEquals("1", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  \"a\" INT;\n  a INT;\nBEGIN\n  RETURN "
                + "1;\nEND;\n$$"));
        assertEquals("1", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT DEFAULT 1;\nBEGIN\n  BEGIN\n    LET a := "
                + "2;\n  END;\n  RETURN a;\nEND;\n$$"));
        assertEquals("1", scalar("EXECUTE IMMEDIATE $$\nBEGIN\n  LET a := 1;\n  BEGIN\n    LET a := 2;\n  END;\n  "
                + "RETURN a;\nEND;\n$$"));
        assertEquals("0", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  c1 CURSOR FOR SELECT 1 AS x;\n  r INT DEFAULT "
                + "0;\nBEGIN\n  FOR r IN c1 DO\n    LET z := 1;\n  END FOR;\n  RETURN r;\nEND;\n$$"));
        assertEquals("1", scalar("EXECUTE IMMEDIATE $$\nDECLARE\n  a INT;\nBEGIN\n  FOR a IN 1 TO 2 DO\n    LET a "
                + ":= 5;\n  END FOR;\n  RETURN 1;\nEND;\n$$"));
    }

    @Test
    public void aProcedureRefusesDeclarationsAtCreateAndLetsAtCall() {
        engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS VARCHAR LANGUAGE SQL AS $$\nDECLARE\n  "
                + "v_foo VARCHAR DEFAULT '';\nBEGIN\n  LET v_foo VARCHAR := 'bar';\n  RETURN "
                + ":v_foo;\nEND;\n$$");
        assertRefused("CALL p1()",
            "SQL compilation error: error line 5 at position 2\n Variable with name 'V_FOO' "
                + "declared twice.");
        engine.execute("CREATE OR REPLACE PROCEDURE p3(x INT) RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  "
                + "LET x := 7;\n  RETURN x;\nEND;\n$$");
        assertRefused("CALL p3(1)",
            "SQL compilation error: error line 3 at position 2\n Variable with name 'X' "
                + "declared twice.");
        engine.execute("CREATE OR REPLACE PROCEDURE q4() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  LET a "
                + ":= 1;\n  LET a := 2;\n  RETURN a;\nEND;\n$$");
        assertRefused("CALL q4()",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        engine.execute("CREATE OR REPLACE PROCEDURE q9() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  a "
                + "INT;\nBEGIN\n  RETURN nosuch;\nEND;\n$$");
        assertRefused("CALL q9()",
            "SQL compilation error: error line 5 at position 9\ninvalid identifier 'NOSUCH'");
        engine.execute("CREATE OR REPLACE PROCEDURE q6(x INT) RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  "
                + "DECLARE\n    x INT DEFAULT 5;\n  BEGIN\n    RETURN x;\n  END;\nEND;\n$$");
        assertEquals("5", scalar("CALL q6(1)"));
        assertRefused("CREATE OR REPLACE PROCEDURE p2(x INT) RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n "
                + " x INT DEFAULT 5;\nBEGIN\n  RETURN x;\nEND;\n$$",
            "SQL compilation error: error line 3 at position 2\n Variable with name 'X' "
                + "declared twice.");
        assertRefused("CREATE OR REPLACE PROCEDURE q1() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  a "
                + "INT;\n  a INT;\nBEGIN\n  RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("CREATE OR REPLACE PROCEDURE q3() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  a "
                + "INT;\n  a CURSOR FOR SELECT 1;\nBEGIN\n  RETURN 1;\nEND;\n$$",
            "SQL compilation error: error line 4 at position 2\n Variable with name 'A' "
                + "declared twice.");
        assertRefused("CREATE OR REPLACE PROCEDURE q8() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  "
                + "DECLARE\n    b INT;\n    b INT;\n  BEGIN\n    RETURN 1;\n  END;\nEND;\n$$",
            "SQL compilation error: error line 5 at position 4\n Variable with name 'B' "
                + "declared twice.");
        assertRefused("CREATE OR REPLACE PROCEDURE q11(x INT) RETURNS INT LANGUAGE SQL AS "
                + "$$\nDECLARE\n  X INT DEFAULT 5;\nBEGIN\n  RETURN x;\nEND;\n$$",
            "SQL compilation error: error line 3 at position 2\n Variable with name 'X' "
                + "declared twice.");
    }

    @Test
    public void aCalledProcedureIsAScopeOfItsOwn() {
        engine.execute("CREATE OR REPLACE PROCEDURE inner_p(b INT) RETURNS INT LANGUAGE SQL AS $$\n"
            + "DECLARE\n  a INT DEFAULT 1;\nBEGIN\n  RETURN a + b;\nEND;\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE outer_p() RETURNS INT LANGUAGE SQL AS $$\n"
            + "DECLARE\n  a INT DEFAULT 5;\n  b INT DEFAULT 10;\n  r INT;\nBEGIN\n"
            + "  r := (CALL inner_p(:b));\n  RETURN a + r;\nEND;\n$$");
        assertEquals("16", scalar("CALL outer_p()"));
    }
}
