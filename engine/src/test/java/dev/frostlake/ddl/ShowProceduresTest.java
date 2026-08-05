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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SYSTEM$ routines the engine implements are FUNCTIONS, not procedures, so SHOW FUNCTIONS lists them
 * with is_builtin = 'Y' while SHOW PROCEDURES does not — matching Snowflake. SHOW USER PROCEDURES and
 * SHOW USER FUNCTIONS list only user-defined routines.
 */
public class ShowProceduresTest extends BaseDatabaseTest {

    private static final int NAME = 1;
    private static final int IS_BUILTIN = 3;

    @Test
    public void systemRoutinesAreListedAsFunctionsNotProcedures() {
        // Live-verified on a real account: SHOW PROCEDURES LIKE 'SYSTEM$WAIT' (and the same
        // for SYSTEM$SET_RETURN_VALUE / SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS) returns ZERO rows,
        // while SHOW FUNCTIONS lists each with is_builtin = 'Y' — even though CALL SYSTEM$WAIT(1) works.
        final Set<String> procedures = new HashSet<String>();
        for (final Row row : engine.executeQuery("SHOW PROCEDURES").getRows()) {
            procedures.add(String.valueOf(row.getValue(NAME)).toUpperCase());
        }
        assertFalse(procedures.contains("SYSTEM$WAIT"), "SYSTEM$WAIT is not a procedure");

        final Set<String> functions = new HashSet<String>();
        boolean sawBuiltin = false;
        for (final Row row : engine.executeQuery("SHOW FUNCTIONS").getRows()) {
            functions.add(String.valueOf(row.getValue(NAME)).toUpperCase());
            if ("Y".equals(row.getValue(IS_BUILTIN))) {
                sawBuiltin = true;
            }
        }
        assertTrue(sawBuiltin, "SHOW FUNCTIONS should list built-in functions");
        assertTrue(functions.contains("SYSTEM$WAIT"), "expected built-in SYSTEM$WAIT");
        assertTrue(functions.contains("SYSTEM$SET_RETURN_VALUE"));
        assertTrue(functions.contains("SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS"));
    }

    @Test
    public void showUserProceduresExcludesBuiltins() {
        engine.execute("""
            CREATE PROCEDURE my_proc()
            RETURNS VARCHAR
            LANGUAGE SQL
            AS
            BEGIN
              RETURN 'x';
            END;
            """);
        final ResultSet allFunctions = engine.executeQuery("SHOW FUNCTIONS");
        final ResultSet userFunctions = engine.executeQuery("SHOW USER FUNCTIONS");
        final ResultSet userOnly = engine.executeQuery("SHOW USER PROCEDURES");

        assertTrue(allFunctions.getRowCount() > userFunctions.getRowCount(),
            "SHOW FUNCTIONS should list more rows (built-ins) than SHOW USER FUNCTIONS");
        boolean sawUdf = false;
        for (final Row row : userOnly.getRows()) {
            assertEquals("N", row.getValue(IS_BUILTIN),
                "SHOW USER PROCEDURES should list only user-defined procedures");
            if ("MY_PROC".equals(String.valueOf(row.getValue(NAME)).toUpperCase())) {
                sawUdf = true;
            }
        }
        assertTrue(sawUdf, "the user procedure should be listed by SHOW USER PROCEDURES");
    }

    @Test
    public void showUserFunctionsExcludesBuiltins() {
        engine.execute("CREATE FUNCTION my_fn(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        final ResultSet userOnly = engine.executeQuery("SHOW USER FUNCTIONS");
        boolean sawUdf = false;
        for (final Row row : userOnly.getRows()) {
            assertEquals("N", row.getValue(IS_BUILTIN),
                "SHOW USER FUNCTIONS should list only user-defined functions");
            if ("MY_FN".equals(String.valueOf(row.getValue(NAME)).toUpperCase())) {
                sawUdf = true;
            }
        }
        assertTrue(sawUdf, "the user function should be listed by SHOW USER FUNCTIONS");
    }
}
