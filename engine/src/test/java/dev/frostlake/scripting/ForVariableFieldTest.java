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
 * The variable of a FOR over a cursor or a RESULTSET is a record; over anything else it has no fields, and a
 * field read from it is refused while the block compiles, on a branch that never runs too: "invalid identifier
 * 'R.V'" for a loop over a value in parentheses, and the counter alone, "invalid identifier 'R'", for a loop
 * over a range. A name alone in parentheses that nothing declares is refused where the parentheses open
 * (live-verified).
 */
public class ForVariableFieldTest extends MeasuredCellsTestSupport {

    @Test
    public void aFieldOfWhatIsNoRecord() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 97 | invalid identifier 'R.V'",
            "DECLARE s INT DEFAULT 2; x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp WHERE k = :s) DO x := "
                + "r.v; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : Single-row subquery "
                + "returns more than one row.",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO x := x + 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 67 | invalid identifier 'R.V'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO x := r.v; END FOR; RETURN x; END;");
        assertAnswer(
            "30",
            "DECLARE x INT DEFAULT 0; c CURSOR FOR SELECT v FROM tp; BEGIN FOR r IN c DO x := x + r.v; END FOR; "
                + "RETURN x; END;");
        assertAnswer(
            "30",
            "DECLARE x INT DEFAULT 0; BEGIN LET rs RESULTSET := (SELECT v FROM tp); FOR r IN rs DO x := x + r.v; "
                + "END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : Single-row subquery "
                + "returns more than one row.",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO RETURN 1; END FOR; RETURN 0; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH_ZQ7'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (nosuch_zq7) DO x := 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 67 | invalid identifier 'R.NOSUCH'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO x := r.nosuch; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 83 | invalid identifier 'R.V'",
            "DECLARE x INT DEFAULT 0; BEGIN IF (FALSE) THEN FOR r IN (SELECT v FROM tp) DO x := r.v; END FOR; END "
                + "IF; RETURN x; END;");
    }

    @Test
    public void theOperandAndOtherVariables() {
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'NOSUCH_ZQ7'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (1 + nosuch_zq7) DO x := 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH_ZQ7'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN nosuch_zq7 DO x := 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : Single-row subquery "
                + "returns more than one row.",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO x := r; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'",
            "DECLARE x INT DEFAULT 0; BEGIN x := nosuch.v; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'R'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN 1 TO 2 DO x := r.v; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | invalid identifier 'R.V'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO LET z INT := r.v; END FOR; RETURN x; "
                + "END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 69 | invalid identifier 'R.V'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO RETURN r.v; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 90 unexpected '.'.",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO INSERT INTO tp VALUES (9, :r.v); END "
                + "FOR; RETURN x; END;");
    }

    @Test
    public void parenthesesAndCounters() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        answer(
            "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
        answer(
            "INSERT INTO tq VALUES (1.5, 'ab')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH_ZQ7'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN ((nosuch_zq7)) DO x := 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH_ZQ7'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (nosuch_zq7.f) DO x := 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'I'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR i IN 1 TO 2 DO x := i.v + 1; END FOR; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 71 | invalid identifier 'R.V'",
            "DECLARE x INT DEFAULT 0; BEGIN FOR r IN (SELECT v FROM tp) DO x := x + r.v; END FOR; RETURN x; END;");
    }
}
