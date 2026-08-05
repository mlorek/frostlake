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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CALL <proc>(name => value)} — Snowflake named arguments. A named argument binds to the parameter
 * whose name it matches (so order is free and a defaulted parameter may be skipped); a CALL's arguments
 * are all-named or all-positional — mixing them is an error. Exercised for the top-level CALL, the
 * {@code var := (CALL …)} assignment form, and a bare CALL inside a procedure body.
 */
public class CallNamedArgumentsTest extends BaseDatabaseTest {

    private int callInt(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).intValue();
    }

    private void defineAB() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_ab(a INT, b INT) RETURNS INT LANGUAGE SQL AS
            $$ BEGIN RETURN :a * 10 + :b; END $$
            """);
    }

    @Test
    public void namedArgumentsBindByName() {
        defineAB();
        assertEquals(12, callInt("CALL p_ab(1, 2)"));                 // positional
        assertEquals(12, callInt("CALL p_ab(a => 1, b => 2)"));       // named
        assertEquals(12, callInt("CALL p_ab(b => 2, a => 1)"));       // named, out of order
        // A CALL is all-named or all-positional. Live-verified on a real account:
        // CALL p_ab(1, b => 2) fails "illegal mixing of named and positional arguments for function
        // P_AB". The restriction is CALL's alone — the same mixed shape on a UDF works there.
        final RuntimeException mixed = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                callInt("CALL p_ab(1, b => 2)");
            }
        });
        assertTrue(mixed.getMessage().contains("illegal mixing of named and positional arguments"),
            mixed.getMessage());
    }

    @Test
    public void namedArgumentMaySkipDefaultedParameter() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_def(a INT, b INT DEFAULT 5) RETURNS INT LANGUAGE SQL AS
            $$ BEGIN RETURN :a * 10 + :b; END $$
            """);
        assertEquals(35, callInt("CALL p_def(a => 3)"));             // b defaults to 5
        assertEquals(39, callInt("CALL p_def(a => 3, b => 9)"));     // b overridden
    }

    @Test
    public void namedArgumentsInAssignmentCallForm() {
        defineAB();
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_assign() RETURNS INT LANGUAGE SQL AS
            $$ DECLARE r INT; BEGIN r := (CALL p_ab(b => 2, a => 1)); RETURN r; END $$
            """);
        assertEquals(12, callInt("CALL p_assign()"));
    }

    @Test
    public void namedArgumentsInBareProceduralCall() {
        engine.execute("CREATE TABLE call_log (v INT)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_ins(v INT) RETURNS INT LANGUAGE SQL AS
            $$ BEGIN INSERT INTO call_log VALUES (:v); RETURN 1; END $$
            """);
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_caller() RETURNS INT LANGUAGE SQL AS
            $$ BEGIN CALL p_ins(v => 77); RETURN 0; END $$
            """);
        engine.execute("CALL p_caller()");
        assertEquals(77, ((Number) engine.executeQuery("SELECT v FROM call_log").getRows()
            .get(0).getValue(0)).intValue());
    }

    @Test
    public void unknownArgumentNameIsRejected() {
        defineAB();
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CALL p_ab(a => 1, nope => 2)");
            }
        });
    }
}
