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
 * A block with faults of its own besides a word that names no type is refused for its own faults, the word read
 * as a name, run on its own and through EXECUTE IMMEDIATE alike; a statement missing its select list or its
 * table there reads on, and two statements written with nothing between them are refused at the second. A SQL
 * function's body with such a word is refused at CREATE for the type once its syntax and its relations are read,
 * ahead of its columns and calls (all live-verified).
 */
public class TypeWordOwnFaultsTest extends MeasuredCellsTestSupport {

    @Test
    public void blocksAndBodies() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT FROM; CREATE TABLE tb (a NOSUCHTYPE); END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE tb (a NOSUCHTYPE); SELECT FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE tb (a NOSUCHTYPE) $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FROM'. | syntax "
                + "error line 1 at position 41 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN LET x NOSUCHTYPE := 1; SELECT FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT FROM; LET x NOSUCHTYPE := 1; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1::NOSUCHTYPE; SELECT FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb1() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb2() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb3() RETURNS INT LANGUAGE SQL AS $$ CAST(1 AS NOSUCHTYPE) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb4() RETURNS INT LANGUAGE SQL AS $$ 1::NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb5() RETURNS INT LANGUAGE SQL AS $$ SELECT TRY_CAST('1' AS NOSUCHTYPE) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb6() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE + 1 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb7() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE FROM g1 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb8() RETURNS INT LANGUAGE SQL AS $$ SELECT n::NOSUCHTYPE FROM g1 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCH2'.",
            "CREATE OR REPLACE FUNCTION fb9() RETURNS INT AS 'SELECT CAST(1 AS NOSUCH2)'");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb10() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE, 2 $$");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH.",
            "CREATE OR REPLACE FUNCTION fb11() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE FROM nosuch $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb12() RETURNS INT LANGUAGE SQL AS $$ SELECT CAST(1 AS NOSUCHTYPE) FROM g1 "
                + "WHERE n > 0 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb13() RETURNS INT LANGUAGE SQL AS $$ SELECT 1 WHERE 1::NOSUCHTYPE = 1 $$");
        assertAnswer("", "SHOW USER FUNCTIONS LIKE 'FB%'");
    }

    @Test
    public void moreBlocksAndBodies() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'.",
            "BEGIN SELECT FROM; CREATE TABLE tb (a NOSUCHTYPE); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'FROM'. | syntax "
                + "error line 1 at position 49 unexpected ';'.",
            "BEGIN CREATE TABLE tb (a NOSUCHTYPE); SELECT FROM; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'FROM'. | syntax "
                + "error line 1 at position 17 unexpected ';'.",
            "BEGIN SELECT FROM; LET x NOSUCHTYPE := 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected ';'.",
            "BEGIN LET x NOSUCHTYPE := 1; SELECT FROM; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FROM'. | syntax "
                + "error line 1 at position 39 unexpected ';'.",
            "BEGIN SELECT 1::NOSUCHTYPE; SELECT FROM; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 19 unexpected 'WHERE'. | syntax error line 1 at position 24 unexpected "
                + "';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT FROM WHERE; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 24 unexpected 'y'. | syntax error line 1 at position 27 unexpected 'END'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT FROM g1 x y; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1::NOSUCHTYPE; LET x := ; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ')'.",
            "EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE tb (a NOSUCHTYPE, b INT,); END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT CAST(1 AS NOSUCHTYPE) + ; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1::NOSUCHTYPE(5) FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'END'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1::NOSUCHTYPE; RETURN 1 END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN 1; END SELECT 2 $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ DECLARE x NOSUCHTYPE; BEGIN SELECT FROM; END $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'FROM'. | syntax "
                + "error line 1 at position 18 unexpected ';'. | syntax error line 1 at position 27 unexpected "
                + "'FROM'. | syntax error line 1 at position 31 unexpected ';'.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT FROM; SELECT FROM; END $$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | Unsupported data type "
                + "'NOSUCHTYPE(5)'.",
            "CREATE OR REPLACE FUNCTION fb21() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE(5) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb22() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE, 1::OTHERTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb23() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::x.NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE OR REPLACE FUNCTION fb24() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::\"nosuch\" $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb25(a NUMBER) RETURNS INT LANGUAGE SQL AS $$ a::NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb26() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE FROM g1 WHERE "
                + "nosuchcol = 1 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb27() RETURNS INT LANGUAGE SQL AS $$ SELECT nosuchcol::NOSUCHTYPE FROM g1 $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb28() RETURNS INT LANGUAGE SQL AS $$ SELECT NOSUCHFN(1)::NOSUCHTYPE $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb29() RETURNS INT LANGUAGE SQL AS $$ SELECT 1::NOSUCHTYPE + 'a'::INT $$");
        assertAnswer("REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH.",
            "CREATE OR REPLACE FUNCTION fb30() RETURNS TABLE (a INT) LANGUAGE SQL AS $$ SELECT n::NOSUCHTYPE FROM "
                + "nosuch $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb31() RETURNS INT LANGUAGE SQL AS $$ SELECT (SELECT 1::NOSUCHTYPE) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb32() RETURNS INT LANGUAGE SQL AS $$ WITH c AS (SELECT 1::NOSUCHTYPE AS v) "
                + "SELECT v FROM c $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb33() RETURNS INT LANGUAGE SQL AS $$ (SELECT 1::NOSUCHTYPE) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb34() RETURNS INT LANGUAGE SQL AS $$ IFF(TRUE, 1::NOSUCHTYPE, 2) $$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION fb35() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 'a'::NOSUCHTYPE $$");
        assertAnswer("", "SHOW USER FUNCTIONS LIKE 'FB%'");
    }
}
