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

/**
 * A routine's SECRETS clause binds a secret only for a role that may read it: READ or OWNERSHIP on the secret, and
 * USAGE on its database and schema (documented, CREATE FUNCTION's access control table). A routine that names a
 * secret hands its value to the handler, so binding any secret read it (FL-432). A secret the role may not read is
 * refused as one that is not there. Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class RoutineSecretReadPrivilegeTest {

    private static final String JS = "CREATE FUNCTION f(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT ";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("CREATE SCHEMA p.hid");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE FUNCTION ON SCHEMA p.public TO ROLE r1");
        engine.execute("CREATE SECRET p.hid.sec TYPE = GENERIC_STRING SECRET_STRING = 'topsecret'");
        engine.execute("CREATE SECRET pub TYPE = GENERIC_STRING SECRET_STRING = 'p'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    private void asSystem() {
        as("SYSTEM", systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }


    private static String missing(final String name) {
        return "SQL compilation error:\nSecret '" + name + "' does not exist or operation not authorized.";
    }

    @Test
    public void aRoutineBindsNoSecretItsRoleMayNotRead() {
        as("U1", "R1");
        assertEquals(missing("SEC"), refusal(JS + "SECRETS = ('k' = p.hid.sec) AS 'return X;'"));
        assertEquals(missing("PUB"), refusal(JS + "SECRETS = ('k' = pub) AS 'return X;'"));
    }

    @Test
    public void readOnTheSecretAndUsageOnItsSchemaBindIt() {
        engine.execute("GRANT READ ON SECRET pub TO ROLE r1");
        engine.execute("GRANT READ ON SECRET p.hid.sec TO ROLE r1");
        as("U1", "R1");
        engine.execute(JS + "SECRETS = ('k' = pub) AS 'return X;'");
        // READ on the secret is not enough without USAGE on the schema that holds it.
        assertEquals(missing("SEC"), refusal("CREATE FUNCTION g(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT"
            + " SECRETS = ('k' = p.hid.sec) AS 'return X;'"));
        asSystem();
        engine.execute("GRANT USAGE ON SCHEMA p.hid TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE FUNCTION g(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT SECRETS = ('k' = p.hid.sec)"
            + " AS 'return X;'");
    }

    @Test
    public void theSecretsOwnerBindsIt() {
        engine.execute("GRANT CREATE SECRET ON SCHEMA p.public TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE SECRET mine TYPE = GENERIC_STRING SECRET_STRING = 'm'");
        engine.execute(JS + "SECRETS = ('k' = mine) AS 'return X;'");
    }
}
