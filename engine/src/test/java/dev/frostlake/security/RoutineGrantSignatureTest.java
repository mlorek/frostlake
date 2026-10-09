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

package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The argument list a GRANT or a REVOKE writes after a function's or a procedure's name holds types. A plain word
 * there is refused as a type that does not exist, before the routine is looked for.
 */
public class RoutineGrantSignatureTest extends BaseDatabaseTest {

    private static final String NO_TYPE = "SQL compilation error:\nUnsupported data type 'X'.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE FUNCTION rgs_f() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION rgs_f(x INT) RETURNS INT AS 'x'");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aPlainWordIsNoType() {
        assertEquals(NO_TYPE, refusalOf("GRANT USAGE ON FUNCTION rgs_f(x) TO ROLE PUBLIC"));
        assertEquals(NO_TYPE, refusalOf("REVOKE USAGE ON FUNCTION rgs_f(x) FROM ROLE PUBLIC"));
        assertEquals(NO_TYPE, refusalOf("GRANT USAGE ON FUNCTION rgs_f(INT, x) TO ROLE PUBLIC"));
        assertEquals(NO_TYPE, refusalOf("GRANT USAGE ON PROCEDURE rgs_f(x) TO ROLE PUBLIC"));
        // Before the routine is looked for.
        assertEquals(NO_TYPE, refusalOf("GRANT USAGE ON FUNCTION rgs_nosuch(x) TO ROLE PUBLIC"));
    }

    @Test
    public void aNamedArgumentAndANumberAreSyntaxErrors() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 32 unexpected 'INT'.",
            refusalOf("GRANT USAGE ON FUNCTION rgs_f(x INT) TO ROLE PUBLIC"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 30 unexpected '1'.",
            refusalOf("GRANT USAGE ON FUNCTION rgs_f(1) TO ROLE PUBLIC"));
    }

    @Test
    public void typesThatNoOverloadTakesNameNoRoutine() {
        assertEquals(hinted("SQL compilation error:\nFunction 'TEST_DB.TEST_SCHEMA.RGS_F' does not exist or not"
            + " authorized."), refusalOf("GRANT USAGE ON FUNCTION rgs_f(VARCHAR) TO ROLE PUBLIC"));
    }
}
