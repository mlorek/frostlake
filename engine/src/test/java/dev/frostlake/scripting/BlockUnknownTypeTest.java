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
 * A block whose words where types stand name no type is refused for the first of them, Unsupported data type,
 * before any of it runs, handler or no handler: under any scripting construct, in CREATE TABLE, ALTER TABLE,
 * DECLARE, LET, CAST and ::, run on its own or as a procedure's body; a block with a fault of its own is
 * refused for that fault (all live-verified).
 */
public class BlockUnknownTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void unknownTypeWords() {
        engine.execute("CREATE OR REPLACE TABLE hc672b (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t1 (a NOSUCHTYPE); RETURN 'none'; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t2 (a NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t3 (a INT, b NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE(10)'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t4 (a NOSUCHTYPE(10)); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t5 (a NOSUCHTYPE NOT NULL); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE OR REPLACE TABLE hc672t6 (a NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TEMPORARY TABLE hc672t7 (a NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t8 (a NOSUCHTYPE, b ALSONOTATYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t9 (a VARCHAR(10), b NUMBER(5,2), c NOSUCHTYPE); RETURN "
                + "'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t10 (a NOSUCHTYPE); END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN CREATE TABLE hc672t11 (a NOSUCHTYPE); END IF; RETURN 'x'; END "
                + "$$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN CREATE TABLE hc672t12 (a NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE hc672b ADD COLUMN b NOSUCHTYPE; RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "ALTER TABLE hc672b ADD COLUMN b NOSUCHTYPE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ DECLARE x NOSUCHTYPE; BEGIN RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN LET x NOSUCHTYPE := 1; RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN CAST(1 AS NOSUCHTYPE); END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT 1::NOSUCHTYPE); END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT CAST(1 AS NOSUCHTYPE)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.", "SELECT 1::NOSUCHTYPE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE hc672t13 (a NOSUCHTYPE)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE 'CREATE TABLE hc672t14 (a NOSUCHTYPE)'");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "BEGIN CREATE TABLE hc672t15 (a NOSUCHTYPE); RETURN 'none'; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE PROCEDURE hc672p() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN CREATE TABLE hc672t16 (a "
                + "NOSUCHTYPE); RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unknown function HC672P.", "CALL hc672p()");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t17 AS SELECT 1::NOSUCHTYPE AS a; RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t18 (a NOSUCHTYPE) AS SELECT 1; RETURN 'none'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hc672t19 (a ARRAY(NOSUCHTYPE)); RETURN 'none'; END $$");
    }

    @Test
    public void unknownTypeWordsUnderConstructs() {
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t3 (a \"nosuch\"); RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t4 (a x.nosuch); RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t5 (a VARCHAR DEFAULT CASE WHEN TRUE THEN 'x' END, b "
                + "NOSUCHTYPE); RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO CREATE TABLE hd673t6 (a NOSUCHTYPE); END FOR; RETURN 'x'; "
                + "END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN LOOP CREATE TABLE hd673t7 (a NOSUCHTYPE); BREAK; END LOOP; RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN CREATE TABLE hd673t8 (a NOSUCHTYPE); "
                + "RETURN 2; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t9 (a INT); ALTER TABLE hd673t9 ADD COLUMN b NOSUCHTYPE; "
                + "RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "EXECUTE IMMEDIATE $$ BEGIN LET q r; RETURN q; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT CAST(1 AS NOSUCHTYPE)); END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT (SELECT 1::NOSUCHTYPE)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE hd673t10 (a ARRAY(NOSUCHTYPE))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1::NOSUCHTYPE; BEGIN RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE) DO CREATE TABLE hd673t11 (a NOSUCHTYPE); END WHILE; RETURN "
                + "'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CASE WHEN TRUE THEN CREATE TABLE hd673t12 (a NOSUCHTYPE); END CASE; RETURN "
                + "'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN REPEAT CREATE TABLE hd673t13 (a NOSUCHTYPE); UNTIL (TRUE) END REPEAT; RETURN "
                + "'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN RETURN 1; ELSE CREATE TABLE hd673t14 (a NOSUCHTYPE); END IF; "
                + "RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE PROCEDURE hd673p() RETURNS INT LANGUAGE SQL AS $$ DECLARE x NOSUCHTYPE; BEGIN RETURN "
                + "1; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE(1, 2)'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t16 (a NUMBER(5,2), b NOSUCHTYPE(1, 2)); RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t17 (a NOSUCHTYPE); CREATE TABLE hd673t18 (b ALSONOT); "
                + "RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1::NOSUCHTYPE; RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN LET y VARCHAR := CAST('1' AS NOSUCHTYPE); RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1::NOSUCHTYPE; BEGIN RETURN 'x'; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "BEGIN CREATE TABLE hd673t19 (a INT); CREATE TABLE hd673t20 (a NOSUCHTYPE); RETURN 'x'; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE hd673t21 (a NOSUCHTYPE); RETURN 'x'; END; $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN CREATE TABLE hd673t22 (a NOSUCHTYPE); END; RETURN 'x'; END $$");
    }

    @Test
    public void routineBodies() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'END'.",
            "CREATE OR REPLACE PROCEDURE he674p2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1 END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax "
                + "error line 1 at position 21 unexpected 'RETURN'.",
            "CREATE OR REPLACE PROCEDURE he674p5() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1 x y; RETURN 1; END "
                + "$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'LET'.",
            "CREATE OR REPLACE FUNCTION he674f4() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x NOSUCHTYPE := 1; RETURN "
                + "1; END $$");
    }
}
