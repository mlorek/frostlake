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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MEMOIZABLE on CREATE FUNCTION: a scalar SQL UDF takes it, with or without arguments, and SHOW FUNCTIONS
 * and INFORMATION_SCHEMA.FUNCTIONS report it. Its place among the options is a syntax rule, and anything
 * but a scalar SQL UDF over non-semi-structured arguments refuses it with live's own sentences, judged in
 * live's order. The word stays usable as a name. Every cell is live-verified.
 */
public class MemoizableFunctionTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql, final int column) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(column));
    }

    @Test
    public void aScalarSqlUdfTakesMemoizable() {
        final String[] creates = {
            "CREATE OR REPLACE FUNCTION STORE_PROFIT() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE "
                + "AS $$ SELECT 'FOO' $$",
            "CREATE OR REPLACE FUNCTION MEMO_ARG(x NUMBER) RETURNS NUMBER LANGUAGE SQL "
                + "MEMOIZABLE AS $$ x + 1 $$",
            "CREATE OR REPLACE FUNCTION m5() RETURNS VARCHAR MEMOIZABLE AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m9() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE COMMENT = "
                + "'c' AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE SECURE FUNCTION m10() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE AS "
                + "$$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m11() RETURNS VARCHAR LANGUAGE SQL IMMUTABLE MEMOIZABLE "
                + "AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m18(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE "
                + "AS $$ x $$",
            "CREATE OR REPLACE FUNCTION m20() RETURNS VARCHAR LANGUAGE SQL VOLATILE MEMOIZABLE "
                + "AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m21() RETURNS VARCHAR NOT NULL LANGUAGE SQL MEMOIZABLE "
                + "AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m22() RETURNS VARCHAR LANGUAGE SQL CALLED ON NULL INPUT "
                + "MEMOIZABLE AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION m27(x NUMBER, y NUMBER) RETURNS NUMBER LANGUAGE SQL "
                + "MEMOIZABLE AS $$ x + y $$",
            "CREATE OR REPLACE FUNCTION m29() RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS $$ "
                + "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES $$",
            "CREATE OR REPLACE TEMPORARY FUNCTION t1() RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS "
                + "$$ 1 $$",
            "CREATE FUNCTION IF NOT EXISTS t2() RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION t3() RETURNS NUMBER STRICT MEMOIZABLE AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION t4() RETURNS NUMBER RETURNS NULL ON NULL INPUT IMMUTABLE "
                + "MEMOIZABLE COMMENT = 'x' AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION t5() RETURNS NUMBER LANGUAGE SQL memoizable AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION t6() RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS '1'",
            "CREATE OR REPLACE FUNCTION a3(x DATE) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS $$ "
                + "1 $$",
            "CREATE OR REPLACE FUNCTION a4(x TIMESTAMP_NTZ) RETURNS NUMBER LANGUAGE SQL "
                + "MEMOIZABLE AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION a5(x BOOLEAN) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS "
                + "$$ 1 $$",
            "CREATE OR REPLACE FUNCTION a6(x FLOAT) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS $$ "
                + "1 $$",
            "CREATE OR REPLACE FUNCTION a7(x BINARY) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS "
                + "$$ 1 $$",
            "CREATE OR REPLACE FUNCTION a8(x GEOGRAPHY) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE "
                + "AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION a9(x VARCHAR(10)) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE "
                + "AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION a10(x TIME) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS $$ "
                + "1 $$",
            "CREATE OR REPLACE FUNCTION r1() RETURNS ARRAY LANGUAGE SQL MEMOIZABLE AS $$ SELECT "
                + "ARRAY_CONSTRUCT(1) $$",
            "CREATE OR REPLACE FUNCTION g1() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE COMMENT = "
                + "'hello' AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE SECURE FUNCTION g3() RETURNS VARCHAR STRICT IMMUTABLE MEMOIZABLE "
                + "AS $$ SELECT 'A' $$",
            "CREATE OR REPLACE FUNCTION g5(x NUMBER) RETURNS NUMBER MEMOIZABLE AS 'x + 1'",
            "CREATE OR REPLACE FUNCTION e7() RETURNS GEOGRAPHY LANGUAGE SQL MEMOIZABLE AS $$ "
                + "TO_GEOGRAPHY('POINT(1 1)') $$",
            "CREATE OR REPLACE FUNCTION e8() RETURNS VECTOR(INT, 3) LANGUAGE SQL MEMOIZABLE AS "
                + "$$ [1,2,3]::VECTOR(INT, 3) $$",
            "CREATE OR REPLACE FUNCTION e9(x VECTOR(INT, 3)) RETURNS NUMBER LANGUAGE SQL "
                + "MEMOIZABLE AS $$ 1 $$",
            "CREATE OR REPLACE FUNCTION e10() RETURNS BINARY LANGUAGE SQL MEMOIZABLE AS $$ "
                + "TO_BINARY('AB', 'HEX') $$",
        };
        for (final String create : creates) {
            engine.execute(create);
        }
        assertEquals("FOO", scalar("SELECT STORE_PROFIT()", 0));
        assertEquals("2", scalar("SELECT MEMO_ARG(1)", 0));
        assertEquals("1", scalar("SELECT t4(), t6()", 0));
        assertEquals("1", scalar("SELECT t4(), t6()", 1));
        assertEquals("A", scalar("SELECT g3(), g5(1)", 0));
        assertEquals("2", scalar("SELECT g3(), g5(1)", 1));
    }

    @Test
    public void itsPlaceAmongTheOptionsIsASyntaxRule() {
        assertRefused("CREATE OR REPLACE FUNCTION m6() RETURNS VARCHAR MEMOIZABLE LANGUAGE SQL AS $$ "
            + "SELECT 'A' $$",
            "syntax error line 1 at position 68 unexpected 'SQL'.");
        assertRefused("CREATE OR REPLACE FUNCTION m8() RETURNS VARCHAR LANGUAGE SQL COMMENT = 'c' "
            + "MEMOIZABLE AS $$ SELECT 'A' $$",
            "syntax error line 1 at position 86 unexpected 'AS'.");
        assertRefused("CREATE OR REPLACE FUNCTION m12() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE IMMUTABLE "
            + "AS $$ SELECT 'A' $$",
            "syntax error line 1 at position 83 unexpected 'AS'.");
        assertRefused("CREATE OR REPLACE FUNCTION m13() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE MEMOIZABLE "
            + "AS $$ SELECT 'A' $$",
            "syntax error line 1 at position 84 unexpected 'AS'.");
        assertRefused("CREATE OR REPLACE FUNCTION m16() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.10' HANDLER = 'h' MEMOIZABLE AS $$def h(): return 'A'$$",
            "syntax error line 1 at position 115 unexpected 'AS'.");
        assertRefused("CREATE OR REPLACE FUNCTION m17() RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.h' "
            + "MEMOIZABLE AS $$class H { public static String h() { return \"A\"; } }$$",
            "syntax error line 1 at position 90 unexpected 'AS'.");
        assertRefused("CREATE OR REPLACE FUNCTION m23() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE CALLED ON "
            + "NULL INPUT AS $$ SELECT 'A' $$",
            "syntax error line 1 at position 80 unexpected 'ON'.");
        assertRefused("CREATE OR REPLACE FUNCTION m24() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 'A' $$ "
            + "MEMOIZABLE",
            "syntax error line 1 at position 82 unexpected 'MEMOIZABLE'.");
        assertRefused("CREATE OR REPLACE PROCEDURE q1() RETURNS VARCHAR MEMOIZABLE LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'A'; END $$",
            "syntax error line 1 at position 69 unexpected 'SQL'.");
    }

    @Test
    public void onlyAScalarSqlUdfOverPlainArgumentsMayBeMemoizable() {
        assertRefused("CREATE OR REPLACE FUNCTION m14() RETURNS TABLE (a VARCHAR) LANGUAGE SQL MEMOIZABLE "
            + "AS $$ SELECT 'A' $$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE FUNCTION m15() RETURNS VARCHAR LANGUAGE JAVASCRIPT MEMOIZABLE AS "
            + "$$ return 'A'; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE PROCEDURE p19() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE AS $$ "
            + "BEGIN RETURN 'A'; END $$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE FUNCTION m28(x ARRAY) RETURNS NUMBER LANGUAGE SQL MEMOIZABLE AS "
            + "$$ ARRAY_SIZE(x) $$",
            "Unsupported data type 'ARRAY'.");
        assertRefused("CREATE OR REPLACE FUNCTION t8() RETURNS TABLE (a NUMBER) MEMOIZABLE AS $$ SELECT 1 "
            + "$$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE FUNCTION t9(x NUMBER) RETURNS TABLE (a NUMBER) LANGUAGE SQL "
            + "MEMOIZABLE AS $$ SELECT x $$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE FUNCTION t12() RETURNS NUMBER LANGUAGE JAVA MEMOIZABLE HANDLER = "
            + "'H.h' AS $$class H { public static int h() { return 1; } }$$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION t13() RETURNS NUMBER LANGUAGE PYTHON MEMOIZABLE "
            + "RUNTIME_VERSION = '3.10' HANDLER = 'h' AS $$def h(): return 1$$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION t15() RETURNS NUMBER LANGUAGE JAVASCRIPT MEMOIZABLE AS "
            + "$$ return 1; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION t16() RETURNS NUMBER LANGUAGE JAVASCRIPT STRICT "
            + "MEMOIZABLE AS $$ return 1; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION t17(x ARRAY) RETURNS NUMBER MEMOIZABLE AS $$ 1 $$",
            "Unsupported data type 'ARRAY'.");
        assertRefused("CREATE OR REPLACE FUNCTION t18(x OBJECT, y ARRAY) RETURNS NUMBER LANGUAGE SQL "
            + "MEMOIZABLE AS $$ 1 $$",
            "Unsupported data type 'OBJECT'.");
        assertRefused("CREATE OR REPLACE FUNCTION e1() RETURNS TABLE (a VARCHAR) LANGUAGE JAVASCRIPT "
            + "MEMOIZABLE AS $$ {processRow: function (row, rowWriter, context) {}} $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION e2(x ARRAY) RETURNS VARCHAR LANGUAGE JAVASCRIPT "
            + "MEMOIZABLE AS $$ return 'a'; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION e3(x ARRAY) RETURNS VARIANT LANGUAGE SQL MEMOIZABLE AS "
            + "$$ x $$",
            "SQL compilation error: Memoizable function does not support VARIANT return type.");
        assertRefused("CREATE OR REPLACE FUNCTION e4(x ARRAY) RETURNS TABLE (a NUMBER) LANGUAGE SQL "
            + "MEMOIZABLE AS $$ SELECT 1 $$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE FUNCTION e5() RETURNS VARIANT LANGUAGE JAVASCRIPT MEMOIZABLE AS "
            + "$$ return 1; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
        assertRefused("CREATE OR REPLACE FUNCTION e6(x VARIANT) RETURNS OBJECT LANGUAGE SQL MEMOIZABLE AS "
            + "$$ OBJECT_CONSTRUCT() $$",
            "SQL compilation error: Memoizable function does not support OBJECT return type.");
        assertRefused("CREATE OR REPLACE FUNCTION r2() RETURNS VARIANT LANGUAGE SQL MEMOIZABLE AS $$ "
            + "SELECT 1::VARIANT $$",
            "SQL compilation error: Memoizable function does not support VARIANT return type.");
        assertRefused("CREATE OR REPLACE FUNCTION r3() RETURNS OBJECT LANGUAGE SQL MEMOIZABLE AS $$ SELECT "
            + "OBJECT_CONSTRUCT('a', 1) $$",
            "SQL compilation error: Memoizable function does not support OBJECT return type.");
        assertRefused("CREATE OR REPLACE PROCEDURE q2() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE EXECUTE AS "
            + "CALLER AS $$ BEGIN RETURN 'A'; END $$",
            "SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero "
            + "arguments.");
        assertRefused("CREATE OR REPLACE PROCEDURE q4() RETURNS VARCHAR LANGUAGE JAVASCRIPT MEMOIZABLE AS "
            + "$$ return 'a'; $$",
            "SQL compilation error: Memoizable function supports only SQL language.");
    }

    @Test
    public void theFlagIsReported() {
        engine.execute("CREATE OR REPLACE FUNCTION plain() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 'A' $$");
        engine.execute("CREATE OR REPLACE FUNCTION memo() RETURNS VARCHAR LANGUAGE SQL MEMOIZABLE "
            + "AS $$ SELECT 'A' $$");
        final ResultSet memo = engine.executeQuery("SHOW USER FUNCTIONS LIKE 'MEMO'");
        assertEquals("Y", cell(memo, memo.getRows().get(0), "is_memoizable"));
        final ResultSet plain = engine.executeQuery("SHOW USER FUNCTIONS LIKE 'PLAIN'");
        assertEquals("N", cell(plain, plain.getRows().get(0), "is_memoizable"));
        final String flags = "SELECT is_memoizable FROM information_schema.functions WHERE function_name = ";
        assertEquals("YES", scalar(flags + "'MEMO'", 0));
        assertEquals("NO", scalar(flags + "'PLAIN'", 0));
        engine.execute("ALTER FUNCTION memo() SET COMMENT = 'changed'");
        assertEquals("YES", scalar(flags + "'MEMO'", 0));
        engine.execute("CREATE OR REPLACE FUNCTION memo() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 'A' $$");
        assertEquals("NO", scalar(flags + "'MEMO'", 0));
    }

    @Test
    public void memoizableStaysAName() {
        engine.execute("CREATE OR REPLACE TABLE memoizable (memoizable INT)");
        engine.execute("INSERT INTO memoizable VALUES (7)");
        assertEquals("7", scalar("SELECT memoizable AS memoizable FROM memoizable", 0));
        assertEquals("7", scalar("SELECT m.memoizable FROM memoizable m", 0));
        assertEquals("1", scalar("SELECT 1 AS memoizable", 0));
    }
}
