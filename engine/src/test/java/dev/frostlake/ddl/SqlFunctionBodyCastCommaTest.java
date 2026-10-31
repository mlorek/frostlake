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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function's body that writes a comma inside a CAST's or a TRY_CAST's parentheses is refused as the
 * function is created, read in its frame between parentheses: a query's first word is named, then where the
 * reading resumes, the token after the first '(' or the cast's comma, and an expression's comma and then the
 * frame's closing ')' (all live-verified).
 */
public class SqlFunctionBodyCastCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void queryAndExpressionBodies() {
        engine.execute("CREATE OR REPLACE TABLE ta750 (a INT)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fa1() RETURNS INT AS 'SELECT UPPER(CAST(1, 2))'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "7 unexpected ','. | syntax error line 1 at position 11 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fa2() RETURNS INT AS 'CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa3() RETURNS INT AS 'SELECT CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "11 unexpected ','. | syntax error line 1 at position 15 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fa4() RETURNS INT AS 'TRY_CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa5() RETURNS INT AS 'SELECT TRY_CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "13 unexpected ','. | syntax error line 1 at position 18 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fa6() RETURNS INT AS 'UPPER(CAST(1, 2))'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected ','.",
            "CREATE OR REPLACE FUNCTION fa7() RETURNS INT AS 'SELECT 1 + CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fa8() RETURNS INT AS 'SELECT a FROM (SELECT CAST(1, 2) AS a)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fa9() RETURNS INT AS 'SELECT UPPER(CAST(1, 2)) FROM ta750'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa10() RETURNS INT AS 'SELECT CAST(1 AS INT), CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fa11() RETURNS INT AS 'SELECT UPPER(CAST(1, 2), 3)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa12() RETURNS INT AS 'SELECT CAST(1, 2) WHERE TRUE'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'WITH'. | syntax error line 1 at position 12 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fa13() RETURNS INT AS 'WITH c AS (SELECT CAST(1, 2) AS a) SELECT a FROM c'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fa14() RETURNS INT AS 'SELECT CAST(CAST(1, 2) AS INT)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa15() RETURNS INT AS 'SELECT CAST(1, 2, 3)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa16() RETURNS INT AS 'SELECT TRY_CAST(1, 2) FROM ta750'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "2 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa17() RETURNS INT AS ' SELECT CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "11 unexpected ','. | syntax error line 1 at position 15 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fa18() RETURNS INT AS '1 + CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fa19() RETURNS INT AS $$SELECT UPPER(CAST(1, 2))$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa20() RETURNS TABLE (a INT) AS 'SELECT CAST(1, 2) AS a'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'.",
            "CREATE OR REPLACE TEMPORARY FUNCTION fa21() RETURNS INT AS 'SELECT CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at position "
                + "0 unexpected 'SELECT'. | syntax error line 3 at position 7 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION fa22() RETURNS INT AS '\nSELECT\n  CAST(1, 2)'");
        assertAnswer("", "SHOW USER FUNCTIONS LIKE 'FA%' ->> SELECT \"name\" FROM $1 ORDER BY 1");
    }

    @Test
    public void whereTheReadingResumes() {
        engine.execute("CREATE OR REPLACE TABLE ta752 (a INT)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected ','.",
            "CREATE OR REPLACE FUNCTION fb1() RETURNS INT AS 'SELECT a + CAST(1, 2) FROM ta752'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 21 unexpected ','.",
            "CREATE OR REPLACE FUNCTION fb2() RETURNS INT AS 'SELECT ''x'' || CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected ','.",
            "CREATE OR REPLACE FUNCTION fb3() RETURNS INT AS 'SELECT -CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 9 unexpected 'CAST'.",
            "CREATE OR REPLACE FUNCTION fb4() RETURNS INT AS 'SELECT (CAST(1, 2))'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected ','. | syntax error line 1 "
                + "at position 21 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb5() RETURNS INT AS 'SELECT 1, CAST(1, 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "13 unexpected ','. | syntax error line 1 at position 21 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb6() RETURNS INT AS 'UPPER(CAST(1, 2), 3)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected ','. | syntax error line 1 "
                + "at position 22 unexpected 'FROM'.",
            "CREATE OR REPLACE FUNCTION fb7() RETURNS INT AS 'SELECT a, CAST(1, 2) FROM ta752'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "8 unexpected ','. | syntax error line 1 at position 13 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb8() RETURNS INT AS '(CAST(1, 2))'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 "
                + "at position 41 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb9() RETURNS INT AS 'SELECT CAST(a, 2) FROM ta752 WHERE a > 0'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at position "
                + "6 unexpected ','. | syntax error line 3 at position 3 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb10() RETURNS INT AS '\nCAST(1,\n 2)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "12 unexpected ','. | syntax error line 1 at position 16 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fb11() RETURNS INT AS ' TRY_CAST(1, 2)'");
    }
}
