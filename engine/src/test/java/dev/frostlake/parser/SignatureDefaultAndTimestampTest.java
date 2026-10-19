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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DROP's and a DESCRIBE's signature, the items that were left: DEFAULT heading an item reads as a field's
 * name does and wants a type after it, the word TIMESTAMP names a '.' after it twice in a DESCRIBE, an ARRAY's
 * and an OBJECT's parentheses are not theirs before a SELECT, DEFAULT names a field and BOOLEAN a VECTOR's
 * element (live-verified). No DROP here names a table that exists.
 */
public class SignatureDefaultAndTimestampTest extends MeasuredCellsTestSupport {

    @Test
    public void defaultAsAnItem() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "DESCRIBE TABLE t1 (DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "DESCRIBE TABLE t1 (DEFAULT 1)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DEFAULT x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','.",
            "DESCRIBE TABLE t1 (DEFAULT, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "DESCRIBE TABLE t1 (INT, DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'DEFAULT'.",
            "DESCRIBE TABLE t1 (DEFAULT DEFAULT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DEFAULT INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (DEFAULT (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '='.",
            "DESCRIBE TABLE t1 (DEFAULT = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'.",
            "DESCRIBE TABLE t1 (DEFAULT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'DEFAULT'.",
            "DESCRIBE TABLE t1 (x DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'DEFAULT'. | syntax "
                + "error line 1 at position 31 unexpected '1'. | syntax error line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (INT DEFAULT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (DEFAULT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''a''.",
            "DESCRIBE TABLE t1 (DEFAULT 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TRUE'.",
            "DESCRIBE TABLE t1 (DEFAULT TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "DESCRIBE TABLE t1 (DEFAULT x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "DESCRIBE TABLE t1 (DEFAULT 1, INT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (DEFAULT INT, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (DEFAULT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT 1)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (INT, DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'DEFAULT'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT DEFAULT)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '='.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT = 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'DEFAULT'.",
            "DROP TABLE IF EXISTS nosuch_x (x DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'DEFAULT'.",
            "DROP TABLE IF EXISTS nosuch_x (INT DEFAULT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'NULL'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ''a''.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'TRUE'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT 1, INT)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT INT, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT))");
    }

    @Test
    public void aTypesWordHeadingAPath() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'. | syntax error line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '.'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIME.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '.'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 (DATE.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'. | syntax error line 1 at position 30 unexpected '.'. | "
                + "syntax error line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP.a.b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '.'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP_NTZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '.'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP_LTZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '.'. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP_TZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '.'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 (DATETIME.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'. | syntax error line 1 at position 30 unexpected ','. | "
                + "syntax error line 1 at position 35 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP.a, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '.'. | syntax error "
                + "line 1 at position 33 unexpected '.'. | syntax error line 1 at position 35 unexpected ')'.",
            "DESCRIBE TABLE t1 (INT, TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'a'.",
            "DESCRIBE TABLE t1 (TIMESTAMP a)");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '.'. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "DESCRIBE TABLE t1 (TIMESTAMP(3).a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'.",
            "DESCRIBE TABLE t1 (TIMESTAMP.)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '.'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "DESCRIBE TABLE t1 (INT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (VARCHAR.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '.'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (NUMBER.a)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (a.b)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (a.b.c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'd'.",
            "DESCRIBE TABLE t1 (a.b.c.d)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'd'. | syntax error "
                + "line 1 at position 26 unexpected '.'. | syntax error line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (a.b.c.d.e)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'd'.",
            "DESCRIBE TABLE t1 (TABLE(a.b.c.d.e))");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP WITH)");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (TIMESTAMP WITH LOCAL TIME ZONE)");
        answer(
            "DESCRIBE TABLE t1 (TIME WITH TIME ZONE)");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE)");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP WITHOUT TIME ZONE)");
        answer(
            "DESCRIBE TABLE t1 (TIMESTAMP WITH TIME ZONE, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIME.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (DATE.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP.a.b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP_NTZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP_LTZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP_TZ.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (DATETIME.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP.a, INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (INT, TIMESTAMP.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'a'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP(3).a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP.)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (INT.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (VARCHAR.a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '.'.",
            "DROP TABLE IF EXISTS nosuch_x (NUMBER.a)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (a.b)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (a.b.c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'd'.",
            "DROP TABLE IF EXISTS nosuch_x (a.b.c.d)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'd'.",
            "DROP TABLE IF EXISTS nosuch_x (a.b.c.d.e)");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TABLE(a.b.c.d.e))");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH)");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH TIME)");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_X already dropped).",
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH LOCAL TIME ZONE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'WITH'.",
            "DROP TABLE IF EXISTS nosuch_x (TIME WITH TIME ZONE)");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH TIME ZONE)");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITHOUT TIME ZONE)");
        answer(
            "DROP TABLE IF EXISTS nosuch_x (TIMESTAMP WITH TIME ZONE, INT)");
    }

    @Test
    public void fieldsElementsAndBrackets() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(DEFAULT INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'NULL'.",
            "DESCRIBE TABLE t1 (OBJECT(NULL INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(SELECT INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "DESCRIBE TABLE t1 (OBJECT(1 INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(\"a\" INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax error "
                + "line 1 at position 35 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(TABLE INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(TRUE INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (OBJECT(IF INT))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(SMALLINT, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(TINYINT, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(BYTEINT, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(FLOAT8, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'DECIMAL'.",
            "DESCRIBE TABLE t1 (VECTOR(DECIMAL, 3))");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t1 (VECTOR(BOOLEAN, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('.",
            "DESCRIBE TABLE t1 (ARRAY(SELECT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'NULL'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "DESCRIBE TABLE t1 (OBJECT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'DEFAULT'.",
            "DESCRIBE TABLE t1 (ARRAY(DEFAULT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ''a''. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY('a'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(TABLE(a)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "DESCRIBE TABLE t1 (ARRAY(1))");
    }

    @Test
    public void otherStatements() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE VIEW t1 (DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'. | syntax error "
                + "line 1 at position 40 unexpected 'y'.",
            "DESCRIBE TABLE t1 (DEFAULT); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '.'. | syntax error "
                + "line 1 at position 28 unexpected '.'. | syntax error line 1 at position 30 unexpected ')'. | "
                + "syntax error line 1 at position 44 unexpected 'y'.",
            "DESCRIBE TABLE t1 (TIMESTAMP.a); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'.",
            "DROP TABLE IF EXISTS nosuch_x (DEFAULT) CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'.",
            "DROP VIEW IF EXISTS nosuch_x (DEFAULT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '.'.",
            "DROP VIEW IF EXISTS nosuch_x (TIMESTAMP.a)");
    }
}
