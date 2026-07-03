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

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DDL and GRANT authorization for CUSTOM roles (previously only table DML was enforced). Ownership
 * or an administrative role (ACCOUNTADMIN / SYSADMIN) confers authority; a bare custom role cannot
 * DROP a table it doesn't own, CREATE a table without CREATE privilege, or GRANT privileges on an
 * object it doesn't own. The SYSTEM user and admin roles are unaffected (all existing tests create
 * their objects as SYSTEM).
 */
public class DdlPrivilegeEnforcementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE ROLE app_role");
        engine.execute("CREATE ROLE other_role");
        engine.execute("CREATE USER app_user");
        engine.execute("GRANT ROLE app_role TO USER app_user");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Switch the session to app_user with ONLY app_role active (no SYSADMIN bypass). */
    private void asAppRole() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("app_user");
        engine.setCurrentRole("app_role");
    }

    // ── DROP ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void nonOwnerCustomRoleCannotDropTable() {
        engine.execute("CREATE TABLE t (id INTEGER)"); // owned by SYSADMIN (created as SYSTEM)
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE t");
            }
        });
    }

    @Test
    public void ownerCanDropTable() {
        engine.execute("CREATE TABLE owned (id INTEGER)");
        engine.execute("GRANT OWNERSHIP ON TABLE owned TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE owned");
            }
        });
    }

    @Test
    public void dropGrantAllowsDrop() {
        engine.execute("CREATE TABLE g (id INTEGER)");
        engine.execute("GRANT DROP ON TABLE g TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE g");
            }
        });
    }

    @Test
    public void adminRoleCanDropAnyTable() {
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        engine.setCurrentUser("app_user");
        engine.setCurrentRole("SYSADMIN"); // an administrative role
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE t2");
            }
        });
    }

    // ── CREATE ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void customRoleWithoutGrantCannotCreateTable() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE newt (id INTEGER)");
            }
        });
    }

    @Test
    public void schemaOwnerCanCreateTable() {
        // Give app_role ownership of the schema; owning the schema authorizes CREATE TABLE in it.
        engine.execute("GRANT OWNERSHIP ON SCHEMA PUBLIC TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE newt (id INTEGER)");
            }
        });
    }

    // ── GRANT authorization ───────────────────────────────────────────────────────────────────────

    @Test
    public void customRoleCannotGrantOnObjectItDoesNotOwn() {
        engine.execute("CREATE TABLE t (id INTEGER)"); // owned by SYSADMIN
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT SELECT ON TABLE t TO ROLE other_role");
            }
        });
    }

    @Test
    public void objectOwnerCanGrant() {
        engine.execute("CREATE TABLE og (id INTEGER)");
        engine.execute("GRANT OWNERSHIP ON TABLE og TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT SELECT ON TABLE og TO ROLE other_role");
            }
        });
    }

    // The SYSTEM user (default) bypasses all of this — a regression guard for the 3000-test suite.
    @Test
    public void systemUserBypassesDdlChecks() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE sys_t (id INTEGER)");
                engine.execute("GRANT SELECT ON TABLE sys_t TO ROLE app_role");
                engine.execute("DROP TABLE sys_t");
            }
        });
    }
}
