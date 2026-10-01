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
 * A SQL UDF body that reads as a parenthesised list of values is a ROW, which no declared type takes, so it is refused
 * at CREATE naming the ROW's values' types: the bare word NULL and a call folding to it as NULL, a scalar subquery by
 * its query, and a query of several columns as a ROW of its own. A query body's untyped NULL column is spelled NULL too,
 * and meets every declared type on its own. Every cell is live-verified.
 */
public class SqlUdfRowBodyTest extends BaseDatabaseTest {

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String row(final String declared, final String values) {
        return "Declared return type '" + declared + "' is incompatible with actual return type 'ROW(" + values + ")'";
    }

    /** Each {name, signature and RETURNS, body, expected} cell. */
    private void assertBodies(final String[][] cells) {
        for (final String[] cell : cells) {
            final String ddl = "CREATE FUNCTION " + cell[0] + " AS $$" + cell[1] + "$$";
            assertEquals(cell[2], create(ddl), ddl);
        }
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT, d DATE, s VARCHAR(5))");
        engine.execute("INSERT INTO t VALUES (1, '2020-01-01', 'abc'), (2, '2021-01-01', 'de')");
    }

    @Test
    public void aNullValueIsSpelledNull() {
        assertBodies(new String[][] {
            {"r1() RETURNS INT", "NULL, 1", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r2(x INT) RETURNS INT", "x, NULL", row("NUMBER(38,0)", "NUMBER(38,0), NULL")},
            {"r3() RETURNS INT", "NULL, NULL", row("NUMBER(38,0)", "NULL, NULL")},
            {"r16() RETURNS INT", "IFF(TRUE, NULL, NULL), 1", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r18() RETURNS VARIANT", "NULL, 1", row("VARIANT", "NULL, NUMBER(1,0)")},
            {"r19() RETURNS INT", "NULL, 1);", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r20() RETURNS INT", "((NULL, 1))", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r34() RETURNS INT", "ARRAY_CONSTRUCT(NULL), NULL", row("NUMBER(38,0)", "ARRAY, NULL")},
            // A NULL that is typed is its type.
            {"r10() RETURNS INT", "NULL::INT, 1", row("NUMBER(38,0)", "NUMBER(38,0), NUMBER(1,0)")},
            {"r11() RETURNS INT", "CAST(NULL AS DATE), 1", row("NUMBER(38,0)", "DATE, NUMBER(1,0)")},
            {"r17() RETURNS INT", "NULL + 1, 2", row("NUMBER(38,0)", "NUMBER(19,0), NUMBER(1,0)")},
            {"r35() RETURNS INT", "TO_DATE(NULL), 1", row("NUMBER(38,0)", "DATE, NUMBER(1,0)")},
        });
    }

    @Test
    public void aScalarSubqueryValueIsTypedByItsQuery() {
        assertBodies(new String[][] {
            {"r4() RETURNS INT", "(SELECT 1), 2", row("NUMBER(38,0)", "NUMBER(1,0), NUMBER(1,0)")},
            {"r5() RETURNS INT", "(SELECT MAX(n) FROM t), 1", row("NUMBER(38,0)", "NUMBER(38,0), NUMBER(1,0)")},
            {"r6() RETURNS INT", "1, (SELECT 2)", row("NUMBER(38,0)", "NUMBER(1,0), NUMBER(1,0)")},
            {"r7() RETURNS INT", "(SELECT NULL), 1", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r8(x DATE) RETURNS INT", "(SELECT x), 1", row("NUMBER(38,0)", "DATE, NUMBER(1,0)")},
            {"r9() RETURNS INT", "(SELECT s FROM t LIMIT 1), 'a'", row("NUMBER(38,0)", "VARCHAR(5), VARCHAR(1)")},
            {"r12() RETURNS INT", "(SELECT d FROM t LIMIT 1), 1", row("NUMBER(38,0)", "DATE, NUMBER(1,0)")},
            {"r13() RETURNS INT", "EXISTS (SELECT 1), 1", row("NUMBER(38,0)", "BOOLEAN, NUMBER(1,0)")},
            {"r14(x INT) RETURNS INT", "x IN (SELECT n FROM t), 1", row("NUMBER(38,0)", "BOOLEAN, NUMBER(1,0)")},
            {"r15() RETURNS INT", "(SELECT COUNT(*) FROM t), 1", row("NUMBER(38,0)", "NUMBER(18,0), NUMBER(1,0)")},
            {"r21() RETURNS INT", "(SELECT 1 UNION SELECT 2), 1", row("NUMBER(38,0)", "NUMBER(1,0), NUMBER(1,0)")},
            {"r22() RETURNS INT", "(SELECT n FROM t), 1", row("NUMBER(38,0)", "NUMBER(38,0), NUMBER(1,0)")},
            {"r23() RETURNS INT", "(SELECT n, d FROM t), 1",
                row("NUMBER(38,0)", "ROW(NUMBER(38,0), DATE), NUMBER(1,0)")},
            {"r32() RETURNS INT", "(SELECT 1), (SELECT 'a')", row("NUMBER(38,0)", "NUMBER(1,0), VARCHAR(1)")},
            {"r36() RETURNS INT", "(SELECT NULL::DATE), 1", row("NUMBER(38,0)", "DATE, NUMBER(1,0)")},
            {"r37(x INT) RETURNS INT", "(SELECT x + 1), NULL", row("NUMBER(38,0)", "NUMBER(38,0), NULL")},
            {"r38() RETURNS INT", "(SELECT 1 FROM t WHERE 1 = 0), 2", row("NUMBER(38,0)", "NUMBER(1,0), NUMBER(1,0)")},
            {"r24() RETURNS INT", "COUNT(*), 1", row("NUMBER(38,0)", "NUMBER(18,0), NUMBER(1,0)")},
        });
    }

    @Test
    public void aValueCallingAFunctionIsTypedByItsReturnType() {
        assertEquals("created", create("CREATE FUNCTION mk1() RETURNS DATE AS $$CURRENT_DATE$$"));
        assertEquals(row("NUMBER(38,0)", "DATE, NUMBER(1,0)"), create("CREATE FUNCTION r25() RETURNS INT AS $$mk1(), 1$$"));
    }

    @Test
    public void aValuesNameIsResolvedFirst() {
        assertBodies(new String[][] {
            {"r26() RETURNS INT", "(SELECT nosuch FROM t), 1",
                "SQL compilation error: error line 1 at position 9|invalid identifier 'NOSUCH'"},
            {"r28() RETURNS INT", "NULL, nosuch", "SQL compilation error: error line 1 at position 7|invalid identifier 'NOSUCH'"},
            {"r27() RETURNS INT", "(SELECT 1 FROM nosuchtable), 1",
                hinted("SQL compilation error:|Object 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE' does not exist or not authorized.")},
            {"r39() RETURNS INT", "1, (SELECT 1 +)",
                "Compilation of SQL UDF failed: SQL compilation error:|syntax error line 1 at position 5 unexpected 'SELECT'."},
        });
    }

    @Test
    public void aQueryBodysUntypedNullColumnIsSpelledNullAndMeetsEveryType() {
        assertBodies(new String[][] {
            {"q8() RETURNS INT", "SELECT NULL, NULL", row("NUMBER(38,0)", "NULL, NULL")},
            {"q11() RETURNS INT", "SELECT IFF(TRUE, NULL, NULL), 1", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"q15(x INT) RETURNS INT", "SELECT x, NULL FROM t", row("NUMBER(38,0)", "NUMBER(38,0), NULL")},
            {"r29() RETURNS INT", "SELECT NULL, 1", row("NUMBER(38,0)", "NULL, NUMBER(1,0)")},
            {"r30() RETURNS INT", "SELECT 1, NULL", row("NUMBER(38,0)", "NUMBER(1,0), NULL")},
            {"r31() RETURNS INT", "SELECT NULL", "created"},
            {"q5() RETURNS INT", "SELECT NULL FROM t", "created"},
            {"q6() RETURNS DATE", "SELECT NULL UNION ALL SELECT NULL", "created"},
            {"q7() RETURNS INT", "NULL", "created"},
            {"q9() RETURNS INT", "(SELECT NULL)", "created"},
            {"q10() RETURNS INT", "SELECT COALESCE(NULL, NULL)", "created"},
            {"q14() RETURNS BOOLEAN", "SELECT NULL", "created"},
            // A branch that is typed types the column.
            {"q13() RETURNS INT", "SELECT s FROM t WHERE 1 = 0 UNION ALL SELECT NULL",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'VARCHAR(5)'"},
        });
    }
}
