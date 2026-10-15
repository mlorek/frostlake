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
 * The name a cursor is declared over is a RESULTSET declared before the cursor, in its block or in one around
 * it. Any other name is refused while the body compiles, at the name, whether the cursor is opened or not: a
 * name nothing declares, a variable that is no RESULTSET, the cursor's own name, a RESULTSET declared after the
 * cursor or in a block that has ended, and a table's name; a quoted name keeps its quotes. A procedure with
 * such a cursor is created and refused when it is called (live-verified).
 */
public class CursorSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameThatIsNoResultSet() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_RS'",
            "DECLARE c CURSOR FOR nosuch_rs; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_RS'",
            "DECLARE c CURSOR FOR nosuch_rs; BEGIN OPEN c; RETURN 1; END;");
        assertAnswer(
            "1",
            "DECLARE r RESULTSET DEFAULT (SELECT 1); c CURSOR FOR r; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'X'",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR x; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'C'",
            "DECLARE c CURSOR FOR c; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_RS'",
            "BEGIN LET c CURSOR FOR nosuch_rs; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier '\"nosuch\"'",
            "DECLARE c CURSOR FOR \"nosuch\"; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '.'.",
            "DECLARE c CURSOR FOR a.b; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'T1'",
            "DECLARE c CURSOR FOR t1; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Stored procedure "
                + "execution error: uninitialized resultSet 'R'",
            "DECLARE r RESULTSET; c CURSOR FOR r; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'R'",
            "DECLARE c CURSOR FOR r; r RESULTSET DEFAULT (SELECT 1); BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'SHOW'",
            "BEGIN LET c CURSOR FOR SHOW; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'TABLES'",
            "DECLARE c CURSOR FOR TABLES; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'.",
            "DECLARE c CURSOR FOR x y; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'IDENTIFIER'",
            "DECLARE c CURSOR FOR IDENTIFIER; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'X'",
            "DECLARE x INT DEFAULT 1; c CURSOR FOR x; BEGIN OPEN c; RETURN 1; END;");
        assertAnswer(
            "2",
            "DECLARE r RESULTSET DEFAULT (SELECT 1); c CURSOR FOR r; BEGIN OPEN c; CLOSE c; RETURN 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'X'",
            "BEGIN LET x := 1; LET c CURSOR FOR x; RETURN 1; END;");
    }

    @Test
    public void theWordShowAndOtherStatements() {
        answer(
            "CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'SHOW'",
            "DECLARE c CURSOR FOR SHOW; BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'SHOW'",
            "DECLARE c CURSOR FOR SHOW; BEGIN OPEN c; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TABLE'.",
            "DECLARE c CURSOR FOR DESCRIBE TABLE t1; BEGIN RETURN 1; END;");
        assertAnswer(
            "1",
            "DECLARE c CURSOR FOR SELECT 1; BEGIN RETURN 1; END;");
    }

    @Test
    public void inAProcedureAndAcrossBlocks() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t1 (a INT)");
            assertAnswer(
                "Function PC1 successfully created.",
                "CREATE OR REPLACE PROCEDURE pc1() RETURNS INT LANGUAGE SQL AS $$ DECLARE c CURSOR FOR nosuch_rs; "
                    + "BEGIN RETURN 1; END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_RS'",
                "CALL pc1()");
            assertAnswer(
                "Function PC2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pc2() RETURNS INT LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT "
                    + "(SELECT 1); c CURSOR FOR r; BEGIN RETURN 1; END $$");
            assertAnswer(
                "1",
                "CALL pc2()");
            assertAnswer(
                "1",
                "BEGIN LET r RESULTSET := (SELECT 1); LET c CURSOR FOR r; RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE r RESULTSET DEFAULT (SELECT 1); BEGIN BEGIN LET c CURSOR FOR r; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'NOSUCH_RS'",
                "BEGIN IF (TRUE) THEN LET c CURSOR FOR nosuch_rs; END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 65 | invalid identifier 'R'",
                "BEGIN BEGIN LET r RESULTSET := (SELECT 1); END; LET c CURSOR FOR r; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_RS'",
                "DECLARE c CURSOR FOR nosuch_rs; x INT DEFAULT nosuch_var; BEGIN RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'NOSUCH_RS'",
                "DECLARE x INT DEFAULT nosuch_var; c CURSOR FOR nosuch_rs; BEGIN RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pc1()");
            answer(
                "DROP PROCEDURE IF EXISTS pc2()");
        }
    }
}
