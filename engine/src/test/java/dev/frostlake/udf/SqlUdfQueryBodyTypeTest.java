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
 * A SQL UDF whose body is a query — one reading a relation, a set operation, a CTE, a scalar subquery — is typed
 * at CREATE by the result the query compiles to: its one column must share the declared RETURNS type's family,
 * and several columns are a ROW no declared type takes. Each column is spelled as it carries its type, a table's
 * bare VARCHAR as VARCHAR(16777216) and a cast to bare VARCHAR as VARCHAR(134217728). Every cell is live-verified.
 */
public class SqlUdfQueryBodyTypeTest extends BaseDatabaseTest {

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

    @Test
    public void aQueryBodyIsTypedByItsResult() {
        engine.execute("CREATE TABLE t (d DATE, n INT, s VARCHAR, s5 VARCHAR(5), b BINARY)");
        final String[][] cells = {
            {"CREATE FUNCTION q1() RETURNS INT AS 'SELECT d FROM t'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION q2() RETURNS INT AS 'SELECT CURRENT_DATE UNION ALL SELECT CURRENT_DATE'",
                incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION q3() RETURNS VARCHAR AS 'SELECT n FROM t'", incompatible("VARCHAR(134217728)", "NUMBER(38,0)")},
            {"CREATE FUNCTION q4() RETURNS DATE AS 'WITH c AS (SELECT n FROM t) SELECT n FROM c'",
                incompatible("DATE", "NUMBER(38,0)")},
            {"CREATE FUNCTION q5() RETURNS INT AS 'SELECT (SELECT d FROM t)'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION q6() RETURNS INT AS 'SELECT * FROM (SELECT d FROM t)'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION q7() RETURNS INT AS 'SELECT CURRENT_DATE FROM (SELECT 1)'", incompatible("NUMBER(38,0)", "DATE")},
            {"CREATE FUNCTION q8() RETURNS INT AS 'SELECT s FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(16777216)")},
            {"CREATE FUNCTION q9() RETURNS INT AS 'SELECT s::VARCHAR FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(134217728)")},
            {"CREATE FUNCTION q10() RETURNS INT AS 'SELECT s5 FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(5)")},
            {"CREATE FUNCTION q11() RETURNS INT AS 'SELECT b FROM t'", incompatible("NUMBER(38,0)", "BINARY(8388608)")},
            {"CREATE FUNCTION q12() RETURNS INT AS 'SELECT ''abc'' FROM t'", incompatible("NUMBER(38,0)", "VARCHAR(3)")},
            {"CREATE FUNCTION q13() RETURNS INT AS 'SELECT n, s5, d FROM t'",
                incompatible("NUMBER(38,0)", "ROW(NUMBER(38,0), VARCHAR(5), DATE)")},
            {"CREATE FUNCTION q14() RETURNS VARIANT AS 'SELECT n, s FROM t'",
                incompatible("VARIANT", "ROW(NUMBER(38,0), VARCHAR(16777216))")},
            {"CREATE FUNCTION q15() RETURNS INT AS 'SELECT n FROM t UNION ALL SELECT d FROM t'",
                "SQL compilation error:|inconsistent data type for result columns for set operator input branches,"
                    + " expected DATE, got NUMBER(38,0)"},
            // A result of the declared type's family is created.
            {"CREATE FUNCTION ok1() RETURNS INT AS 'SELECT COUNT(*) FROM t'", "created"},
            {"CREATE FUNCTION ok2() RETURNS VARCHAR AS 'SELECT s FROM t ORDER BY 1 LIMIT 1'", "created"},
            {"CREATE FUNCTION ok3() RETURNS NUMBER AS 'SELECT n FROM t'", "created"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }
}
