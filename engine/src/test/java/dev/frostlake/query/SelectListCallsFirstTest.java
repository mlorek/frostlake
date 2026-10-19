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
 * A select list's calls are compiled ahead of the WHERE's, the HAVING's, the QUALIFY's and the ORDER BY's
 * (live-verified): of two calls with too many arguments a clause apart, of two casts an operand refuses, and of a
 * miscounted call of the list beside a clause whose predicate is of no BOOLEAN type, the list's is named. A name that
 * is not there still comes first wherever it stands, and within one clause the calls are judged as written.
 */
public class SelectListCallsFirstTest extends MeasuredCellsTestSupport {

    @Test
    public void theSelectListsCallsAreJudgedBeforeTheOtherClauses() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1)");
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE OR REPLACE TABLE vt (v VECTOR(INT, 3))");
        engine.execute("INSERT INTO vt SELECT [1,2,3]::VECTOR(INT, 3)");
        assertCells(new String[][] {
            {"SELECT f2(1) FROM t1 WHERE ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT ABS(1, 2) FROM t1 WHERE f2(1) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT f2(1) FROM t1 ORDER BY ABS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT ABS(1, 2) FROM t1 ORDER BY f2(1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT f2(1) FROM t1 HAVING ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT f2(1) FROM t1 QUALIFY ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT TO_ARRAY(vt.v)::DATE FROM vt WHERE TO_ARRAY(v)::NUMBER = 1",
                "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS DATE)] for parameter "
                + "'TO_DATE'"},
            {"SELECT TO_ARRAY(vt.v)::DATE FROM vt ORDER BY TO_ARRAY(v)::NUMBER",
                "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS DATE)] for parameter "
                + "'TO_DATE'"},
            {"SELECT TO_ARRAY(vt.v)::DATE FROM vt QUALIFY TO_ARRAY(v)::NUMBER = 1",
                "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS DATE)] for parameter "
                + "'TO_DATE'"},
            {"SELECT TO_ARRAY(vt.v)::DATE FROM vt HAVING TO_ARRAY(v)::NUMBER = 1",
                "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS DATE)] for parameter "
                + "'TO_DATE'"},
            {"SELECT f2(1) FROM vt WHERE TO_ARRAY(v)::NUMBER = 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT TO_ARRAY(vt.v)::DATE FROM vt WHERE ABS(1, 2) > 0",
                "REFUSED SQL compilation error: | invalid type [CAST(FROM_VECTOR(VT.V) AS DATE)] for parameter "
                + "'TO_DATE'"},
            {"SELECT ABS(1, 2) FROM t1 WHERE nosuch > 0",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH'"},
            {"SELECT ABS(1, 2) FROM t1 ORDER BY nosuch",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH'"},
            {"SELECT f2(1) FROM t1 WHERE ABS(1, 2) > 0 ORDER BY nosuch",
                "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'NOSUCH'"},
            {"SELECT f2(1), ABS(1, 2) FROM t1",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT 1 FROM t1 WHERE ABS(1, 2) > 0 AND f2(1) > 0",
                "REFUSED SQL compilation error: error line 1 at position 23 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT f2(1) FROM t1 JOIN t1 t2 ON ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 35 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT f2(1) FROM t1 GROUP BY ABS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT f2(1) FROM t1 WHERE 'x'",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT ABS(1, 2) FROM t1 QUALIFY 'x'",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT ABS(1, 2) x FROM t1 WHERE ABS(1, 2, 3) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT SUM(a, 1) FROM t1 WHERE ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SUM(T1.A, 1)] expected 1, got 2"},
            {"SELECT ABS(1, 2) FROM t1 WHERE a IN (SELECT ABS(1, 2, 3))",
                "REFUSED SQL compilation error: error line 1 at position 44 | too many arguments for function "
                + "[ABS(1, 2, 3)] expected 1, got 3"},
        });
    }
}
