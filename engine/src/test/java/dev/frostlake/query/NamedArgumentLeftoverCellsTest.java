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
 * A positional argument written after a named one, where its refusal is read (live-verified): a SQL procedure's body is
 * refused at CREATE, at the argument in the body's own text; an unknown table function called so is named in upper
 * case; and the calls judged alike on both sides stay so.
 */
public class NamedArgumentLeftoverCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureBodyAndAnUnknownTableFunctionAreJudgedAsTheAccountJudgesThem() {
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, s VARCHAR, c INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a', 1)");
        assertCells(new String[][] {
            {"SELECT f2(1) FROM t1 GROUP BY ABS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT ABS(1, 2) FROM t1 JOIN t1 t2 ON f2(1) > 0",
                "REFUSED SQL compilation error: error line 1 at position 39 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"SELECT f2(1) FROM t1 JOIN t1 t2 ON ABS(1, 2) > 0",
                "REFUSED SQL compilation error: error line 1 at position 35 | too many arguments for function "
                + "[ABS(1, 2)] expected 1, got 2"},
            {"SELECT SYSTEM$TYPEOF(a => 1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow "
                + "named argument in function call 'SYSTEM$TYPEOF'"},
            {"CREATE OR REPLACE PROCEDURE c1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n1(a => 1, 2); END; $$",
                "REFUSED SQL compilation error: error line 1 at position 25 | positional argument cannot follow "
                + "named argument in function call 'N1'"},
            {"SELECT * FROM TABLE(nosuchtf(a => 1, 'x'))",
                "REFUSED SQL compilation error: | Unknown table function NOSUCHTF"},
        });
    }
}
