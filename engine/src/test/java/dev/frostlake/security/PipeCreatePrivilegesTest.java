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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What CREATE PIPE asks of the role creating the pipe, as Snowflake's CREATE PIPE page lists it: CREATE PIPE on the
 * schema, READ on an internal stage its COPY reads or USAGE on an external one, and SELECT and INSERT on the table the
 * COPY loads. The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is the documented one, held
 * by the embedded engine.
 */
public class PipeCreatePrivilegesTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (a STRING)");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE STAGE ext URL = 's3://some-bucket/data/'");
        engine.execute("CREATE ROLE loader");
        engine.execute("CREATE USER lu");
        engine.execute("GRANT ROLE loader TO USER lu");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE loader");
        engine.execute("GRANT USAGE, CREATE PIPE ON SCHEMA db.public TO ROLE loader");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asLoader() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("lu");
        engine.setCurrentRole("loader");
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

    @Test
    public void anInternalStageAsksReadAndTheTableSelectAndInsert() {
        final String create = "CREATE PIPE pi AS COPY INTO t FROM @st";
        asLoader();
        assertTrue(refusal(create).contains("stage 'ST'"), refusal(create));
        asSystem();
        engine.execute("GRANT READ ON STAGE st TO ROLE loader");
        asLoader();
        assertTrue(refusal(create).contains("INSERT"), refusal(create));
        asSystem();
        engine.execute("GRANT INSERT ON TABLE t TO ROLE loader");
        asLoader();
        assertTrue(refusal(create).contains("SELECT"), refusal(create));
        asSystem();
        engine.execute("GRANT SELECT ON TABLE t TO ROLE loader");
        asLoader();
        engine.execute(create);
        assertEquals(1, engine.executeQuery("SHOW PIPES LIKE 'PI'").getRowCount());
    }

    @Test
    public void anExternalStageAsksUsage() {
        final String create = "CREATE PIPE pe AS COPY INTO t FROM @ext";
        engine.execute("GRANT SELECT, INSERT ON TABLE t TO ROLE loader");
        asLoader();
        assertTrue(refusal(create).contains("stage 'EXT'"), refusal(create));
        asSystem();
        engine.execute("GRANT USAGE ON STAGE ext TO ROLE loader");
        asLoader();
        engine.execute(create);
        assertEquals(1, engine.executeQuery("SHOW PIPES LIKE 'PE'").getRowCount());
    }
}
