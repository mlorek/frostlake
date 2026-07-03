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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests USE ROLE and USE SECONDARY ROLES — switching the session's primary role (reflected by
 * {@code CURRENT_ROLE()}) and adjusting secondary roles, with existence validation.
 */
public class UseRoleTest extends BaseDatabaseTest {

    private String currentRole() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_ROLE()");
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void useRoleSwitchesCurrentRole() {
        engine.execute("CREATE ROLE analyst");
        engine.execute("USE ROLE analyst");
        assertEquals("ANALYST", currentRole());
    }

    @Test
    public void useSystemRoleSwitches() {
        engine.execute("USE ROLE SYSADMIN");
        assertEquals("SYSADMIN", currentRole());
        engine.execute("USE ROLE ACCOUNTADMIN");
        assertEquals("ACCOUNTADMIN", currentRole());
    }

    @Test
    public void useNonexistentRoleFails() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("USE ROLE no_such_role");
            }
        });
    }

    @Test
    public void useSecondaryRolesAllThenNone() {
        engine.execute("CREATE ROLE r1");
        // ALL then NONE adjust active roles without error.
        engine.execute("USE SECONDARY ROLES ALL");
        engine.execute("USE SECONDARY ROLES NONE");
    }
}
