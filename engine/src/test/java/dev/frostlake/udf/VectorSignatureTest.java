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
 * A VECTOR may stand in the signature of a SQL or PYTHON function and of a PYTHON procedure, as an argument, a
 * result or a table column. Any other routine — a JAVASCRIPT, JAVA or SCALA function, and a SQL, JAVASCRIPT, JAVA
 * or SCALA procedure — is refused at CREATE as an unsupported data type, naming the first VECTOR as the type is
 * spelled. Only MEMOIZABLE's language rule is judged before it. Every cell is live-verified.
 */
public class VectorSignatureTest extends BaseDatabaseTest {

    private static final String FLOAT3 = "SQL compilation error:|Unsupported data type 'VECTOR(FLOAT, 3)'.";

    private static final String INT2 = "SQL compilation error:|Unsupported data type 'VECTOR(INT, 2)'.";

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

    @Test
    public void aSqlOrPythonRoutineTakesAVector() {
        assertCells(new String[][] {
            {"CREATE FUNCTION sr1() RETURNS VECTOR(FLOAT,3) LANGUAGE SQL AS '[1,2,3]::VECTOR(FLOAT,3)'", "created"},
            {"CREATE FUNCTION sa1(v VECTOR(FLOAT,3)) RETURNS INT LANGUAGE SQL AS '1'", "created"},
            {"CREATE FUNCTION st1() RETURNS TABLE (v VECTOR(FLOAT,3)) LANGUAGE SQL AS 'SELECT [1,2,3]::VECTOR(FLOAT,3)'", "created"},
            {"CREATE FUNCTION sr2() RETURNS VECTOR(INT,16) AS 'NULL'", "created"},
            {"CREATE FUNCTION mm() RETURNS VECTOR(FLOAT,3) MEMOIZABLE AS '[1,2,3]::VECTOR(FLOAT,3)'", "created"},
            {"CREATE TABLE tv (v VECTOR(FLOAT, 3))", "created"},
            {"CREATE FUNCTION pv1() RETURNS VECTOR(FLOAT,3) LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'f'"
                + " AS $$\ndef f():\n    return None\n$$", "created"},
            {"CREATE FUNCTION pv2(v VECTOR(FLOAT,3)) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'f'"
                + " AS $$\ndef f(v):\n    return 1\n$$", "created"},
            {"CREATE PROCEDURE pyp() RETURNS VECTOR(FLOAT,3) LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
                + " PACKAGES = ('snowflake-snowpark-python') HANDLER = 'f' AS $$\ndef f(session):\n    return None\n$$", "created"},
            {"CREATE PROCEDURE pyp2(v VECTOR(FLOAT,3)) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11'"
                + " PACKAGES = ('snowflake-snowpark-python') HANDLER = 'f' AS $$\ndef f(session, v):\n    return 1\n$$", "created"},
        });
    }

    @Test
    public void aJavaScriptJavaOrScalaFunctionIsRefused() {
        assertCells(new String[][] {
            {"CREATE FUNCTION jr18() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jr19(x VECTOR(FLOAT,3)) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jst() RETURNS TABLE (v VECTOR(FLOAT,3)) LANGUAGE JAVASCRIPT AS $${processRow: function (r, w, c) {}}$$", FLOAT3},
            {"CREATE FUNCTION jsa(a VECTOR(FLOAT,3)) RETURNS TABLE (x FLOAT) LANGUAGE JAVASCRIPT"
                + " AS $${processRow: function (r, w, c) {}}$$", FLOAT3},
            {"CREATE OR REPLACE SECURE FUNCTION jss() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jsv7(v VECTOR(INT,2) DEFAULT NULL) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", INT2},
            {"CREATE FUNCTION jv4(a VECTOR( INT , 2 )) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", INT2},
            {"CREATE FUNCTION jv1() RETURNS VECTOR(INT, 2) LANGUAGE JAVA HANDLER = 'C.f'"
                + " AS $$ class C { public static int[] f() { return null; } } $$", INT2},
            {"CREATE FUNCTION ja(v VECTOR(INT,2)) RETURNS INT LANGUAGE JAVA HANDLER = 'C.f'"
                + " AS $$ class C { public static int f(int[] v) { return 1; } } $$", INT2},
            {"CREATE FUNCTION jrt() RETURNS TABLE (v VECTOR(INT,2)) LANGUAGE JAVA HANDLER = 'C' AS $$ class C {} $$", INT2},
            {"CREATE FUNCTION scv() RETURNS VECTOR(FLOAT,3) LANGUAGE SCALA RUNTIME_VERSION = '2.12' HANDLER = 'C.f'"
                + " AS $$ object C { def f(): Array[Float] = null } $$", FLOAT3},
            {"CREATE FUNCTION sv(v VECTOR(INT,2)) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12' HANDLER = 'C.f'"
                + " AS $$ object C { def f(v: Array[Int]): Int = 1 } $$", INT2},
        });
    }

    @Test
    public void aProcedureInAnyLanguageButPythonIsRefused() {
        assertCells(new String[][] {
            {"CREATE PROCEDURE sp1() RETURNS VECTOR(FLOAT,3) LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$", FLOAT3},
            {"CREATE PROCEDURE sp2(v VECTOR(INT,2)) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$", INT2},
            {"CREATE PROCEDURE spt() RETURNS TABLE (v VECTOR(FLOAT,3)) LANGUAGE SQL"
                + " AS $$ BEGIN RETURN TABLE(SELECT [1,2,3]::VECTOR(FLOAT,3)); END; $$", FLOAT3},
            {"CREATE PROCEDURE spv(v VECTOR(INT,2)) RETURNS TABLE () LANGUAGE SQL AS $$ BEGIN RETURN TABLE(SELECT 1); END; $$", INT2},
            {"CREATE PROCEDURE jsp() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return null; $$", FLOAT3},
            {"CREATE PROCEDURE jsp2(v VECTOR(FLOAT,3)) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE PROCEDURE jvp() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVA RUNTIME_VERSION = '11'"
                + " PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'C.f'"
                + " AS $$ class C { public static Object f(com.snowflake.snowpark_java.Session s) { return null; } } $$", FLOAT3},
            {"CREATE PROCEDURE scp() RETURNS VECTOR(FLOAT,3) LANGUAGE SCALA RUNTIME_VERSION = '2.12'"
                + " PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'C.f'"
                + " AS $$ object C { def f(s: com.snowflake.snowpark.Session): String = null } $$", FLOAT3},
        });
    }

    @Test
    public void theFirstVectorIsNamedBeforeAnythingElseIsJudged() {
        engine.execute("CREATE FUNCTION jsok() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        assertCells(new String[][] {
            {"CREATE FUNCTION jb(a VECTOR(INT,2)) RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", INT2},
            {"CREATE FUNCTION jb2(a VECTOR(INT,2), b VECTOR(FLOAT,4)) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", INT2},
            {"CREATE FUNCTION jb3(a VECTOR(INT,2), a INT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$", INT2},
            {"CREATE FUNCTION jo1(a NUMBER) RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jo2(a VECTOR(FLOAT,3)) RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jsvv(v ARRAY) RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION \"delete\"() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE PROCEDURE \"delete\"() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION IF NOT EXISTS jsok() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT AS $$ return 1; $$", FLOAT3},
            {"CREATE FUNCTION jsn(a VECTOR(FLOAT,3)) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ syntax error here ( $$", FLOAT3},
            {"CREATE FUNCTION jsvi(v VECTOR(INT,2)) RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return ?;'", INT2},
            {"CREATE FUNCTION jh(v VECTOR(INT,2)) RETURNS INT LANGUAGE JAVA AS $$ class C {} $$", INT2},
            {"CREATE PROCEDURE sps() RETURNS VECTOR(FLOAT,3) LANGUAGE SQL AS $$ BEGIN RETURN NULL END; $$", FLOAT3},
            {"CREATE PROCEDURE spvq(v VECTOR(INT,2)) RETURNS INT LANGUAGE SQL AS 'SELECT 1'", INT2},
            {"CREATE FUNCTION jm() RETURNS VECTOR(FLOAT,3) LANGUAGE JAVASCRIPT MEMOIZABLE AS $$ return 1; $$",
                "SQL compilation error: Memoizable function supports only SQL language."},
        });
    }
}
