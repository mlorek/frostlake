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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DROP / ALTER authorization for CUSTOM roles on NON-table object types (views, schemas, streams,
 * stages, warehouses, ...). Ownership, an administrative role (ACCOUNTADMIN / SYSADMIN), or a
 * DROP / ALTER grant confers authority; a bare custom role has none. Complements
 * {@link DdlPrivilegeEnforcementTest}, which covers the table slice. The SYSTEM user (default) and
 * admin roles bypass everything — the 3000-test suite creates its objects as SYSTEM.
 */
public class NonTableDdlPrivilegeEnforcementTest {

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

    // ── DROP (non-table) ─────────────────────────────────────────────────────────────────────────

    @Test
    public void nonOwnerCustomRoleCannotDropView() {
        engine.execute("CREATE VIEW v AS SELECT 1 AS x"); // owned by SYSADMIN (created as SYSTEM)
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP VIEW v");
            }
        });
    }

    @Test
    public void viewOwnerCanDropView() {
        engine.execute("CREATE VIEW ov AS SELECT 1 AS x");
        engine.execute("GRANT OWNERSHIP ON VIEW ov TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP VIEW ov");
            }
        });
    }

    @Test
    public void nonOwnerCustomRoleCannotDropSchema() {
        engine.execute("CREATE SCHEMA sch1");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP SCHEMA sch1");
            }
        });
    }

    /** DROP is no privilege of a stream either: dropping one takes its OWNERSHIP. */
    @Test
    public void dropIsNoPrivilegeOfAStream() {
        engine.execute("CREATE TABLE base (id INTEGER)");
        engine.execute("CREATE STREAM strm ON TABLE base");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT DROP ON STREAM strm TO ROLE app_role");
            }
        });
        assertEquals("SQL compilation error:\nInvalid object type 'STREAM' for privilege 'DROP'.", refused.getMessage());
    }

    @Test
    public void nonOwnerCustomRoleCannotDropStage() {
        engine.execute("CREATE STAGE stg URL='s3://bucket/path/'");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP STAGE stg");
            }
        });
    }

    @Test
    public void adminRoleCanDropAnyView() {
        engine.execute("CREATE VIEW av AS SELECT 1 AS x");
        engine.setCurrentUser("app_user");
        engine.setCurrentRole("SYSADMIN"); // an administrative role
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP VIEW av");
            }
        });
    }

    // ── ALTER ────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void nonOwnerCustomRoleCannotAlterTable() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE t ADD COLUMN c INTEGER");
            }
        });
    }

    @Test
    public void tableOwnerCanAlter() {
        engine.execute("CREATE TABLE ot (id INTEGER)");
        engine.execute("GRANT OWNERSHIP ON TABLE ot TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ot ADD COLUMN c INTEGER");
            }
        });
    }

    @Test
    public void alterGrantAllowsAlterTable() {
        engine.execute("CREATE TABLE gt (id INTEGER)");
        engine.execute("GRANT ALTER ON TABLE gt TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE gt ADD COLUMN c INTEGER");
            }
        });
    }

    @Test
    public void nonOwnerCustomRoleCannotAlterView() {
        engine.execute("CREATE VIEW vv AS SELECT 1 AS x");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER VIEW vv SET COMMENT = 'nope'");
            }
        });
    }

    @Test
    public void nonOwnerCustomRoleCannotAlterSchema() {
        engine.execute("CREATE SCHEMA sch2");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER SCHEMA sch2 SET COMMENT = 'nope'");
            }
        });
    }

    @Test
    public void nonOwnerCustomRoleCannotAlterStream() {
        engine.execute("CREATE TABLE base2 (id INTEGER)");
        engine.execute("CREATE STREAM strm2 ON TABLE base2");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER STREAM strm2 SET COMMENT = 'nope'");
            }
        });
    }

    @Test
    public void nonOwnerCustomRoleCannotAlterWarehouse() {
        engine.execute("CREATE WAREHOUSE wh");
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE wh SUSPEND");
            }
        });
    }

    // The SYSTEM user (default) bypasses all of this — a regression guard for the 3000-test suite.
    @Test
    public void systemUserBypassesNonTableDdl() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW sysv AS SELECT 1 AS x");
                engine.execute("ALTER VIEW sysv SET COMMENT = 'ok'");
                engine.execute("DROP VIEW sysv");
                engine.execute("CREATE STAGE sysstg URL='s3://bucket/path/'");
                engine.execute("DROP STAGE sysstg");
            }
        });
    }
}
