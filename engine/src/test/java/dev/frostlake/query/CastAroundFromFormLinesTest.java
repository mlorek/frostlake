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
 * A cast that holds an EXTRACT or a SUBSTRING written in the FROM form, inside one other call of a select item:
 * the FROM, and then the cast's closing parenthesis. Every answer is live-verified.
 */
public class CastAroundFromFormLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theCastInsideOneCall() {
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
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT), CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'FROM'. | syntax "
                + "error line 1 at position 51 unexpected ')'.",
            "SELECT UPPER(CAST(CAST(EXTRACT('wks' FROM d) AS INT) AS VARCHAR)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)), a b c FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'FROM'. | syntax "
                + "error line 1 at position 49 unexpected ')'.",
            "SELECT 1, UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT) || 'x') FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'FROM'. | syntax "
                + "error line 1 at position 53 unexpected ')'.",
            "SELECT UPPER('x' || CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) + 1 AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)::VARCHAR) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'FROM'. | syntax "
                + "error line 1 at position 39 unexpected ')'.",
            "SELECT UPPER(EXTRACT('wks' FROM d)::INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'FROM'.",
            "SELECT a FROM t WHERE UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "SELECT IFF(CAST(EXTRACT('wks' FROM d) AS INT) = 1, 1, 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'FROM'. | syntax "
                + "error line 1 at position 48 unexpected ')'.",
            "SELECT UPPER(TRY_CAST(SUBSTRING(b FROM 2) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'FROM'. | syntax "
                + "error line 1 at position 44 unexpected ')'.",
            "SELECT COALESCE(CAST(SUBSTR(b FROM 2) AS INT), 0) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'FROM'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "SELECT 1 + UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FROM'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) AS z FROM t");
    }
}
