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
package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scripting variable binds by the type it was declared with: a text declared without a length at the width
 * nothing bounds, and any variable handed to a user-defined function as its declared type, whatever it holds.
 * Every statement carries an alias of its own, as the account answers a text it has compiled before from that plan.
 */
public class DeclaredTypeBindTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION fd(d DATE) RETURNS VARCHAR AS $$ 'date' $$");
        engine.execute("CREATE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS $$ 'num' $$");
        engine.execute("CREATE FUNCTION fo(v VARCHAR) RETURNS VARCHAR AS $$ 'var' $$");
    }

    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    /** The one cell of a RESULTSET whose query binds {@code s}, declared as {@code declaration}. */
    private String selected(final String declaration, final String item) {
        return value("DECLARE s " + declaration + "; res RESULTSET; BEGIN res := (SELECT " + item
            + "); RETURN TABLE(res); END;");
    }

    /** The value a SELECT … INTO of the block's own reads for {@code s}, declared as {@code declaration}. */
    private String selectedInto(final String declaration, final String item) {
        return value("DECLARE s " + declaration + "; t VARCHAR; BEGIN SELECT " + item + " INTO :t; RETURN t; END;");
    }

    private void refused(final String sql, final String fragment) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refusal.getMessage().contains(fragment), sql + " => " + refusal.getMessage());
    }

    private void refusedBlock(final String declaration, final String item, final String fragment) {
        refused("DECLARE s " + declaration + "; res RESULTSET; BEGIN res := (SELECT " + item
            + "); RETURN TABLE(res); END;", fragment);
    }

    @Test
    public void aTextDeclaredWithoutALengthBindsAtTheWidthNothingBounds() {
        assertEquals("VARCHAR(134217728)[LOB]", selected("VARCHAR", "SYSTEM$TYPEOF(:s) AS dt_a1"));
        assertEquals("VARCHAR(134217728)[LOB]", selected("STRING", "SYSTEM$TYPEOF(:s) AS dt_a2"));
        assertEquals("VARCHAR(134217728)[LOB]", selected("TEXT", "SYSTEM$TYPEOF(:s) AS dt_a3"));
        assertEquals("VARCHAR(134217728)[LOB]", selectedInto("VARCHAR", "SYSTEM$TYPEOF(:s) AS dt_a4"));
        assertEquals("VARCHAR(134217728)[LOB]", selectedInto("STRING", "SYSTEM$TYPEOF(:s) AS dt_a5"));
        assertEquals("VARCHAR(134217728)[LOB]", selected("VARCHAR", "SYSTEM$TYPEOF(:s || 'a') AS dt_a6"));
        assertEquals("VARCHAR(134217728)[LOB]", selected("TEXT", "SYSTEM$TYPEOF(COALESCE(:s, 'ab')) AS dt_a7"));
        // A length that was written is the bind's, the default's own included.
        assertEquals("VARCHAR(16777216)[LOB]", selected("VARCHAR(16777216)", "SYSTEM$TYPEOF(:s) AS dt_a8"));
        assertEquals("VARCHAR(16777216)[LOB]", selectedInto("VARCHAR(16777216)", "SYSTEM$TYPEOF(:s) AS dt_a9"));
        assertEquals("VARCHAR(16777217)[LOB]", selected("VARCHAR(16777216)", "SYSTEM$TYPEOF(:s || 'a') AS dt_a10"));
        assertEquals("VARCHAR(16777216)[LOB]",
            selected("VARCHAR(16777216)", "SYSTEM$TYPEOF(COALESCE(:s, 'ab')) AS dt_a11"));
        assertEquals("VARCHAR(100)[LOB]", selected("VARCHAR(100)", "SYSTEM$TYPEOF(:s) AS dt_a12"));
        assertEquals("VARCHAR(16777217)[LOB]", selected("VARCHAR(16777217)", "SYSTEM$TYPEOF(:s) AS dt_a13"));
        assertEquals("VARCHAR(1)[LOB]", selected("CHAR", "SYSTEM$TYPEOF(:s) AS dt_a14"));
    }

    @Test
    public void aUserDefinedFunctionIsHandedTheDeclaredTypeOfANullBind() {
        refusedBlock("NUMBER(10,2)", "fd(:s) AS dt_b1", "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refusedBlock("BOOLEAN", "fd(:s) AS dt_b2", "Invalid argument types for function 'FD': (BOOLEAN)");
        assertEquals("date", selected("DATE", "fd(:s) AS dt_b3"));
        assertEquals("date", selected("VARCHAR(10)", "fd(:s) AS dt_b4"));
        assertEquals("date", selected("TIMESTAMP_NTZ", "fd(:s) AS dt_b5"));
        // The overload is the declared type's: a text of any length is the text one's.
        assertEquals("var", selected("VARCHAR(10)", "fo(:s) AS dt_b6"));
        assertEquals("var", selected("VARCHAR", "fo(:s) AS dt_b7"));
        assertEquals("var", selected("DATE", "fo(:s) AS dt_b8"));
        assertEquals("var", selected("BOOLEAN", "fo(:s) AS dt_b9"));
        assertEquals("num", selected("NUMBER(10,2)", "fo(:s) AS dt_b10"));
        assertEquals("var", selectedInto("VARCHAR(10)", "fo(:s) AS dt_b11"));
        // Wherever the call stands in the query.
        refusedBlock("NUMBER(10,2)", "COALESCE(fd(:s), 'x') AS dt_b12",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refusedBlock("NUMBER(10,2)", "1 AS dt_b13 WHERE fd(:s) IS NULL",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refused("DECLARE s NUMBER(10,2); t VARCHAR; BEGIN SELECT fd(:s) AS dt_b14 INTO :t; RETURN t; END;",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refused("DECLARE s BOOLEAN; BEGIN LET r RESULTSET := (SELECT fd(:s) AS dt_b15); RETURN TABLE(r); END;",
            "Invalid argument types for function 'FD': (BOOLEAN)");
        refused("DECLARE s NUMBER(10,2); BEGIN CREATE OR REPLACE TABLE dt_b16 AS SELECT fd(:s) AS c; RETURN 'ok'; END;",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refused("DECLARE res RESULTSET; BEGIN res := (SELECT fd(NULL::NUMBER(10,2)) AS dt_b17); RETURN TABLE(res); END;",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
    }

    @Test
    public void aNumberIsHandedOverAsItWasDeclaredWhateverItHolds() {
        refusedBlock("NUMBER(10,2) DEFAULT 1.5", "fd(:s) AS dt_c1",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refusedBlock("NUMBER DEFAULT 5", "fd(:s) AS dt_c2", "Invalid argument types for function 'FD': (NUMBER(38,0))");
        refusedBlock("FLOAT DEFAULT 1.5", "fd(:s) AS dt_c3", "Invalid argument types for function 'FD': (FLOAT)");
        refused("DECLARE s NUMBER(10,2) DEFAULT 1.5; t VARCHAR; BEGIN SELECT fd(:s) AS dt_c4 INTO :t; RETURN t; END;",
            "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refused("DECLARE s BOOLEAN DEFAULT TRUE; t VARCHAR; BEGIN SELECT fd(:s) AS dt_c5 INTO :t; RETURN t; END;",
            "Invalid argument types for function 'FD': (BOOLEAN)");
        assertEquals("num", selected("NUMBER(10,2) DEFAULT 1.5", "fo(:s) AS dt_c6"));
        assertEquals("date", selected("DATE DEFAULT '2024-01-01'", "fd(:s) AS dt_c7"));
        assertEquals("var", selected("DATE DEFAULT '2024-01-01'", "fo(:s) AS dt_c8"));
        assertEquals("date", selected("VARCHAR(10) DEFAULT '2024-01-01'", "fd(:s) AS dt_c9"));
        // A procedure's argument is a variable of the type the procedure declares.
        engine.execute("CREATE OR REPLACE PROCEDURE dt_pfd(s NUMBER(10,2)) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ DECLARE t VARCHAR; BEGIN SELECT fd(:s) INTO :t; RETURN t; END; $$");
        refused("CALL dt_pfd(NULL)", "Invalid argument types for function 'FD': (NUMBER(10,2))");
        refused("CALL dt_pfd(1.5)", "Invalid argument types for function 'FD': (NUMBER(10,2))");
        engine.execute("CREATE OR REPLACE PROCEDURE dt_pfo(s VARCHAR) RETURNS TABLE() LANGUAGE SQL AS"
            + " $$ DECLARE res RESULTSET; BEGIN res := (SELECT fo(:s) AS c); RETURN TABLE(res); END; $$");
        assertEquals("var", value("CALL dt_pfo(NULL)"));
        assertEquals("var", value("CALL dt_pfo('x')"));
    }

    @Test
    public void aBinaryBesideANullIsRefusedInArithmetic() {
        refused("SELECT NULL::BINARY(10) + NULL AS dt_d1", "SQL compilation error: error line 1 at position 24\n"
            + "Invalid argument types for function '+': (BINARY(10), NULL)");
        refused("SELECT NULL + NULL::BINARY(10) AS dt_d2", "SQL compilation error: error line 1 at position 12\n"
            + "Invalid argument types for function '+': (NULL, BINARY(10))");
        refused("SELECT TO_BINARY('00') + NULL AS dt_d3", "SQL compilation error: error line 1 at position 23\n"
            + "Invalid argument types for function '+': (BINARY(67108864), NULL)");
        refused("SELECT NULL::BINARY(10) - NULL AS dt_d4", "Invalid argument types for function '-': (BINARY(10), NULL)");
        refused("SELECT NULL::BINARY(10) * NULL AS dt_d5", "Invalid argument types for function '*': (BINARY(10), NULL)");
        assertEquals("null", value("SELECT NULL::BINARY(10) || NULL AS dt_d6"));
        assertEquals("null", value("SELECT NULL::BINARY(10) = NULL AS dt_d7"));
        refusedBlock("BINARY(10)", ":s + NULL AS dt_d8", "Invalid argument types for function '+': (BINARY(10), NULL)");
        refusedBlock("BINARY(10)", "NULL + :s AS dt_d9", "Invalid argument types for function '+': (NULL, BINARY(10))");
        assertEquals("null", selected("BINARY(10)", ":s || NULL AS dt_d10"));
        assertEquals("BINARY(10)[LOB]", selected("BINARY(10)", "SYSTEM$TYPEOF(:s) AS dt_d11"));
    }
}
