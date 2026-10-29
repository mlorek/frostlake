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
 * A statement of a block refused at a word, its semicolon following: the statement is given up together with
 * the construct whose statement it is - the block, or the body of the IF, CASE, FOR, WHILE, LOOP or REPEAT -
 * and the rest is read from the construct around it. Outside every block a SQL statement and a whole block
 * read on, and a scripting statement, a VALUES, a stray END and a branch keyword are refused at their first
 * word; a block's END takes one word after it, IF, CASE, WHILE or LOOP but not FOR; a branch keyword refused
 * while a block is still open stacks the token after the first name read from it on, and an END followed by
 * another construct's word gives that construct up too. Every answer is live-verified.
 */
public class BlockReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void aShowWithAnInstanceReadsOn() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'z'.",
                "BEGIN SHOW TABLES IN nosuch x y z; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; CALL p(); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN nosuch x y; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 59 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; INSERT INTO t VALUES (1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 31 unexpected '.'.",
                "BEGIN SHOW TABLES IN nosuch x y.z; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'LET'.",
                "BEGIN SHOW TABLES IN nosuch x y; LET b := 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 28 unexpected 'END'.",
                "BEGIN SELECT 1 FROM t1 x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 38 unexpected 'END'.",
                "BEGIN SELECT 1 FROM t1 x y; SELECT 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 56 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 56 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x y; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 48 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN nosuch x y; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'.",
                "BEGIN SHOW TABLES IN nosuch x y END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; SELECT 1; SELECT 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN DATASET y; SELECT 1; RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 39 unexpected 'END'.",
                "BEGIN SHOW TABLES IN USER x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN USER x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "BEGIN SHOW TABLES IN USER x;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
                "BEGIN SHOW TABLES IN DATASET y;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
                "BEGIN SHOW TABLES IN DATASET y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; END; SELECT 1;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'VIEW'. | syntax "
                    + "error line 1 at position 39 unexpected 'y'.",
                "BEGIN SHOW TABLES IN MATERIALIZED VIEW y z; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'VIEW'. | syntax "
                    + "error line 1 at position 40 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN MATERIALIZED VIEW; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; BEGIN RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'IF'.",
                "BEGIN SHOW TABLES IN DATASET y; IF (TRUE) THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 68 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; DECLARE c INT; BEGIN RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 45 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; SHOW TABLES; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "BEGIN SHOW TABLES IN DATASET y; y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; UPDATE t1 SET id = 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 46 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; SHOW TABLES; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; DELETE FROM t1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 57 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; CREATE TABLE q (a INT); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; SELECT 1; SELECT 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN nosuch x y; SELECT 1; RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; SELECT 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'END'.",
                "BEGIN BEGIN BEGIN SHOW TABLES IN nosuch x y; END; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; END; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'RETURN'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'. | syntax "
                    + "error line 1 at position 46 unexpected 'END'.",
                "BEGIN SHOW FUNCTIONS IN nosuch x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'IF'.",
                "BEGIN SHOW TABLES IN nosuch x y; IF (TRUE) THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 41 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; COMMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'END'.",
                "BEGIN BEGIN SELECT 1 FROM t1 x y; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; SELECT 1; END; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void everyStatementKindReadsOn() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'. | syntax "
                    + "error line 1 at position 65 unexpected 'RETURN'.",
                "BEGIN BEGIN BEGIN SHOW TABLES IN nosuch x y; END; RETURN 1; END; RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'RETURN'.",
                "BEGIN BEGIN SELECT 1 FROM t1 x y; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'END'.",
                "BEGIN BEGIN SELECT 1 FROM t1 x y; RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'RETURN'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; END; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'y'. | syntax "
                    + "error line 1 at position 79 unexpected 'END'.",
                "BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN nosuch x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'y'. | syntax "
                    + "error line 1 at position 80 unexpected 'END'.",
                "BEGIN BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN nosuch x y; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'y'. | syntax "
                    + "error line 1 at position 59 unexpected 'END'.",
                "BEGIN DECLARE a INT; BEGIN SHOW TABLES IN nosuch x y; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; BEGIN RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'EXCEPTION'.",
                "BEGIN SHOW TABLES IN nosuch x y; EXCEPTION WHEN OTHER THEN RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'BREAK'.",
                "BEGIN SHOW TABLES IN nosuch x y; BREAK; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'y'.",
                "BEGIN SHOW TABLES IN nosuch x y; y := 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected '1'.",
                "BEGIN SHOW TABLES IN nosuch x y; SELECT 1 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; TRUNCATE TABLE t1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 63 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; EXECUTE IMMEDIATE 'SELECT 1'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; USE SCHEMA s; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; SET v = 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; DROP TABLE q; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 92 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; MERGE INTO t1 USING t1 u ON TRUE WHEN MATCHED THEN DELETE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 71 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; WITH w AS (SELECT 1) SELECT * FROM w; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'VALUES'.",
                "BEGIN SHOW TABLES IN nosuch x y; VALUES (1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 87 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; GRANT USAGE ON SCHEMA nosuch_s TO ROLE nosuch_role_x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 66 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; ALTER TABLE t1 ADD COLUMN z INT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; DESCRIBE TABLE t1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; ROLLBACK; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'NULL'.",
                "BEGIN SHOW TABLES IN nosuch x y; NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'OPEN'.",
                "BEGIN SHOW TABLES IN nosuch x y; OPEN c; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'RAISE'.",
                "BEGIN SHOW TABLES IN nosuch x y; RAISE e; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'FOR'.",
                "BEGIN SHOW TABLES IN nosuch x y; FOR i IN 1 TO 2 DO RETURN 1; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'LOOP'.",
                "BEGIN SHOW TABLES IN nosuch x y; LOOP BREAK; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'CASE'.",
                "BEGIN SHOW TABLES IN nosuch x y; CASE WHEN TRUE THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 51 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; EXPLAIN SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; COPY INTO t1 FROM @s; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 50 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; UNDROP TABLE t1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 61 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; COMMENT ON TABLE t1 IS 'x'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax "
                    + "error line 1 at position 33 unexpected 'END'.",
                "BEGIN DROP TABLE t1 x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax "
                    + "error line 1 at position 45 unexpected 'END'.",
                "BEGIN INSERT INTO t1 VALUES (1) x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'. | syntax "
                    + "error line 1 at position 36 unexpected 'END'.",
                "BEGIN DELETE FROM t1 x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'END'.",
                "BEGIN CALL p() x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                    + "error line 1 at position 31 unexpected 'END'.",
                "BEGIN SHOW TABLES x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'b'. | syntax "
                    + "error line 1 at position 32 unexpected 'END'.",
                "BEGIN USE SCHEMA a b; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax "
                    + "error line 1 at position 37 unexpected 'END'.",
                "BEGIN TRUNCATE TABLE t1 x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "BEGIN UPDATE t1 SET id = 1 x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                    + "error line 1 at position 21 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax "
                    + "error line 1 at position 23 unexpected 'RETURN'.",
                "BEGIN DROP TABLE t1 x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax "
                    + "error line 1 at position 40 unexpected 'END'.",
                "BEGIN SHOW TABLES LIKE 'a' x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN SHOW SCHEMAS IN nosuch x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW VIEWS IN nosuch x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN SHOW TABLES IN SCHEMA s x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax "
                    + "error line 1 at position 45 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATABASE d x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN SHOW TABLES IN ACCOUNT x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'. | syntax "
                    + "error line 1 at position 40 unexpected 'END'.",
                "BEGIN SHOW TABLES IN a.b x y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; SELECT 1; END; SELECT 2;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 28 unexpected 'RETURN'.",
                "BEGIN SELECT 1 FROM t1 x y; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 28 unexpected 'LET'.",
                "BEGIN SELECT 1 FROM t1 x y; LET a := 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'END'.",
                "BEGIN BEGIN SELECT 1 FROM t1 x y; SELECT 2; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'. | syntax "
                    + "error line 1 at position 50 unexpected 'END'.",
                "BEGIN BEGIN BEGIN SELECT 1 FROM t1 x y; END; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'END'.",
                "BEGIN BEGIN SELECT 1 FROM t1 x y; END; SELECT 1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void afterAKeywordScope() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'VIEW'. | syntax "
                    + "error line 1 at position 39 unexpected 'y'.",
                "BEGIN SHOW TABLES IN MATERIALIZED VIEW y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN DATASET y; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "BEGIN SHOW FUNCTIONS IN OWNER x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y z; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'END'.",
                "BEGIN SHOW TABLES IN USER x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 27 unexpected '.'.",
                "BEGIN SHOW TABLES IN USER x.y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN LET a := 1; SHOW TABLES IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'EXCEPTION'.",
                "BEGIN SHOW TABLES IN DATASET y; EXCEPTION WHEN OTHER THEN RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'y'. | syntax "
                    + "error line 1 at position 68 unexpected 'END'.",
                "BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "DECLARE a INT; BEGIN SHOW TABLES IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
                "BEGIN SHOW TABLES IN DATASET y END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
                "BEGIN SHOW TABLES IN USER LIMIT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '\"y\"'. | syntax "
                    + "error line 1 at position 34 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET \"y\"; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 31 unexpected 'LIMIT'.",
                "BEGIN SHOW TABLES IN DATASET y LIMIT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 31 unexpected 'z'.",
                "BEGIN SHOW TABLES IN DATASET y z; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax "
                    + "error line 1 at position 34 unexpected 'END'.",
                "BEGIN SHOW TABLES IN FLATTEN x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "BEGIN SHOW TABLES IN MODEL x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'. | syntax "
                    + "error line 1 at position 35 unexpected 'END'.",
                "BEGIN SHOW FUNCTIONS IN MODEL x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'a'. | syntax "
                    + "error line 1 at position 30 unexpected '.'.",
                "BEGIN SHOW TABLES IN DATASET a.b; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'. | "
                    + "syntax error line 1 at position 39 unexpected '('.",
                "BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'LET'.",
                "BEGIN SHOW TABLES IN DATASET y; LET b := 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'END'.",
                "EXECUTE IMMEDIATE $$BEGIN SHOW TABLES IN DATASET y; END;$$");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'VIEW'. | syntax "
                    + "error line 1 at position 40 unexpected 'END'.",
                "BEGIN SHOW TABLES IN MATERIALIZED VIEW; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; CALL p(); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'BREAK'.",
                "BEGIN SHOW TABLES IN DATASET y; BREAK; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 58 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; INSERT INTO t VALUES (1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'y'.",
                "BEGIN SHOW TABLES IN DATASET y; y := 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN DATASET y; RETURN :v + 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 2 at position 2 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y\n; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 2 at position 25 unexpected 'y'. | syntax "
                    + "error line 3 at position 0 unexpected 'END'.",
                "BEGIN\n  SHOW TABLES IN DATASET y;\nEND;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN DATASET y; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected '\"y\"'.",
                "BEGIN SHOW TABLES IN USER x \"y\"; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'. | syntax "
                    + "error line 1 at position 41 unexpected 'END'.",
                "BEGIN SHOW TABLES LIKE 'a' IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 38 unexpected 'END'.",
                "BEGIN SHOW TERSE TABLES IN DATASET y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 31 unexpected 'STARTS'.",
                "BEGIN SHOW TABLES IN DATASET y STARTS WITH 'a'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET y; END");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATE x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'y'.",
                "BEGIN SHOW SCHEMAS IN USER x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 57 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y z; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax "
                    + "error line 1 at position 52 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN USER x; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 58 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x y z; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 59 unexpected 'END'.",
                "BEGIN WHILE (TRUE) DO SHOW TABLES IN DATASET y; END WHILE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'UNTIL'. | syntax error line 1 at position 45 "
                    + "unexpected '('.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 62 unexpected 'END'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'x'. | syntax "
                    + "error line 1 at position 54 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN USER x y; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax "
                    + "error line 1 at position 46 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN USER x y; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'. | syntax "
                    + "error line 1 at position 64 unexpected 'y'.",
                "BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN USER x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'y'. | syntax "
                    + "error line 1 at position 65 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SELECT 1; SHOW TABLES IN DATASET y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 65 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 41 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET y; SELECT 1 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN DATASET y; RETURN 1 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'y'.",
                "BEGIN SHOW TABLES IN USER x y; RETURN 1 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 29 unexpected 'RETURN'.",
                "BEGIN SHOW TABLES IN DATE x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'.",
                "BEGIN LET r RESULTSET := (SHOW TABLES IN DATASET y); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'y'. | syntax "
                    + "error line 1 at position 84 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN RETURN 1; ELSEIF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSEIF'. | syntax error line 1 at position 54 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSEIF (TRUE) THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'WHEN'. | syntax error line 1 at position 57 "
                    + "unexpected 'FALSE'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; WHEN FALSE THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'ELSE'. | syntax error line 1 at position 64 "
                    + "unexpected '1'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; ELSE RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 69 unexpected 'END'.",
                "BEGIN WHILE (TRUE) DO SHOW TABLES IN DATASET y; RETURN 1; END WHILE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax "
                    + "error line 1 at position 32 unexpected 'EXCEPTION'.",
                "BEGIN SHOW TABLES IN DATASET y; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 1; WHEN OTHER THEN "
                    + "RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax "
                    + "error line 1 at position 54 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN DATASET y; BREAK; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'UNTIL'. | syntax error line 1 at position 55 "
                    + "unexpected '('.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; RETURN 1; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 56 "
                    + "unexpected 'a'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE LET a := 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN 1; RETURN 2; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'UNTIL'. | syntax error line 1 at position 45 "
                    + "unexpected '('.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; UNTIL (1 = 1) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'UNTIL'. | syntax error line 1 at position 45 "
                    + "unexpected 'TRUE'.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; UNTIL TRUE END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''x''. | syntax "
                    + "error line 1 at position 29 unexpected ''x''.",
                "BEGIN SHOW TABLES IN DATASET 'x'; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'. | syntax "
                    + "error line 1 at position 26 unexpected '1'.",
                "BEGIN SHOW TABLES IN USER 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'.",
                "DECLARE c CURSOR FOR SHOW TABLES; BEGIN RETURN 1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void branchKeywordsAndNestedConstructs() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE CALL p(); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE BREAK; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 54 "
                    + "unexpected ':='.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE x := 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 55 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE IF (TRUE) THEN RETURN 1; END IF; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 65 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE BEGIN RETURN 1; END; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE NULL; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 67 "
                    + "unexpected 'VALUES'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE INSERT INTO t1 VALUES (1); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 60 "
                    + "unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 62 "
                    + "unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE CONTINUE; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 54 "
                    + "unexpected 'b'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE a b c; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; RETURN 2; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 57 "
                    + "unexpected 'TABLES'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SHOW TABLES; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN 1 + 2; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN (1); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected 'x'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN x; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 59 "
                    + "unexpected ''a''.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RETURN 'a'; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 56 "
                    + "unexpected 'a'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE LET a INT := 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 58 "
                    + "unexpected 'e'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE RAISE e; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE (SELECT 1); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSEIF'. | syntax error line 1 at position 54 "
                    + "unexpected 'TRUE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSEIF TRUE THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSEIF'. | syntax error line 1 at position 54 "
                    + "unexpected 'x'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSEIF x THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSEIF'. | syntax error line 1 at position 54 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSEIF (TRUE) THEN SELECT 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'WHEN'. | syntax error line 1 at position 57 "
                    + "unexpected 'x'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; WHEN x THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'WHEN'. | syntax error line 1 at position 57 "
                    + "unexpected '('.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; WHEN (1 = 1) THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'ELSE'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'WHEN'. | syntax error line 1 at position 58 "
                    + "unexpected '2'.",
                "BEGIN CASE (1) WHEN 1 THEN SHOW TABLES IN DATASET y; WHEN 2 THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 39 unexpected 'UNTIL'. | syntax error line 1 at position 45 "
                    + "unexpected 'x'.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; UNTIL x END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 49 unexpected 'UNTIL'. | syntax error line 1 at position 55 "
                    + "unexpected '('.",
                "BEGIN REPEAT SHOW TABLES IN DATASET y; SELECT 1; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 57 unexpected 'ELSE'. | syntax error line 1 at position 69 "
                    + "unexpected '2'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; RETURN 1; ELSE RETURN 2; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 57 unexpected 'ELSE'. | syntax error line 1 at position 69 "
                    + "unexpected '2'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; SELECT 1; ELSE RETURN 2; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 59 unexpected 'ELSEIF'. | syntax error line 1 at position 66 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; LET a := 1; ELSEIF (TRUE) THEN RETURN 2; END IF; "
                    + "END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 62 unexpected 'WHEN'. | syntax error line 1 at position 67 "
                    + "unexpected 'FALSE'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN DATASET y; RETURN 1; WHEN FALSE THEN RETURN 2; END "
                    + "CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 104 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; IF (FALSE) THEN RETURN 1; ELSE RETURN 2; END IF; "
                    + "END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 81 unexpected 'ELSE'. | syntax error line 1 at position 93 "
                    + "unexpected '2'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; IF (FALSE) THEN RETURN 1; END IF; ELSE RETURN 2; "
                    + "END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'y'. | syntax "
                    + "error line 1 at position 56 unexpected ';'. | syntax error line 1 at position 66 unexpected "
                    + "'END'.",
                "BEGIN IF (TRUE) THEN BEGIN SHOW TABLES IN DATASET y; END; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'y'. | syntax "
                    + "error line 1 at position 66 unexpected 'END'.",
                "BEGIN BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'RETURN'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 65 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'y'. | syntax "
                    + "error line 1 at position 55 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'y'. | syntax "
                    + "error line 1 at position 65 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; RETURN 1; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 59 unexpected 'RETURN'.",
                "BEGIN WHILE (TRUE) DO SHOW TABLES IN DATASET y; END WHILE; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax "
                    + "error line 1 at position 46 unexpected 'lbl'.",
                "BEGIN LOOP SHOW TABLES IN DATASET y; END LOOP lbl; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'y'. | syntax "
                    + "error line 1 at position 78 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'y'. | syntax "
                    + "error line 1 at position 70 unexpected 'ELSE'. | syntax error line 1 at position 82 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'y'. | syntax "
                    + "error line 1 at position 56 unexpected 'IF'. | syntax error line 1 at position 77 unexpected "
                    + "'END'.",
                "BEGIN LOOP IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; BREAK; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'y'. | syntax "
                    + "error line 1 at position 70 unexpected 'IF'. | syntax error line 1 at position 78 unexpected "
                    + "'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO IF (TRUE) THEN SHOW TABLES IN DATASET y; END IF; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'y'. | syntax "
                    + "error line 1 at position 70 unexpected 'FOR'. | syntax error line 1 at position 83 unexpected "
                    + "'END'.",
                "BEGIN IF (TRUE) THEN FOR i IN 1 TO 2 DO SHOW TABLES IN DATASET y; END FOR; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'ELSE'. | syntax error line 1 at position 55 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SELECT 1 FROM t1 x y; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 48 unexpected 'ELSE'. | syntax error line 1 at position 60 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x y; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'. | syntax "
                    + "error line 1 at position 35 unexpected 'UNTIL'. | syntax error line 1 at position 41 "
                    + "unexpected '('.",
                "BEGIN REPEAT SELECT 1 FROM t1 x y; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'. | syntax "
                    + "error line 1 at position 40 unexpected 'UNTIL'. | syntax error line 1 at position 46 "
                    + "unexpected '('.",
                "BEGIN REPEAT SHOW TABLES IN nosuch x y; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 51 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO SELECT 1 FROM t1 x y; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'. | syntax "
                    + "error line 1 at position 51 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SELECT 1 FROM t1 x y; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'. | syntax "
                    + "error line 1 at position 61 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SELECT 1 FROM t1 x y; RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 48 unexpected 'WHEN'. | syntax error line 1 at position 53 "
                    + "unexpected 'FALSE'.",
                "BEGIN CASE WHEN TRUE THEN SELECT 1 FROM t1 x y; WHEN FALSE THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'y'. | syntax "
                    + "error line 1 at position 53 unexpected 'WHEN'. | syntax error line 1 at position 58 "
                    + "unexpected 'FALSE'.",
                "BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x y; WHEN FALSE THEN RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax "
                    + "error line 1 at position 48 unexpected 'ELSEIF'. | syntax error line 1 at position 55 "
                    + "unexpected '('.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x y; ELSEIF (TRUE) THEN RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax "
                    + "error line 1 at position 44 unexpected 'ELSE'. | syntax error line 1 at position 56 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN USER x; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 37 unexpected '1'.",
                "BEGIN SELECT 1 FROM t1 x y; SELECT 1 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | parse error line 1 at position 23 near '<EOF>'.",
                "BEGIN SELECT 'abc; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.",
                "SELECT 'abc");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
                "SHOW TABLES IN DATASET 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''x''.",
                "SHOW TABLES IN DATASET 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '1'.",
                "SHOW TABLES IN USER 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1.5'. | syntax "
                    + "error line 1 at position 29 unexpected '1.5'.",
                "BEGIN SHOW TABLES IN DATASET 1.5; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ':'. | syntax "
                    + "error line 1 at position 29 unexpected ':'.",
                "BEGIN SHOW TABLES IN DATASET :v; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '$v'. | syntax "
                    + "error line 1 at position 29 unexpected '$v'.",
                "BEGIN SHOW TABLES IN DATASET $v; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TRUE'. | syntax "
                    + "error line 1 at position 35 unexpected 'END'.",
                "BEGIN SHOW TABLES IN DATASET TRUE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'NULL'. | syntax "
                    + "error line 1 at position 29 unexpected 'NULL'.",
                "BEGIN SHOW TABLES IN DATASET NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '-'. | syntax "
                    + "error line 1 at position 29 unexpected '-'.",
                "BEGIN SHOW TABLES IN DATASET -1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1 END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax "
                    + "error line 1 at position 55 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'TABLES'. | "
                    + "syntax error line 1 at position 35 unexpected 'IN'.",
                "BEGIN LET c CURSOR FOR SHOW TABLES IN DATASET y; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TABLE'.",
                "DECLARE c CURSOR FOR DESCRIBE TABLE t1; BEGIN RETURN 1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void otherStatementsAndModifiers() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'FOR'.",
                "BEGIN RETURN 1; END FOR;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'END'.",
                "BEGIN RETURN 1; END END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax "
                    + "error line 1 at position 43 unexpected 'RETURN'.",
                "BEGIN IF (TRUE) THEN LET a := 1 x; END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax "
                    + "error line 1 at position 35 unexpected 'END'.",
                "BEGIN LOOP LET a := 1 x; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'. | syntax "
                    + "error line 1 at position 44 unexpected 'IF'. | syntax error line 1 at position 65 unexpected "
                    + "'END'.",
                "BEGIN LOOP IF (TRUE) THEN LET a := 1 x; END IF; BREAK; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'. | syntax "
                    + "error line 1 at position 46 unexpected 'FOR'.",
                "BEGIN FOR i IN 1 TO 2 DO DROP TABLE t1 x; END FOR; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax "
                    + "error line 1 at position 31 unexpected 'RETURN'.",
                "BEGIN BEGIN LET a := 1 x; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax "
                    + "error line 1 at position 30 unexpected 'END'.",
                "BEGIN LET a := 1 x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'BREAK'. | syntax "
                    + "error line 1 at position 34 unexpected 'END'.",
                "BEGIN LET a := 1 BREAK; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax "
                    + "error line 1 at position 20 unexpected 'RETURN'.",
                "BEGIN LET a := 1 x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax "
                    + "error line 1 at position 28 unexpected 'EXCEPTION'.",
                "BEGIN SELECT 1 FROM t1 x y; EXCEPTION WHEN OTHER THEN RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                    + "error line 1 at position 18 unexpected 'RETURN'.",
                "BEGIN CALL p() x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'. | syntax "
                    + "error line 1 at position 38 unexpected 'ELSE'. | syntax error line 1 at position 50 "
                    + "unexpected '1'.",
                "BEGIN IF (TRUE) THEN DROP TABLE t1 x; ELSE RETURN 1; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'x'. | syntax "
                    + "error line 1 at position 50 unexpected 'END'.",
                "BEGIN WHILE (TRUE) DO DROP TABLE t1 x; END WHILE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax "
                    + "error line 1 at position 30 unexpected 'UNTIL'. | syntax error line 1 at position 36 "
                    + "unexpected '('.",
                "BEGIN REPEAT DROP TABLE t1 x; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'x'. | syntax "
                    + "error line 1 at position 43 unexpected 'ELSE'. | syntax error line 1 at position 55 "
                    + "unexpected '1'.",
                "BEGIN CASE WHEN TRUE THEN DROP TABLE t1 x; ELSE RETURN 1; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'. | syntax "
                    + "error line 1 at position 56 unexpected 'END'.",
                "BEGIN IF (TRUE) THEN DROP TABLE t1 x; END IF; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 44 unexpected 'END'.",
                "BEGIN BEGIN DROP TABLE t1 x; RETURN 1; END; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax "
                    + "error line 1 at position 34 unexpected 'RETURN'.",
                "BEGIN BEGIN DROP TABLE t1 x; END; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'x'. | syntax "
                    + "error line 1 at position 69 unexpected 'END'.",
                "BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN DROP TABLE t1 x; SELECT 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'y'. | syntax "
                    + "error line 1 at position 78 unexpected 'WHEN'.",
                "BEGIN SELECT 1; EXCEPTION WHEN STATEMENT_ERROR THEN SHOW TABLES IN DATASET y; WHEN OTHER THEN "
                    + "RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE CALL p(); CALL q(); END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE NULL; NULL; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; END IF; SELECT 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'ELSE'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; END IF; RETURN 2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN DATASET y; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax "
                    + "error line 1 at position 47 unexpected 'RETURN'.",
                "BEGIN LOOP SHOW TABLES IN DATASET y; END LOOP; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN DATASET y; END END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax "
                    + "error line 1 at position 43 unexpected 'END'.",
                "BEGIN BEGIN SHOW TABLES IN nosuch x y; END END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'VALUES'.",
                "BEGIN SHOW TABLES IN nosuch x y; VALUES (1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 45 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; (SELECT 1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 52 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; BEGIN TRANSACTION; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 90 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; REVOKE USAGE ON SCHEMA nosuch_s FROM ROLE nosuch_role_x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; UNSET v; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 42 unexpected 'END'.",
                "BEGIN SHOW TABLES IN nosuch x y; LIST @~; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'CONTINUE'.",
                "BEGIN SHOW TABLES IN nosuch x y; CONTINUE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'FETCH'.",
                "BEGIN SHOW TABLES IN nosuch x y; FETCH c INTO a; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'WHILE'.",
                "BEGIN SHOW TABLES IN nosuch x y; WHILE (TRUE) DO BREAK; END WHILE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax "
                    + "error line 1 at position 33 unexpected 'REPEAT'.",
                "BEGIN SHOW TABLES IN nosuch x y; REPEAT BREAK; UNTIL (TRUE) END REPEAT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax "
                    + "error line 1 at position 29 unexpected '1'.",
                "BEGIN SHOW TABLES IN DATASET 1; EXCEPTION WHEN OTHER THEN RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '1'. | syntax "
                    + "error line 1 at position 65 unexpected '1'.",
                "BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN DATASET 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax "
                    + "error line 1 at position 47 unexpected 'END'.",
                "BEGIN LOOP SHOW TABLES IN DATASET 1; END LOOP; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax "
                    + "error line 1 at position 30 unexpected '1'.",
                "BEGIN SHOW SCHEMAS IN DATASET 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax "
                    + "error line 1 at position 28 unexpected 'END'.",
                "BEGIN SHOW TABLES WITH x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'nosuch'. | "
                    + "syntax error line 1 at position 50 unexpected 'END'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES nosuch nosuch2; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax "
                    + "error line 1 at position 26 unexpected 'END'.",
                "BEGIN SHOW GRANTS IN x y; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'.",
                "BEGIN IF (TRUE) THEN SHOW TABLES WITH x; END IF; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
                "BEGIN SHOW TABLES STARTS WITH x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
                "BEGIN SHOW TABLES LIMIT x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "BEGIN SHOW TABLES LIKE x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
                "BEGIN SHOW TABLES LIMIT 1 FROM x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'nosuch'.",
                "BEGIN SHOW TABLES WITH PRIVILEGES USAGE, nosuch; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "BEGIN SHOW TABLES WITH x; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax "
                    + "error line 1 at position 34 unexpected 'END'.",
                "BEGIN SHOW GRANTS IN x; SELECT 1; END;");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    /** A SHOW is no cursor's query: a DECLARE section's cursor over one is refused at the word after SHOW. */
    @Test
    public void aCursorOverAShowIsRefusedAtTheWordAfterIt() {
        assertCells(new String[][] {
            {"DECLARE c CURSOR FOR SHOW TABLES IN DATASET y; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN nosuch; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN a; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN a b; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN SCHEMA s; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN USER x; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN a.b c; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN DATABASE d LIMIT 1; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIKE 'x'; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIKE 'x' IN DATASET y; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIMIT 1; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIMIT 0; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES STARTS WITH 'a'; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW x; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"DECLARE c CURSOR FOR SHOW TABLES x; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN 1; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES 1; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES IN a 1 2; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIKE; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
            {"DECLARE c CURSOR FOR SHOW TABLES LIKE 'x' y; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLES'."},
        });
    }
}
