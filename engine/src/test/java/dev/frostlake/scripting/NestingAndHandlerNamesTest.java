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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Dynamic texts nest inside one another eighteen deep and run; a handler that names no exception is refused
 * when the procedure holding it is called, and an anonymous block for it while it compiles, before a name a
 * statement writes; STATEMENT_ERROR and EXPRESSION_ERROR may be declared as variables and as exceptions of a
 * nested block; a Java procedure that calls itself through its session is refused at its seventeenth level,
 * which its sixteenth catches (live-verified).
 */
public class NestingAndHandlerNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void dynamicTextsNestEighteenDeep() {
        String nested = "SELECT 1";
        for (int i = 1; i <= 18; i++) {
            nested = "EXECUTE IMMEDIATE '" + nested.replace("'", "''") + "'";
            if (i >= 15) {
                assertAnswer("1", nested);
            }
        }
    }

    @Test
    public void handlerNamesAndTheBuiltInExceptions() {
        try {
            assertAnswer(
                "Function DH successfully created.",
                "CREATE OR REPLACE PROCEDURE dh() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; EXCEPTION WHEN "
                    + "no_such_exc THEN RETURN 2; END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NO_SUCH_EXC'",
                "CALL dh()");
            assertAnswer(
                "1",
                "DECLARE statement_error INT DEFAULT 1; BEGIN RETURN statement_error; END;");
            assertAnswer(
                "1",
                "BEGIN LET statement_error := 1; RETURN statement_error; END;");
            assertAnswer(
                "1",
                "BEGIN DECLARE statement_error EXCEPTION (-20002, 'y'); BEGIN RETURN 1; END; END;");
            assertAnswer(
                "1",
                "DECLARE expression_error INT; BEGIN RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 98 : z",
                "DECLARE e1 EXCEPTION (-20001, 'x'); BEGIN DECLARE expression_error EXCEPTION (-20003, 'z'); "
                    + "BEGIN RAISE expression_error; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'NO_SUCH_EXC'",
                "BEGIN RETURN nosuch_x; EXCEPTION WHEN no_such_exc THEN RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NO_SUCH_EXC'",
                "BEGIN RETURN 1; EXCEPTION WHEN no_such_exc THEN RETURN nosuch_x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 33 | The OTHER handler must be the last "
                    + "handler",
                "BEGIN RETURN nosuch_x; EXCEPTION WHEN OTHER THEN RETURN 1; WHEN no_such_exc THEN RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'nosuch_b'",
                "BEGIN LET y := :nosuch_b; RETURN y; EXCEPTION WHEN no_such_exc THEN RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NO_SUCH_EXC'",
                "BEGIN RETURN 1; EXCEPTION WHEN no_such_exc OR statement_error THEN RETURN 2; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS dh()");
            answer(
                "DROP PROCEDURE IF EXISTS jrec(FLOAT)");
        }
    }

    @Test
    public void aJavaBodysDepth() {
        try {
            assertAnswer(
                "Function JREC successfully created.",
                "CREATE OR REPLACE PROCEDURE jrec(n FLOAT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
                    + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                    + "com.snowflake.snowpark_java.*; class H { public static String run(Session s, double n) { if "
                    + "(n >= 40) return \"bottom\"; try { return s.sql(\"CALL jrec(\" + (n + 1) + "
                    + "\")\").collect()[0].getString(0); } catch (Exception e) { return (int) n + \": \" + "
                    + "e.getMessage(); } } } $$");
            assertAnswer(
                "16: Stored procedure execution error: Stored procedure exceeds the maximum allowed number of "
                    + "recursive calls.",
                "CALL jrec(1)");
            assertAnswer(
                "bottom",
                "CALL jrec(30)");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS dh()");
            answer(
                "DROP PROCEDURE IF EXISTS jrec(FLOAT)");
        }
    }
}
