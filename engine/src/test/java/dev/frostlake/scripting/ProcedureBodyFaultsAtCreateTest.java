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
 * A SQL procedure's block body with a fault of its own - an operand missing, a bracket left open, a select list or
 * a table missing, an END where END IF and the like is due, a word after the block's END - is refused at CREATE
 * in the lines the body run on its own earns, and a body of more than one statement by their count; a CASE
 * closed by END alone is created (all live-verified).
 */
public class ProcedureBodyFaultsAtCreateTest extends MeasuredCellsTestSupport {

    @Test
    public void ownFaults() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc1() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc2() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := ; RETURN 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc3() RETURNS INT LANGUAGE SQL AS $$ DECLARE x INT; BEGIN RETURN x + ; END "
                + "$$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pc4() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; SELECT 2 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pc5() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1 END $$");
        assertAnswer("Function PC6 successfully created.",
            "CREATE OR REPLACE PROCEDURE pc6() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pc7() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; BEGIN RETURN 2; "
                + "END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc8() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc9() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN RETURN 1; END; "
                + "END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 3 at position 4 unexpected '<EOF>'.",
            "CREATE OR REPLACE PROCEDURE pc10() RETURNS INT LANGUAGE SQL AS $$ SELECT 1 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc11() RETURNS INT LANGUAGE SQL AS 'BEGIN SELECT FROM; END'");
        assertAnswer("Function PC12 successfully created.",
            "CREATE OR REPLACE PROCEDURE pc12() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; ; $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax "
                + "error line 1 at position 21 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE pc13() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1 x y; RETURN 1; END $$");
        assertAnswer("Function PC14 successfully created.",
            "CREATE OR REPLACE PROCEDURE pc14() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; -- trailing $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'.",
            "CREATE OR REPLACE PROCEDURE pc15() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END SELECT 2 $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pc16() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; DROP TABLE "
                + "nosuch $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pc17() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN SELECT FROM; "
                + "END $$");
    }

    @Test
    public void moreOwnFaults() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd1() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1 FROM; RETURN 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd2() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x INT := 1 +; RETURN x; END "
                + "$$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd3() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := (1 + 2; RETURN x; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd4() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1 + ; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd5() RETURNS INT LANGUAGE SQL AS $$ BEGIN WHILE (TRUE) DO RETURN 1; END; "
                + "END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd6() RETURNS INT LANGUAGE SQL AS $$ BEGIN LOOP RETURN 1; END; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd7() RETURNS INT LANGUAGE SQL AS $$ BEGIN FOR i IN 1 TO 2 DO RETURN 1; "
                + "END; END $$");
        assertAnswer("Function PD8 successfully created.",
            "CREATE OR REPLACE PROCEDURE pd8() RETURNS INT LANGUAGE SQL AS $$ BEGIN CASE WHEN TRUE THEN RETURN 1; "
                + "END; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd9() RETURNS INT LANGUAGE SQL AS $$ BEGIN REPEAT RETURN 1; UNTIL (TRUE) "
                + "END; END $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd10() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; SELECT 2; $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd11() RETURNS INT LANGUAGE SQL AS $$ DECLARE x INT; BEGIN RETURN 1; END; "
                + "SELECT 1 $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd12() RETURNS INT LANGUAGE SQL AS $$ SELECT 1; SELECT 2; $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd13() RETURNS INT LANGUAGE SQL AS $$ SELECT 1; BEGIN RETURN 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'BEGIN'.",
            "CREATE OR REPLACE PROCEDURE pd14() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END BEGIN RETURN 2; "
                + "END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd15() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT * FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "CREATE OR REPLACE PROCEDURE pd16() RETURNS INT LANGUAGE SQL AS $$ BEGIN INSERT INTO t VALUES (1,); END "
                + "$$");
        assertAnswer("REFUSED Actual statement count 3 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd17() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; SELECT 2; SELECT "
                + "3 $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd18() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END;; SELECT 2 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 19 unexpected 'WHERE'. | syntax error line 1 at position 24 unexpected "
                + "';'.",
            "CREATE OR REPLACE PROCEDURE pd19() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT FROM WHERE; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'.",
            "CREATE OR REPLACE PROCEDURE pd20() RETURNS INT LANGUAGE SQL AS $$ BEGIN x := 1 *; RETURN 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pd21() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE pd22() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; RETURN 2 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'LET'.",
            "CREATE OR REPLACE PROCEDURE pd23() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; LET x := 1 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pd24() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '5'.",
            "CREATE OR REPLACE PROCEDURE pd25() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END 5 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'. | "
                + "syntax error line 1 at position 26 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd26() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1 FROM WHERE; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE pd27() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN RETURN 1; END IF "
                + "END $$");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "CREATE OR REPLACE PROCEDURE pd28() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; BEGIN $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 3 at position 9 unexpected 'FROM'. | syntax "
                + "error line 3 at position 13 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd29() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  SELECT FROM;\nEND\n$$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE PROCEDURE pd30() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT (1 + ; END $$");
    }
}
