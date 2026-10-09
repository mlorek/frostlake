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
package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a routine's arguments are told and matched (live-verified): a listing writes the argument types alone and
 * marks one that has a default; a named argument is matched by its exact name, in a SELECT, a table function and
 * a CALL alike; and a CALL refuses a shape no signature takes in the account's own words and at its own place.
 */
public class RoutineArgumentSurfacesTest extends BaseDatabaseTest {

    private void routines() {
        engine.execute("CREATE FUNCTION fa(a NUMBER, b VARCHAR) RETURNS NUMBER AS 'a'");
        engine.execute("CREATE FUNCTION fd(a NUMBER, b VARCHAR DEFAULT 'x', c NUMBER(10,2) DEFAULT 1.5) RETURNS"
            + " VARCHAR AS 'b'");
        engine.execute("CREATE FUNCTION ft(n NUMBER) RETURNS TABLE (x NUMBER, y VARCHAR) AS 'SELECT n, ''a'''");
        engine.execute("CREATE PROCEDURE pa(a NUMBER, b VARCHAR DEFAULT 'q') RETURNS VARCHAR LANGUAGE SQL AS"
            + " 'BEGIN RETURN b; END'");
        engine.execute("CREATE PROCEDURE pn() RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aListingWritesTheTypesAndMarksADefault() {
        routines();
        final ResultSet functions = engine.executeQuery("SHOW USER FUNCTIONS LIKE 'FD'");
        assertEquals("FD(NUMBER, DEFAULT VARCHAR, DEFAULT NUMBER) RETURN VARCHAR",
            cell(functions, functions.getRows().get(0), "arguments"));
        assertEquals("3", cell(functions, functions.getRows().get(0), "min_num_arguments"),
            "a defaulted argument still counts");
        assertEquals("3", cell(functions, functions.getRows().get(0), "max_num_arguments"));
        final ResultSet procedures = engine.executeQuery("SHOW PROCEDURES LIKE 'PA'");
        assertEquals("PA(NUMBER, DEFAULT VARCHAR) RETURN VARCHAR",
            cell(procedures, procedures.getRows().get(0), "arguments"));
        final ResultSet described = engine.executeQuery("DESCRIBE FUNCTION fd(NUMBER, VARCHAR, NUMBER)");
        assertEquals("(A NUMBER, B VARCHAR, C NUMBER)", cell(described, soleRowWhere(described, "property",
            "signature"), "value"), "a description names the arguments and tells no default");
    }

    @Test
    public void aNamedArgumentIsMatchedByItsExactName() {
        routines();
        assertEquals("1", first("SELECT fa(\"A\" => 1, \"B\" => 'x')"));
        assertEquals("1", first("SELECT fa(b => 'x', a => 1)"));
        assertTrue(refusal("SELECT fa(\"a\" => 1, b => 'x')").contains("error line 1 at position 7\n"
            + "named arguments [a, B] do not match any signature for function FA"));
        assertEquals("1", first("SELECT x FROM TABLE(ft(\"N\" => 1))"));
        assertTrue(refusal("SELECT * FROM TABLE(ft(\"n\" => 1))").contains("error line 1 at position 20\n"
            + "named arguments [n] do not match any signature for function FT"));
        assertEquals("q", first("CALL pa(\"A\" => 1)"));
        assertEquals("z", first("CALL pa(b => 'z', a => 1)"));
        assertTrue(refusal("CALL pa(\"a\" => 1)").contains("error line 0 at position -1\n"
            + "named arguments [a] do not match any signature for function PA"));
    }

    @Test
    public void aCallRefusesAShapeNoSignatureTakes() {
        routines();
        assertTrue(refusal("CALL pa(a => 1, a => 2)").contains("error line 1 at position 16\n"
            + "duplicate named argument A for function PA"), "at the second name");
        assertTrue(refusal("CALL pa(a => 1, 'y')").contains("error line 1 at position 5\n"
            + "illegal mixing of named and positional arguments for function PA"), "at the procedure's name");
        assertTrue(refusal("CALL pn(a => 1)").contains("error line 0 at position -1\n"
            + "too many arguments for function [PN(1)] expected 0, got 1"));
        assertTrue(refusal("SELECT fd(a => 1, a => 2)").contains("error line 1 at position 23\n"
            + "duplicate named argument A for function FD"));
        assertEquals("x", first("SELECT fd(1, c => 2)"), "a positional argument may come before a named one");
    }
}
