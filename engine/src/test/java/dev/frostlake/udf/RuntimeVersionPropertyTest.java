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
 * A routine's RUNTIME_VERSION must be one its language offers, matched as written, and a routine's properties are
 * judged before its argument names and its body: an inline language's RUNTIME_VERSION or HANDLER, a missing IMPORTS
 * stage, a missing RUNTIME_VERSION, a version the language does not offer, then a missing HANDLER. Only a VECTOR in
 * the signature comes first. Every cell is live-verified.
 */
public class RuntimeVersionPropertyTest extends BaseDatabaseTest {

    private static final String PY_BODY = " HANDLER='h' AS $$\ndef h(): return 1\n$$";

    private static final String JAVA_BODY = " HANDLER='C.f' AS $$ class C { public static int f() { return 1; } } $$";

    private static final String SCALA_BODY = " HANDLER='C.f' AS $$ object C { def f(): Int = 1 } $$";

    /** "created", or the refusal on one line. */
    private String create(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String decommissioned(final String version) {
        return "SQL compilation error: Python runtime version " + version + " is decommissioned."
            + " Please update your code to Python runtime version 3.10 or later.";
    }

    private static String invalid(final String version) {
        return "SQL compilation error:|invalid value '" + version + "' for property 'RUNTIME_VERSION'";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], create(cell[0]), cell[0]);
        }
    }

    @Test
    public void aPythonRoutineTakesOnlyAVersionTheLanguageOffers() {
        assertCells(new String[][] {
            {"CREATE FUNCTION py10() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.10'" + PY_BODY, "created"},
            {"CREATE FUNCTION py11() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11'" + PY_BODY, "created"},
            {"CREATE FUNCTION py12() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.12'" + PY_BODY, "created"},
            {"CREATE FUNCTION py13() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.13'" + PY_BODY, "created"},
            {"CREATE FUNCTION py14() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.14'" + PY_BODY, "created"},
            {"CREATE FUNCTION pyn1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=3.11" + PY_BODY, "created"},
            {"CREATE FUNCTION pyn2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=3.10" + PY_BODY, "created"},
            {"CREATE FUNCTION pyx1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='4.0'" + PY_BODY, invalid("4.0")},
            {"CREATE FUNCTION pyx2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='x'" + PY_BODY, invalid("x")},
            {"CREATE FUNCTION pyx3() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3'" + PY_BODY, invalid("3")},
            {"CREATE FUNCTION pyx4() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='2.7'" + PY_BODY, invalid("2.7")},
            {"CREATE FUNCTION pyx5() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9.1'" + PY_BODY, invalid("3.9.1")},
            {"CREATE FUNCTION pyx6() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=''" + PY_BODY, invalid("")},
            {"CREATE FUNCTION pyx7() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=' 3.11'" + PY_BODY, invalid(" 3.11")},
            {"CREATE FUNCTION pyx8() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11 '" + PY_BODY, invalid("3.11 ")},
            {"CREATE FUNCTION pyx9() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11.2'" + PY_BODY, invalid("3.11.2")},
            {"CREATE FUNCTION pyx10() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.09'" + PY_BODY, invalid("3.09")},
            {"CREATE FUNCTION pyx11() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.7'" + PY_BODY, invalid("3.7")},
            {"CREATE FUNCTION pyx12() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.15'" + PY_BODY, invalid("3.15")},
            {"CREATE FUNCTION pyx13() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.1'" + PY_BODY, invalid("3.1")},
            {"CREATE FUNCTION pyx14() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=3" + PY_BODY, invalid("3")},
            {"CREATE PROCEDURE pyp1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='4.0' PACKAGES=('snowflake-snowpark-python')"
                + " HANDLER='h' AS $$\ndef h(s): return 1\n$$", invalid("4.0")},
            {"CREATE FUNCTION pym() RETURNS INT LANGUAGE PYTHON HANDLER='h' AS $$\ndef h(): return 1\n$$",
                "Property 'runtime_version' must be specified"},
        });
    }

    @Test
    public void aJavaOrScalaRoutineTakesOnlyAVersionTheLanguageOffers() {
        assertCells(new String[][] {
            {"CREATE FUNCTION j11() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='11'" + JAVA_BODY, "created"},
            {"CREATE FUNCTION j17() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='17'" + JAVA_BODY, "created"},
            {"CREATE FUNCTION j21() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='21'" + JAVA_BODY, "created"},
            {"CREATE FUNCTION jn11() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION=11" + JAVA_BODY, "created"},
            {"CREATE FUNCTION jnone() RETURNS INT LANGUAGE JAVA" + JAVA_BODY, "created"},
            {"CREATE FUNCTION jx1() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='1.8'" + JAVA_BODY, invalid("1.8")},
            {"CREATE FUNCTION jx2() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='22'" + JAVA_BODY, invalid("22")},
            {"CREATE FUNCTION jx3() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='11.0'" + JAVA_BODY, invalid("11.0")},
            {"CREATE FUNCTION jx4() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='8'" + JAVA_BODY, invalid("8")},
            {"CREATE PROCEDURE jp8() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='8' PACKAGES=('com.snowflake:snowpark:latest')"
                + " HANDLER='C.f' AS $$ class C { public static int f(com.snowflake.snowpark_java.Session s) { return 1; } } $$", invalid("8")},
            {"CREATE FUNCTION s212() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION='2.12'" + SCALA_BODY, "created"},
            {"CREATE FUNCTION s213() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION='2.13'" + SCALA_BODY, "created"},
            {"CREATE FUNCTION sn212() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION=2.12" + SCALA_BODY, "created"},
            {"CREATE FUNCTION sx1() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION='3'" + SCALA_BODY, invalid("3")},
            {"CREATE FUNCTION sx2() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION='2.11'" + SCALA_BODY, invalid("2.11")},
            {"CREATE PROCEDURE sp211() RETURNS INT LANGUAGE SCALA RUNTIME_VERSION='2.11' PACKAGES=('com.snowflake:snowpark:latest')"
                + " HANDLER='C.f' AS $$ object C { def f(s: com.snowflake.snowpark.Session): Int = 1 } $$", invalid("2.11")},
        });
    }

    @Test
    public void thePropertiesAreJudgedBeforeTheNamesAndTheBody() {
        final String noStage = hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
            + "authorized.");
        assertCells(new String[][] {
            {"CREATE FUNCTION o1(a INT, a INT) RETURNS INT LANGUAGE JAVASCRIPT RUNTIME_VERSION='x' AS $$ return 1; $$",
                "SQL compilation error:|invalid property 'RUNTIME_VERSION' for 'FUNCTION'"},
            {"CREATE FUNCTION o2(a INT, a INT) RETURNS INT LANGUAGE SQL HANDLER='h' AS '1'",
                "SQL compilation error:|invalid property 'handler' for 'SQL function'"},
            {"CREATE FUNCTION o3() RETURNS INT LANGUAGE SQL RUNTIME_VERSION='x' AS 'x y'",
                "SQL compilation error:|invalid property 'RUNTIME_VERSION' for 'FUNCTION'"},
            {"CREATE PROCEDURE o4() RETURNS INT LANGUAGE SQL RUNTIME_VERSION='x' AS $$ BEGIN RETURN 1 END; $$",
                "SQL compilation error:|invalid property 'RUNTIME_VERSION' for 'FUNCTION'"},
            {"CREATE FUNCTION o5(a INT, a INT) RETURNS INT LANGUAGE PYTHON HANDLER='h' AS $$\ndef h(a, b): return 1\n$$",
                "Property 'runtime_version' must be specified"},
            {"CREATE FUNCTION o6(a INT, a INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' AS $$\ndef h(a): return 1\n$$",
                "Property 'handler' must be specified"},
            {"CREATE FUNCTION o7(a INT, a INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h'"
                + " IMPORTS=('@nosuch/x.py') AS $$\ndef h(a): return 1\n$$", noStage},
            {"CREATE FUNCTION o8() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='x' HANDLER='h' IMPORTS=('@nosuch/x.py')"
                + " AS $$\ndef h(): return 1\n$$", noStage},
            {"CREATE FUNCTION o9(a INT, a INT) RETURNS INT LANGUAGE JAVA HANDLER='C.f' IMPORTS=('@nosuch/x.jar')"
                + " AS $$ class C { public static int f(int a, int b) { return 1; } } $$", noStage},
            {"CREATE FUNCTION o10(a INT, a INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='x' HANDLER='h'"
                + " AS $$\ndef h(a): return 1\n$$", invalid("x")},
            {"CREATE FUNCTION o11(a INT, a INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='8' HANDLER='C.f'"
                + " AS $$ class C { public static int f(int a, int b) { return 1; } } $$", invalid("8")},
            {"CREATE PROCEDURE o12(a INT, a INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='x'"
                + " PACKAGES=('snowflake-snowpark-python') HANDLER='h' AS $$\ndef h(s, a, b): return 1\n$$", invalid("x")},
            {"CREATE FUNCTION o13() RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='8' AS $$ class C {} $$", invalid("8")},
            {"CREATE FUNCTION o14(v VECTOR(INT,2)) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION='8' HANDLER='C.f' AS $$ class C {} $$",
                "SQL compilation error:|Unsupported data type 'VECTOR(INT, 2)'."},
            {"CREATE FUNCTION o15(a INT, a INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h'"
                + " AS $$\ndef h(a, b): return 1\n$$", "Argument 'A' repeats in the function signature."},
        });
    }

    @Test
    public void aRetiredPythonVersionIsRefusedByItsOwnSentence() {
        assertCells(new String[][] {
            {"CREATE FUNCTION d1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9'" + PY_BODY, decommissioned("3.9")},
            {"CREATE FUNCTION d2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.8'" + PY_BODY, decommissioned("3.8")},
            {"CREATE FUNCTION d3() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION=3.9" + PY_BODY, decommissioned("3.9")},
            {"CREATE PROCEDURE d4() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9'"
                + " PACKAGES=('snowflake-snowpark-python') HANDLER='h' AS $$\ndef h(s): return 1\n$$",
                decommissioned("3.9")},
            {"CREATE OR REPLACE FUNCTION d5() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9'" + PY_BODY,
                decommissioned("3.9")},
            {"CREATE FUNCTION IF NOT EXISTS d6() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9'" + PY_BODY,
                decommissioned("3.9")},
            {"CREATE FUNCTION d7(\"N\" NUMBER(38,0)) RETURNS ARRAY LANGUAGE PYTHON RUNTIME_VERSION='3.9'"
                + " HANDLER='h' AS $$\ndef h(n): return []\n$$", decommissioned("3.9")},
        });
    }

    /** A version the language no longer runs is refused only once the rest of the properties are in order. */
    @Test
    public void aMissingHandlerIsRefusedBeforeTheRetiredVersion() {
        assertCells(new String[][] {
            {"CREATE FUNCTION dh1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.9' AS $$\ndef h(): return 1\n$$",
                "Property 'handler' must be specified"},
        });
    }
}
