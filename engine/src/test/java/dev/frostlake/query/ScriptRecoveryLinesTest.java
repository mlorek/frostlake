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
 * The lines after a script's first fault, shape by shape. A comma where an UPDATE's SET item belongs is
 * the statement's one line and the later statements are read. A join's USING with no list after it is
 * refused itself and ends the report. Anything run in after IS TRUE or IS FALSE refuses the IS. An
 * operator run in after a DROP's name or a SHOW's kind, and a second literal after a value, let the
 * later statements be read however many tokens are run in. A SELECT, DROP, CREATE, GRANT, DELETE, UPDATE
 * or ALTER run in after a statement reads on where a string would — after a value, a table of a FROM
 * list, a DROP's name — and any other statement word ends the report. A comma refused in a later
 * statement ends that statement's lines as one refused in the first does. Every answer is live-verified.
 */
public class ScriptRecoveryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theLinesOfTheFiledShapes() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "UPDATE t SET ,; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','.",
            "UPDATE t SET ,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'.",
            "UPDATE t SET; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "UPDATE t SET a; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "UPDATE t SET a =; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'.",
            "UPDATE t SET a = 1,; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 37 unexpected 'y'.",
            "UPDATE t SET ,; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING (; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
            "DESCRIBE TABLE t x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'.",
            "DESCRIBE STAGE s x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "CREATE TABLE IF NOT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "CREATE TABLE IF; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "CREATE TABLE IF NOT; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
            "SHOW TABLES IN t x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SHOW TABLES IN SCHEMA s x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '='. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '='. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW TABLES = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '+'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t + 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '+'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW TABLES + 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '-'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DROP TABLE t -; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '='. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "DROP TABLE t =; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '='.",
            "USE SCHEMA s = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '='.",
            "COMMIT = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "DROP TABLE t , ; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 'q' 'r'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'DROP'. | syntax "
                + "error line 1 at position 56 unexpected 'y'.",
            "UPDATE t SET a = 1 WHERE b = 2 DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 47 unexpected 'y'.",
            "DELETE FROM t WHERE a = 1 SELECT 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'DROP'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "UPDATE t SET a = 1 DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'DROP'. | syntax "
                + "error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM t DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'DROP'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SELECT 1 DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 19 unexpected ','.",
            "SELECT 1 +; UNSET x, y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 24 unexpected ','. | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 +; DROP TABLE t, u; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ','.",
            "UNSET x, y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ','. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "DROP TABLE t, u; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 19 unexpected ','.",
            "SELECT 1 +; UNSET x, y; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 24 unexpected ','.",
            "SELECT 1 +; DROP TABLE t, u");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 29 unexpected 'y'. | syntax error line 1 at position 32 unexpected 'END'.",
            "SELECT 1 +; BEGIN SELECT 1 x y; END; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 42 unexpected '1'.",
            "SELECT 1 +; DECLARE a INT; BEGIN RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "CREATE SCHEMA s x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "CREATE SCHEMA s x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "CREATE DATABASE d x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "CREATE DATABASE d x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "CREATE SCHEMA s x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "CREATE SCHEMA s x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "CREATE SCHEMA IF NOT EXISTS s x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "CREATE OR REPLACE SCHEMA s x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "CREATE TRANSIENT SCHEMA s x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                + "error line 1 at position 32 unexpected 'y'.",
            "CREATE SCHEMA s.t x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
            "CREATE SCHEMA s x =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'.",
            "CREATE SCHEMA s x 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''y''.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') ('y')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''y''. | syntax "
                + "error line 1 at position 57 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') ('y'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''y''. | syntax "
                + "error line 1 at position 57 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a LIKE ALL ('x') ('y'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS TRUE ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS NULL ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS FALSE ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS NOT TRUE ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ')'.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') (y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '1'.",
            "DELETE FROM t 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '1'.",
            "DELETE FROM t 1; SELECT 1 x y");
    }

    @Test
    public void theirNeighbours() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 +; SELECT 1 x y; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 +; SELECT 1 x y; SELECT 2; SELECT 3 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 25 unexpected 'x'. | syntax error line 1 at position 39 unexpected 'y'.",
            "SELECT 1 +; DROP TABLE t x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 19 unexpected 'x'.",
            "SELECT 1 +; COMMIT x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected 'y'. | syntax error line 1 at position 44 unexpected 'w'.",
            "SELECT 1 +; SELECT 1 FROM t x y; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 40 unexpected 'x'.",
            "SELECT 1 +; SELECT 1 FROM t WHERE a = 1 x; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 24 unexpected 'x'. | syntax error line 1 at position 38 unexpected 'y'.",
            "SELECT 1 +; SHOW TABLES x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 23 unexpected '('.",
            "SELECT 1 +; SELECT 1 x (2); SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 20 unexpected 'y'. | syntax error line 1 at position 34 unexpected 'y'.",
            "SELECT 1 +; UNSET x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 25 unexpected 'x'.",
            "SELECT 1 +; USE SCHEMA s x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 24 unexpected ';'. | syntax error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 +; CREATE TABLE; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 +; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TABLES'.",
            "SELECT 1 SHOW TABLES; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected 'y'.",
            "SELECT 1 FROM t SELECT 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'DROP'.",
            "COMMIT DROP TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'DROP'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "DROP TABLE t DROP TABLE u; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'USE'.",
            "SELECT 1 FROM t WHERE a = 1 USE SCHEMA s; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'COMMIT'.",
            "SELECT 1 FROM t WHERE a = 1 COMMIT; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SET'.",
            "SELECT 1 FROM t WHERE a = 1 SET x = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'CALL'.",
            "SELECT 1 FROM t WHERE a = 1 CALL p(); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'WITH'.",
            "SELECT 1 FROM t WHERE a = 1 WITH c AS (SELECT 1) SELECT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'INSERT'.",
            "SELECT 1 FROM t WHERE a = 1 INSERT INTO t VALUES (1); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'CREATE'. | "
                + "syntax error line 1 at position 63 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 CREATE TABLE z (a INT); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'. | syntax "
                + "error line 1 at position 63 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'BEGIN'.",
            "SELECT 1 FROM t WHERE a = 1 BEGIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'EXPLAIN'.",
            "SELECT 1 FROM t WHERE a = 1 EXPLAIN SELECT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'GRANT'. | syntax "
                + "error line 1 at position 68 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 GRANT SELECT ON t TO ROLE r; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'TRUNCATE'.",
            "SELECT 1 FROM t WHERE a = 1 TRUNCATE TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DELETE'. | "
                + "syntax error line 1 at position 54 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 DELETE FROM t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'UPDATE'. | "
                + "syntax error line 1 at position 59 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 UPDATE t SET a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SHOW'.",
            "SELECT 1 FROM t WHERE a = 1 SHOW TABLES; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DESCRIBE'.",
            "SELECT 1 FROM t WHERE a = 1 DESCRIBE TABLE t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ALTER'. | syntax "
                + "error line 1 at position 71 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 ALTER TABLE t ADD COLUMN c INT; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ROLLBACK'.",
            "SELECT 1 FROM t WHERE a = 1 ROLLBACK; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'DROP'.",
            "SELECT 1 FROM t WHERE a = 1 DROP TABLE t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = 1 SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t x 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'x'. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "SHOW TABLES x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '='. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "DROP TABLE t = 1 + 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t x y z; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'y'. | syntax "
                + "error line 1 at position 24 unexpected 'y'.",
            "UNSET x y z; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 2 3 4; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "DROP TABLE t 1 x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''q''. | syntax "
                + "error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = 1 'q' x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 27 unexpected 'w'.",
            "DROP TABLE t x; SELECT 2 z w; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "UPDATE t SET , a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "UPDATE t SET a = 1, , b = 2; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "UPDATE t SET ,; SELECT 1 x y; SELECT 2 z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 39 unexpected 'y'.",
            "UPDATE t SET , WHERE a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 28 unexpected 'y'.",
            "UPDATE t SET , ; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','.",
            "UPDATE t SET ,;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING; SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING (a; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING WHERE a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'USING'.",
            "SELECT 1 FROM t JOIN n USING;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'USING'.",
            "SELECT 1 FROM t LEFT JOIN n USING; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 35 unexpected 'USING'.",
            "SELECT 1 +; SELECT 1 FROM t JOIN n USING; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'.",
            "DESCRIBE TABLE t x = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'.",
            "DESCRIBE TABLE t x;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "DESCRIBE TABLE t x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
            "SHOW TABLES IN t x; SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "SHOW TABLES IN DATABASE d x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
            "SHOW TABLES LIKE 'a' x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'. | syntax "
                + "error line 1 at position 55 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a IS NOT FALSE ('x'); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE ('x') AND b = 'y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a IS TRUE (+)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'IS'.",
            "SELECT 1 FROM t WHERE a = 1 AND a IS TRUE ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 'foo', INSERT ON");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ON'.",
            "SELECT INSERT ON t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ON'. | syntax "
                + "error line 1 at position 19 unexpected 'x'.",
            "SELECT INSERT ON t x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 'foo', INSERT ON t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SELECT 'foo', INSERT ON t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 'foo', INSERT ON 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ON'. | syntax "
                + "error line 1 at position 20 unexpected 'ON'.",
            "SELECT 'foo', RLIKE ON t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 26 unexpected 'x'.",
            "SELECT 'foo', INSERT ON t x y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 25 unexpected '.'.",
            "SELECT 'foo', INSERT ON t.a x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'ON'. | syntax "
                + "error line 1 at position 19 unexpected 'x'.",
            "SELECT INSERT ON t x; SELECT 1 x y");
    }
}
