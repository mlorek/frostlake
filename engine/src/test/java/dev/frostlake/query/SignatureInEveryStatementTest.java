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
 * A DROP's or a DESCRIBE's signature is judged wherever the statement stands: beside another statement of
 * the request, in a block and in the text of EXECUTE IMMEDIATE. A keyword as an item is refused there too, so
 * the DROP drops nothing, and the statement reports the lines it reports standing alone - in a block the word
 * refused is read on as the next statement's first. Every answer is live-verified.
 */
public class SignatureInEveryStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void besideAnotherStatementAndInABlock() {
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE VIEW vv AS SELECT 1 AS c");
        engine.execute(
            "CREATE OR REPLACE TABLE t3 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t5 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t7 (x INT)");
        engine.execute(
            "CREATE OR REPLACE TABLE t8 (x INT)");
        engine.execute(
            "DROP TABLE IF EXISTS t6");
        engine.execute(
            "DROP PROCEDURE IF EXISTS p1()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VIEW'.",
            "DROP TABLE t5 (VIEW); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'VIEW'.",
            "SELECT 1; DROP TABLE t8 (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TYPE'. | syntax "
                + "error line 1 at position 23 unexpected ')'.",
            "DESCRIBE TABLE t1 (TYPE); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'.",
            "DESCRIBE TABLE t1 ((a), b); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '('. | syntax error "
                + "line 1 at position 20 unexpected ')'. | syntax error line 1 at position 35 unexpected 'y'.",
            "DESCRIBE TABLE t ((a)); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 17 unexpected 'VIEW'. | syntax "
                + "error line 2 at position 21 unexpected ')'.",
            "BEGIN\n  DROP TABLE t3 (VIEW);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 21 unexpected 'TYPE'. | syntax "
                + "error line 3 at position 25 unexpected ')'.",
            "BEGIN\n  CREATE TABLE t6 (x INT);\n  DESCRIBE TABLE t1 (TYPE);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 21 unexpected '('. | syntax error "
                + "line 2 at position 23 unexpected ')'. | syntax error line 2 at position 27 unexpected ')'.",
            "BEGIN\n  DESCRIBE TABLE t1 ((a), b);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 23 unexpected 'TRUE'. | syntax "
                + "error line 3 at position 29 unexpected '1'. | syntax error line 3 at position 31 unexpected ')'.",
            "EXECUTE IMMEDIATE $$\nBEGIN\n  DESCRIBE TABLE t1 (a(TRUE, 1));\nEND;\n$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VIEW'.",
            "DROP TABLE t1 (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VIEW'.",
            "EXECUTE IMMEDIATE 'DROP TABLE t1 (VIEW)'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '('. | syntax error "
                + "line 1 at position 21 unexpected ')'. | syntax error line 1 at position 25 unexpected ')'.",
            "EXECUTE IMMEDIATE 'DESCRIBE TABLE t1 ((a), b)'");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t3");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t5");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'T6' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE T6.",
            "DESCRIBE TABLE t6");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t7");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t8");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('. | syntax error "
                + "line 1 at position 31 unexpected ')'. | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT 1; DESCRIBE TABLE t1 ((a), b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TYPE'. | syntax "
                + "error line 1 at position 33 unexpected ')'.",
            "SELECT 1; DESCRIBE TABLE t1 (TYPE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'VIEW'.",
            "SELECT 1; SELECT 2; DROP TABLE t8 (VIEW)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'b'.",
            "DROP TABLE t5 (a b); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'.",
            "DROP TABLE t5 (VECTOR); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 23 unexpected ')'.",
            "BEGIN\n  DROP TABLE t3 (VECTOR);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 19 unexpected 'b'.",
            "BEGIN\n  DROP TABLE t3 (a b);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 3 at position 21 unexpected 'TYPE'. | syntax "
                + "error line 3 at position 25 unexpected ')'.",
            "BEGIN\n  LET n := 1;\n  DESCRIBE TABLE t1 (TYPE);\n  RETURN n;\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 17 unexpected 'IF'. | syntax error "
                + "line 4 at position 19 unexpected ')'.",
            "DECLARE\n  n INT;\nBEGIN\n  DROP TABLE t3 (IF);\nEND;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'IF'.",
            "DROP TABLE t5 (IF); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ')'.",
            "DROP TABLE t5 (TABLE); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'TYPE'.",
            "DROP VIEW vv (TYPE); SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'VIEW'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "DESCRIBE VIEW vv (VIEW); SELECT 1");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t3");
        assertAnswer(
            "X|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE t5");
        assertAnswer(
            "C|NUMBER(1,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW vv");
    }
}
