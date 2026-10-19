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
 * A block's INSERT ... VALUES written with a variable. A variable that holds NULL, written alone as a row's item
 * - :s or (:s), in one row or several, a LET variable or a procedure's argument - carries no type, so it fits
 * any column whatever it is declared as; a value that is not NULL keeps its own type, and so does an
 * expression over the variable and an INSERT ... SELECT :s. A column's rows fold to the type of the items
 * that have one. A bare name is no value in a VALUES row, a date part's word included: it is an invalid
 * identifier, placed in the statement. Every answer is live-verified.
 */
public class NullVariableInValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariableHoldingNullFitsAnyColumn() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE tvr (v VARIANT, a ARRAY, o OBJECT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (v VARIANT, a ARRAY, o OBJECT, n NUMBER(10,2), d DATE, c VARCHAR(5), b "
                + "BOOLEAN)");
        try {
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v, a, o) VALUES (:s, :s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s,"
                    + " :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2); BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, "
                    + ":s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s DATE; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s BOOLEAN; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, "
                    + ":s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s TIMESTAMP_NTZ; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, "
                    + ":s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s FLOAT; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARIANT; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, "
                    + ":s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s ARRAY; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s OBJECT; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, "
                    + ":s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s TIME; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s BINARY; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, "
                    + ":s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s INT; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR; BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, "
                    + ":s); RETURN 'ok'; END;");
            assertAnswer(
                "13",
                "SELECT COUNT(*) FROM tv");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tvr");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tvr (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting ARRAY but got VARCHAR(3) "
                    + "for column A",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tvr (a) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting OBJECT but got VARCHAR(3) "
                    + "for column O",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tvr (o) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; BEGIN INSERT INTO tv (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting DATE but got NUMBER(10,2) "
                    + "for column D",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; BEGIN INSERT INTO tv (d) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; BEGIN INSERT INTO tv (b) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; BEGIN INSERT INTO tv (c) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting NUMBER(10,2) but got DATE "
                    + "for column N",
                "DECLARE s DATE DEFAULT '2024-01-01'; BEGIN INSERT INTO tv (n) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got DATE for "
                    + "column V",
                "DECLARE s DATE DEFAULT '2024-01-01'; BEGIN INSERT INTO tv (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : DML operation to "
                    + "table TV failed on column B with error: Boolean value 'abc' is not recognized",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tv (b) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : DML operation to "
                    + "table TV failed on column N with error: Numeric value 'abc' is not recognized",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tv (n) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 48 : DML operation to "
                    + "table TV failed on column C with error: String 'abcdefgh' is too long and would be truncated",
                "DECLARE s VARCHAR(10) DEFAULT 'abcdefgh'; BEGIN INSERT INTO tv (c) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting DATE but got BOOLEAN for "
                    + "column D",
                "DECLARE s BOOLEAN DEFAULT TRUE; BEGIN INSERT INTO tv (d) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting NUMBER(10,2) but got "
                    + "BOOLEAN for column N",
                "DECLARE s BOOLEAN DEFAULT TRUE; BEGIN INSERT INTO tv (n) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(11) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (:s || 'x'); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(134217728) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (COALESCE(:s, NULL)); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES ((:s)); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(134217728) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (:s::VARCHAR); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(10) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (NVL(:s, :s)); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (:s), (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v, a) VALUES (:s, :s), (1, NULL); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: error line 1 at position 28 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(10) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (v) SELECT :s; RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (c) SELECT :s; RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN IF (TRUE) THEN INSERT INTO tvr (v, a, o) VALUES (:s, :s, :s); END "
                    + "IF; RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                    + "VARIANT but got VARCHAR(10) for column V",
                "INSERT INTO tvr (v, a, o) VALUES (NULL::VARCHAR(10), NULL::VARCHAR(10), NULL::VARCHAR(10))");
            assertAnswer(
                "ok",
                "BEGIN LET s VARCHAR(10) := NULL; INSERT INTO tvr (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'S' cannot have its type "
                    + "inferred from initializer",
                "BEGIN LET s := NULL; INSERT INTO tvr (v, a, o) VALUES (:s, :s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "Function P_INS successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ins(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO "
                    + "tvr (v, a, o) VALUES (:s, :s, :s); RETURN ''ok''; END;'");
            assertAnswer(
                "ok",
                "CALL p_ins(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "CALL p_ins('abc')");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); c CURSOR FOR SELECT 1; BEGIN FOR r IN c DO INSERT INTO tvr (v, a, o) "
                    + "VALUES (:s, :s, :s); END FOR; RETURN 'ok'; END;");
            assertAnswer(
                "17",
                "SELECT COUNT(*) FROM tv");
            assertAnswer(
                "10",
                "SELECT COUNT(*) FROM tvr");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void theOtherFormsOfTheInsert() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE tvr (v VARIANT, a ARRAY, o OBJECT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (v VARIANT, a ARRAY, o OBJECT, n NUMBER(10,2), d DATE, c VARCHAR(5), b "
                + "BOOLEAN)");
        engine.execute(
            "INSERT INTO tv (c) VALUES ('r')");
        try {
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 1 at position 27 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tv (c) VALUES (s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 1 at position 26 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN INSERT INTO tv (c) SELECT s; RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN LET r RESULTSET := (SELECT s); RETURN TABLE(r); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 7 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; r VARCHAR; BEGIN SELECT s INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: error line 1 at position 27 | invalid identifier 'S'",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (c) VALUES (s); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : SQL compilation "
                    + "error: error line 1 at position 27 | invalid identifier 'N'",
                "DECLARE n NUMBER DEFAULT 5; BEGIN INSERT INTO tv (n) VALUES (n); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE n NUMBER DEFAULT 5; BEGIN INSERT INTO tv (n) SELECT n FROM tv; RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr VALUES (:s, :s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT OVERWRITE INTO tvr (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (((:s))); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (:s), ('abc'); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES ('abc'), (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (:s), (1); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v) VALUES (1), (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s BOOLEAN; BEGIN INSERT INTO tv (n) VALUES (:s), (1); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting NUMBER(10,2) but got "
                    + "BOOLEAN for column N",
                "DECLARE s NUMBER(10,2); BEGIN INSERT INTO tv (n) VALUES (:s), (TRUE); RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting NUMBER(10,2) but got DATE "
                    + "for column N",
                "DECLARE s NUMBER(10,2); BEGIN INSERT INTO tv (n) VALUES (:s), ('2024-01-01'::DATE); RETURN 'ok'; "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting DATE but got NUMBER(2,1) "
                    + "for column D",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (d) VALUES (:s), (1.5); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tv (d, n) VALUES (:s, :s), ('2024-01-01', 1); RETURN "
                    + "'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN EXECUTE IMMEDIATE 'INSERT INTO tvr (v) VALUES (?)' USING (s); "
                    + "RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT ALL INTO tvr (v) VALUES (:s) SELECT 1; RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(10) for column V",
                "DECLARE s VARCHAR(10); BEGIN INSERT ALL INTO tvr (v) VALUES (x) SELECT :s AS x; RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN BEGIN INSERT INTO tvr (v) VALUES (:s); END; RETURN 'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN IF (TRUE) THEN INSERT INTO tvr (v) VALUES (:s); END "
                    + "IF; RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN s := NULL; INSERT INTO tvr (v) VALUES (:s); RETURN "
                    + "'ok'; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting VARIANT but got "
                    + "VARCHAR(3) for column V",
                "DECLARE s VARCHAR(10); BEGIN s := 'abc'; INSERT INTO tvr (v) VALUES (:s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2); BEGIN SELECT NULL INTO :s; INSERT INTO tv (d) VALUES (:s); RETURN 'ok'; "
                    + "END;");
            assertAnswer(
                "Function P_N successfully created.",
                "CREATE OR REPLACE PROCEDURE p_n(s NUMBER) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO tv "
                    + "(v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, :s, :s); RETURN ''ok''; END;'");
            assertAnswer(
                "ok",
                "CALL p_n(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Expression type does not match column data type, expecting ARRAY but got NUMBER(38,"
                    + "0) for column A",
                "CALL p_n(1)");
            assertAnswer(
                "Function P_INS successfully created.",
                "CREATE OR REPLACE PROCEDURE p_ins(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO "
                    + "tvr (v, a, o) VALUES (:s, :s, :s); RETURN ''ok''; END;'");
            assertAnswer(
                "ok",
                "CALL p_ins(NULL::VARCHAR)");
            assertAnswer(
                "ok",
                "CALL p_ins(NULL)");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aNameIsNoValueInAValuesRow() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        engine.execute(
            "CREATE OR REPLACE TABLE tvr (v VARIANT, a ARRAY, o OBJECT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (v VARIANT, a ARRAY, o OBJECT, n NUMBER(10,2), d DATE, c VARCHAR(5), b "
                + "BOOLEAN)");
        engine.execute(
            "INSERT INTO tv (c) VALUES ('r')");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'D'",
                "INSERT INTO tv (c) VALUES (d)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'MS'",
                "INSERT INTO tv (c) VALUES (ms)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'ZZ'",
                "INSERT INTO tv (n) VALUES (zz)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'TV.C'",
                "INSERT INTO tv (c) VALUES (tv.c)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'YEAR'",
                "INSERT INTO tv (c) VALUES (year)");
            assertAnswer(
                "1",
                "INSERT INTO tv (d) VALUES (DATEADD(DAY, 1, CURRENT_DATE))");
            assertAnswer(
                "1",
                "INSERT INTO tv (d) VALUES (DATE_TRUNC(month, CURRENT_DATE))");
            assertAnswer(
                "1",
                "INSERT INTO tv (c) VALUES (CURRENT_USER)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'S'",
                "INSERT INTO tv (c) VALUES (s), (ms)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'S'",
                "INSERT INTO tv (c) VALUES ('a'), (s)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'S'",
                "INSERT INTO tv (c, n) VALUES (s, zz)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'ZZ'",
                "INSERT INTO tv (n, c) VALUES (zz, s)");
            assertAnswer(
                "ok",
                "DECLARE s VARCHAR(10); BEGIN INSERT INTO tvr (v, a, o) VALUES (:s, :s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "ok",
                "DECLARE s NUMBER(10,2); BEGIN INSERT INTO tv (v, a, o, n, d, c, b) VALUES (:s, :s, :s, :s, :s, "
                    + ":s, :s); RETURN 'ok'; END;");
            assertAnswer(
                "1|0|0|0",
                "SELECT COUNT(*), COUNT(v), COUNT(a), COUNT(o) FROM tvr");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
