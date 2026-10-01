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
 * The phases a CREATE FUNCTION or CREATE PROCEDURE is judged in: the options' order, then the widths of the types
 * it declares, then its language and a function's EXECUTE AS; then the database and the schema; then, for a plain
 * CREATE, an existing routine of the same signature; and only then the properties, the handler against the
 * signature, the signature's repeated names and the body. IF NOT EXISTS over an existing routine still judges all of
 * it. Every cell is live-verified.
 */
public class RoutineCheckPrecedenceTest extends BaseDatabaseTest {

    private static final String JAVA_INT_FOR_TEXT = "Snowflake type TEXT[LOB](134217728){nullable} is not supported "
        + "for Java return type int in function ";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String noSchema() {
        return hinted("SQL compilation error:\nSchema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not authorized.");
    }

    private static String exists(final String name) {
        return "SQL compilation error:\nObject '" + name + "' already exists.";
    }

    private static String badWidth(final int position) {
        return "SQL compilation error: error line 1 at position " + position
            + "\nInvalid character length: 0. Must be between 1 and 134,217,728.";
    }

    @Test
    public void theSchemaAndTheDatabaseAreJudgedBeforeTheHandlerAndTheSignature() {
        assertEquals(noSchema(), refusalOf("CREATE FUNCTION nosuch_schema.rw_e4() RETURNS INT LANGUAGE JAVA "
            + "HANDLER='H.h' AS $$ class H { public static int h(int a) { return 1; } } $$"));
        assertEquals(hinted("SQL compilation error:\nDatabase 'NOSUCH_DB' does not exist or not authorized."),
            refusalOf("CREATE FUNCTION nosuch_db.nosuch_schema.rw_e3() RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' "
                + "AS $$ class H { public static int h() { return 1; } } $$"));
        assertEquals(noSchema(), refusalOf("CREATE FUNCTION IF NOT EXISTS nosuch_schema.rw_e2() RETURNS VARCHAR "
            + "LANGUAGE JAVA HANDLER='H.h' AS $$ class H { public static int h() { return 1; } } $$"));
        assertEquals(noSchema(), refusalOf("CREATE OR REPLACE FUNCTION nosuch_schema.rw_e5() RETURNS VARCHAR "
            + "LANGUAGE JAVA HANDLER='H.h' AS $$ class H { public static int h() { return 1; } } $$"));
        assertEquals(noSchema(), refusalOf("CREATE PROCEDURE nosuch_schema.rw_e7() RETURNS NUMBER(10,2) LANGUAGE JAVA "
            + "RUNTIME_VERSION='11' PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ "
            + "import com.snowflake.snowpark_java.*; class H { public static double h(Session s) { return 1.5; } } $$"));
        assertEquals(noSchema(), refusalOf("CREATE FUNCTION nosuch_schema.rx_1(a INT, a INT) RETURNS INT AS '1'"));
        assertEquals(noSchema(), refusalOf("CREATE OR REPLACE FUNCTION nosuch_schema.rx_5(a INT, a INT) RETURNS INT "
            + "AS '1'"));
        assertEquals(noSchema(), refusalOf("CREATE FUNCTION nosuch_schema.rx_6(a INT, a INT) RETURNS INT LANGUAGE JAVA "
            + "HANDLER='H.h' AS $$ class H { public static int h(int a) { return 1; } } $$"));
        assertEquals(noSchema(), refusalOf("CREATE FUNCTION nosuch_schema.rx_8() RETURNS TABLE (x FLOAT) "
            + "AS 'SELECT 1'"));
    }

    @Test
    public void theLanguageAndAFunctionsInvocationTypeComeBeforeTheSchema() {
        assertEquals("SQL compilation error:\nUnknown function language: COBOL.",
            refusalOf("CREATE FUNCTION nosuch_schema.rx_9() RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals("Unsupported invocation type for function.",
            refusalOf("CREATE FUNCTION nosuch_schema.rx_10() RETURNS INT EXECUTE AS CALLER AS '1'"));
    }

    @Test
    public void aPlainCreateOverAnExistingRoutineIsRefusedBeforeItsHandlerAndSignature() {
        engine.execute("CREATE FUNCTION rw_e1() RETURNS INT AS '1'");
        assertEquals(exists("RW_E1"), refusalOf("CREATE FUNCTION rw_e1() RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' "
            + "AS $$ class H { public static int h() { return 1; } } $$"));
        assertEquals(exists("RW_E1"), refusalOf("CREATE FUNCTION rw_e1() RETURNS INT LANGUAGE JAVA HANDLER='H.h' "
            + "AS $$ class H { public static int h(int a) { return 1; } } $$"));
        assertEquals(exists("RW_E1"), refusalOf("CREATE FUNCTION rw_e1() RETURNS INT LANGUAGE JAVA HANDLER='H.h' "
            + "AS $$ class H { syntax error $$"));
        engine.execute("CREATE PROCEDURE rw_e6() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        assertEquals(exists("RW_E6"), refusalOf("CREATE PROCEDURE rw_e6() RETURNS NUMBER(10,2) LANGUAGE JAVA "
            + "RUNTIME_VERSION='11' PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ "
            + "import com.snowflake.snowpark_java.*; class H { public static double h(Session s) { return 1.5; } } $$"));
        engine.execute("CREATE FUNCTION rx_2(a INT, b INT) RETURNS INT AS '1'");
        assertEquals(exists("RX_2"), refusalOf("CREATE FUNCTION rx_2(a INT, a INT) RETURNS INT AS '1'"));
        assertEquals(exists("RX_2"), refusalOf("CREATE FUNCTION rx_2(a INT, a INT) RETURNS VARCHAR LANGUAGE JAVA "
            + "HANDLER='H.h' AS $$ class H { public static int h(int a, int b) { return 1; } } $$"));
        assertEquals(exists("RX_2"), refusalOf("CREATE FUNCTION rx_2(a INT, b INT) RETURNS TABLE (x FLOAT) "
            + "AS 'SELECT 1'"));
    }

    @Test
    public void ifNotExistsOverAnExistingRoutineStillJudgesTheHandler() {
        engine.execute("CREATE FUNCTION rw_e1() RETURNS INT AS '1'");
        assertEquals(JAVA_INT_FOR_TEXT + "RW_E1 with handler H.h", refusalOf("CREATE FUNCTION IF NOT EXISTS rw_e1() "
            + "RETURNS VARCHAR LANGUAGE JAVA HANDLER='H.h' AS $$ class H { public static int h() { return 1; } } $$"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT rw_e1()").getRows().get(0).getValue(0)));
    }

    @Test
    public void aRefusedReplacementKeepsTheRoutineItWouldReplace() {
        engine.execute("CREATE OR REPLACE FUNCTION rv_n7() RETURNS INT AS '7'");
        assertEquals("SQL compilation error:\nUnknown function language: COBOL.",
            refusalOf("CREATE OR REPLACE FUNCTION rv_n7() RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals("7", String.valueOf(engine.executeQuery("SELECT rv_n7()").getRows().get(0).getValue(0)));
        engine.execute("CREATE OR REPLACE PROCEDURE rv_n8() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 8; END $$");
        assertEquals("Snowflake type FIXED[SB16](10,2){nullable} is not supported for Java return type double in "
            + "function RV_N8 with handler H.h", refusalOf("CREATE OR REPLACE PROCEDURE rv_n8() RETURNS NUMBER(10,2) "
            + "LANGUAGE JAVA RUNTIME_VERSION='11' PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ "
            + "import com.snowflake.snowpark_java.*; class H { public static double h(Session s) { return 1.5; } } $$"));
        assertEquals("8", String.valueOf(engine.executeQuery("CALL rv_n8()").getRows().get(0).getValue(0)));
    }

    @Test
    public void aDeclaredWidthIsJudgedBeforeTheLanguageAndTheSchema() {
        assertEquals(badWidth(30), refusalOf("CREATE FUNCTION bp9(a VARCHAR(0)) RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals(badWidth(31), refusalOf("CREATE FUNCTION bp10(a VARCHAR(0)) RETURNS INT EXECUTE AS CALLER "
            + "AS '1'"));
        assertEquals(badWidth(38), refusalOf("CREATE FUNCTION wo2() RETURNS VARCHAR(0) LANGUAGE COBOL AS 'x'"));
        assertEquals(badWidth(47), refusalOf("CREATE FUNCTION wo3() RETURNS TABLE (x VARCHAR(0)) LANGUAGE COBOL "
            + "AS 'x'"));
        assertEquals(badWidth(31), refusalOf("CREATE PROCEDURE wo4(a VARCHAR(0)) RETURNS INT LANGUAGE COBOL AS 'x'"));
        assertEquals(badWidth(44), refusalOf("CREATE FUNCTION nosuch_schema.wo6(a VARCHAR(0)) RETURNS INT AS '1'"));
        assertEquals(badWidth(30), refusalOf("CREATE FUNCTION wo9(a VARCHAR(0), a INT) RETURNS INT AS '1'"));
        assertEquals(badWidth(49), refusalOf("CREATE FUNCTION wo10(a VECTOR(INT, 2), b VARCHAR(0)) RETURNS INT "
            + "AS '1'"));
        assertEquals("SQL compilation error: error line 1 at position 30\nInvalid number precision: 39. Must be "
            + "between 0 and 38.", refusalOf("CREATE FUNCTION wo15(a NUMBER(39,0)) RETURNS INT LANGUAGE COBOL "
            + "AS 'x'"));
        engine.execute("CREATE FUNCTION wo16(a INT) RETURNS INT AS '1'");
        assertEquals(badWidth(45), refusalOf("CREATE FUNCTION IF NOT EXISTS wo16(a VARCHAR(0)) RETURNS INT "
            + "LANGUAGE COBOL AS 'x'"));
        assertEquals("SQL compilation error:\nUnknown function language: COBOL.",
            refusalOf("CREATE FUNCTION wo14() RETURNS INT LANGUAGE COBOL AS 'SELECT CAST(1 AS VARCHAR(0))'"));
    }

    @Test
    public void theOptionsOrderIsJudgedBeforeADeclaredWidth() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 65 unexpected 'AS'.",
            refusalOf("CREATE FUNCTION wo1(a VARCHAR(0)) RETURNS INT COMMENT='c' STRICT AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 70 unexpected 'SQL'.",
            refusalOf("CREATE FUNCTION wo7(a VARCHAR(0)) RETURNS INT LANGUAGE COBOL LANGUAGE SQL AS 'x'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 79 unexpected 'COMMENT'.",
            refusalOf("CREATE PROCEDURE wo11(a VARCHAR(0)) RETURNS INT LANGUAGE SQL EXECUTE AS CALLER COMMENT='c' "
                + "AS $$ BEGIN RETURN 1; END $$"));
    }
}
