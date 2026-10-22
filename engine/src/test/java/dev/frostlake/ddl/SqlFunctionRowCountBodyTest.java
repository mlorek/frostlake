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
 * A SQL function's query body whose row count is no count is refused at CREATE in the frame's positions: a minus
 * after LIMIT or FETCH FIRST names 'SELECT' and the token after the number, a text after FETCH FIRST names the
 * text alone, and a parameter where the count stands names 'SELECT' (all live-verified).
 */
public class SqlFunctionRowCountBodyTest extends MeasuredCellsTestSupport {

    @Test
    public void rowCountBodies() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION fl6() RETURNS INT AS 'SELECT a FROM t LIMIT -1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "29 unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION flf() RETURNS INT AS 'SELECT a FROM t FETCH FIRST ''x'' ROWS ONLY'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 26 unexpected 'OFFSET'.",
            "CREATE OR REPLACE FUNCTION fl7() RETURNS INT AS 'SELECT a FROM t LIMIT -1 OFFSET 1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "23 unexpected ''x''.",
            "CREATE OR REPLACE FUNCTION fl8() RETURNS INT AS 'SELECT a FROM t LIMIT ''x'''");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'ROWS'.",
            "CREATE OR REPLACE FUNCTION fl9() RETURNS INT AS 'SELECT a FROM t FETCH FIRST -1 ROWS ONLY'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION fln(n NUMBER) RETURNS NUMBER AS 'SELECT a FROM t LIMIT n'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION flo(n NUMBER) RETURNS NUMBER AS 'SELECT a FROM t LIMIT 1 OFFSET n'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'. | syntax error line 1 at position 28 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION flp(n NUMBER) RETURNS NUMBER AS 'SELECT a FROM t LIMIT n + 1'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION flt(n NUMBER) RETURNS NUMBER AS 'SELECT TOP n a FROM t'");
        assertAnswer("Function FLNULL successfully created.",
            "CREATE OR REPLACE FUNCTION flnull() RETURNS NUMBER AS 'SELECT a FROM t LIMIT NULL'");
        assertAnswer("Function C_IDT successfully created.",
            "CREATE OR REPLACE FUNCTION c_idt() RETURNS NUMBER AS 'SELECT a FROM IDENTIFIER(''t'')'");
        assertAnswer("REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                + "1 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION c_ff(n NUMBER) RETURNS NUMBER AS 'SELECT a FROM t FETCH FIRST n ROWS ONLY'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '-'. | syntax "
                + "error line 1 at position 22 unexpected '-'.",
            "SELECT a FROM t LIMIT -1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ''x''.",
            "SELECT a FROM t FETCH FIRST 'x' ROWS ONLY");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '-'. | syntax "
                + "error line 1 at position 44 unexpected '-'.",
            "SELECT COUNT(*) FROM (SELECT a FROM t LIMIT -1)");
    }
}
