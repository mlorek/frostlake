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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Snowscript UDF's body read as an expression, more shapes of its first bracket: an IN list and a BETWEEN after
 * the refused token, a finished IS DISTINCT FROM test whose operand is more than one token, an operator inside a
 * bracket the refused token opens; the echo of an aggregate over a frame of the whole partition, of CUBE and
 * ROLLUP grouping sets and of set operations; and a GROUP BY's second item given a quantifier (all live-verified).
 */
public class SnowscriptBodyBracketShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void bracketShapes() {
        engine.execute("CREATE OR REPLACE TABLE rc_t (a INT, b INT, c INT)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 37 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c IN (1, 2)); END $$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 43 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c BETWEEN 1 AND 2); END "
                + "$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 47 unexpected 'IS'.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS DISTINCT FROM 1 + 1 IS "
                + "NULL); END $$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '('. | syntax error line 1 "
                + "at position 32 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a.b.c + 1)); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT SUM(A) OVER () AS \"SUM(A) "
                + "OVER (ORDER BY A ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT SUM(a) OVER (ORDER "
                + "BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM rc_t)); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT MAX(A) OVER () AS \"MAX(A) "
                + "OVER (ORDER BY A ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT MAX(a) OVER (ORDER "
                + "BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) FROM rc_t)); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T "
                + "GROUP BY GROUPING SETS ((), (A), (B), (A, B), (C), (A, C), (B, C), (A, B, C))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP BY "
                + "CUBE (a, b, c))); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T "
                + "GROUP BY GROUPING SETS ((), (B), (A), (A, B))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP BY "
                + "ROLLUP (a), ROLLUP (b))); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" "
                + "FROM RC_T AS RC_T) OB$0 UNION (SELECT * FROM (SELECT B AS \"B\" FROM RC_T AS RC_T) OB$1 UNION "
                + "(SELECT C AS \"C\" FROM RC_T AS RC_T) OB$2) OB$3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "(SELECT b FROM rc_t UNION SELECT c FROM rc_t))); END $$");
        assertAnswer("REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT * FROM (SELECT "
                + "A AS \"A\" FROM RC_T AS RC_T) OB$0 UNION (SELECT B AS \"B\" FROM RC_T AS RC_T) OB$1) OB$2 MINUS "
                + "(SELECT C AS \"C\" FROM RC_T AS RC_T) OB$3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "SELECT b FROM rc_t EXCEPT SELECT c FROM rc_t)); END $$");
        assertAnswer("a", "SELECT c FROM (SELECT 'a' c) GROUP BY c, c LIKE ANY ('x', 'y')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ALL'. | syntax "
                + "error line 1 at position 58 unexpected ''x''. | syntax error line 1 at position 66 unexpected ')'.",
            "SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT ILIKE ALL ('x', 'y')");
    }
}
