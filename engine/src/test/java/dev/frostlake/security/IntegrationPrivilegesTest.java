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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An integration is an account object with an owner (documented, each statement's access control table): CREATE takes
 * CREATE INTEGRATION on the account, ALTER and DROP its OWNERSHIP, SHOW lists and DESCRIBE answers for it only to a
 * role holding some privilege on it, and a stage's STORAGE_INTEGRATION, a routine's EXTERNAL_ACCESS_INTEGRATIONS and an
 * external function's API_INTEGRATION take USAGE on it. Every role could create, widen, disable and drop every
 * integration. USAGE and OWNERSHIP on one are granted, revoked and listed as on any object. Engine
 * only: the live account allows no CREATE ROLE or GRANT.
 */
public class IntegrationPrivilegesTest {

    private DatabaseEngine engine;
    private String systemRole;
    private String account;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        account = engine.getCatalog().getAccountLocator();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE STAGE, CREATE FUNCTION ON SCHEMA p.public TO ROLE r1");
        engine.execute("CREATE NETWORK RULE nr MODE = EGRESS TYPE = HOST_PORT VALUE_LIST = ('example.com')");
        engine.execute("CREATE EXTERNAL ACCESS INTEGRATION eai ALLOWED_NETWORK_RULES = (nr) ENABLED = TRUE");
        engine.execute("CREATE STORAGE INTEGRATION si TYPE = EXTERNAL_STAGE STORAGE_PROVIDER = 'S3'"
            + " STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::001234567890:role/r' STORAGE_ALLOWED_LOCATIONS = ('s3://b/')"
            + " ENABLED = TRUE");
        engine.execute("CREATE SECURITY INTEGRATION oi TYPE = OAUTH OAUTH_CLIENT = CUSTOM"
            + " OAUTH_CLIENT_TYPE = 'CONFIDENTIAL' OAUTH_REDIRECT_URI = 'https://x.example/cb' ENABLED = TRUE");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asUser() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u1");
        engine.setCurrentRole("r1");
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

    private String names(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    private static String missing(final String name) {
        return "Integration '" + name + "' does not exist or not authorized.";
    }

    @Test
    public void aRoleWithNoPrivilegeNeitherSeesNorChangesAnIntegration() {
        asUser();
        assertEquals("", names("SHOW INTEGRATIONS"));
        assertTrue(refusal("DESCRIBE INTEGRATION si").contains(missing("SI")));
        assertTrue(refusal("ALTER STORAGE INTEGRATION si SET STORAGE_ALLOWED_LOCATIONS = ('s3://evil/')")
            .contains(missing("SI")));
        assertTrue(refusal("ALTER INTEGRATION eai SET ENABLED = FALSE").contains(missing("EAI")));
        assertTrue(refusal("DROP INTEGRATION oi").contains(missing("OI")));
        asSystem();
        assertEquals("EAI,OI,SI", names("SHOW INTEGRATIONS"));
        String allowed = null;
        for (final Row row : engine.executeQuery("DESCRIBE INTEGRATION si").getRows()) {
            if ("STORAGE_ALLOWED_LOCATIONS".equals(row.getValue(0))) {
                allowed = String.valueOf(row.getValue(2));
            }
        }
        assertEquals("s3://b/", allowed);
    }

    @Test
    public void createIntegrationTakesTheAccountsCreateIntegration() {
        asUser();
        assertEquals("SQL access control error:\nInsufficient privileges to operate on account '" + account
            + "'. Your primary role R1 must have CREATE INTEGRATION granted on ACCOUNT " + account + ".",
            refusal("CREATE EXTERNAL ACCESS INTEGRATION eai2 ALLOWED_NETWORK_RULES = (nr) ENABLED = TRUE"));
        asSystem();
        engine.execute("GRANT CREATE INTEGRATION ON ACCOUNT TO ROLE r1");
        asUser();
        engine.execute("CREATE EXTERNAL ACCESS INTEGRATION eai2 ALLOWED_NETWORK_RULES = (nr) ENABLED = TRUE");
        assertEquals("EAI2", names("SHOW INTEGRATIONS"));
        engine.execute("ALTER INTEGRATION eai2 SET ENABLED = FALSE");
        // Replacing one the role does not own takes its OWNERSHIP.
        assertTrue(refusal("CREATE OR REPLACE EXTERNAL ACCESS INTEGRATION eai ALLOWED_NETWORK_RULES = (nr)"
            + " ENABLED = TRUE").contains("OWNERSHIP"));
        engine.execute("DROP INTEGRATION eai2");
    }

    @Test
    public void usageLetsARoleSeeAndUseAnIntegrationButNotChangeIt() {
        asUser();
        assertTrue(refusal("CREATE STAGE stg URL = 's3://b/x/' STORAGE_INTEGRATION = si").contains(missing("SI")));
        assertTrue(refusal("CREATE FUNCTION f(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT"
            + " EXTERNAL_ACCESS_INTEGRATIONS = (eai) AS 'return X;'").contains(missing("EAI")));
        asSystem();
        engine.execute("GRANT USAGE ON INTEGRATION si TO ROLE r1");
        engine.execute("GRANT USAGE ON INTEGRATION eai TO ROLE r1");
        assertEquals("USAGE,OWNERSHIP", privileges("SHOW GRANTS ON INTEGRATION si"));
        asUser();
        assertEquals("EAI,SI", names("SHOW INTEGRATIONS"));
        engine.execute("DESCRIBE INTEGRATION si");
        engine.execute("CREATE STAGE stg URL = 's3://b/x/' STORAGE_INTEGRATION = si");
        engine.execute("CREATE FUNCTION f(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT"
            + " EXTERNAL_ACCESS_INTEGRATIONS = (eai) AS 'return X;'");
        assertTrue(refusal("ALTER INTEGRATION eai SET ENABLED = FALSE").contains("must have OWNERSHIP granted on"
            + " INTEGRATION EAI"));
        assertTrue(refusal("DROP INTEGRATION si").contains("must have OWNERSHIP granted on INTEGRATION SI"));
        asSystem();
        engine.execute("REVOKE USAGE ON INTEGRATION si FROM ROLE r1");
        asUser();
        assertEquals("EAI", names("SHOW INTEGRATIONS"));
    }

    @Test
    public void ownershipOfAnIntegrationIsTransferred() {
        asSystem();
        engine.execute("GRANT OWNERSHIP ON INTEGRATION oi TO ROLE r1");
        asUser();
        engine.execute("ALTER INTEGRATION oi SET ENABLED = FALSE");
        engine.execute("DROP INTEGRATION oi");
        asSystem();
        assertEquals("EAI,SI", names("SHOW INTEGRATIONS"));
    }

    private String privileges(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(1));
        }
        return out.toString();
    }
}
