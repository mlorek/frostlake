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
 * A row written where a value is wanted: a typed variable's assignment is refused as the cast it is, a table
 * function's positional row by its place, a bare star over GENERATOR before the call's arguments, a row among
 * PIVOT's values as an equality, and ORDER BY and SAMPLE rows with the account's two lines (all live-verified).
 */
public class ClauseRowRefusalsTest extends MeasuredCellsTestSupport {

    @Test
    public void rowsWhereValuesGo() {
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO t2 VALUES (1, 'x', '2024-01-01')");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : SQL "
                + "compilation error: | invalid type [CAST(ROW(1, 2) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "BEGIN LET x INT := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: | invalid type [CAST(ROW(1, 2) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "DECLARE x INT; BEGIN x := (1, 2); RETURN x; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL "
                + "compilation error: | invalid type [CAST(ROW(1, 2) AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'",
            "BEGIN LET x VARCHAR := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL "
                + "compilation error: | invalid type [CAST(ROW(1, 2) AS DATE)] for parameter 'TO_DATE'",
            "BEGIN LET x DATE := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | invalid type [CAST(ROW('a', 'b') AS VARCHAR(134217728))] for parameter "
                + "'TO_VARCHAR'",
            "DECLARE x VARCHAR; BEGIN x := ('a', 'b'); RETURN x; END");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL "
                + "compilation error: | invalid type [CAST(ROW(1, 2) AS VARIANT)] for parameter 'TO_VARIANT'",
            "BEGIN LET x VARIANT := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its type "
                + "inferred from initializer",
            "BEGIN LET x := (1, 2); RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter '1'",
            "SELECT * FROM TABLE(FLATTEN((1, 2)))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter "
                + "'INPUT'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter '1'",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE((1, 2), ','))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter '2'",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('a,b', (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
            "SELECT * FROM TABLE(GENERATOR(ROWCOUNT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
            "SELECT * FROM TABLE(GENERATOR(ROWCOUNT => 2, TIMELIMIT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
            "SELECT * FROM TABLE(GENERATOR((1, 2)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'ROW(1, 2)'",
            "SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'ROW(1, 2)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function '=': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(16777216))",
            "SELECT a FROM t2 PIVOT (SUM(a) FOR b IN ((1, 2)))");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function '=': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(16777216))",
            "SELECT * FROM t2 PIVOT (SUM(a) FOR b IN ((1, 2), (3, 4)))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'DESC'. | syntax "
                + "error line 1 at position 36 unexpected ')'.",
            "SELECT a FROM t2 ORDER BY (b DESC, a)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax "
                + "error line 1 at position 29 unexpected '2'.",
            "SELECT a FROM t2 SAMPLE ((1, 2))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter '1'",
            "SELECT * FROM TABLE(FLATTEN((1, 2), 'x'))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter '1'",
            "SELECT * FROM TABLE(SPLIT_TO_TABLE((1, 2), (3, 4)))");
    }
}
