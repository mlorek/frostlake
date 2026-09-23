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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF query body that reads the routine's own parameters is typed at CREATE with those parameters in scope,
 * each as declared: a parameter wins over a same-named column, while a qualified name still reads the column. Its
 * names are resolved so too, so a name nothing declares is refused where the body writes it, and a comparison or a
 * set operation the parameter's type cannot meet is refused as the account refuses it. Every cell is live-verified.
 */
public class SqlUdfQueryBodyParameterTest extends BaseDatabaseTest {

    private static String incompatible(final String declared, final String actual) {
        return "Declared return type '" + declared + "' is incompatible with actual return type '" + actual + "'";
    }

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT, d DATE, s VARCHAR(5))");
        engine.execute("INSERT INTO t VALUES (1, '2020-01-01', 'abc'), (2, '2021-01-01', 'de')");
    }

    @Test
    public void aQueryBodyIsTypedWithItsParametersAsDeclared() {
        assertCells(new String[][] {
            {"CREATE FUNCTION p2(x DATE) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p4(x INT) RETURNS DATE AS 'SELECT MAX(n) + x FROM t'", incompatible("DATE", "NUMBER(38,0)")},
            {"CREATE FUNCTION p6(x DATE) RETURNS INT AS 'SELECT x'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p7(x DATE) RETURNS INT AS 'SELECT x + 1 FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p9(x DATE) RETURNS INT AS 'SELECT x, n FROM t'",
                incompatible("NUMBER(38,0)", "ROW(DATE, NUMBER(38,0))")},
            {"CREATE FUNCTION p20(x DATE) RETURNS INT AS 'SELECT (SELECT x) FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p21(x DATE) RETURNS INT AS 'WITH c AS (SELECT x AS y FROM t) SELECT y FROM c'",
                incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p31(x DATE) RETURNS INT AS 'SELECT x FROM t t2'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p32(X DATE) RETURNS INT AS 'select x from t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p14(x INT) RETURNS VARCHAR AS 'SELECT x FROM t'",
                incompatible("VARCHAR(134217728)", "NUMBER(38,0)")},
            {"CREATE FUNCTION p15(x INT) RETURNS VARCHAR AS 'SELECT x + n FROM t'",
                incompatible("VARCHAR(134217728)", "NUMBER(38,0)")},
            {"CREATE FUNCTION p16(x NUMBER(10,2)) RETURNS VARCHAR AS 'SELECT x * 2 FROM t'",
                incompatible("VARCHAR(134217728)", "NUMBER(11,2)")},
            // A result of the declared type's family is created, and a parameter read only in a filter or an order
            // types nothing.
            {"CREATE FUNCTION p3(x DATE) RETURNS INT AS 'SELECT n FROM t WHERE d = x'", "created"},
            {"CREATE FUNCTION p8(x DATE) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x'", "created"},
            {"CREATE FUNCTION p28(x DATE) RETURNS INT AS 'SELECT n FROM t ORDER BY x LIMIT 1'", "created"},
            {"CREATE FUNCTION p29(x DATE) RETURNS VARCHAR AS 'SELECT s FROM t WHERE d = x'", "created"},
        });
    }

    @Test
    public void aParameterWinsOverASameNamedColumn() {
        assertCells(new String[][] {
            {"CREATE FUNCTION p1(n DATE) RETURNS INT AS 'SELECT n FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p10(n DATE) RETURNS INT AS 'SELECT MAX(n) FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION p22(n DATE) RETURNS DATE AS 'SELECT n FROM t'", "created"},
            {"CREATE FUNCTION p23(n DATE) RETURNS DATE AS 'SELECT n FROM t WHERE 1 = 0'", "created"},
            // A qualified name reads the column.
            {"CREATE FUNCTION p5(n DATE) RETURNS INT AS 'SELECT t.n FROM t'", "created"},
        });
        assertEquals("[null]", String.valueOf(engine.executeQuery("SELECT p23('2024-01-01')").getRows().get(0)
            .getValues()));
    }

    @Test
    public void aParameterIsSpelledAsItsDeclaredType() {
        assertCells(new String[][] {
            {"CREATE FUNCTION q11(x VARCHAR) RETURNS INT AS 'SELECT x FROM t'",
                incompatible("NUMBER(38,0)", "VARCHAR(134217728)")},
            {"CREATE FUNCTION q12(x VARCHAR(5)) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(5)")},
            {"CREATE FUNCTION q13(x NUMBER(10,2)) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "NUMBER(10,2)")},
            {"CREATE FUNCTION q14(x FLOAT) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "FLOAT")},
            {"CREATE FUNCTION q15(x TIMESTAMP) RETURNS INT AS 'SELECT x FROM t'",
                incompatible("NUMBER(38,0)", "TIMESTAMP_NTZ(9)")},
            {"CREATE FUNCTION q16(x BINARY) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "BINARY(67108864)")},
            {"CREATE FUNCTION q17(x CHAR) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(1)")},
            {"CREATE FUNCTION q18(x TEXT) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(134217728)")},
            {"CREATE FUNCTION q19(x VARBINARY) RETURNS INT AS 'SELECT x FROM t'",
                incompatible("NUMBER(38,0)", "BINARY(67108864)")},
            {"CREATE FUNCTION q20(x BINARY(10)) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "BINARY(10)")},
            {"CREATE FUNCTION q21(x DOUBLE) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "FLOAT")},
            {"CREATE FUNCTION q22(x NUMBER) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "NUMBER(38,0)")},
            {"CREATE FUNCTION q23(x DECIMAL(5,1)) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "NUMBER(5,1)")},
            {"CREATE FUNCTION q24(x TIMESTAMP_LTZ) RETURNS INT AS 'SELECT x FROM t'",
                incompatible("NUMBER(38,0)", "TIMESTAMP_LTZ(9)")},
            {"CREATE FUNCTION q25(x TIMESTAMP_TZ(3)) RETURNS INT AS 'SELECT x FROM t'",
                incompatible("NUMBER(38,0)", "TIMESTAMP_TZ(3)")},
            {"CREATE FUNCTION q26(x ARRAY) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "ARRAY")},
            {"CREATE FUNCTION q27(x VARIANT) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "VARIANT")},
            {"CREATE FUNCTION q28(x OBJECT) RETURNS INT AS 'SELECT x:a FROM t'", incompatible("NUMBER(38,0)", "VARIANT")},
            {"CREATE FUNCTION q29(x BOOLEAN) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "BOOLEAN")},
            {"CREATE FUNCTION q30(x TIME) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "TIME(9)")},
            {"CREATE FUNCTION q31(x TIME(3)) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "TIME(3)")},
            {"CREATE FUNCTION q32(x VARCHAR) RETURNS INT AS 'SELECT x || ''a'' FROM t'",
                incompatible("NUMBER(38,0)", "VARCHAR(134217728)")},
            {"CREATE FUNCTION q33(x VARCHAR(3)) RETURNS INT AS 'SELECT x || ''ab'' FROM t'",
                incompatible("NUMBER(38,0)", "VARCHAR(5)")},
            {"CREATE FUNCTION q34(x INT DEFAULT 5) RETURNS DATE AS 'SELECT x FROM t'", incompatible("DATE", "NUMBER(38,0)")},
        });
    }

    @Test
    public void aNameNothingDeclaresIsRefusedWhereTheBodyWritesIt() {
        assertCells(new String[][] {
            {"CREATE FUNCTION r1(x INT) RETURNS INT AS 'SELECT x, nosuch FROM t'",
                "SQL compilation error: error line 1 at position 11|invalid identifier 'NOSUCH'"},
            {"CREATE FUNCTION r2(x INT) RETURNS INT AS 'SELECT x FROM t WHERE nosuch = 1'",
                "SQL compilation error: error line 1 at position 23|invalid identifier 'NOSUCH'"},
            {"CREATE FUNCTION r3(x INT) RETURNS INT AS 'SELECT n FROM t WHERE x = 1 AND nosuch = 2'",
                "SQL compilation error: error line 1 at position 33|invalid identifier 'NOSUCH'"},
            {"CREATE FUNCTION r4(x DATE) RETURNS INT AS 'SELECT n FROM t WHERE nosuch = x'",
                "SQL compilation error: error line 1 at position 23|invalid identifier 'NOSUCH'"},
            // A quoted name is no parameter's.
            {"CREATE FUNCTION r5(\"x\" DATE) RETURNS INT AS 'SELECT \"x\" FROM t'",
                "SQL compilation error: error line 1 at position 8|invalid identifier '\"x\"'"},
            {"CREATE FUNCTION r6(\"x\" DATE) RETURNS INT AS 'SELECT x FROM t'", incompatible("NUMBER(38,0)", "DATE")},
        });
    }

    @Test
    public void whatAParameterCannotMeetIsRefusedAsTheAccountRefusesIt() {
        assertCells(new String[][] {
            {"CREATE FUNCTION s1(x DATE) RETURNS INT AS 'SELECT x UNION ALL SELECT n FROM t'",
                "SQL compilation error:|inconsistent data type for result columns for set operator input branches,"
                    + " expected NUMBER(38,0), got DATE"},
            {"CREATE FUNCTION s2(n DATE) RETURNS INT AS 'SELECT d FROM t WHERE n = 1'",
                "SQL compilation error:|Can not convert parameter '1' of type [NUMBER(1,0)] into expected type [DATE]"},
            {"CREATE FUNCTION s3(x DATE) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE x = 1'",
                "SQL compilation error:|Can not convert parameter '1' of type [NUMBER(1,0)] into expected type [DATE]"},
            {"CREATE FUNCTION s4(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE x = d'",
                "SQL compilation error:|Can not convert parameter 'T.D' of type [DATE] into expected type [NUMBER(38,0)]"},
            {"CREATE FUNCTION s5(x DATE) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE x = d'", "created"},
            {"CREATE FUNCTION s6(x VARCHAR) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE x = 1'", "created"},
        });
    }

    private static String unconvertible(final String operand, final String type, final String expected) {
        return "SQL compilation error:|Can not convert parameter '" + operand + "' of type [" + type
            + "] into expected type [" + expected + "]";
    }

    @Test
    public void aParameterTheComparisonConvertsIsNamedAsTheAccountNamesIt() {
        final String parameter = "CORRELATION(null)";
        assertCells(new String[][] {
            {"CREATE FUNCTION f1(x BOOLEAN) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x'",
                unconvertible(parameter, "BOOLEAN", "DATE")},
            {"CREATE FUNCTION f2(x DATE) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE n = x'",
                unconvertible(parameter, "DATE", "NUMBER(38,0)")},
            {"CREATE FUNCTION f3(x TIMESTAMP) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE n = x'",
                unconvertible(parameter, "TIMESTAMP_NTZ(9)", "NUMBER(38,0)")},
            {"CREATE FUNCTION f4(x FLOAT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x'",
                unconvertible(parameter, "FLOAT", "DATE")},
            {"CREATE FUNCTION f5(x NUMBER(10,2)) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x'",
                unconvertible(parameter, "NUMBER(10,2)", "DATE")},
            {"CREATE FUNCTION f6(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d IN (x)'",
                unconvertible(parameter, "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION f8(x INT, y DATE) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE y = x'",
                unconvertible(parameter, "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION f9(x ARRAY) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE n = x'",
                unconvertible(parameter, "ARRAY", "NUMBER(38,0)")},
            {"CREATE FUNCTION f10(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d > x'",
                unconvertible(parameter, "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe10(x INT) RETURNS INT AS 'SELECT CASE WHEN d = x THEN 1 END FROM t'",
                unconvertible(parameter, "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe28(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d IS DISTINCT FROM x'",
                unconvertible(parameter, "NUMBER(38,0)", "DATE")},
            // The plan reads the parameter as it reads a column, inside whatever holds it.
            {"CREATE FUNCTION pe1(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x + 1'",
                unconvertible("CORRELATION(null) + 1", "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe19(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = x * 2'",
                unconvertible("CORRELATION(null) * 2", "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe2(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = ABS(x)'",
                unconvertible("ABS(CORRELATION(null))", "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe3(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = -x'",
                unconvertible("NEGATE(CORRELATION(null))", "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe13(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = COALESCE(x, 1)'",
                unconvertible("IFNULL(CORRELATION(null), 1)", "NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION pe25(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = (SELECT x)'",
                unconvertible("(SELECT CORRELATION(null) AS \"X\" FROM (VALUES (null)) DUAL)", "NUMBER(38,0)", "DATE")},
            // A typed NULL the body writes itself is the plan's typed NULL.
            {"CREATE FUNCTION pe23(x INT) RETURNS INT AS 'SELECT COUNT(*) FROM t WHERE d = CAST(NULL AS INT)'",
                unconvertible("SYSTEM$NULL_TO_FIXED(null)", "NUMBER(38,0)", "DATE")},
        });
    }
}
