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
 * A block written inside a loop's body runs from its own text, and what fails in it is placed where it stands in the
 * block that wrote it: a cursor's query that reads no loop record is refused at the FOR or the OPEN that opens it, at
 * 105 where the nested block itself starts at 56, and a statement of a nested BEGIN or DECLARE block at its own place
 * likewise. A FOR over a cursor an earlier FOR ran to its end runs no iteration, a FETCH finds no row, an OPEN is
 * refused, and a CLOSE closes it. An owner's procedure refuses a session variable where it runs, its block variable
 * of the same name aside, and a caller's reads it (live-verified).
 */
public class NestedBlockPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aNestedBlockIsPlacedInTheBlockThatWroteIt() {
        assertCells(new String[][] {
            {"DECLARE c CURSOR FOR SELECT 1 AS n; BEGIN FOR r IN c DO DECLARE c CURSOR FOR SELECT r.n * 10 AS "
                + "m; BEGIN FOR q IN c DO RETURN q.m; END FOR; END; END FOR; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 105 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'R.N'"},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; BEGIN FOR r IN c DO DECLARE d CURSOR FOR SELECT r.n * 10 AS "
                + "m; BEGIN OPEN d; RETURN 5; END; END FOR; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 105 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'R.N'"},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; BEGIN FOR r IN c DO DECLARE x INT; BEGIN SELECT nosuch_col "
                + "FROM nosuch_t_zq; END; END FOR; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 77 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN FOR i IN 1 TO 1 DO BEGIN SELECT nosuch_col FROM nosuch_t_zq; END; END FOR; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN DECLARE x INT; BEGIN SELECT nosuch_col FROM nosuch_t_zq; END; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN DECLARE d CURSOR FOR SELECT nosuch_col FROM nosuch_t_zq; BEGIN OPEN d; RETURN 5; END; "
                + "RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 69 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN DECLARE d CURSOR FOR SELECT nosuch_col FROM nosuch_t_zq; BEGIN FOR q IN d DO RETURN 1; END "
                + "FOR; END; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 69 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN\n  DECLARE x INT;\n  BEGIN\n    SELECT nosuch_col FROM nosuch_t_zq;\n  END;\n  RETURN 0;\nEND;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 4 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN DECLARE x INT; BEGIN LET y INT := 1 / 0; END; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : Division by zero"},
            {"BEGIN DECLARE x INT DEFAULT 1 / 0; BEGIN RETURN x; END; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : Division by zero"},
            {"BEGIN DECLARE x INT; BEGIN DECLARE z INT; BEGIN SELECT nosuch_col FROM nosuch_t_zq; END; END; "
                + "RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"BEGIN BEGIN DECLARE x INT; BEGIN SELECT nosuch_col FROM nosuch_t_zq; END; END; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : SQL compilation "
                + "error: | Object 'NOSUCH_T_ZQ' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T_ZQ."},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; x INT DEFAULT 0; BEGIN FOR r IN c DO x := x + 1; END FOR; "
                + "OPEN c; RETURN x; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 94 : Stored procedure "
                + "execution error: CURSOR 'C' is already open."},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; x INT DEFAULT 0; BEGIN FOR r IN c DO x := x + 1; END FOR; FOR "
                + "r IN c DO x := x + 10; END FOR; RETURN x; END;",
                "1"},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; x INT DEFAULT 0; y INT; BEGIN FOR r IN c DO x := x + 1; END "
                + "FOR; FETCH c INTO y; RETURN COALESCE(y, -1); END;",
                "-1"},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; x INT DEFAULT 0; BEGIN FOR r IN c DO x := x + 1; END FOR; "
                + "CLOSE c; RETURN x; END;",
                "1"},
            {"DECLARE c CURSOR FOR SELECT 1 AS n; x INT DEFAULT 0; BEGIN FOR r IN c DO x := x + 1; END FOR; "
                + "CLOSE c; OPEN c; RETURN x; END;",
                "1"},
        });
    }

    @Test
    public void aSessionVariableBesideABlockVariable() {
        assertCells(new String[][] {
            {"SET v = 'session'", "Statement executed successfully."},
            {"CREATE OR REPLACE PROCEDURE pown() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ DECLARE v "
                + "VARCHAR DEFAULT 'block'; BEGIN RETURN $v; END $$",
                "Function POWN successfully created."},
            {"CALL pown()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure"},
            {"CREATE OR REPLACE PROCEDURE pcall() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ DECLARE "
                + "v VARCHAR DEFAULT 'block'; BEGIN RETURN $v; END $$",
                "Function PCALL successfully created."},
            {"CALL pcall()", "session"},
            {"CREATE OR REPLACE PROCEDURE pown2() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN "
                + "RETURN $v; END $$",
                "Function POWN2 successfully created."},
            {"CALL pown2()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure"},
            {"CREATE OR REPLACE PROCEDURE pown3() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ DECLARE v "
                + "VARCHAR DEFAULT 'block'; BEGIN RETURN v; END $$",
                "Function POWN3 successfully created."},
            {"CALL pown3()", "block"},
            {"UNSET v", "Statement executed successfully."},
            {"CALL pown()",
                "REFUSED SQL compilation error: error line 1 at position 49 | Session variable '$V' does not exist"},
            {"CALL pcall()",
                "REFUSED SQL compilation error: error line 1 at position 49 | Session variable '$V' does not exist"},
            {"CALL pown2()",
                "REFUSED SQL compilation error: error line 1 at position 14 | Session variable '$V' does not exist"},
            {"CALL pown3()", "block"},
        });
    }
}
