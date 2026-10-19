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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GRANT OWNERSHIP over an object a database role or a user holds a grant on: the grant is an outbound grant like an
 * account role's, as Snowflake's GRANT OWNERSHIP page has every one of them. With no tail it blocks the move, REVOKE
 * CURRENT GRANTS drops it, and COPY CURRENT GRANTS keeps it as granted by the new owner. The live account allows no
 * CREATE ROLE, CREATE USER or GRANT, so the rule is the documented one, held by the embedded engine.
 */
public class OwnershipTransferHolderKindsTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE DATABASE p");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE ROLE r");
        engine.execute("CREATE ROLE o");
        engine.execute("CREATE DATABASE ROLE p.dr");
        engine.execute("CREATE USER u");
        engine.execute("GRANT ROLE r TO USER u");
        engine.execute("GRANT USAGE ON DATABASE p TO DATABASE ROLE p.dr");
        engine.execute("GRANT USAGE ON SCHEMA p.public TO DATABASE ROLE p.dr");
        engine.execute("GRANT SELECT ON TABLE t TO DATABASE ROLE p.dr");
        engine.execute("GRANT DATABASE ROLE p.dr TO ROLE r");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asReader() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u");
        engine.setCurrentRole("r");
    }

    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** SHOW GRANTS ON TABLE t, each row as "privilege granted_to grantee_name granted_by". */
    private List<String> grants() {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON TABLE t");
        final List<String> rows = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            rows.add(row.getValue(1) + " " + row.getValue(4) + " " + row.getValue(5) + " " + row.getValue(7));
        }
        return rows;
    }

    @Test
    public void aDatabaseRolesGrantBlocksTheMove() {
        final String refused = refusal("GRANT OWNERSHIP ON TABLE t TO ROLE o");
        assertTrue(refused.startsWith("SQL execution error: Dependent grant of privilege 'SELECT' on securable"
            + " 'P.PUBLIC.T' to database role 'P.DR' exists."), refused);
        asReader();
        assertEquals(1, engine.executeQuery("SELECT * FROM t").getRowCount());
    }

    @Test
    public void revokeCurrentGrantsDropsADatabaseRolesGrant() {
        engine.execute("GRANT OWNERSHIP ON TABLE t TO ROLE o REVOKE CURRENT GRANTS");
        assertEquals("[OWNERSHIP ROLE O O]", grants().toString());
        asReader();
        refusal("SELECT * FROM t");
    }

    @Test
    public void copyCurrentGrantsKeepsItUnderTheNewOwner() {
        engine.execute("GRANT OWNERSHIP ON TABLE t TO ROLE o COPY CURRENT GRANTS");
        assertTrue(grants().contains("SELECT DATABASE_ROLE DR O"), grants().toString());
        asReader();
        assertEquals(1, engine.executeQuery("SELECT * FROM t").getRowCount());
    }

    @Test
    public void aUsersGrantIsOutboundToo() {
        engine.execute("REVOKE SELECT ON TABLE t FROM DATABASE ROLE p.dr");
        engine.execute("CREATE USER u2");
        engine.execute("GRANT SELECT ON TABLE t TO USER u2");
        final String refused = refusal("GRANT OWNERSHIP ON TABLE t TO ROLE o");
        assertTrue(refused.startsWith("SQL execution error: Dependent grant of privilege 'SELECT' on securable"
            + " 'P.PUBLIC.T' to user 'U2' exists."), refused);
        asSystem();
        engine.execute("GRANT OWNERSHIP ON TABLE t TO ROLE o REVOKE CURRENT GRANTS");
        assertEquals("[OWNERSHIP ROLE O O]", grants().toString());
    }
}
