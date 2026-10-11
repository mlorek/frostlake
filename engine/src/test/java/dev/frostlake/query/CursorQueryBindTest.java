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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A cursor's query binds none of the block's variables. A :name in it - a declared variable's, a procedure's
 * parameter's or nothing's - is refused when the cursor opens, by OPEN or by a FOR loop over it, as a bind
 * nothing sets, placed in the query's own text; a cursor that is never opened refuses nothing, and the block
 * compiles. Only a question mark is bound, by OPEN ... USING, and a cursor over a RESULTSET reads the rows the
 * RESULTSET's own query answered, binds included. A colon inside a text, a cast or a path is no bind. Every
 * answer is live-verified.
 */
public class CursorQueryBindTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameInACursorsQuery() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : SQL compilation "
                    + "error: error line 1 at position 21 | Bind variable :s not set.",
                "DECLARE s NUMBER(10,2); c CURSOR FOR SELECT SYSTEM$TYPEOF(:s) AS e1; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: error line 1 at position 21 | Bind variable :s not set.",
                "DECLARE s VARCHAR(10); c CURSOR FOR SELECT SYSTEM$TYPEOF(:s) AS e2; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 67 : SQL compilation "
                    + "error: error line 1 at position 21 | Bind variable :s not set.",
                "DECLARE s DATE; c CURSOR FOR SELECT SYSTEM$TYPEOF(:s) AS e3; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 71 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; c CURSOR FOR SELECT :s AS v; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 82 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; c CURSOR FOR SELECT :s AS v; t VARCHAR; BEGIN FOR r IN c DO "
                    + "t := r.v; END FOR; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 102 : SQL compilation "
                    + "error: error line 1 at position 21 | Bind variable :s not set.",
                "DECLARE s NUMBER(10,2); c CURSOR FOR SELECT SYSTEM$TYPEOF(:s) AS cf8; t VARCHAR DEFAULT 'none'; "
                    + "BEGIN FOR r IN c DO t := r.cf8; END FOR; RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                    + "error: error line 1 at position 7 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 3; BEGIN LET c CURSOR FOR SELECT :s AS v; OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = ?; BEGIN OPEN c USING (s); "
                    + "RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = ?; BEGIN OPEN c USING (:s); "
                    + "RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 75 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "1",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s; BEGIN RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE c CURSOR FOR SELECT v FROM tp WHERE k = :nosuch; BEGIN RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :nosuch not set.",
                "DECLARE c CURSOR FOR SELECT v FROM tp WHERE k = :nosuch; BEGIN OPEN c; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 82 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s; x INT; BEGIN OPEN c; FETCH "
                    + "c INTO x; CLOSE c; RETURN x; END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; res RESULTSET DEFAULT (SELECT v FROM tp WHERE k = :s); c CURSOR FOR "
                    + "res; x INT; BEGIN OPEN c; FETCH c INTO x; RETURN x; END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; x INT; BEGIN LET res RESULTSET := (SELECT v FROM tp WHERE k = :s); LET "
                    + "c CURSOR FOR res; OPEN c; FETCH c INTO x; RETURN x; END;");
            assertAnswer(
                "Function PCUR successfully created.",
                "CREATE OR REPLACE PROCEDURE pcur(p INT) RETURNS INT LANGUAGE SQL AS 'DECLARE c CURSOR FOR SELECT "
                    + "v FROM tp WHERE k = :p; x INT; BEGIN OPEN c; FETCH c INTO x; RETURN x; END;'");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 65 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :p not set.",
                "CALL pcur(2)");
            assertAnswer(
                "Function PCUR2 successfully created.",
                "CREATE OR REPLACE PROCEDURE pcur2(p INT) RETURNS INT LANGUAGE SQL AS 'DECLARE c CURSOR FOR "
                    + "SELECT v FROM tp WHERE k = ?; x INT; BEGIN OPEN c USING (p); FETCH c INTO x; RETURN x; END;'");
            assertAnswer(
                "20",
                "CALL pcur2(2)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: error line 1 at position 27 | invalid identifier 'S'",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = s; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable ? not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = ?; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "20",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = ?; BEGIN OPEN c USING (s, s); "
                    + "RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 84 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s OR k = ?; BEGIN OPEN c USING "
                    + "(s); RETURN TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 22 : SQL compilation "
                    + "error: error line 2 at position 12 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp\n  WHERE k = :s; BEGIN OPEN c; RETURN 1; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 79 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; BEGIN LET c CURSOR FOR SELECT v FROM tp WHERE k = :s; FOR r IN c DO "
                    + "RETURN r.v; END FOR; RETURN 0; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void colonsThatAreNoBinds() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute(
            "CREATE OR REPLACE TABLE tb (b BOOLEAN, n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tb VALUES (TRUE, 1.5, 0)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (n NUMBER(10,2))");
        try {
            assertAnswer(
                ":notabind",
                "DECLARE c CURSOR FOR SELECT ':notabind' AS v; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "10",
                "DECLARE c CURSOR FOR SELECT v::VARCHAR AS v FROM tp WHERE k = 1; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "1",
                "DECLARE c CURSOR FOR SELECT PARSE_JSON('{\"a\":1}'):a AS v; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable ? not set.",
                "DECLARE c CURSOR FOR SELECT v FROM tp WHERE k = ?; x INT DEFAULT 0; BEGIN FOR r IN c DO x := "
                    + "r.v; END FOR; RETURN x; END;");
            assertAnswer(
                "",
                "DECLARE s INT; c CURSOR FOR SELECT v FROM tp WHERE k = ?; BEGIN OPEN c USING (s); RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 84 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s /* :x */; BEGIN OPEN c; "
                    + "RETURN 1; END;");
            assertAnswer(
                "10",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = 1 -- :s\n; BEGIN OPEN c; RETURN "
                    + "TABLE(RESULTSET_FROM_CURSOR(c)); END;");
            assertAnswer(
                "SQL compilation error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; c CURSOR FOR SELECT v FROM tp WHERE k = :s; BEGIN BEGIN OPEN c; "
                    + "EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 90 : SQL compilation "
                    + "error: error line 1 at position 27 | Bind variable :s not set.",
                "DECLARE s INT DEFAULT 2; BEGIN LET c CURSOR FOR SELECT v FROM tp WHERE k = :s AND k = :t; OPEN "
                    + "c; RETURN 1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
