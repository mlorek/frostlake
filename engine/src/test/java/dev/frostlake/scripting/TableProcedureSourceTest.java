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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A procedure declared {@code RETURNS TABLE (…)} read as a FROM source, {@code TABLE(p())} (live-verified). Where
 * a CALL hands the rows back as the procedure returned them, the FROM form converts each value to its declared
 * column's type and refuses one that does not fit; a failure of the procedure is reported where its name stands
 * in the statement; arguments it does not take are refused at its name as argument types; and a procedure
 * declaring no columns, or returning no table, is no FROM source.
 */
public class TableProcedureSourceTest extends BaseDatabaseTest {

    private static final String AT_NAME = "Stored procedure execution error on line 1 position 20: ";
    private static final String MISMATCH =
        "Stored procedure execution error: data type of returned table does not match expected returned table type";

    private void procedure(final String name, final String returns, final String query) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + " RETURNS TABLE (" + returns + ") LANGUAGE SQL AS"
            + " $$ BEGIN LET r RESULTSET := (" + query + "); RETURN TABLE(r); END; $$");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() == 0 ? "" : " / ");
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c == 0 ? "" : "|").append(row.getValue(c));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }, sql).getMessage();
    }

    @Test
    public void theFromFormConvertsToTheDeclaredColumns() {
        procedure("tps_n()", "a NUMBER(5,2)", "SELECT 1.7777 AS a");
        assertEquals("1.7777", rows("CALL tps_n()"));
        assertEquals("1.78", rows("SELECT * FROM TABLE(tps_n())"));
        procedure("tps_i()", "a INT", "SELECT 1.7 AS a");
        assertEquals("2", rows("SELECT * FROM TABLE(tps_i())"));
        assertEquals("1.78|2", rows("SELECT t.a, u.a FROM TABLE(tps_n()) t, TABLE(tps_i()) u"));
        assertEquals("1.78", rows("SELECT a FROM TABLE(tps_n()) WHERE a > 1"));
        procedure("tps_null()", "a NUMBER(5,2)", "SELECT NULL::NUMBER AS a");
        assertEquals("null", rows("SELECT * FROM TABLE(tps_null())"));
        procedure("tps_x()", "x INT", "SELECT 5 AS a");
        assertEquals("5", rows("SELECT x FROM TABLE(tps_x())"));
    }

    @Test
    public void aValueThatDoesNotFitItsColumnIsRefused() {
        procedure("tps_t()", "a NUMBER(5,2), b VARCHAR(3)", "SELECT 1.7777 AS a, 'abcdef' AS b");
        assertEquals("1.7777|abcdef", rows("CALL tps_t()"));
        assertEquals("String 'abcdef' is too long and would be truncated", refusal("SELECT * FROM TABLE(tps_t())"));
        procedure("tps_s()", "s VARCHAR(3)", "SELECT 'abc' AS s UNION ALL SELECT 'abcd'");
        assertEquals("String 'abcd' is too long and would be truncated", refusal("SELECT * FROM TABLE(tps_s()) LIMIT 1"));
        procedure("tps_o()", "a NUMBER(3,0)", "SELECT 12345 AS a");
        assertEquals("12345", rows("CALL tps_o()"));
        assertEquals("Failed to cast variant value \"12345\" to FIXED", refusal("SELECT * FROM TABLE(tps_o())"));
    }

    @Test
    public void aFailureOfTheProcedureIsReportedAtItsName() {
        procedure("tps_x()", "a INT", "SELECT 'x' AS a");
        assertEquals(MISMATCH, refusal("CALL tps_x()"));
        assertEquals(AT_NAME + MISMATCH, refusal("SELECT * FROM TABLE(tps_x())"));
        assertEquals("Stored procedure execution error on line 2 position 13: " + MISMATCH,
            refusal("SELECT *\n  FROM TABLE(tps_x())"));
        engine.execute("CREATE OR REPLACE PROCEDURE tps_noreturn() RETURNS TABLE (a INT) LANGUAGE SQL AS"
            + " $$ BEGIN LET x := 1; END; $$");
        assertEquals(AT_NAME + "SQL compilation error: stored procedure is missing a return statement",
            refusal("SELECT * FROM TABLE(tps_noreturn())"));
        procedure("tps_z()", "a INT", "SELECT 1/0 AS a");
        assertEquals(AT_NAME + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 27 :"
            + " Division by zero", refusal("SELECT * FROM TABLE(tps_z())"));
    }

    @Test
    public void argumentsTheProcedureDoesNotTakeAreRefusedAtItsName() {
        procedure("tps_a(x INT)", "a INT", "SELECT :x AS a");
        assertEquals("1", rows("SELECT * FROM TABLE(tps_a(1))"));
        assertEquals("3", rows("SELECT * FROM TABLE(tps_a(x => 3))"));
        final String at = "SQL compilation error: error line 1 at position 20\n";
        assertEquals(at + "Invalid argument types for function 'TPS_A': ()", refusal("SELECT * FROM TABLE(tps_a())"));
        assertEquals(at + "Invalid argument types for function 'TPS_A': (NUMBER(1,0), NUMBER(1,0))",
            refusal("SELECT * FROM TABLE(tps_a(1, 2))"));
    }

    @Test
    public void aProcedureWithoutColumnsOrWithoutATableIsNoSource() {
        engine.execute("CREATE OR REPLACE PROCEDURE tps_e() RETURNS TABLE () LANGUAGE SQL AS"
            + " $$ BEGIN LET r RESULTSET := (SELECT 1 AS a, 'x' AS b); RETURN TABLE(r); END; $$");
        assertEquals("1|x", rows("CALL tps_e()"));
        assertEquals("SQL compilation error: Invalid stored procedure 'TPS_E' in FROM clause: Return table must"
            + " declare a nonzero number of columns.", refusal("SELECT * FROM TABLE(tps_e())"));
        engine.execute("CREATE OR REPLACE PROCEDURE tps_scalar() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN RETURN 'x'; END; $$");
        assertEquals("SQL compilation error:\nUnknown table function TPS_SCALAR",
            refusal("SELECT * FROM TABLE(tps_scalar())"));
    }
}
