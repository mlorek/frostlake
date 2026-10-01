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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A SQL table function's query body is typed against its declared {@code RETURNS TABLE (...)} columns at CREATE:
 * the column count first, then each column in order against its declared type's family, a TIME's or timestamp's
 * precision included. The declared type is spelled in full and the column named as it resolves. Every cell is
 * live-verified.
 */
public class SqlTableFunctionReturnTypeTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String incompatible(final String declared, final String column, final String actual) {
        return "Declared return type '" + declared + "' for column '" + column
            + "' is incompatible with actual return type '" + actual + "'";
    }

    @Test
    public void aColumnOfAnotherFamilyIsRefused() {
        assertEquals(incompatible("FLOAT", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION t1() RETURNS TABLE (x FLOAT) AS 'SELECT 1.0'"));
        assertEquals(incompatible("VARCHAR(134217728)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION t2() RETURNS TABLE (x VARCHAR) AS 'SELECT 1'"));
        assertEquals(incompatible("NUMBER(38,0)", "X", "VARCHAR(1)"),
            refusalOf("CREATE FUNCTION t3() RETURNS TABLE (x INT) AS 'SELECT ''a'''"));
        assertEquals(incompatible("DATE", "X", "TIMESTAMP_LTZ(9)"),
            refusalOf("CREATE FUNCTION t6() RETURNS TABLE (x DATE) AS 'SELECT CURRENT_TIMESTAMP()'"));
        assertEquals(incompatible("VARIANT", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION t7() RETURNS TABLE (x VARIANT) AS 'SELECT 1'"));
        assertEquals(incompatible("VARIANT", "X", "ARRAY"),
            refusalOf("CREATE FUNCTION m3() RETURNS TABLE (x VARIANT) AS 'SELECT ARRAY_CONSTRUCT(1)'"));
        assertEquals(incompatible("ARRAY", "X", "VARIANT"),
            refusalOf("CREATE FUNCTION m18() RETURNS TABLE (x ARRAY) AS 'SELECT PARSE_JSON(''[1]'')'"));
        assertEquals(incompatible("VARCHAR(134217728)", "X", "BOOLEAN"),
            refusalOf("CREATE FUNCTION m2() RETURNS TABLE (x VARCHAR) AS 'SELECT TRUE'"));
        assertEquals(incompatible("TIMESTAMP_LTZ(9)", "X", "TIMESTAMP_NTZ(9)"),
            refusalOf("CREATE FUNCTION m1() RETURNS TABLE (x TIMESTAMP_LTZ) AS "
                + "'SELECT CURRENT_TIMESTAMP()::TIMESTAMP_NTZ'"));
        assertEquals(incompatible("GEOGRAPHY", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m44() RETURNS TABLE (x GEOGRAPHY) AS 'SELECT 1'"));
        assertEquals(incompatible("VECTOR(INT, 2)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION n17() RETURNS TABLE (x VECTOR(INT, 2)) AS 'SELECT 1'"));
    }

    @Test
    public void theDeclaredTypeIsSpelledInFullAndTheColumnAsItResolves() {
        assertEquals(incompatible("FLOAT", "x", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m17() RETURNS TABLE (\"x\" FLOAT) AS 'SELECT 1'"));
        assertEquals(incompatible("FLOAT", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m30() RETURNS TABLE (x DOUBLE) AS 'SELECT 1'"));
        assertEquals(incompatible("VARCHAR(3)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m35() RETURNS TABLE (x CHAR(3)) AS 'SELECT 1'"));
        assertEquals(incompatible("TIMESTAMP_NTZ(9)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m36() RETURNS TABLE (x TIMESTAMP) AS 'SELECT 1'"));
        assertEquals(incompatible("BINARY(67108864)", "X", "VARCHAR(2)"),
            refusalOf("CREATE FUNCTION m19() RETURNS TABLE (x BINARY) AS 'SELECT ''ab'''"));
        assertEquals(incompatible("NUMBER(5,2)", "X", "VARCHAR(1)"),
            refusalOf("CREATE FUNCTION m39() RETURNS TABLE (x DECIMAL(5,2)) AS 'SELECT ''a'''"));
        engine.execute("CREATE TABLE tn (n NUMBER(5,0), s VARCHAR(20), d DATE)");
        assertEquals(incompatible("VARCHAR(134217728)", "X", "NUMBER(5,0)"),
            refusalOf("CREATE FUNCTION m8() RETURNS TABLE (x VARCHAR) AS 'SELECT n FROM tn'"));
        assertEquals(incompatible("NUMBER(38,0)", "Y", "VARCHAR(20)"),
            refusalOf("CREATE FUNCTION m24() RETURNS TABLE (x VARCHAR, y INT) AS 'SELECT s, s FROM tn'"));
        assertEquals(incompatible("FLOAT", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m15() RETURNS TABLE (x FLOAT) AS 'SELECT 1 UNION ALL SELECT 2'"));
    }

    @Test
    public void aTimesPrecisionMustMatchWhereANumbersOrATextsWidthNeedNot() {
        assertEquals(incompatible("TIMESTAMP_NTZ(3)", "X", "TIMESTAMP_NTZ(9)"),
            refusalOf("CREATE FUNCTION n19() RETURNS TABLE (x TIMESTAMP_NTZ(3)) AS "
                + "'SELECT ''2020-01-01''::TIMESTAMP_NTZ(9)'"));
        assertEquals(incompatible("TIMESTAMP_NTZ(9)", "X", "TIMESTAMP_NTZ(3)"),
            refusalOf("CREATE FUNCTION p1() RETURNS TABLE (x TIMESTAMP_NTZ(9)) AS "
                + "'SELECT ''2020-01-01''::TIMESTAMP_NTZ(3)'"));
        assertEquals(incompatible("TIME(9)", "X", "TIME(0)"),
            refusalOf("CREATE FUNCTION p14() RETURNS TABLE (x TIME) AS 'SELECT ''01:02:03''::TIME(0)'"));
        assertEquals("Declared return type 'TIMESTAMP_NTZ(3)' is incompatible with actual return type "
            + "'TIMESTAMP_NTZ(9)'",
            refusalOf("CREATE FUNCTION p6() RETURNS TIMESTAMP_NTZ(3) AS '''2020-01-01''::TIMESTAMP_NTZ(9)'"));
        engine.execute("CREATE TABLE tp (s VARCHAR(20), n NUMBER(12,4))");
        engine.execute("CREATE FUNCTION p8() RETURNS TABLE (x NUMBER(10,2)) AS 'SELECT 1.555'");
        engine.execute("CREATE FUNCTION p10() RETURNS TABLE (x VARCHAR(2)) AS 'SELECT s FROM tp'");
        engine.execute("CREATE FUNCTION p12() RETURNS TABLE (x NUMBER(5,0)) AS 'SELECT n FROM tp'");
        engine.execute("CREATE FUNCTION p13() RETURNS TABLE (x TIMESTAMP_NTZ(6)) AS "
            + "'SELECT ''2020-01-01''::TIMESTAMP_NTZ(6)'");
    }

    @Test
    public void theColumnCountIsJudgedFirst() {
        assertEquals("Mismatch between declared return signature column count (1) and actual column count (2)",
            refusalOf("CREATE FUNCTION n1() RETURNS TABLE (x FLOAT) AS 'SELECT 1, 2'"));
        assertEquals("Mismatch between declared return signature column count (2) and actual column count (1)",
            refusalOf("CREATE FUNCTION m5() RETURNS TABLE (x INT, y INT) AS 'SELECT 1'"));
        assertEquals(incompatible("FLOAT", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION m6() RETURNS TABLE (x FLOAT, y VARCHAR) AS 'SELECT 1, 2'"));
    }

    @Test
    public void theSignatureAndMemoizableRulesComeBeforeIt() {
        assertEquals("Argument 'A' repeats in the function signature.",
            refusalOf("CREATE FUNCTION n5(a INT, a INT) RETURNS TABLE (x FLOAT) AS 'SELECT 1'"));
        assertEquals("Return signature contains a duplicate column name 'X'.",
            refusalOf("CREATE FUNCTION n6() RETURNS TABLE (x FLOAT, x INT) AS 'SELECT 1, 2'"));
        assertEquals("SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero arguments.",
            refusalOf("CREATE FUNCTION n15() RETURNS TABLE (x INT) MEMOIZABLE AS 'SELECT ''a'''"));
    }

    @Test
    public void aBodyOfTheDeclaredFamiliesIsCreated() {
        engine.execute("CREATE FUNCTION c1() RETURNS TABLE (x NUMBER(10,2)) AS 'SELECT 1.5'");
        engine.execute("CREATE FUNCTION c2() RETURNS TABLE (x FLOAT) AS 'SELECT 1.5::FLOAT'");
        engine.execute("CREATE FUNCTION c3() RETURNS TABLE (x FLOAT, y INT) AS 'SELECT 1::FLOAT, 2.5'");
        engine.execute("CREATE FUNCTION m9() RETURNS TABLE (x DATE) AS 'SELECT NULL'");
        engine.execute("CREATE FUNCTION m20() RETURNS TABLE (x INT) AS 'SELECT 1.5'");
        engine.execute("CREATE FUNCTION m45() RETURNS TABLE (x NUMBER(38,0)) AS 'SELECT 1e0'");
        engine.execute("CREATE FUNCTION e3() RETURNS TABLE (x INT, y DATE) AS 'SELECT NULL, NULL'");
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM TABLE(c3())").getRows().get(0)
            .getValue(0)));
    }

    @Test
    public void aRefusedReplacementLeavesTheExistingFunction() {
        engine.execute("CREATE OR REPLACE FUNCTION r2() RETURNS TABLE (x INT) AS 'SELECT 1'");
        assertEquals(incompatible("VARCHAR(134217728)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE OR REPLACE FUNCTION r2() RETURNS TABLE (x VARCHAR) AS 'SELECT 1'"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT * FROM TABLE(r2())").getRows().get(0)
            .getValue(0)));
        engine.execute("CREATE OR REPLACE FUNCTION r1() RETURNS INT AS '1'");
        assertEquals("Declared return type 'VARCHAR(134217728)' is incompatible with actual return type 'NUMBER(1,0)'",
            refusalOf("CREATE OR REPLACE FUNCTION r1() RETURNS VARCHAR AS '1'"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT r1()").getRows().get(0).getValue(0)));
        assertEquals(incompatible("VARCHAR(134217728)", "X", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION IF NOT EXISTS r2() RETURNS TABLE (x VARCHAR) AS 'SELECT 1'"));
    }

    @Test
    public void aBodyClosingItsFrameIsJudgedByWhatTheFrameHolds() {
        assertEquals(incompatible("NUMBER(38,0)", "A", "VARCHAR(1)"),
            refusalOf("CREATE FUNCTION f1() RETURNS TABLE (a INT) AS 'SELECT ''x'' AS a);'"));
        assertEquals("Mismatch between declared return signature column count (1) and actual column count (2)",
            refusalOf("CREATE FUNCTION f2() RETURNS TABLE (a INT) AS 'SELECT 1 AS a, 2 AS b);'"));
        assertEquals("Mismatch between declared return signature column count (2) and actual column count (1)",
            refusalOf("CREATE FUNCTION f3() RETURNS TABLE (a INT, b INT) AS 'SELECT 1 AS a);'"));
        assertEquals(incompatible("DATE", "A", "NUMBER(1,0)"),
            refusalOf("CREATE FUNCTION f4() RETURNS TABLE (a DATE) AS '(SELECT 1 AS a));'"));
        assertEquals(incompatible("TIMESTAMP_NTZ(9)", "A", "TIMESTAMP_NTZ(3)"),
            refusalOf("CREATE FUNCTION f5() RETURNS TABLE (a TIMESTAMP_NTZ(9)) AS "
                + "'SELECT ''2020-01-01''::TIMESTAMP_NTZ(3) AS a);'"));
        assertEquals(incompatible("TIME(3)", "X", "TIME(9)"),
            refusalOf("CREATE FUNCTION f6() RETURNS TABLE (x TIME(3)) AS 'SELECT ''01:02:03''::TIME(9));'"));
        engine.execute("CREATE FUNCTION f7() RETURNS TABLE (a INT) AS 'SELECT 1 AS a) ;  garbage'");
        engine.execute("CREATE FUNCTION f8() RETURNS TABLE (a VARCHAR, b INT) AS 'SELECT ''x'' AS a, 1 AS b);'");
        engine.execute("CREATE FUNCTION f9() RETURNS TABLE (a INT) AS 'SELECT NULL AS a);'");
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT a FROM TABLE(f7())").getRows().get(0)
            .getValue(0)));
        engine.execute("CREATE OR REPLACE FUNCTION f10() RETURNS TABLE (a INT) AS 'SELECT 1 AS a'");
        assertEquals(incompatible("DATE", "A", "NUMBER(1,0)"),
            refusalOf("CREATE OR REPLACE FUNCTION f10() RETURNS TABLE (a DATE) AS 'SELECT 1 AS a);'"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT a FROM TABLE(f10())").getRows().get(0)
            .getValue(0)));
    }
}
