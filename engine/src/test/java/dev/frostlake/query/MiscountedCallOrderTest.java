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
 * Of several calls given more or fewer arguments than they take, the one refused is the first the account
 * compiles: within one expression the calls in the order written, each after the calls its arguments hold, a
 * built-in and a user's function alike - {@code ABS(1, 2) + f(1)} is refused for the ABS, {@code f(ABS(1, 2))}
 * too. A name nobody defined and a column that is not there come before any count. Every answer is
 * live-verified.
 */
public class MiscountedCallOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void theFirstMiscountedCallIsTheOneRefused() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x INT) RETURNS INT AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x VARCHAR) RETURNS VARCHAR AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]')");
        answer(
            "CREATE OR REPLACE PROCEDURE pa(a INT, b VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT ABS(f2(1), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 10 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT f2(ABS(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2), f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1), ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + f2(1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) + ABS(1, 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2), f2(1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1), ABS(1, 2) FROM t1");
        answer(
            "SELECT f2(1) FROM t1 WHERE ABS(1, 2) > 0");
        answer(
            "SELECT ABS(1, 2) FROM t1 WHERE f2(1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "CREATE OR REPLACE FUNCTION b6() RETURNS INT AS 'ABS(1, 2) + f2(1)'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "CREATE OR REPLACE FUNCTION b7() RETURNS INT AS 'f2(1) + ABS(1, 2)'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT ABS(1, 2) + nosuchfn(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT nosuchfn(1) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT f2(1) + nosuchfn(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT nosuchfn(1) + f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCHCOL'",
            "SELECT ABS(1, 2) + nosuchcol FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCHCOL'",
            "SELECT nosuchcol + ABS(1, 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'NOSUCHCOL'",
            "SELECT f2(1) + nosuchcol FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCHCOL'",
            "SELECT nosuchcol + f2(1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [UPPER(1,"
                + " 2, 3, 4)] expected 1, got 4",
            "SELECT UPPER(1, 2, 3, 4) || f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT CASE WHEN ABS(1, 2) > 0 THEN f2(1) END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT CASE WHEN f2(1) > 0 THEN ABS(1, 2) END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[ABS()], expected 1, got 0",
            "SELECT ABS() + f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) + ABS()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + n1(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'N1': (NUMBER(1,0))",
            "SELECT n1(1) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT ABS('x') + f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) + ABS('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + n1('x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT n1('x', 'y') + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + SQRT(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [SQRT(1, "
                + "2)] expected 1, got 2",
            "SELECT SQRT(1, 2) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) + n1(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'N1': (NUMBER(1,0))",
            "SELECT n1(1) + f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) + ABS(3, 4, 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT COALESCE(ABS(1, 2), f2(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT COALESCE(f2(1), ABS(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'NOSUCHCOL'",
            "SELECT ABS(1, 2) x, f2(1) y FROM t1 WHERE nosuchcol = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT 1 FROM t1 WHERE ABS(1, 2) > 0 AND f2(1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT 1 FROM t1 WHERE f2(1) > 0 AND ABS(1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT 1 FROM t1 ORDER BY ABS(1, 2), f2(1)");
        answer(
            "SELECT f2(1) FROM t1 ORDER BY ABS(1, 2)");
        answer(
            "SELECT ABS(1, 2) FROM t1 ORDER BY f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT ABS(1, 2) FROM t1 GROUP BY f2(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT f2(1) FROM t1 GROUP BY ABS(1, 2)");
        answer(
            "SELECT f2(1) FROM t1 HAVING ABS(1, 2) > 0");
        answer(
            "SELECT f2(1) FROM t1 QUALIFY ABS(1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT ABS(1, 2) FROM t1 a JOIN t1 b ON f2(1) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "SELECT f2(1) FROM t1 a JOIN t1 b ON ABS(1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT (SELECT f2(1)) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "SELECT ABS(1, 2) + (SELECT f2(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "CREATE OR REPLACE VIEW vc AS SELECT ABS(1, 2) + f2(1) x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "CREATE OR REPLACE VIEW vc AS SELECT f2(1) + ABS(1, 2) x");
    }
}
