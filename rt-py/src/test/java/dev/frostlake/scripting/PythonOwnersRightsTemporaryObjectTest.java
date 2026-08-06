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
 * The owner's rights temporary-object rule is not Java's alone. Measured on a real account, a PYTHON
 * procedure declared {@code EXECUTE AS OWNER} is refused a temporary table with the same sentence a
 * Java one gets, and the same procedure under {@code EXECUTE AS CALLER} creates it — so the
 * restriction belongs to the handler languages, and the Python session has to carry it too.
 */
public class PythonOwnersRightsTemporaryObjectTest extends BaseDatabaseTest {

    private void pythonProcedure(final String name, final String rights, final String statement) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS STRING LANGUAGE PYTHON"
            + " RUNTIME_VERSION='3.10' PACKAGES=('snowflake-snowpark-python') HANDLER='go'"
            + " EXECUTE AS " + rights + " AS $$\n"
            + "def go(session):\n"
            + "    session.sql(\"" + statement + "\").collect()\n"
            + "    return 'created'\n"
            + "$$");
    }

    private String rootMessage(final Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }

    @Test
    public void ownersRightsRefusesATemporaryTable() {
        pythonProcedure("py_owner_temp", "OWNER", "CREATE TEMPORARY TABLE py_t1 (a INTEGER)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL py_owner_temp()");
            }
        });
        assertTrue(rootMessage(ex).contains(
            "Stored procedure execution error: Unsupported statement type 'temporary TABLE'."),
            rootMessage(ex));
    }

    @Test
    public void callersRightsCreatesIt() {
        pythonProcedure("py_caller_temp", "CALLER", "CREATE TEMPORARY TABLE py_t2 (a INTEGER)");
        assertEquals("created",
            String.valueOf(engine.executeQuery("CALL py_caller_temp()").getRows().get(0).getValue(0)));
    }

    /** TRANSIENT is not temporary here either. */
    @Test
    public void ownersRightsAllowsATransientTable() {
        pythonProcedure("py_owner_transient", "OWNER", "CREATE TRANSIENT TABLE py_t3 (a INTEGER)");
        assertEquals("created",
            String.valueOf(engine.executeQuery("CALL py_owner_transient()").getRows().get(0).getValue(0)));
    }
}
