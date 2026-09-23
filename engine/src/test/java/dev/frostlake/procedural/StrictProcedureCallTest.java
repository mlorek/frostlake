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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A procedure declaring STRICT or RETURNS NULL ON NULL INPUT runs no body when a CALL hands it a NULL, a defaulted
 * one included: a SQL procedure's CALL is refused with {@code NULL result in a non-nullable column}, whatever it
 * returns, a JavaScript one answers NULL, and a Java one runs as if the clause were not there. CALLED ON NULL INPUT
 * runs the body, and so does a STRICT procedure given no NULL. A VARIANT holding a JSON null is a value. GET_DDL
 * writes the clause back as STRICT. Every cell is live-verified.
 */
public class StrictProcedureCallTest extends BaseDatabaseTest {

    private static final String REFUSAL = "NULL result in a non-nullable column";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private Object only(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRows().size(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void aNullArgumentRefusesAStrictSqlProcedure() {
        engine.execute("CREATE PROCEDURE s1(a INT) RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s1(NULL)"));
        assertEquals("5", String.valueOf(only("CALL s1(1)")));
        assertEquals(REFUSAL, refusalOf("CALL s1(NULL::INT)"));
        assertEquals(REFUSAL, refusalOf("CALL s1(1 + NULL)"));
        assertEquals(REFUSAL, refusalOf("CALL s1(NULLIF(1, 1))"));
        assertEquals(REFUSAL, refusalOf("CALL s1(a => NULL)"));
        engine.execute("CREATE PROCEDURE s2(a INT) RETURNS INT LANGUAGE SQL RETURNS NULL ON NULL INPUT "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s2(NULL)"));
        assertEquals("5", String.valueOf(only("CALL s2(2)")));
        engine.execute("CREATE PROCEDURE s4(a INT, b INT) RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s4(1, NULL)"));
        engine.execute("CREATE PROCEDURE s9(a INT) RETURNS INT LANGUAGE SQL STRICT IMMUTABLE "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s9(NULL)"));
    }

    @Test
    public void theRefusalHoldsWhateverTheProcedureReturns() {
        engine.execute("CREATE PROCEDURE rw_s2(a INT) RETURNS INT NULL LANGUAGE SQL STRICT AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL rw_s2(NULL)"));
        engine.execute("CREATE PROCEDURE rw_s3(a INT) RETURNS VARCHAR LANGUAGE SQL STRICT "
            + "AS $$ BEGIN RETURN 'x'; END $$");
        assertEquals(REFUSAL, refusalOf("CALL rw_s3(NULL)"));
        engine.execute("CREATE PROCEDURE rw_s7(a INT) RETURNS TABLE (x INT) LANGUAGE SQL STRICT AS $$ DECLARE "
            + "r RESULTSET DEFAULT (SELECT 1 AS x); BEGIN RETURN TABLE(r); END $$");
        assertEquals(REFUSAL, refusalOf("CALL rw_s7(NULL)"));
        engine.execute("CREATE PROCEDURE s14(a INT) RETURNS TABLE () LANGUAGE SQL STRICT AS $$ DECLARE "
            + "r RESULTSET DEFAULT (SELECT 1 AS x); BEGIN RETURN TABLE(r); END $$");
        assertEquals(REFUSAL, refusalOf("CALL s14(NULL)"));
        engine.execute("CREATE PROCEDURE s15(a INT) RETURNS VARCHAR NOT NULL LANGUAGE SQL STRICT "
            + "AS $$ BEGIN RETURN 'x'; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s15(NULL)"));
    }

    @Test
    public void theBodyDoesNotRun() {
        engine.execute("CREATE TABLE s_side (a INT)");
        engine.execute("CREATE PROCEDURE s6(a INT) RETURNS INT LANGUAGE SQL STRICT "
            + "AS $$ BEGIN INSERT INTO s_side VALUES (1); RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s6(NULL)"));
        assertEquals("0", String.valueOf(only("SELECT COUNT(*) FROM s_side")));
    }

    @Test
    public void aDefaultedNullCountsAndAJsonNullDoesNot() {
        engine.execute("CREATE PROCEDURE s13(a INT DEFAULT NULL) RETURNS INT LANGUAGE SQL STRICT "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s13()"));
        assertEquals("5", String.valueOf(only("CALL s13(1)")));
        engine.execute("CREATE PROCEDURE s4v(a VARIANT) RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 5; END $$");
        assertEquals("5", String.valueOf(only("CALL s4v(PARSE_JSON('null'))")));
        assertEquals(REFUSAL, refusalOf("CALL s4v(NULL)"));
        engine.execute("CREATE PROCEDURE s18(a VARCHAR, b INT) RETURNS INT LANGUAGE SQL STRICT "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals("5", String.valueOf(only("CALL s18('', 1)")));
    }

    @Test
    public void calledOnNullInputAndNoNullArgumentRunTheBody() {
        engine.execute("CREATE PROCEDURE rw_s5(a INT) RETURNS INT LANGUAGE SQL CALLED ON NULL INPUT "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals("5", String.valueOf(only("CALL rw_s5(NULL)")));
        engine.execute("CREATE PROCEDURE rw_s8() RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 8; END $$");
        assertEquals("8", String.valueOf(only("CALL rw_s8()")));
        engine.execute("CREATE PROCEDURE s20(a INT) RETURNS INT LANGUAGE SQL STRICT EXECUTE AS CALLER "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals(REFUSAL, refusalOf("CALL s20(NULL)"));
        engine.execute("CREATE OR REPLACE PROCEDURE s20(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 6; END $$");
        assertEquals("6", String.valueOf(only("CALL s20(NULL)")));
    }

    @Test
    public void aJavaScriptProcedureAnswersNullAndAJavaOneRuns() {
        engine.execute("CREATE PROCEDURE s3(a FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT STRICT AS 'return 5;'");
        assertNull(only("CALL s3(NULL)"));
        engine.execute("CREATE PROCEDURE s11(a INT) RETURNS INT LANGUAGE JAVA STRICT RUNTIME_VERSION='11' "
            + "PACKAGES=('com.snowflake:snowpark:latest') HANDLER='H.h' AS $$ import com.snowflake.snowpark_java.*; "
            + "class H { public static int h(Session s, Integer a) { return 5; } } $$");
        assertEquals("5", String.valueOf(only("CALL s11(NULL)")));
    }

    @Test
    public void inABlockItIsAStatementErrorWithItsOwnCodes() {
        engine.execute("CREATE PROCEDURE s1(a INT) RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 5; END $$");
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : " + REFUSAL,
            refusalOf("EXECUTE IMMEDIATE $$\nBEGIN\n  CALL s1(NULL);\n  RETURN 'after';\nEND;\n$$"));
        assertEquals("100072 00000 " + REFUSAL, String.valueOf(only("EXECUTE IMMEDIATE $$\nBEGIN\n  CALL s1(NULL);\n"
            + "  RETURN 'x';\nEXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\n"
            + "END;\n$$")));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 5 at position 2 : " + REFUSAL,
            refusalOf("EXECUTE IMMEDIATE $$\nDECLARE\n  v INT DEFAULT NULL;\nBEGIN\n  CALL s1(:v);\n"
                + "  RETURN 'after';\nEND;\n$$"));
    }

    @Test
    public void aNotNullColumnFaultInABlockCarriesTheSameCodes() {
        engine.execute("CREATE TABLE nn (a INT NOT NULL)");
        assertEquals("100072 00000 DML operation to table NN failed on column A with error: " + REFUSAL,
            String.valueOf(only("EXECUTE IMMEDIATE $$\nBEGIN\n  INSERT INTO nn VALUES (NULL);\n  RETURN 'x';\n"
                + "EXCEPTION\n  WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;\n$$")));
    }

    @Test
    public void getDdlWritesTheClausesBack() {
        engine.execute("CREATE PROCEDURE s2(a INT) RETURNS INT LANGUAGE SQL RETURNS NULL ON NULL INPUT "
            + "AS $$ BEGIN RETURN 5; END $$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"S2\"(\"A\" NUMBER(38,0))\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\n"
            + "STRICT\nEXECUTE AS OWNER\nAS ' BEGIN RETURN 5; END ';", String.valueOf(only(
                "SELECT GET_DDL('PROCEDURE', 's2(INT)')")));
        engine.execute("CREATE PROCEDURE g1() RETURNS INT LANGUAGE SQL IMMUTABLE AS $$ BEGIN RETURN 1; END $$");
        assertEquals("CREATE OR REPLACE PROCEDURE \"G1\"()\nRETURNS NUMBER(38,0)\nLANGUAGE SQL\nIMMUTABLE\n"
            + "EXECUTE AS OWNER\nAS ' BEGIN RETURN 1; END ';", String.valueOf(only(
                "SELECT GET_DDL('PROCEDURE', 'g1()')")));
    }
}
