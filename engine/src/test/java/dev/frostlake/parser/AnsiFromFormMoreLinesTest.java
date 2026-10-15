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
 * The lines after a call in an ANSI FROM form is refused, the places that were left: a cast around the call
 * under more than one bracket or a CASE, a name with no FROM form of its own, a FOR tail under a cast, a
 * clause's own operand read on from, and a block's query (live-verified).
 */
public class AnsiFromFormMoreLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastAroundTheCall() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'. | syntax "
                + "error line 1 at position 41 unexpected '3'.",
            "SELECT UPPER(CAST(SUBSTRING(b FROM 2 FOR 3) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected 'AS'. | syntax error line 1 at position 52 unexpected ')'.",
            "SELECT UPPER(LOWER(CAST(EXTRACT('wks' FROM d) AS INT))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'FROM'.",
            "SELECT UPPER(CAST(UPPER(EXTRACT('wks' FROM d)) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected ')'.",
            "SELECT UPPER(CAST((EXTRACT('wks' FROM d)) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected 'AS'. | syntax error line 1 at position 56 unexpected ')'.",
            "SELECT CASE WHEN UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FROM'. | syntax "
                + "error line 1 at position 35 unexpected 'AS'. | syntax error line 1 at position 41 unexpected ')'.",
            "SELECT UPPER(CAST(TRIM(' ' FROM b) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "SELECT UPPER(CAST(SUBSTRING(b FROM 2) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'FROM'. | syntax "
                + "error line 1 at position 35 unexpected '3'.",
            "SELECT CAST(SUBSTRING(b FROM 2 FOR 3) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'FROM'. | syntax "
                + "error line 1 at position 36 unexpected '3'.",
            "SELECT UPPER(SUBSTRING(b FROM 2 FOR 3)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 40 unexpected 'AS'. | syntax error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(TRIM(BOTH ' ' FROM b) AS INT)) FROM t");
        answer(
            "SELECT UPPER(CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'FROM'. | syntax "
                + "error line 1 at position 52 unexpected 'AS'. | syntax error line 1 at position 59 unexpected ')'.",
            "SELECT LOWER(UPPER(LOWER(CAST(EXTRACT('wks' FROM d) AS INT)))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 42 unexpected ')'. | syntax error line 1 at position 50 unexpected ')'.",
            "SELECT CASE WHEN CAST(EXTRACT('wks' FROM d) AS INT) = 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FROM'. | syntax "
                + "error line 1 at position 45 unexpected 'AS'. | syntax error line 1 at position 51 unexpected ')'.",
            "SELECT CASE UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) WHEN 1 THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'FROM'. | syntax "
                + "error line 1 at position 61 unexpected 'AS'. | syntax error line 1 at position 67 unexpected ')'.",
            "SELECT CASE WHEN 1 = 1 THEN UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "SELECT UPPER(TRY_CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT), 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FROM'. | syntax "
                + "error line 1 at position 49 unexpected ')'.",
            "SELECT UPPER(1, CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'FROM'. | syntax "
                + "error line 1 at position 52 unexpected ')'.",
            "SELECT UPPER(CAST(UPPER(LOWER(EXTRACT('wks' FROM d))) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'FROM'. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "SELECT UPPER(CAST(((EXTRACT('wks' FROM d))) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) + 1 AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'. | syntax "
                + "error line 1 at position 36 unexpected 'AS'. | syntax error line 1 at position 42 unexpected ')'.",
            "SELECT UPPER(CAST(LTRIM(' ' FROM b) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'FROM'. | syntax "
                + "error line 1 at position 37 unexpected 'AS'. | syntax error line 1 at position 43 unexpected ')'.",
            "SELECT UPPER(CAST(nosuch(' ' FROM b) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'FROM'. | syntax "
                + "error line 1 at position 32 unexpected ')'.",
            "SELECT CAST(EXTRACT('wks' FROM d) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 35 unexpected ')'.",
            "SELECT CAST(TRIM(' ' FROM b) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'FROM'. | syntax "
                + "error line 1 at position 29 unexpected ')'.",
            "SELECT UPPER(TRIM(' ' FROM b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'. | syntax "
                + "error line 1 at position 36 unexpected ')'.",
            "SELECT UPPER(LOWER(TRIM(' ' FROM b))) FROM t");
    }

    @Test
    public void groupsAndBlocks() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'. | syntax "
                + "error line 1 at position 36 unexpected 'AS'. | syntax error line 1 at position 42 unexpected ')'.",
            "SELECT ((CAST(EXTRACT('wks' FROM 2) AS INT)) = 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FROM'. | syntax "
                + "error line 1 at position 41 unexpected ')'.",
            "SELECT (CAST(EXTRACT('wks' FROM 2) AS INT) = 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FROM'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "SELECT ((EXTRACT('wks' FROM 2)) = 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FROM'. | syntax "
                + "error line 1 at position 33 unexpected ')'.",
            "SELECT ((EXTRACT('wks' FROM 2, 1))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'FROM'. | syntax "
                + "error line 1 at position 27 unexpected '2'. | syntax error line 1 at position 30 unexpected '1'.",
            "SELECT (EXTRACT('wks' FROM 2, 1)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 26 unexpected '2'. | syntax error line 1 at position 29 unexpected '1'.",
            "SELECT EXTRACT('wks' FROM 2, 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 26 unexpected 'FROM'. | syntax "
                + "error line 2 at position 34 unexpected ')'. | syntax error line 3 at position 0 unexpected 'END'.",
            "BEGIN\n  SELECT (((EXTRACT('wks' FROM d)))) FROM t;\nEND");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FROM'. | syntax "
                + "error line 1 at position 33 unexpected ')'. | syntax error line 1 at position 43 unexpected 'END'.",
            "BEGIN SELECT EXTRACT('wks' FROM d) FROM t; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'FROM'. | syntax "
                + "error line 1 at position 52 unexpected ')'. | syntax error line 1 at position 63 unexpected 'END'.",
            "BEGIN SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t; END");
    }

    @Test
    public void aClausesOperandIsReadOnFrom() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 59 unexpected 'x'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1 ORDER BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 60 unexpected 'y'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'FROM'. | syntax "
                + "error line 1 at position 84 unexpected 'FROM'.",
            "SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 AND CAST(EXTRACT('wks' FROM d) AS INT) "
                + "= 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'FROM'. | syntax "
                + "error line 1 at position 67 unexpected 'x'.",
            "SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 54 unexpected 'b'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1 AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 59 unexpected 'x'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1 GROUP BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 66 unexpected 'FROM'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1 AND EXTRACT('wks' FROM d) = 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 54 unexpected 'x'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1 LIMIT x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'.",
            "SELECT a FROM t WHERE EXTRACT('wks' FROM d) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FROM'. | syntax "
                + "error line 1 at position 60 unexpected 'x'.",
            "SELECT a FROM t HAVING EXTRACT('wks' FROM d) = 1 ORDER BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected 'x'.",
            "SELECT a FROM t ORDER BY EXTRACT('wks' FROM d), a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT EXTRACT('wks' FROM d) FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'FROM'. | syntax "
                + "error line 1 at position 27 unexpected ')'.",
            "SELECT EXTRACT('wks' FROM d), 2 x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'FROM'. | syntax "
                + "error line 1 at position 69 unexpected 'x'.",
            "SELECT a FROM t WHERE a = 1 AND EXTRACT('wks' FROM d) = 1 ORDER BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'FROM'. | syntax "
                + "error line 1 at position 45 unexpected '3'.",
            "SELECT a FROM t WHERE SUBSTRING(b FROM 2 FOR 3) = 'a' ORDER BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'FROM'. | syntax "
                + "error line 1 at position 56 unexpected 'x'.",
            "SELECT a FROM t WHERE TRIM(' ' FROM b) = 'a' ORDER BY a x y");
    }
}
