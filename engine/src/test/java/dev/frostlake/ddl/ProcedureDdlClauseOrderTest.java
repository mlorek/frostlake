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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GET_DDL renders a procedure's clauses in the account's order: LANGUAGE, then a handler's RUNTIME_VERSION,
 * PACKAGES and HANDLER, then COMMENT, then EXECUTE AS, then the body. A handler-backed function renders its
 * RUNTIME_VERSION and HANDLER after LANGUAGE the same way. Every cell is live-verified.
 */
public class ProcedureDdlClauseOrderTest extends BaseDatabaseTest {

    @BeforeEach
    public void createDatabase() {
        engine.execute("CREATE OR REPLACE DATABASE PROCEDURE_DDL_ORDER_DB");
    }

    @AfterEach
    public void dropDatabase() {
        engine.execute("DROP DATABASE IF EXISTS PROCEDURE_DDL_ORDER_DB");
    }

    /** A routine's DDL with every newline shown as {@code ~}. */
    private String ddl(final String kind, final String signature) {
        return String.valueOf(engine.executeQuery("SELECT REPLACE(GET_DDL('" + kind + "', '"
            + signature.replace("'", "''") + "'), CHR(10), '~')").getRows().get(0).getValue(0));
    }

    @Test
    public void aCommentIsWrittenBeforeExecuteAs() {
        engine.execute("CREATE OR REPLACE PROCEDURE \"procCase\"() RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'p'; END; $$");
        engine.execute("COMMENT ON PROCEDURE \"procCase\"() IS 'c'");
        assertEquals("CREATE OR REPLACE PROCEDURE \"procCase\"()~RETURNS VARCHAR~LANGUAGE SQL~COMMENT='c'"
            + "~EXECUTE AS OWNER~AS ' BEGIN RETURN ''p''; END; ';", ddl("PROCEDURE", "\"procCase\"()"));

        engine.execute("CREATE OR REPLACE PROCEDURE p2(x INT) RETURNS VARCHAR LANGUAGE SQL COMMENT = 'two' "
            + "EXECUTE AS CALLER AS $$ BEGIN RETURN 'p'; END; $$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"P2\"(\"X\" NUMBER(38,0))~RETURNS VARCHAR~LANGUAGE SQL"
            + "~COMMENT='two'~EXECUTE AS CALLER~AS ' BEGIN RETURN ''p''; END; ';", ddl("PROCEDURE", "p2(INT)"));

        engine.execute("CREATE OR REPLACE PROCEDURE p5() RETURNS VARCHAR LANGUAGE JAVASCRIPT COMMENT = 'five' "
            + "EXECUTE AS CALLER AS $$ return 'x'; $$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"P5\"()~RETURNS VARCHAR~LANGUAGE JAVASCRIPT~COMMENT='five'"
            + "~EXECUTE AS CALLER~AS ' return ''x''; ';", ddl("PROCEDURE", "p5()"));

        engine.execute("CREATE OR REPLACE PROCEDURE p7() RETURNS TABLE (a INT) LANGUAGE SQL COMMENT = 'seven' "
            + "AS $$ DECLARE r RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END; $$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"P7\"()~RETURNS TABLE (\"A\" NUMBER(38,0))~LANGUAGE SQL"
            + "~COMMENT='seven'~EXECUTE AS OWNER"
            + "~AS ' DECLARE r RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(r); END; ';",
            ddl("PROCEDURE", "p7()"));
    }

    @Test
    public void aHandlersPropertiesFollowTheLanguage() {
        engine.execute("CREATE OR REPLACE PROCEDURE p8() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "PACKAGES = ('com.snowflake:snowpark:1.16.2') HANDLER = 'H.run' COMMENT = 'scala' AS $$\n"
            + "object H { def run(session: com.snowflake.snowpark.Session): String = \"x\" }\n$$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"P8\"()~RETURNS VARCHAR~LANGUAGE SCALA~RUNTIME_VERSION = '2.12'"
            + "~PACKAGES = ('com.snowflake:snowpark:1.16.2')~HANDLER = 'H.run'~COMMENT='scala'~EXECUTE AS OWNER"
            + "~AS '~object H { def run(session: com.snowflake.snowpark.Session): String = \"x\" }~';",
            ddl("PROCEDURE", "p8()"));

        engine.execute("CREATE OR REPLACE FUNCTION f2(x INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "HANDLER = 'h' AS $$\ndef h(x):\n    return x\n$$");
        assertEquals("CREATE OR REPLACE FUNCTION \"F2\"(\"X\" NUMBER(38,0))~RETURNS NUMBER(38,0)~LANGUAGE PYTHON"
            + "~RUNTIME_VERSION = '3.11'~HANDLER = 'h'~AS '~def h(x):~    return x~';", ddl("FUNCTION", "f2(INT)"));

        engine.execute("CREATE OR REPLACE FUNCTION f3(x VARCHAR) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' "
            + "HANDLER = 'F.h' AS $$\nclass F { public static String h(String x) { return x; } }\n$$");
        assertEquals("CREATE OR REPLACE FUNCTION \"F3\"(\"X\" VARCHAR)~RETURNS VARCHAR~LANGUAGE JAVA"
            + "~RUNTIME_VERSION = '11'~HANDLER = 'F.h'~AS '~class F { public static String h(String x) { return x; } }~';",
            ddl("FUNCTION", "f3(VARCHAR)"));
    }
}
