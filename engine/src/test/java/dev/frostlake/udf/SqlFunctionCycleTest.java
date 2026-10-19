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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a cycle of SQL functions is refused. Creating a function whose body reaches itself is {@code Detected a
 * cycle in SQL UDF: F} - through another function, through itself, through three functions, through a view and
 * a view of a view, and through table functions - and leaves the earlier definition in place. A cycle made by
 * renaming a function onto a name its own body calls, and one written with qualified names, are not seen when
 * they are made: calling one, the account abandons the statement as an internal failure of its own, which this
 * engine does not imitate - it refuses the call as the cycle it is - and the session goes on. Every other answer
 * is live-verified.
 */
public class SqlFunctionCycleTest extends MeasuredCellsTestSupport {

    /**
     * A call into a cycle no CREATE refused fails on both sides: the account abandons it as an internal
     * failure of its own, and this engine refuses it as the cycle it is.
     */
    private void assertRefused(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED "), answered);
    }

    @Test
    public void aCycleIsRefusedWhenTheFunctionIsCreated() {
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (v NUMBER)");
        assertAnswer(
            "Function FV successfully created.",
            "CREATE OR REPLACE FUNCTION fv() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t'");
        assertAnswer(
            "View VV successfully created.",
            "CREATE OR REPLACE VIEW vv AS SELECT fv() AS c");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: FV",
            "CREATE OR REPLACE FUNCTION fv() RETURNS NUMBER AS 'SELECT MAX(c) FROM vv'");
        assertAnswer(
            "0",
            "SELECT fv() AS boom");
        assertAnswer(
            "0",
            "SELECT c FROM vv");
        assertAnswer(
            "Function R1 successfully created.",
            "CREATE OR REPLACE FUNCTION r1(n NUMBER) RETURNS NUMBER AS 'n'");
        assertAnswer(
            "Function R2 successfully created.",
            "CREATE OR REPLACE FUNCTION r2(n NUMBER) RETURNS NUMBER AS 'r1(n)'");
        assertAnswer(
            "R1 successfully dropped.",
            "DROP FUNCTION r1(NUMBER)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FUNCTION r2(NUMBER) RENAME TO r1");
        assertRefused(
            "SELECT r1(1)");
        assertAnswer(
            "Function MB successfully created.",
            "CREATE OR REPLACE FUNCTION mb(n NUMBER) RETURNS NUMBER AS 'n'");
        assertAnswer(
            "Function MA successfully created.",
            "CREATE OR REPLACE FUNCTION ma(n NUMBER) RETURNS NUMBER AS 'mb(n)'");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: MB",
            "CREATE OR REPLACE FUNCTION mb(n NUMBER) RETURNS NUMBER AS 'ma(n)'");
        assertAnswer(
            "1",
            "SELECT ma(1)");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: SELFR",
            "CREATE OR REPLACE FUNCTION selfr(n NUMBER) RETURNS NUMBER AS 'IFF(n <= 0, 0, selfr(n - 1) + 1)'");
        assertAnswer(
            "Function QB successfully created.",
            "CREATE OR REPLACE FUNCTION qb() RETURNS NUMBER AS '1'");
        assertAnswer(
            "Function QA successfully created.",
            "CREATE OR REPLACE FUNCTION qa() RETURNS NUMBER AS 'test_db.test_schema.qb()'");
        assertAnswer(
            "Function QB successfully created.",
            "CREATE OR REPLACE FUNCTION qb() RETURNS NUMBER AS 'test_db.test_schema.qa()'");
        assertRefused(
            "SELECT qa()");
        assertAnswer(
            "still usable",
            "SELECT 'still usable'");
    }

    @Test
    public void throughViewsAndTableFunctions() {
        assertAnswer(
            "Table T successfully created.",
            "CREATE OR REPLACE TABLE t (v NUMBER)");
        assertAnswer(
            "Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS 'SELECT COUNT(*) FROM t'");
        assertAnswer(
            "View V1 successfully created.",
            "CREATE OR REPLACE VIEW v1 AS SELECT f1() AS c");
        assertAnswer(
            "View V2 successfully created.",
            "CREATE OR REPLACE VIEW v2 AS SELECT c FROM v1");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: F1",
            "CREATE OR REPLACE FUNCTION f1() RETURNS NUMBER AS 'SELECT MAX(c) FROM v2'");
        assertAnswer(
            "Function G1 successfully created.",
            "CREATE OR REPLACE FUNCTION g1(n NUMBER) RETURNS NUMBER AS 'n'");
        assertAnswer(
            "Function G2 successfully created.",
            "CREATE OR REPLACE FUNCTION g2(n NUMBER) RETURNS NUMBER AS 'g1(n)'");
        assertAnswer(
            "Function G3 successfully created.",
            "CREATE OR REPLACE FUNCTION g3(n NUMBER) RETURNS NUMBER AS 'g2(n)'");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: G1",
            "CREATE OR REPLACE FUNCTION g1(n NUMBER) RETURNS NUMBER AS 'g3(n) + 1'");
        assertAnswer(
            "Function TF1 successfully created.",
            "CREATE OR REPLACE FUNCTION tf1() RETURNS TABLE (c NUMBER) AS 'SELECT 1'");
        assertAnswer(
            "Function TF2 successfully created.",
            "CREATE OR REPLACE FUNCTION tf2() RETURNS TABLE (c NUMBER) AS 'SELECT c FROM TABLE(tf1())'");
        assertAnswer(
            "REFUSED Detected a cycle in SQL UDF: TF1",
            "CREATE OR REPLACE FUNCTION tf1() RETURNS TABLE (c NUMBER) AS 'SELECT c FROM TABLE(tf2())'");
        assertAnswer(
            "still usable",
            "SELECT 'still usable'");
    }
}
