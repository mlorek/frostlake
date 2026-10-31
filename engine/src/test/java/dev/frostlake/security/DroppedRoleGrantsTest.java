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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DROP ROLE revokes every grant that names the role: the role granted to roles and to users, and what it granted as
 * the grantor - roles, database roles and privileges, those on the objects it owned too, which pass to the dropping
 * role whatever their kind (documented). Its future grants go with it. The session's own primary role cannot be
 * dropped, IF EXISTS or not.
 */
public class DroppedRoleGrantsTest extends BaseDatabaseTest {

    private String user;
    private String originalRole;

    @BeforeEach
    public void namesTheSession() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account allows no CREATE ROLE");
        originalRole = String.valueOf(engine.executeQuery("SELECT CURRENT_ROLE()").getRows().get(0).getValue(0));
        user = String.valueOf(engine.executeQuery("SELECT CURRENT_USER()").getRows().get(0).getValue(0));
        engine.execute("CREATE USER IF NOT EXISTS \"" + user + "\"");
    }

    /** The {@code name} cell of each row a SHOW GRANTS lists. */
    private List<String> grantedNames(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue("name")));
        }
        return out;
    }

    /** Each row a SHOW GRANTS lists as its privilege, grantee and grantor, sorted. */
    private List<String> grants(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(rs.getValue("privilege") + "|" + rs.getValue("grantee_name") + "|" + rs.getValue("granted_by"));
        }
        Collections.sort(out);
        return out;
    }

    @Test
    public void aDroppedRoleIsNoLongerGrantedToRolesOrUsers() {
        engine.execute("CREATE ROLE dr_gr");
        engine.execute("GRANT ROLE dr_gr TO ROLE sysadmin");
        engine.execute("GRANT ROLE dr_gr TO USER \"" + user + "\"");
        assertTrue(grantedNames("SHOW GRANTS TO ROLE sysadmin").contains("DR_GR"));
        engine.execute("DROP ROLE dr_gr");
        assertFalse(grantedNames("SHOW GRANTS TO ROLE sysadmin").contains("DR_GR"));
        assertFalse(grantedNames("SHOW GRANTS TO USER \"" + user + "\"").contains("DR_GR"));
        assertFalse(String.valueOf(engine.executeQuery("SELECT CURRENT_AVAILABLE_ROLES()").getRows().get(0)
            .getValue(0)).contains("DR_GR"));
        // A role created again under the name is granted to no one.
        engine.execute("CREATE ROLE dr_gr");
        assertEquals(List.of(), grantedNames("SHOW GRANTS OF ROLE dr_gr"));
        engine.execute("DROP ROLE dr_gr");
    }

    @Test
    public void theRolesItGrantedGoWithIt() {
        engine.execute("CREATE ROLE dr_grantor");
        engine.execute("CREATE ROLE dr_granted");
        engine.execute("GRANT OWNERSHIP ON ROLE dr_granted TO ROLE dr_grantor");
        engine.execute("GRANT ROLE dr_grantor TO USER \"" + user + "\"");
        engine.execute("USE ROLE dr_grantor");
        engine.execute("GRANT ROLE dr_granted TO USER \"" + user + "\"");
        engine.execute("USE ROLE \"" + originalRole + "\"");
        assertTrue(grantedNames("SHOW GRANTS TO USER \"" + user + "\"").contains("DR_GRANTED"));
        engine.execute("DROP ROLE dr_grantor");
        assertFalse(grantedNames("SHOW GRANTS TO USER \"" + user + "\"").contains("DR_GRANTED"));
        assertEquals(1, engine.executeQuery("SHOW ROLES LIKE 'DR_GRANTED'").getRows().size());
        engine.execute("DROP ROLE dr_granted");
    }

    @Test
    public void thePrivilegesItGrantedGoWithItThoseOnTheObjectsItOwnedToo() {
        engine.execute("CREATE ROLE dr_owner");
        engine.execute("CREATE ROLE dr_reader");
        engine.execute("CREATE DATABASE ROLE dr_readers");
        engine.execute("CREATE TABLE dr_owned (a INT)");
        engine.execute("GRANT OWNERSHIP ON TABLE dr_owned TO ROLE dr_owner");
        engine.execute("GRANT ROLE dr_owner TO USER \"" + user + "\"");
        engine.execute("USE ROLE dr_owner");
        engine.execute("GRANT SELECT ON TABLE dr_owned TO ROLE dr_reader");
        engine.execute("GRANT INSERT ON TABLE dr_owned TO DATABASE ROLE dr_readers");
        engine.execute("USE ROLE \"" + originalRole + "\"");
        assertEquals(List.of("INSERT|DR_READERS|DR_OWNER", "OWNERSHIP|DR_OWNER|DR_OWNER", "SELECT|DR_READER|DR_OWNER"),
            grants("SHOW GRANTS ON TABLE dr_owned"));
        engine.execute("DROP ROLE dr_owner");
        // The table is the dropping role's, and what the dropped role granted on it is taken back.
        assertEquals(List.of("OWNERSHIP|" + originalRole + "|" + originalRole),
            grants("SHOW GRANTS ON TABLE dr_owned"));
        assertEquals(List.of(), grants("SHOW GRANTS TO ROLE dr_reader"));
        assertFalse(grantedNames("SHOW GRANTS TO DATABASE ROLE dr_readers").contains("TEST_DB.TEST_SCHEMA.DR_OWNED"));
        engine.execute("DROP DATABASE ROLE dr_readers");
        engine.execute("DROP ROLE dr_reader");
        engine.execute("DROP TABLE dr_owned");
    }

    @Test
    public void theDatabaseRolesItGrantedGoWithItAndThoseItOwnedPassOn() {
        engine.execute("CREATE ROLE dr_dbowner");
        engine.execute("CREATE ROLE dr_member");
        engine.execute("CREATE DATABASE ROLE dr_dbrole");
        engine.execute("GRANT OWNERSHIP ON DATABASE ROLE dr_dbrole TO ROLE dr_dbowner");
        engine.execute("GRANT ROLE dr_dbowner TO USER \"" + user + "\"");
        engine.execute("USE ROLE dr_dbowner");
        engine.execute("GRANT DATABASE ROLE dr_dbrole TO ROLE dr_member");
        engine.execute("USE ROLE \"" + originalRole + "\"");
        assertTrue(grantedNames("SHOW GRANTS TO ROLE dr_member").contains("TEST_DB.DR_DBROLE"));
        engine.execute("DROP ROLE dr_dbowner");
        assertFalse(grantedNames("SHOW GRANTS TO ROLE dr_member").contains("TEST_DB.DR_DBROLE"));
        assertEquals(0, engine.executeQuery("SHOW GRANTS OF DATABASE ROLE dr_dbrole").getRows().size());
        final ResultSet roles = engine.executeQuery("SHOW DATABASE ROLES LIKE 'DR_DBROLE' IN DATABASE test_db");
        assertTrue(roles.next());
        assertEquals(originalRole, roles.getValue("owner"));
        engine.execute("DROP DATABASE ROLE dr_dbrole");
        engine.execute("DROP ROLE dr_member");
    }

    @Test
    public void whatItOwnedOfAnyKindPassesToTheDroppingRole() {
        engine.execute("CREATE ROLE dr_kinds");
        engine.execute("GRANT ROLE dr_kinds TO USER \"" + user + "\"");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE dr_kinds");
        engine.execute("GRANT ALL ON SCHEMA test_db.test_schema TO ROLE dr_kinds");
        engine.execute("USE ROLE dr_kinds");
        engine.execute("CREATE SECRET dr_secret TYPE = GENERIC_STRING SECRET_STRING = 'x'");
        engine.execute("CREATE NETWORK RULE dr_rule TYPE = IPV4 VALUE_LIST = ('10.0.0.1') MODE = INGRESS");
        engine.execute("CREATE IMAGE REPOSITORY dr_images");
        engine.execute("CREATE NOTEBOOK dr_note");
        engine.execute("USE ROLE \"" + originalRole + "\"");
        engine.execute("DROP ROLE dr_kinds");
        for (final String show : new String[] {"SHOW SECRETS LIKE 'DR_SECRET'", "SHOW NETWORK RULES LIKE 'DR_RULE'",
            "SHOW IMAGE REPOSITORIES LIKE 'DR_IMAGES'", "SHOW NOTEBOOKS LIKE 'DR_NOTE'"}) {
            final ResultSet rs = engine.executeQuery(show);
            assertTrue(rs.next(), show);
            assertEquals(originalRole, rs.getValue("owner"), show);
        }
    }

    @Test
    public void itsFutureGrantsGoWithIt() {
        engine.execute("CREATE ROLE dr_future");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA test_db.test_schema TO ROLE dr_future");
        assertEquals(1, engine.executeQuery("SHOW FUTURE GRANTS IN SCHEMA test_db.test_schema").getRows().size());
        engine.execute("DROP ROLE dr_future");
        assertEquals(0, engine.executeQuery("SHOW FUTURE GRANTS IN SCHEMA test_db.test_schema").getRows().size());
        // A role created again under the name holds nothing on a table created afterwards.
        engine.execute("CREATE ROLE dr_future");
        engine.execute("CREATE TABLE dr_after (a INT)");
        assertEquals(List.of(), grants("SHOW GRANTS TO ROLE dr_future"));
        engine.execute("DROP TABLE dr_after");
        engine.execute("DROP ROLE dr_future");
    }

    @Test
    public void theCurrentPrimaryRoleIsRefused() {
        engine.execute("CREATE ROLE dr_primary");
        engine.execute("GRANT ROLE dr_primary TO USER \"" + user + "\"");
        engine.execute("USE ROLE dr_primary");
        for (final String drop : new String[] {"DROP ROLE dr_primary", "DROP ROLE IF EXISTS dr_primary"}) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute(drop);
                }
            });
            assertEquals("SQL execution error: Cannot drop role DR_PRIMARY as it is the current primary role.",
                refused.getMessage(), drop);
        }
        engine.execute("USE ROLE \"" + originalRole + "\"");
        engine.execute("DROP ROLE dr_primary");
        assertEquals(0, engine.executeQuery("SHOW ROLES LIKE 'DR_PRIMARY'").getRows().size());
    }
}
