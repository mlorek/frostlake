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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A Python procedure declaring STRICT or RETURNS NULL ON NULL INPUT runs no body when a CALL hands it a NULL: a
 * scalar one answers NULL, and one returning a table is refused naming its handler. Given no NULL it runs. Every
 * cell is live-verified.
 */
public class PythonStrictProcedureCallTest extends BaseDatabaseTest {

    private static final String HANDLER = " RUNTIME_VERSION='3.11' PACKAGES=('snowflake-snowpark-python') "
        + "HANDLER='run' AS $$\n";

    private Object only(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRows().size(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void aScalarProcedureAnswersNullWithoutRunning() {
        engine.execute("CREATE PROCEDURE s10(a INT) RETURNS INT LANGUAGE PYTHON STRICT" + HANDLER
            + "def run(session, a):\n    return 5\n$$");
        assertNull(only("CALL s10(NULL)"));
        engine.execute("CREATE PROCEDURE w9(a INT) RETURNS VARCHAR LANGUAGE PYTHON RETURNS NULL ON NULL INPUT" + HANDLER
            + "def run(session, a):\n    return 'ran'\n$$");
        assertNull(only("CALL w9(NULL)"));
        assertEquals("ran", String.valueOf(only("CALL w9(1)")));
    }

    @Test
    public void aTableProcedureIsRefusedNamingItsHandler() {
        engine.execute("CREATE PROCEDURE w8(a INT) RETURNS TABLE (x INT) LANGUAGE PYTHON STRICT" + HANDLER
            + "def run(session, a):\n    return session.sql('SELECT 1 AS x')\n$$");
        assertEquals("NULL result in a non-nullable column of type TEXT in function W8 with handler run",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CALL w8(NULL)");
                }
            }).getMessage());
    }
}
