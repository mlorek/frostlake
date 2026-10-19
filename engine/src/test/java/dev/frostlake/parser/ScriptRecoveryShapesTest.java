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
 * Script recovery in the shapes that were left: a word after a DESCRIBE's or a SHOW's name before a later
 * statement, a block after an operator fault, a statement keyword run in, INSERT and RLIKE as call names, a
 * loop's body (live-verified). Every text is a syntax error, so none of it runs.
 */
public class ScriptRecoveryShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void aWordAfterADescribesOrAShowsName() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax "
                    + "error line 1 at position 31 unexpected 'y'.",
                "DESCRIBE TABLE t x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax "
                    + "error line 1 at position 31 unexpected 'y'.",
                "DESCRIBE STAGE s x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'y'.",
                "DESCRIBE TABLE t x y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'. | syntax "
                    + "error line 1 at position 30 unexpected 'y'.",
                "DESCRIBE VIEW v x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'. | syntax "
                    + "error line 1 at position 37 unexpected 'y'.",
                "DESC TABLE t x; SELECT 2; SELECT 3 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax "
                    + "error line 1 at position 34 unexpected 'y'.",
                "DESCRIBE TABLE t TYPE; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'DESC'. | syntax "
                    + "error line 1 at position 21 unexpected 'y'.",
                "DESC t x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "DESCRIBE SCHEMA s x; SELECT 1 x y");
            answer(
                "SELECT 1 +; DESCRIBE TABLE t x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax "
                    + "error line 1 at position 22 unexpected ';'. | syntax error line 1 at position 35 unexpected "
                    + "'y'.",
                "DESCRIBE TABLE t x y z; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax "
                    + "error line 1 at position 34 unexpected 'y'.",
                "DESCRIBE DATABASE d x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax "
                    + "error line 1 at position 41 unexpected 'y'.",
                "DESCRIBE TABLE t x; SELECT 2; SELECT 3 x y; SELECT 4 z w");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'. | syntax "
                    + "error line 1 at position 34 unexpected 'y'.",
                "DESCRIBE FUNCTION f x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW TABLES IN t x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW TABLES IN t x y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
                "SHOW SCHEMAS IN d x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW TABLES IN t x; SELECT 2; SELECT 3 x y");
            answer(
                "SELECT 1 +; SHOW TABLES IN t x; SELECT 1 x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void laterBlocksAndKeywordsRunIn() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            answer(
                "SELECT 1 +; BEGIN SELECT 1 x y; END");
            answer(
                "SELECT 1 +; BEGIN SELECT 1");
            answer(
                "SELECT 1 +; DESC TABLE t x y");
            answer(
                "SELECT 1 +; DECLARE");
            answer(
                "SELECT 1 +; DESC TABLE t x");
            answer(
                "SELECT 1 +; SELECT 1 FROM t DROP TABLE t; SELECT 1 x y");
            answer(
                "SELECT 1 FROM t WHERE a = 1 DROP TABLE t x y; SELECT 1 x y");
            answer(
                "SELECT 1 FROM t WHERE a = 1 MERGE INTO t USING n ON t.a = n.a WHEN MATCHED THEN DELETE; SELECT 1 "
                    + "x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FROM'.",
                "BEGIN SELECT 'foo', INSERT FROM t; END");
            answer(
                "SELECT 'foo', INSERT INTO t VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'ON'. | syntax "
                    + "error line 1 at position 13 unexpected 'ON'. | syntax error line 1 at position 18 unexpected "
                    + "'x'.",
                "SELECT RLIKE ON t x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                    + "error line 1 at position 25 unexpected 't'. | syntax error line 1 at position 26 unexpected "
                    + "')'.",
                "SELECT 'foo', INSERT ON (t) x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ON'. | syntax "
                    + "error line 1 at position 20 unexpected 'ON'. | syntax error line 1 at position 25 unexpected "
                    + "'x'.",
                "SELECT 'foo', RLIKE ON t x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void loopBodiesAndOthers() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            answer(
                "BEGIN WHILE (FALSE) LOOP SELECT 'foo' RETURN 1; END LOOP; END");
            answer(
                "BEGIN WHILE (FALSE) LOOP LET a := 1 CREATE OR REPLACE TABLE u (a INT); END LOOP; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'. | syntax "
                    + "error line 1 at position 27 unexpected 'UNTIL'. | syntax error line 1 at position 33 "
                    + "unexpected '('.",
                "BEGIN REPEAT SELECT 1 x y; UNTIL (TRUE) END REPEAT; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 60 unexpected 'w'.",
                "BEGIN WHILE (FALSE) LOOP SELECT 1 x y; END LOOP; SELECT 2 z w; END");
            answer(
                "BEGIN WHILE (FALSE) DO SELECT 'foo' RETURN 1; END WHILE; END");
            answer(
                "BEGIN WHILE (FALSE) DO LET a := 1 CREATE OR REPLACE TABLE u (a INT); END WHILE; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'y'. | syntax "
                    + "error line 1 at position 58 unexpected 'IF'. | syntax error line 1 at position 72 unexpected "
                    + "'END'.",
                "BEGIN WHILE (FALSE) LOOP IF (TRUE) THEN SELECT 1 x y; END IF; END LOOP; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 1 DO SELECT 1 x y; END FOR; SELECT 2 z w; END");
            answer(
                "CREATE TABLE IF NOT; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'. | syntax "
                    + "error line 1 at position 27 unexpected 'y'.",
                "DROP TABLE t .; SELECT 1 x y");
            answer(
                "SELECT 1 FROM t WHERE a LIKE ANY ('x') ('y', 'z')");
            answer(
                "SELECT a IS TRUE ('x') FROM t");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void whetherTheNameMatters() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE abc (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'DESC'. | syntax "
                    + "error line 1 at position 21 unexpected 'y'.",
                "DESC t x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'.",
                "DESC abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'.",
                "DESC s x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
                "DESC abc x y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'.",
                "DESCRIBE abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'. | syntax "
                    + "error line 1 at position 33 unexpected 'y'.",
                "DESCRIBE TABLE abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax "
                    + "error line 1 at position 35 unexpected 'y'.",
                "DESCRIBE TABLE abc x y; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax "
                    + "error line 1 at position 24 unexpected ';'. | syntax error line 1 at position 37 unexpected "
                    + "'y'.",
                "DESCRIBE TABLE abc x y z; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "DESCRIBE VIEW abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'.",
                "SHOW TABLES IN abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
                "SHOW TABLES IN ts x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
                "SHOW TABLES IN fn x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'.",
                "SHOW SCHEMAS IN abc x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "DROP TABLE abc .; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'.",
                "DROP TABLE abc . x; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "DROP VIEW abc .; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'. | syntax "
                    + "error line 1 at position 25 unexpected 'y'.",
                "LIST @abc zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                    + "error line 1 at position 38 unexpected 'y'.",
                "DELETE FROM abc USING t 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                    + "error line 1 at position 38 unexpected 'y'.",
                "DELETE FROM t USING abc 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '-'. | syntax "
                    + "error line 1 at position 30 unexpected 'y'.",
                "USE SCHEMA abc -1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ';'. | syntax "
                    + "error line 1 at position 22 unexpected 'y'.",
                "BEGIN abc; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'abc'. | syntax "
                    + "error line 1 at position 38 unexpected 'y'.",
                "EXECUTE IMMEDIATE 'x' abc; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'zz'. | syntax "
                    + "error line 1 at position 47 unexpected 'y'.",
                "SELECT 1 FROM abc GROUP BY a, a zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
                "SELECT 1 FROM abc SAMPLE (10) zz; SELECT 1 x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }
}
