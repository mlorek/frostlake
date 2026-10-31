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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT OWNERSHIP ON ROLE, USER and DATABASE ROLE moves the principal to another owning role, as the account's
 * documentation describes: SHOW ROLES, SHOW USERS and SHOW DATABASE ROLES name the new owner, ownership never passes
 * to a user, REVOKE OWNERSHIP is refused as it is for any object, and a role's own grants never block the move,
 * REVOKE CURRENT GRANTS taking back the roles granted to it.
 */
public class PrincipalOwnershipTransferTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT, USE ROLE and CREATE ROLE are not run on the live account");
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS po_sub");
        engine.execute("CREATE ROLE IF NOT EXISTS po_own");
        engine.execute("CREATE ROLE IF NOT EXISTS po_child");
        engine.execute("CREATE USER IF NOT EXISTS po_user");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("DROP USER IF EXISTS po_user");
        quietly("DROP ROLE IF EXISTS po_child");
        quietly("DROP ROLE IF EXISTS po_sub");
        quietly("DROP ROLE IF EXISTS po_own");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    /** The owner a listing names for the row whose name is {@code name}, or null when no row has it. */
    private String ownerIn(final String listing, final String name) {
        final ResultSet shown = engine.executeQuery(listing);
        for (final Row row : shown.getRows()) {
            if (name.equals(row.getValue(shown.getColumnIndex("name")))) {
                return String.valueOf(row.getValue(shown.getColumnIndex("owner")));
            }
        }
        return null;
    }

    /** The roles granted to a role, by name. */
    private List<String> rolesGrantedTo(final String role) {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS TO ROLE " + role);
        final List<String> granted = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            if ("ROLE".equals(row.getValue(shown.getColumnIndex("granted_on")))) {
                granted.add(String.valueOf(row.getValue(shown.getColumnIndex("name"))));
            }
        }
        return granted;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aRoleAUserAndADatabaseRoleMoveToAnotherOwner() {
        engine.execute("GRANT OWNERSHIP ON ROLE po_sub TO ROLE po_own");
        assertEquals("PO_OWN", ownerIn("SHOW ROLES LIKE 'PO_SUB'", "PO_SUB"));
        engine.execute("GRANT OWNERSHIP ON USER po_user TO ROLE po_own");
        assertEquals("PO_OWN", ownerIn("SHOW USERS LIKE 'PO_USER'", "PO_USER"));
        engine.execute("CREATE DATABASE ROLE po_dr");
        engine.execute("GRANT OWNERSHIP ON DATABASE ROLE test_db.po_dr TO ROLE po_own");
        assertEquals("PO_OWN", ownerIn("SHOW DATABASE ROLES IN DATABASE test_db", "PO_DR"));
    }

    @Test
    public void ownershipPassesToNoUserAndIsNeverRevoked() {
        assertEquals("SQL execution error: Cannot grant OWNERSHIP to users.",
            refusal("GRANT OWNERSHIP ON ROLE po_sub TO USER po_user"));
        assertEquals("SQL execution error: OWNERSHIP can only be transferred.",
            refusal("REVOKE OWNERSHIP ON ROLE po_sub FROM ROLE po_own"));
        assertTrue(refusal("GRANT OWNERSHIP ON ROLE nosuch_po TO ROLE po_own")
            .contains("Role 'NOSUCH_PO' does not exist or not authorized."));
        assertTrue(refusal("GRANT OWNERSHIP ON USER nosuch_po TO ROLE po_own").contains("NOSUCH_PO"));
        assertEquals("ACCOUNTADMIN", ownerIn("SHOW ROLES LIKE 'PO_SUB'", "PO_SUB"));
    }

    @Test
    public void aRolesOwnGrantsNeverBlockTheMove() {
        engine.execute("GRANT ROLE po_child TO ROLE po_sub");
        engine.execute("GRANT OWNERSHIP ON ROLE po_sub TO ROLE po_own COPY CURRENT GRANTS");
        assertTrue(rolesGrantedTo("po_sub").contains("PO_CHILD"));
        engine.execute("GRANT OWNERSHIP ON ROLE po_sub TO ROLE ACCOUNTADMIN");
        assertTrue(rolesGrantedTo("po_sub").contains("PO_CHILD"));
        engine.execute("GRANT OWNERSHIP ON ROLE po_sub TO ROLE po_own REVOKE CURRENT GRANTS");
        assertFalse(rolesGrantedTo("po_sub").contains("PO_CHILD"));
        assertEquals("PO_OWN", ownerIn("SHOW ROLES LIKE 'PO_SUB'", "PO_SUB"));
    }
}
