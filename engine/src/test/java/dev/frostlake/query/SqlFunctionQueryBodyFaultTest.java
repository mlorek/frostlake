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
 * A SQL function's query body with a fault every grammar has — a word after an alias or a relation's name, a text
 * after TOP, a body that runs out — is refused at CREATE as the account frames it, at the body's SELECT; a
 * QUALIFY with no window function is created and refused at each call, in the framed body's positions; and a name
 * the body does not resolve is refused at CREATE on its own line (all live-verified).
 */
public class SqlFunctionQueryBodyFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void aQueryBodyIsJudgedAsTheAccountFramesIt() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fs1() RETURNS INT AS 'SELECT a FROM t x y'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fq5() RETURNS INT AS 'SELECT a a a FROM t'");
        answer("CREATE OR REPLACE FUNCTION fl6() RETURNS INT AS 'SELECT a FROM t LIMIT -1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION flt() RETURNS INT AS 'SELECT TOP ''x'' a FROM t'");
        answer("CREATE OR REPLACE FUNCTION flf() RETURNS INT AS 'SELECT a FROM t FETCH FIRST ''x'' ROWS ONLY'");
        assertAnswer("Function FQ1 successfully created.",
            "CREATE OR REPLACE FUNCTION fq1() RETURNS INT AS 'SELECT a FROM t QUALIFY TRUE'");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no window "
                + "function.",
            "SELECT fq1()");
        assertAnswer("REFUSED SQL compilation error: error line 2 at position 0 | invalid identifier 'NOSUCH'",
            "CREATE OR REPLACE FUNCTION fe2() RETURNS INT AS $$\nnosuch + 1$$");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fs6() RETURNS INT AS 'SELECT a b c'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fs7() RETURNS INT AS 'SELECT a FROM t t2 t3'");
        answer("CREATE OR REPLACE FUNCTION c_lim(n NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM (SELECT a FROM t "
            + "LIMIT n)'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fs8() RETURNS INT AS 'SELECT a FROM t WHERE'");
        assertAnswer("Function FS9 successfully created.",
            "CREATE OR REPLACE FUNCTION fs9() RETURNS INT AS 'SELECT MAX(a) FROM t'");
        assertAnswer("1", "SELECT fs9()");
    }
}
