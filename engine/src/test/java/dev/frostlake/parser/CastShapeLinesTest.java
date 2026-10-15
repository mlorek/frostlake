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
 * The lines after a fault inside a CAST, by where the cast stands: after LIMIT, in a WHERE, an ORDER BY, a
 * CASE, a scalar subquery, a VALUES row, as an operand, left unclosed, and after TOP n (live-verified).
 */
public class CastShapeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aCommaInsideACast() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM t WHERE CAST(1, 2) = 1 AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM t ORDER BY a, CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected ')'. | syntax error line 1 at position 39 unexpected 'THEN'.",
            "SELECT CASE WHEN UPPER(CAST(1, 2)) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected '1'.",
            "SELECT CASE CAST(1, 2) WHEN 1 THEN 1 END FROM t");
        answer(
            "DECLARE\n  x INT;\nBEGIN\n  IF (CAST(1, 2) = 1) THEN\n    RETURN 1;\n  END IF;\n  RETURN 0;\nEND");
        answer(
            "DECLARE\n  x INT;\nBEGIN\n  SELECT CAST(1, 2) INTO :x;\n  RETURN x;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected '('.",
            "SELECT (SELECT CAST(1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '('.",
            "SELECT (SELECT UPPER(CAST(1, 2))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected 'AS'. | syntax error line 1 at position 29 unexpected ')'.",
            "SELECT UPPER(CAST(1, 2 AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | syntax "
                + "error line 1 at position 31 unexpected '2'.",
            "SELECT * FROM (VALUES (CAST(1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'CAST'. | syntax "
                + "error line 1 at position 22 unexpected 'CAST'.",
            "SELECT a FROM t LIMIT CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "SELECT a FROM t WHERE CAST(1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','.",
            "SELECT a FROM t WHERE CAST(1, 2) = 1 AND a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM t WHERE a = CAST(1, 2) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM t ORDER BY CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM t ORDER BY CAST(1, 2), a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM t GROUP BY a, CAST(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected 'THEN'.",
            "SELECT CASE WHEN CAST(1, 2) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ','. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT CASE WHEN 1 = 1 THEN CAST(1, 2) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 ELSE CAST(1, 2) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax error "
                + "line 1 at position 28 unexpected '1'.",
            "SELECT CASE CAST(1, 2) WHEN 1 THEN 1 ELSE 2 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected '('.",
            "SELECT (SELECT CAST(1, 2) FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'VALUES'. | syntax "
                + "error line 1 at position 31 unexpected '2'.",
            "SELECT * FROM (VALUES (CAST(1, 2)), (3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'CAST'. | syntax "
                + "error line 1 at position 22 unexpected 'CAST'.",
            "SELECT a FROM t LIMIT CAST(1 AS INT)");
    }

    @Test
    public void otherFaultsAndAnOpenCast() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '2'. | syntax error "
                + "line 1 at position 30 unexpected 'FROM'.",
            "SELECT CAST(CAST(1 2) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 21 unexpected 'FROM'.",
            "SELECT CAST(1 2) + 1 FROM t");
        answer(
            "SELECT CAST(1 AS INT) FROM t WHERE CAST(1 2) = 1 AND CAST(3 4) = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '2'.",
            "SELECT TOP 1 a, (1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'.",
            "SELECT TOP 1 a, UPPER(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '2'.",
            "SELECT TOP 1 (1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'. | syntax "
                + "error line 1 at position 39 unexpected 'b'.",
            "SELECT a, CAST(b AS INT FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'. | syntax "
                + "error line 1 at position 43 unexpected 'b'.",
            "SELECT a, TRY_CAST(b AS INT FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected 'b'.",
            "SELECT TRY_CAST(b AS INT FROM t WHERE a b");
        answer(
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'.",
            "SELECT CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '2'. | syntax error "
                + "line 1 at position 21 unexpected 'FROM'.",
            "SELECT CAST(1 2) - 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'.",
            "SELECT 1 + CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '2'.",
            "SELECT TOP 1 a, (1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '2'.",
            "SELECT TOP 1 a, CAST(1 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'.",
            "SELECT a, CAST(b AS INT FROM t");
    }
}
