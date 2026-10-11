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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A comma inside a cast that stands in a bracket of its clause: the comma, and then the text read with the
 * cast's own parenthesis given up, so the bracket around the cast closes one parenthesis early and the last one is
 * refused. A call's arguments, a group, an IN list, a window's OVER and an object literal are such brackets, in
 * any clause of a query; a cast that another cast holds directly gives that cast up too and is read on from its
 * AS. In a join's ON the fault of any bracket is the ON alone, in a block's assignment the value's first token,
 * and in a searched CASE's WHEN the comma and then the THEN. Every answer is live-verified.
 */
public class CastCommaNestedLinesTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute(
            "CREATE OR REPLACE TABLE FY (id INT)");
    }

    @Test
    public void theCommaInANestedCast() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2), 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT CONCAT('a', CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ','. | syntax error "
                + "line 1 at position 18 unexpected ')'.",
            "SELECT (CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ','. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "SELECT (CAST(1, 2) + 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT CAST(CAST(1, 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "SELECT TRY_CAST(CAST(1, 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 38 unexpected ')'.",
            "SELECT * FROM t WHERE a IN (CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 38 unexpected ')'.",
            "SELECT 1 FROM t WHERE UPPER(CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT UPPER(TRY_CAST(1, 2), 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT (TRY_CAST(1, 2) + 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT TRY_CAST(TRY_CAST(1, 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "SELECT * FROM t WHERE a IN (TRY_CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected 'THEN'.",
            "SELECT CASE WHEN CAST(1, 2) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','. | syntax error "
                + "line 1 at position 36 unexpected 'THEN'.",
            "SELECT CASE WHEN TRY_CAST(1, 2) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON TRY_CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected 'CAST'.",
            "DECLARE\n  x INT;\nBEGIN\n  x := CAST(1, 2);\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected 'TRY_CAST'.",
            "DECLARE\n  x INT;\nBEGIN\n  x := TRY_CAST(1, 2);\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected ')'.",
            "SELECT TRY_CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "SELECT UPPER(TRY_CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT CONCAT(CAST(1, 2), 'a') FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected ')'.",
            "SELECT ((CAST(1, 2))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT * FROM t WHERE a IN (1, CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 38 unexpected ','.",
            "SELECT * FROM t WHERE a IN (CAST(1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "SELECT * FROM t WHERE CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM t ORDER BY CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT a FROM t GROUP BY UPPER(CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT UPPER(LOWER(CAST(1, 2))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ','. | syntax error "
                + "line 1 at position 17 unexpected ')'.",
            "SELECT [CAST(1, 2)] FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT (1, CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2)), 3 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax error "
                + "line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2), UPPER(CAST(3, 4)) FROM t");
    }

    @Test
    public void theShapesMeasuredSecond() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 38 unexpected ')'.",
            "SELECT a FROM t WHERE UPPER(CAST(1, 2)) = 1 AND a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "SELECT a FROM t WHERE (CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','.",
            "SELECT a FROM t WHERE a = CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "SELECT a FROM t WHERE a = UPPER(CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "SELECT a FROM t WHERE CAST(1, 2) = 1 ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT a FROM t ORDER BY UPPER(CAST(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM t ORDER BY CAST(1, 2), a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM t GROUP BY CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','.",
            "SELECT a FROM t HAVING CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT a FROM t HAVING UPPER(CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "SELECT a FROM t QUALIFY UPPER(CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "SELECT a FROM t WHERE a IN (UPPER(CAST(1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected ','.",
            "SELECT a FROM t WHERE a IN (UPPER(CAST(1, 2)), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','.",
            "SELECT a FROM t WHERE a BETWEEN CAST(1, 2) AND 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "SELECT a FROM t WHERE a BETWEEN UPPER(CAST(1, 2)) AND 3");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON UPPER(CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON t.a = CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON t.a = u.a AND CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON (CAST(1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON CAST(1, 2) = 1 WHERE t.a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON CAST(1 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'b'.",
            "SELECT * FROM t JOIN t u ON t.a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ON'.",
            "SELECT * FROM t JOIN t u ON UPPER(1 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ','. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "SELECT CASE WHEN TRUE THEN CAST(1, 2) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "SELECT CASE WHEN TRUE THEN 1 ELSE CAST(1, 2) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','.",
            "SELECT CASE a WHEN CAST(1, 2) THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "SELECT CASE WHEN TRUE THEN UPPER(CAST(1, 2)) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','.",
            "SELECT CASE WHEN a = CAST(1, 2) THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected 'THEN'.",
            "SELECT CASE WHEN CAST(1, 2) = 1 THEN 1 ELSE 2 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected 'THEN'.",
            "SELECT CASE WHEN CAST(1, 2) = 1 THEN 1 WHEN 2 = 2 THEN 3 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','.",
            "SELECT a FROM t WHERE CASE WHEN CAST(1, 2) = 1 THEN 1 END = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'. | syntax error "
                + "line 1 at position 31 unexpected 'THEN'.",
            "SELECT CASE WHEN CAST(1 2) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'THEN'.",
            "SELECT CASE WHEN (1, 2 = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '2'.",
            "SELECT CASE WHEN UPPER(1 2) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected 'UPPER'. | syntax "
                + "error line 4 at position 17 unexpected '('.",
            "DECLARE\n  x INT;\nBEGIN\n  x := UPPER(CAST(1, 2));\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 15 unexpected ','.",
            "DECLARE\n  x INT;\nBEGIN\n  RETURN CAST(1, 2);\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 17 unexpected ','.",
            "DECLARE\n  x INT;\nBEGIN\n  LET y := CAST(1, 2);\n  RETURN y;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected 'CAST'.",
            "DECLARE\n  x INT;\nBEGIN\n  x := CAST(1 2);\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected '1'.",
            "DECLARE\n  x INT;\nBEGIN\n  x := 1 2;\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected 'UPPER'.",
            "DECLARE\n  x INT;\nBEGIN\n  x := UPPER(1 2);\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 4 at position 7 unexpected '1'. | syntax error "
                + "line 4 at position 15 unexpected '('.",
            "DECLARE\n  x INT;\nBEGIN\n  x := 1 + CAST(1, 2);\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 22 unexpected ','.",
            "DECLARE\n  x INT DEFAULT CAST(1, 2);\nBEGIN\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'.",
            "SELECT UPPER(LOWER(CAST(1, 2)), 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','.",
            "SELECT UPPER(CAST(1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2)) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected 'FROM'. | syntax error line 1 at position 56 unexpected 'b'.",
            "SELECT * FROM (SELECT UPPER(CAST(1, 2)) FROM t) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ','. | syntax error "
                + "line 1 at position 20 unexpected ')'.",
            "SELECT [1, CAST(1, 2)] FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 21 unexpected '2'.",
            "SELECT {'k': CAST(1, 2)} FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 31 unexpected ','. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2), CAST(3, 4)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected ')'.",
            "SELECT CAST(UPPER(CAST(1, 2)) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT UPPER(CAST(1 AS INT, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "INSERT INTO t (a) VALUES (CAST(1, 2)) oops");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT COUNT(*) OVER (ORDER BY CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ','. | syntax error "
                + "line 1 at position 52 unexpected ')'.",
            "SELECT COUNT(*) OVER (PARTITION BY UPPER(CAST(1, 2))) FROM t");
    }
}
