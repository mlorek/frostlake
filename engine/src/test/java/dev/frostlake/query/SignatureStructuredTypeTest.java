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
 * The structured types and the TABLE items of the signature a DROP or a DESCRIBE writes after a table's or a
 * view's name. ARRAY takes one type, OBJECT fields, MAP a key and a value, VECTOR an integer or floating-point
 * type's word and a signed dimension, INTERVAL its fields; an element, a field and a value may end in NOT NULL.
 * TABLE(type, ...) items stand alone in a signature. A fault inside is reported with the lines the account stacks
 * after it, by which of the nested types gives up. Every answer is live-verified.
 */
public class SignatureStructuredTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theItemsOfTheFirstMeasurement() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE ta (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tb (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE td (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE tc (x INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT 1 AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "DROP TABLE ta (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'.",
            "DROP TABLE tb (VECTOR)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "DROP TABLE td (INTERVAL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'.",
            "DROP VIEW vv (MAP)");
        assertAnswer(
            "TC successfully dropped.",
            "DROP TABLE tc (TABLE(a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax error "
                + "line 1 at position 28 unexpected '2'. | syntax error line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(INT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '.'.",
            "DESCRIBE TABLE t1 (ARRAY.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'.",
            "DESCRIBE TABLE t1 (OBJECT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (INTERVAL)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'INT'.",
            "DESCRIBE TABLE t1 (TABLE(a INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax error "
                + "line 1 at position 27 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (TABLE x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'INT'. | syntax error "
                + "line 1 at position 33 unexpected 'b'.",
            "DESCRIBE TABLE t1 (TABLE(a INT), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TABLE'.",
            "DESCRIBE TABLE t1 (a, TABLE)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (MAP(INT, INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(INT, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a, b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'TABLE'.",
            "DESCRIBE TABLE t1 (a, TABLE(b))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT())");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(ARRAY(INT)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (MAP(VARCHAR, ARRAY(INT)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(FLOAT, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '3'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '.'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (INT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (VARCHAR.a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (NUMBER(10, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected '3'. | syntax error line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (NUMBER(1, 2, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TIMESTAMP(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '.'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP_NTZ.a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL DAY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE())");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a), TABLE(b))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a INT, b VARCHAR))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a INT NOT NULL))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(INT NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP(INT, INT, INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'VARCHAR'.",
            "DESCRIBE TABLE t1 (VECTOR(VARCHAR, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('.",
            "DESCRIBE TABLE t1 (VARIANT(1))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INT, VARCHAR)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (a.b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (a.b.c)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a.b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'.",
            "DESCRIBE TABLE t1 (TABLE(a), b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (GEOGRAPHY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(TABLE(a)))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY(INT))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (TABLE(a, b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (VECTOR)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_V already dropped).",
            "DROP VIEW IF EXISTS nosuch_v (OBJECT(a INT))");
    }

    @Test
    public void nestedTypesAndTableItems() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(ARRAY(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('.",
            "DESCRIBE TABLE t1 (ARRAY(VARCHAR(a)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "DESCRIBE TABLE t1 (ARRAY(INT) x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(INT NOT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (ARRAY(INT NULL))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(a))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(a.b))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(\"a\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(INT, INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(NUMBER(10, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax error "
                + "line 1 at position 38 unexpected '3'. | syntax error line 1 at position 40 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(NUMBER(1, 2, 3)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY(VARCHAR(5) NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'NOT'. | syntax error "
                + "line 1 at position 47 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(INT NOT NULL NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "DESCRIBE TABLE t1 (ARRAY(INT)(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '.'. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(INT).a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax error "
                + "line 1 at position 26 unexpected ','.",
            "DESCRIBE TABLE t1 (ARRAY(), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected ','.",
            "DESCRIBE TABLE t1 (ARRAY(1), b c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'b'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT b))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(\"a\" INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(INT INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a ARRAY(1)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a ARRAY(INT)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT NOT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '.'. | syntax error "
                + "line 1 at position 30 unexpected 'INT'. | syntax error line 1 at position 33 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a.b INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT) x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 27 unexpected ')'. | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a, b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "DESCRIBE TABLE t1 (OBJECT(1 INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a NUMBER(10, 2), b VARCHAR(5)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a OBJECT(b INT)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(a MAP(INT, INT)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
            "DESCRIBE TABLE t1 (OBJECT()(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '.'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT).b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (MAP(a, b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "DESCRIBE TABLE t1 (MAP(INT, 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
            "DESCRIBE TABLE t1 (MAP(1, INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP(,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'NOT'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP(INT NOT NULL, INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (MAP(INT, INT NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
            "DESCRIBE TABLE t1 (MAP(INT, INT) x)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (MAP(VARCHAR(5), NUMBER(10, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'INT'.",
            "DESCRIBE TABLE t1 (MAP(INT INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '.'.",
            "DESCRIBE TABLE t1 (MAP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (MAP x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','.",
            "DESCRIBE TABLE t1 (MAP, a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'a'.",
            "DESCRIBE TABLE t1 (VECTOR(INT, a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected '4'. | syntax error line 1 at position 36 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR(INT, 3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'a'.",
            "DESCRIBE TABLE t1 (VECTOR(a, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(FLOAT, -3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NUMBER'.",
            "DESCRIBE TABLE t1 (VECTOR(NUMBER, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(INTEGER, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(DOUBLE, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'.",
            "DESCRIBE TABLE t1 (VECTOR(INT, 3) x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'.",
            "DESCRIBE TABLE t1 (VECTOR.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "DESCRIBE TABLE t1 (VECTOR x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','.",
            "DESCRIBE TABLE t1 (VECTOR, a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '3'.",
            "DESCRIBE TABLE t1 (VECTOR(INT 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR(INT,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '3.5'.",
            "DESCRIBE TABLE t1 (VECTOR(FLOAT, 3.5))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL DAY TO SECOND)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "DESCRIBE TABLE t1 (INTERVAL x)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL DAY(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
            "DESCRIBE TABLE t1 (INTERVAL 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '.'.",
            "DESCRIBE TABLE t1 (INTERVAL.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','.",
            "DESCRIBE TABLE t1 (INTERVAL, a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL YEAR TO MONTH)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TO'.",
            "DESCRIBE TABLE t1 (INTERVAL DAY TO)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
            "DESCRIBE TABLE t1 (INTERVAL DAY x)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL DAY(3) TO SECOND(3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL MONTH TO DAY)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL SECOND(3, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (INTERVAL YEAR, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'.",
            "DESCRIBE TABLE t1 (TIMESTAMP x)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH LOCAL TIME ZONE)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DOUBLE PRECISION)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (CHAR VARYING)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (GEOMETRY)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (FILE)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (UUID)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DECFLOAT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (BINARY(5))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (ARRAY, OBJECT, VARIANT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ARRAY'.",
            "DESCRIBE TABLE t1 (a ARRAY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'c'.",
            "DESCRIBE TABLE t1 (TABLE(a), TABLE(b), c)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(NUMBER(10, 2)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(ARRAY(INT)))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a(b)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'TABLE'. | syntax "
                + "error line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(TABLE(a)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'.",
            "DESCRIBE TABLE t1 (TABLE(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(a,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'b'.",
            "DESCRIBE TABLE t1 (TABLE(a) b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '.'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(a).b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(a),)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(\"a\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'b'.",
            "DESCRIBE TABLE t1 (TABLE(a b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'c'.",
            "DESCRIBE TABLE t1 (TABLE(a, b c))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('.",
            "DESCRIBE TABLE t1 (TABLE(VARCHAR(a)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'd'.",
            "DESCRIBE TABLE t1 (TABLE(a.b.c.d))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'TRUE'.",
            "DESCRIBE TABLE t1 (TABLE(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IF'.",
            "DESCRIBE TABLE t1 (TABLE(IF))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(a), TABLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'c'.",
            "DESCRIBE TABLE t1 (TABLE(a), TABLE(b c))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a) , TABLE(b), TABLE(c))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '.'.",
            "DESCRIBE TABLE t1 (TABLE.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (TABLE(a)) x");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a)) TYPE = COLUMNS");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(OBJECT(a INT)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(MAP))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'INT'.",
            "DESCRIBE TABLE t1 (TABLE(a), INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'TABLE'.",
            "DESCRIBE TABLE t1 (INT, TABLE(a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TABLE(ARRAY(1)))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY(INT NOT NULL))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (OBJECT(a INT, b VARCHAR))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (MAP(VARCHAR, ARRAY(INT)))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (VECTOR(FLOAT, 3))");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (INTERVAL DAY)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (TABLE(a), TABLE(b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'b'.",
            "DROP TABLE IF EXISTS nosuch_x (TABLE(a), b)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (TABLE(a)) CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY(1)) CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (MAP) CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY.a) CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'. | syntax error "
                + "line 1 at position 42 unexpected 'x'.",
            "DROP TABLE IF EXISTS nosuch_x (OBJECT(a)) x");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_V already dropped).",
            "DROP VIEW IF EXISTS nosuch_v (TABLE(a, b))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ')'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "DROP VIEW IF EXISTS nosuch_v (VECTOR(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY(INT(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'. | syntax error "
                + "line 1 at position 39 unexpected '<EOF>'.",
            "DROP TABLE IF EXISTS nosuch_x (TABLE x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (TABLE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('.",
            "DROP TABLE IF EXISTS nosuch_x (ARRAY(TABLE(a)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (INTERVAL)");
    }

    @Test
    public void wordsAndElementTypes() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FN VALUES (1, TRUE), (2, NULL)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
        engine.execute(
            "INSERT INTO FY VALUES (1)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'd'.",
            "DESCRIBE TABLE t1 (a.b.c.d)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'd'. | syntax error "
                + "line 1 at position 26 unexpected '.'. | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (a.b.c.d.e)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "DESCRIBE TABLE t1 (ARRAY x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "DESCRIBE TABLE t1 (OBJECT x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (a(MAP))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (a(ARRAY(1)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'TRUE'.",
            "DESCRIBE TABLE t1 (ARRAY(TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'IF'.",
            "DESCRIBE TABLE t1 (ARRAY(IF))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(TRUE INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(IF INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (OBJECT(a INT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (MAP(INT, INT NULL))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(BIGINT, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(FLOAT4, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(REAL, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TABLE(a.b.c))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'.",
            "BEGIN DESCRIBE TABLE t1 (MAP); RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (VECTOR); SELECT 1");
    }
}
