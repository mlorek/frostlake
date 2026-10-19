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
 * A literal or a word run in after a script's first statement, in the shapes the account reports in a way of
 * its own: a BEGIN, a COMMIT, a DELETE's USING, a DESCRIBE, a staged-file command, the tail of a query
 * (live-verified). Every text ends in a statement that does not parse, so none of it runs.
 */
public class RunInStatementLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void literalsAfterAStatement() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '1'. | syntax "
                    + "error line 1 at position 20 unexpected 'y'.",
                "BEGIN 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected ''q''. | syntax "
                    + "error line 1 at position 22 unexpected 'y'.",
                "BEGIN 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '.'.",
                "SELECT 1 INTO t.u 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'. | syntax "
                    + "error line 1 at position 36 unexpected 'y'.",
                "DELETE FROM t USING n 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''q''. | syntax "
                    + "error line 1 at position 38 unexpected 'y'.",
                "DELETE FROM t USING n 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
                "SELECT ABS(1) 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
                "SELECT a FROM t FOR UPDATE 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''q''.",
                "SELECT a FROM t FOR UPDATE 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'NULL'.",
                "COMMIT NULL; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '-'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "USE SCHEMA s -1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "USE SCHEMA s 1 2; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax "
                    + "error line 1 at position 44 unexpected 'y'.",
                "SELECT 1 FROM t WHERE a = 1 2 3; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "DROP TABLE t 1 2; SELECT 1 x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void wordsAfterAStatement() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "DESCRIBE TABLE t zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "DESC TABLE t zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax "
                    + "error line 1 at position 31 unexpected 'y'.",
                "DESCRIBE VIEW v zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected ';'. | syntax "
                    + "error line 1 at position 21 unexpected 'y'.",
                "BEGIN zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ';'. | syntax "
                    + "error line 1 at position 35 unexpected 'y'.",
                "COPY INTO t FROM @s zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                    + "error line 1 at position 23 unexpected 'y'.",
                "LIST @s zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'. | syntax "
                    + "error line 1 at position 25 unexpected 'y'.",
                "REMOVE @s zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'zz'. | syntax "
                    + "error line 1 at position 37 unexpected 'y'.",
                "EXECUTE IMMEDIATE 'x' zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'zz'.",
                "SELECT a FROM t FETCH FIRST 1 ROWS ONLY zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'zz'.",
                "SELECT 1 FROM t JOIN n USING (a) zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'zz'.",
                "SELECT 1 FROM t SAMPLE (10) zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'. | syntax "
                    + "error line 1 at position 45 unexpected 'y'.",
                "SELECT 1 FROM t GROUP BY a, b zz; SELECT 1 x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }

    @Test
    public void theSameShapesWider() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
            answer(
                "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '1'. | syntax "
                    + "error line 1 at position 22 unexpected 'y'.",
                "BEGIN 1 2; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'.",
                "BEGIN NULL; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'TRUE'. | syntax "
                    + "error line 1 at position 23 unexpected 'y'.",
                "BEGIN TRUE; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '-'. | syntax "
                    + "error line 1 at position 21 unexpected 'y'.",
                "BEGIN -1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '1'.",
                "COMMIT 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ''q''.",
                "COMMIT 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'zz'.",
                "COMMIT zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'NULL'.",
                "ROLLBACK NULL; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '1'.",
                "ROLLBACK 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
                "DELETE FROM t USING n zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax "
                    + "error line 1 at position 38 unexpected 'y'.",
                "DELETE FROM t USING n m 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                    + "error line 1 at position 14 unexpected '1'.",
                "DELETE FROM t 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "SELECT ABS(1) 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'.",
                "SELECT a 'q' FROM t; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ''q''. | syntax "
                    + "error line 1 at position 34 unexpected 'y'.",
                "SELECT t.a 'q' FROM t; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ''q''. | syntax "
                    + "error line 1 at position 25 unexpected 'y'.",
                "SELECT 1 'q'; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '.'.",
                "SELECT 1 INTO t.u; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '.'.",
                "SELECT 1 INTO t.u zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                    + "error line 1 at position 27 unexpected 'y'.",
                "USE SCHEMA s 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'.",
                "USE SCHEMA s zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'. | syntax "
                    + "error line 1 at position 42 unexpected 'y'.",
                "SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'zz'.",
                "SELECT 1 FROM t WHERE a = 1 zz ww; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '1'. | syntax "
                    + "error line 1 at position 27 unexpected 'y'.",
                "DROP TABLE t 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'zz'. | syntax "
                    + "error line 1 at position 31 unexpected 'y'.",
                "DROP TABLE t zz ww; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ww'. | syntax "
                    + "error line 1 at position 35 unexpected 'y'.",
                "DESCRIBE TABLE t zz ww; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax "
                    + "error line 1 at position 31 unexpected 'y'.",
                "DESCRIBE TABLE t 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'ww'. | syntax "
                    + "error line 1 at position 26 unexpected 'y'.",
                "LIST @s zz ww; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'.",
                "LIST @s 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'.",
                "SELECT a FROM t FETCH FIRST 1 ROWS ONLY 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'.",
                "SELECT 1 FROM t JOIN n USING (a) 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'.",
                "SELECT 1 FROM t SAMPLE (10) 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                    + "error line 1 at position 44 unexpected 'y'.",
                "SELECT 1 FROM t GROUP BY a, b 1; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'zz'.",
                "SELECT 1 FROM t ORDER BY a zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'zz'.",
                "SELECT 1 FROM t LIMIT 1 zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'zz'.",
                "SELECT 1 FROM t HAVING 1 = 1 zz; SELECT 1 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'zz'.",
                "SELECT 1 FROM t QUALIFY 1 = 1 zz; SELECT 1 x y");
        } finally {
            answer(
                "ROLLBACK");
        }
    }
}
