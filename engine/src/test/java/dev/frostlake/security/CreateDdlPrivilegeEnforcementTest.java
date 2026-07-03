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
 * CREATE authorization for CUSTOM roles across non-table object types (view, stream, sequence,
 * function, tag, schema, ...). Creating an object needs the matching CREATE privilege on — or
 * ownership of, or an administrative role over — its container (the schema, or the database for
 * CREATE SCHEMA). Previously only CREATE TABLE was enforced.
 *
 * <p>Also covers the multi-word GRANT fix: {@code GRANT CREATE VIEW …} used to throw at grant time
 * ("No enum constant …CREATEVIEW") because the privilege text was concatenated without a separator;
 * multi-word privileges now map to their enum names, so the grants both succeed and authorize.
 */
public class CreateDdlPrivilegeEnforcementTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE ROLE app_role");
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

    // ── CREATE denied for a bare custom role ─────────────────────────────────────────────────────

    @Test
    public void nonOwnerCannotCreateView() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW v AS SELECT 1 AS x");
            }
        });
    }

    @Test
    public void nonOwnerCannotCreateStream() {
        engine.execute("CREATE TABLE base (id INTEGER)"); // as SYSTEM
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE STREAM strm ON TABLE base");
            }
        });
    }

    @Test
    public void nonOwnerCannotCreateSequence() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SEQUENCE seq START = 1 INCREMENT = 1");
            }
        });
    }

    @Test
    public void nonOwnerCannotCreateFunction() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f(x INTEGER) RETURNS INTEGER AS 'x + 1'");
            }
        });
    }

    @Test
    public void nonOwnerCannotCreateTag() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TAG cost_center");
            }
        });
    }

    @Test
    public void nonOwnerCannotCreateSchema() {
        asAppRole();
        assertThrows(SecurityException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SCHEMA sch1");
            }
        });
    }

    // ── Ownership / admin authorizes CREATE ──────────────────────────────────────────────────────

    @Test
    public void schemaOwnerCanCreateView() {
        engine.execute("GRANT OWNERSHIP ON SCHEMA PUBLIC TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW v AS SELECT 1 AS x");
            }
        });
    }

    @Test
    public void adminRoleCanCreateView() {
        engine.setCurrentUser("app_user");
        engine.setCurrentRole("SYSADMIN"); // an administrative role
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW av AS SELECT 1 AS x");
            }
        });
    }

    // ── A CREATE_<X> grant authorizes CREATE (also exercises the multi-word grant fix) ───────────

    @Test
    public void grantCreateViewAuthorizesCreate() {
        engine.execute("GRANT CREATE VIEW ON SCHEMA PUBLIC TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW v AS SELECT 1 AS x");
            }
        });
    }

    @Test
    public void grantCreateTableAuthorizesCreate() {
        // CREATE TABLE was already enforced, but GRANT CREATE TABLE itself used to throw — verify
        // the whole path now works end to end.
        engine.execute("GRANT CREATE TABLE ON SCHEMA PUBLIC TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE t (id INTEGER)");
            }
        });
    }

    @Test
    public void grantCreateSequenceAuthorizesCreate() {
        engine.execute("GRANT CREATE SEQUENCE ON SCHEMA PUBLIC TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SEQUENCE seq START = 1 INCREMENT = 1");
            }
        });
    }

    @Test
    public void grantCreateSchemaOnDatabaseAuthorizesCreate() {
        engine.execute("GRANT CREATE SCHEMA ON DATABASE db TO ROLE app_role");
        asAppRole();
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE SCHEMA sch1");
            }
        });
    }

    // ── The multi-word GRANT fix in isolation ────────────────────────────────────────────────────

    @Test
    public void grantMultiWordObjectPrivilegeNoLongerThrows() {
        // Ran as SYSTEM. Before the fix these threw "No enum constant …CREATEVIEW / …CREATESTREAM".
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT CREATE VIEW ON SCHEMA PUBLIC TO ROLE app_role");
                engine.execute("GRANT CREATE STREAM ON SCHEMA PUBLIC TO ROLE app_role");
                engine.execute("GRANT CREATE MASKING POLICY ON SCHEMA PUBLIC TO ROLE app_role");
            }
        });
    }

    @Test
    public void grantMultiWordGlobalPrivilegeNoLongerThrows() {
        // Global (account-level) multi-word privileges were broken by the same concatenation bug.
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT CREATE DATABASE TO ROLE app_role");
                engine.execute("GRANT MONITOR USAGE TO ROLE app_role");
            }
        });
    }

    // The SYSTEM user (default) bypasses all of this — a regression guard for the 3000-test suite.
    @Test
    public void systemUserBypassesCreateChecks() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE base (id INTEGER)");
                engine.execute("CREATE VIEW sysv AS SELECT 1 AS x");
                engine.execute("CREATE STREAM sysstrm ON TABLE base");
                engine.execute("CREATE SEQUENCE sysseq START = 1 INCREMENT = 1");
                engine.execute("CREATE FUNCTION sysf(x INTEGER) RETURNS INTEGER AS 'x + 1'");
                engine.execute("CREATE TAG systag");
                engine.execute("CREATE SCHEMA syssch");
            }
        });
    }
}
