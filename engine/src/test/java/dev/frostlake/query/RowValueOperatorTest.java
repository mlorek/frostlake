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
 * A parenthesized list of two or more values is one ROW value. A select item that is one is refused as a list
 * element of several columns, at its alias or at its own parenthesis; an operator over one is refused by its
 * argument types at the operator — the signs as NEGATE and UNARY PLUS, a path or an index as GET, BETWEEN as the
 * comparison it makes, a negated operator placed nowhere — and a cast as an invalid type. Two rows compare, with
 * =, the ordering operators, IN, BETWEEN and IS DISTINCT FROM, behind however many parentheses. Every answer
 * is live-verified.
 */
public class RowValueOperatorTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "INSERT INTO t VALUES (1, 'y', '2024-01-04'), (2, 'x', '2024-01-03'), (3, 'x', '2024-01-02'), (1, 'x',"
                + " '2024-01-01'), (NULL, 'z', NULL), (2, NULL, '2024-01-05')");
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fr(x INT) RETURNS INT AS $$ x + 1 $$");
    }

    @Test
    public void aSelectItemThatIsARow() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2), 3 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2) AS r");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2) r FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 10 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT 3, (1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, (2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT ((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (((1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT 3, ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT DISTINCT (a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT ALL (a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT TOP 1 (a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (a, b) x, a FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT t.*, (1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2) FROM t WHERE FALSE");
        assertAnswer(
            "false",
            "SELECT (1, 2) IS DISTINCT FROM (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) IS NOT DISTINCT FROM (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) IN ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'IN': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT (1, 2) IN (1, 2)");
    }

    @Test
    public void anOperatorOverARow() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'+': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) + 1 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function '+': "
                + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 + (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'+': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT (1, 2) + (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'||': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) || 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'||': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 'x' || (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function 'IS "
                + "NULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT (1, 2) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function 'IS "
                + "NOT NULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT (1, 2) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEGATE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT -(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NOT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NOT (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'AND': (ROW(NUMBER(1,0), NUMBER(1,0)), BOOLEAN)",
            "SELECT (1, 2) AND TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'OR': (BOOLEAN, ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TRUE OR (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'",
            "SELECT (1, 2)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'",
            "SELECT CAST((1, 2) AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(ROW(1, 2))] for parameter 'TO_NUMBER'",
            "SELECT TRY_CAST((1, 2) AS INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for function "
                + "'GET': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2):x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | Invalid argument types for function "
                + "'GET': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2)[0]");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'LIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) LIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 'x' LIKE (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'>=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) BETWEEN 1 AND 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'>=': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 BETWEEN (1, 2) AND 3");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'<=': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 BETWEEN 0 AND (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) < (1, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function '=': "
                + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 = (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'IN': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 IN ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'IN': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT 1 IN ((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'*': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) * 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'/': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) / 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'%': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) % 2");
        assertAnswer(
            "false",
            "SELECT (1, 2) <> (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = (1, 2) = TRUE");
        assertAnswer(
            "true",
            "SELECT ((1, 2) = (1, 2))");
        assertAnswer(
            "true",
            "SELECT ((1, 2)) = ((1, 2))");
        assertAnswer(
            "true",
            "SELECT (1, 2) = ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'RLIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) RLIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: 'ROW(1, 2)'",
            "SELECT (1, 2) COLLATE 'en'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'IS'.",
            "SELECT (1, 2) IS TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) = ANY (SELECT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function 'IS "
                + "NULL': (ROW(NULL, NULL))",
            "SELECT (NULL, NULL) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'||': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT (1, 2) || (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'-': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) - 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function '*': "
                + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 2 * (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UNARY PLUS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT +(1, 2)");
    }

    @Test
    public void theOperatorsMeasuredSecond() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'IN': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 NOT IN ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'IN': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT (1, 2) NOT IN (1, 2)");
        assertAnswer(
            "false",
            "SELECT (1, 2) NOT IN ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'ILIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) ILIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'LIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) NOT LIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'ILIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) NOT ILIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'RLIKE': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) NOT RLIKE 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'REGEXP': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT (1, 2) REGEXP 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'RLIKE': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 'x' RLIKE (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'>=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) NOT BETWEEN 1 AND 2");
        assertAnswer(
            "true",
            "SELECT (1, 2) BETWEEN (1, 1) AND (1, 3)");
        assertAnswer(
            "true",
            "SELECT ((1, 2), 3) = ((1, 2), 3)");
        assertAnswer(
            "true",
            "SELECT (1, (2, 3)) = (1, (2, 3))");
        assertAnswer(
            "true",
            "SELECT ((1, 2)) IN (((1, 2)))");
        assertAnswer(
            "false",
            "SELECT ((1, 2), 3) = ((1, 3), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT (1, 2) IS DISTINCT FROM (1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'EQUAL_NULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) IS DISTINCT FROM 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'EQUAL_NULL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 IS NOT DISTINCT FROM (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, NULL) IS NOT DISTINCT FROM (1, NULL)");
        assertAnswer(
            "true",
            "SELECT (1, NULL) IS DISTINCT FROM (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT (1, 2) = (1, 2, 3)");
        assertAnswer(
            "null",
            "SELECT (1, 2) < (1, NULL)");
        assertAnswer(
            "REFUSED Numeric value 'a' is not recognized",
            "SELECT (1, 'a') = (1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = ANY (SELECT 1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = ALL (SELECT 1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) IN (SELECT 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) IN (SELECT 1)");
        assertAnswer(
            "true",
            "SELECT ((1, 2)) = (1, 2)");
        assertAnswer(
            "true",
            "SELECT (((1, 2))) = ((1, 2))");
        assertAnswer(
            "true",
            "SELECT (1, 2) > ((0, 5))");
    }
}
