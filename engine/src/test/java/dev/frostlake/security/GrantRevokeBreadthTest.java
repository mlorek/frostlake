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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GRANT / REVOKE statement breadth beyond the basics: multi-privilege lists, ALL / FUTURE objects in
 * a schema, account-level grants, role-to-user grants, OWNERSHIP transfer, and the matching REVOKEs.
 */
public class GrantRevokeBreadthTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(GrantRevokeBreadthTest.class);

    @BeforeEach
    public void seedPrincipalsAndObjects() {
        engine.execute("CREATE ROLE breadth_role");
        engine.execute("CREATE USER breadth_user");
        engine.execute("CREATE TABLE breadth_t (id INTEGER)");
    }

    @Test
    public void multiPrivilegeGrantAndRevokeOnTable() {
        engine.execute("GRANT SELECT, INSERT, UPDATE ON TABLE breadth_t TO ROLE breadth_role");
        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE breadth_role");
        assertTrue(grants.getRows().size() >= 3, "three privileges granted in one statement");

        engine.execute("REVOKE INSERT, UPDATE ON TABLE breadth_t FROM ROLE breadth_role");
        final ResultSet after = engine.executeQuery("SHOW GRANTS TO ROLE breadth_role");
        assertTrue(after.getRows().size() < grants.getRows().size());
    }

    @Test
    public void allTablesInSchemaGrant() {
        // The bulk REVOKE forms are not modelled (grammar has GRANT-only ALL/FUTURE alternatives).
        engine.execute("GRANT SELECT ON ALL TABLES IN SCHEMA test_schema TO ROLE breadth_role");
        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE breadth_role");
        assertNotNull(grants);
    }

    @Test
    public void futureTablesInSchemaGrant() {
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA test_schema TO ROLE breadth_role");
        final ResultSet grants = engine.executeQuery("SHOW GRANTS TO ROLE breadth_role");
        assertNotNull(grants);
    }

    @Test
    public void accountLevelAndRoleToUserGrants() {
        // An account-level privilege must name its container. Live-verified:
        // GRANT CREATE DATABASE TO ROLE r      -> "syntax error line 1 at position 22 unexpected 'TO'"
        // GRANT CREATE DATABASE ON ACCOUNT TO ROLE r   -> succeeds
        // REVOKE CREATE DATABASE FROM ROLE r   -> "syntax error line 1 at position 23 unexpected 'FROM'"
        // REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE r -> succeeds
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE breadth_role");
        // GRANT ROLE r TO USER u succeeds live, and SHOW GRANTS TO USER u then lists it as a row with
        // privilege USAGE, granted_on ROLE.
        engine.execute("GRANT ROLE breadth_role TO USER breadth_user");
        final ResultSet userGrants = engine.executeQuery("SHOW GRANTS TO USER breadth_user");
        assertNotNull(userGrants);
        assertTrue(userGrants.getRows().size() >= 1, "role grant must be listed for the user");

        engine.execute("REVOKE ROLE breadth_role FROM USER breadth_user");
        engine.execute("REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE breadth_role");
        logger.info("Account-level and role-to-user grant cycle verified");
    }

    @Test
    public void ownershipGrantOnTable() {
        engine.execute("GRANT OWNERSHIP ON TABLE breadth_t TO ROLE breadth_role");
        final ResultSet grants = engine.executeQuery("SHOW GRANTS ON TABLE breadth_t");
        assertNotNull(grants);
        assertTrue(grants.getRows().size() >= 1);
    }
}
