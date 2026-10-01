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
 * A SQL or JavaScript routine runs an inline body, so it takes no IMPORTS and no PACKAGES, an empty list
 * included, function or procedure alike: IMPORTS is refused first, then a RUNTIME_VERSION or a HANDLER, then
 * PACKAGES, whatever order they are written in. They are judged once the schema has resolved and an existing
 * routine of the same signature has been refused, and before anything in the signature or the body. A Java
 * routine keeps both, empty or not. Every cell is live-verified.
 */
public class RoutineImportsPackagesTest extends BaseDatabaseTest {

    private static final String IMPORTS = "SQL compilation error:\n"
        + "invalid property 'imports'; feature 'dependency import list' not enabled";
    private static final String PACKAGES = "SQL compilation error:\ninvalid property 'PACKAGES' for 'FUNCTION'";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void anInlineBodyTakesNoImportsNorPackagesEvenEmpty() {
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i1() RETURNS INT IMPORTS=() AS '1'"));
        assertEquals(PACKAGES, refusalOf("CREATE FUNCTION i2() RETURNS INT PACKAGES=() AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i3() RETURNS INT IMPORTS=('@nost/x.jar') AS '1'"));
        assertEquals(PACKAGES, refusalOf("CREATE FUNCTION i4() RETURNS INT PACKAGES=('numpy') AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i16() RETURNS INT LANGUAGE JAVASCRIPT IMPORTS=() "
            + "AS 'return 1;'"));
        assertEquals(PACKAGES, refusalOf("CREATE FUNCTION i17() RETURNS INT LANGUAGE JAVASCRIPT PACKAGES=() "
            + "AS 'return 1;'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i40() RETURNS TABLE (x INT) IMPORTS=() AS 'SELECT 1'"));
        assertEquals(IMPORTS, refusalOf("CREATE SECURE FUNCTION i39() RETURNS INT IMPORTS=() AS '1'"));
    }

    @Test
    public void aProcedureInSqlOrJavaScriptIsRefusedTheSameWay() {
        assertEquals(IMPORTS, refusalOf("CREATE PROCEDURE i27() RETURNS INT LANGUAGE SQL IMPORTS=() "
            + "AS $$ BEGIN RETURN 1; END $$"));
        assertEquals(PACKAGES, refusalOf("CREATE PROCEDURE i28() RETURNS INT LANGUAGE SQL PACKAGES=() "
            + "AS $$ BEGIN RETURN 1; END $$"));
        assertEquals(PACKAGES, refusalOf("CREATE PROCEDURE i29() RETURNS INT LANGUAGE SQL "
            + "PACKAGES=('snowflake-snowpark-python') AS $$ BEGIN RETURN 1; END $$"));
        assertEquals(IMPORTS, refusalOf("CREATE PROCEDURE i30() RETURNS FLOAT LANGUAGE JAVASCRIPT IMPORTS=() "
            + "AS 'return 1;'"));
        assertEquals(PACKAGES, refusalOf("CREATE PROCEDURE i31() RETURNS FLOAT LANGUAGE JAVASCRIPT PACKAGES=() "
            + "AS 'return 1;'"));
        assertEquals(IMPORTS, refusalOf("CREATE PROCEDURE i35() RETURNS INT LANGUAGE SQL IMPORTS=() "
            + "AS 'not a block'"));
        assertEquals(IMPORTS, refusalOf("CREATE PROCEDURE i44() RETURNS INT LANGUAGE SQL COMMENT='c' IMPORTS=() "
            + "EXECUTE AS CALLER AS $$ BEGIN RETURN 1; END $$"));
    }

    @Test
    public void importsComeFirstThenRuntimeVersionAndHandlerThenPackages() {
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i5() RETURNS INT RUNTIME_VERSION='3.11' IMPORTS=() AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i8() RETURNS INT IMPORTS=() HANDLER='h' AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i14() RETURNS INT PACKAGES=() IMPORTS=() AS '1'"));
        assertEquals("SQL compilation error:\ninvalid property 'RUNTIME_VERSION' for 'FUNCTION'",
            refusalOf("CREATE FUNCTION i10() RETURNS INT PACKAGES=() RUNTIME_VERSION='3.11' AS '1'"));
        assertEquals("SQL compilation error:\ninvalid property 'handler' for 'SQL function'",
            refusalOf("CREATE FUNCTION i12() RETURNS INT PACKAGES=() HANDLER='h' AS '1'"));
        assertEquals("SQL compilation error:\ninvalid property 'handler' for 'JAVASCRIPT function'",
            refusalOf("CREATE FUNCTION i46() RETURNS FLOAT LANGUAGE JAVASCRIPT PACKAGES=() HANDLER='h' "
                + "AS 'return 1;'"));
        assertEquals("SQL compilation error:\ninvalid property 'RUNTIME_VERSION' for 'FUNCTION'",
            refusalOf("CREATE PROCEDURE i33() RETURNS INT LANGUAGE SQL RUNTIME_VERSION='3.11' PACKAGES=() "
                + "AS $$ BEGIN RETURN 1; END $$"));
    }

    @Test
    public void theyAreJudgedAfterTheSchemaAndAnExistingRoutineAndBeforeTheSignature() {
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i18(a INT, a INT) RETURNS INT IMPORTS=() AS '1'"));
        assertEquals(PACKAGES, refusalOf("CREATE PROCEDURE i36(a INT, a INT) RETURNS INT LANGUAGE SQL PACKAGES=() "
            + "AS $$ BEGIN RETURN 1; END $$"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i21() RETURNS INT IMPORTS=() AS 'nosuchcol'"));
        assertEquals(PACKAGES, refusalOf("CREATE FUNCTION i21b() RETURNS DATE PACKAGES=() AS '1'"));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
            + "authorized."), refusalOf("CREATE FUNCTION nosuch_schema.i23() RETURNS INT IMPORTS=() AS '1'"));
        engine.execute("CREATE FUNCTION i22() RETURNS INT AS '1'");
        assertEquals("SQL compilation error:\nObject 'I22' already exists.",
            refusalOf("CREATE FUNCTION i22() RETURNS INT IMPORTS=() AS '1'"));
        assertEquals("SQL compilation error:\nObject 'I22' already exists.",
            refusalOf("CREATE FUNCTION i22() RETURNS INT PACKAGES=() AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION IF NOT EXISTS i22() RETURNS INT IMPORTS=() AS '1'"));
        assertEquals(PACKAGES, refusalOf("CREATE FUNCTION IF NOT EXISTS i22() RETURNS INT PACKAGES=('numpy') "
            + "AS '1'"));
        assertEquals(IMPORTS, refusalOf("CREATE OR REPLACE FUNCTION i22() RETURNS INT IMPORTS=() AS '1'"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT i22()").getRows().get(0).getValue(0)));
    }

    @Test
    public void memoizableShapeIsJudgedFirst() {
        assertEquals(IMPORTS, refusalOf("CREATE FUNCTION i20() RETURNS INT MEMOIZABLE IMPORTS=() AS '1'"));
        assertEquals("SQL compilation error: Memoizable keyword only supports Scalar SQL UDF with zero arguments.",
            refusalOf("CREATE FUNCTION i20b() RETURNS TABLE (x INT) MEMOIZABLE PACKAGES=() AS 'SELECT 1'"));
    }

    @Test
    public void aJavaRoutineKeepsEmptyLists() {
        engine.execute("CREATE FUNCTION i41() RETURNS INT LANGUAGE JAVA IMPORTS=() PACKAGES=() HANDLER='H.h' "
            + "AS $$ class H { public static int h() { return 41; } } $$");
        assertEquals("41", String.valueOf(engine.executeQuery("SELECT i41()").getRows().get(0).getValue(0)));
        engine.execute("CREATE FUNCTION rd4() RETURNS INT LANGUAGE JAVA IMPORTS=() HANDLER='H.h' "
            + "AS $$ class H { public static int h() { return 4; } } $$");
        engine.execute("CREATE FUNCTION rd5() RETURNS INT LANGUAGE JAVA PACKAGES=() HANDLER='H.h' "
            + "AS $$ class H { public static int h() { return 5; } } $$");
        assertEquals("5", String.valueOf(engine.executeQuery("SELECT rd5()").getRows().get(0).getValue(0)));
    }
}
