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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A grant ON ALL objects of a kind is a grant on each object it reaches (documented), so each is recorded as a
 * grant naming the object is: in the object's owner's name, whichever role runs it, or in the name of the role
 * whose grant option passes the privilege on. The owner then takes such a grant back as it takes back its own. The
 * live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the embedded engine.
 */
public class BulkGrantGrantorTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE bgg");
        engine.execute("CREATE ROLE a");
        engine.execute("CREATE ROLE r");
        engine.execute("CREATE ROLE gr");
        engine.execute("CREATE USER ua");
        engine.execute("CREATE USER ug");
        engine.execute("GRANT ROLE a TO USER ua");
        engine.execute("GRANT ROLE gr TO USER ug");
        engine.execute("GRANT USAGE ON DATABASE bgg TO ROLE PUBLIC");
        engine.execute("GRANT USAGE ON SCHEMA bgg.public TO ROLE PUBLIC");
        engine.execute("GRANT CREATE TABLE, CREATE FUNCTION ON SCHEMA bgg.public TO ROLE a");
        as("ua", "a", "CREATE TABLE bgg.public.ta (a INT)");
        as("ua", "a", "CREATE TABLE bgg.public.tb (a INT)");
        as("ua", "a", "CREATE FUNCTION bgg.public.f() RETURNS INT AS '1'");
        engine.execute("GRANT ROLE a TO ROLE SYSADMIN");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** One user's answer - rows as "a, b | c, d", or the refusal - then back to the administrator. */
    private String as(final String user, final String role, final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
        try {
            return rows(sql);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    private String rows(final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    /** The privileges on an object: privilege, grantee and grantor, in that order. */
    private String grantsOn(final String object) {
        return rows("SHOW GRANTS ON " + object + " ->> SELECT \"privilege\", \"grantee_name\", \"granted_by\" "
            + "FROM $1 ORDER BY 1, 2");
    }

    @Test
    public void aGrantOnAllTablesIsRecordedInEachOwnersNameAndTheOwnerRevokesIt() {
        assertEquals("Statement executed successfully. 2 objects affected.",
            rows("GRANT INSERT ON ALL TABLES IN SCHEMA bgg.public TO ROLE r"));
        assertEquals("INSERT, R, A | OWNERSHIP, A, A", grantsOn("TABLE bgg.public.ta"));
        assertEquals("Statement executed successfully. 1 objects affected.",
            as("ua", "a", "REVOKE INSERT ON TABLE bgg.public.ta FROM ROLE r"));
        assertEquals("OWNERSHIP, A, A", grantsOn("TABLE bgg.public.ta"));
        assertEquals("INSERT, R, A | OWNERSHIP, A, A", grantsOn("TABLE bgg.public.tb"));
    }

    @Test
    public void aGrantOnAllFunctionsIsRecordedInTheFunctionOwnersName() {
        assertEquals("Statement executed successfully. 1 objects affected.",
            rows("GRANT USAGE ON ALL FUNCTIONS IN SCHEMA bgg.public TO ROLE r"));
        assertEquals("OWNERSHIP, A, A | USAGE, R, A", grantsOn("FUNCTION bgg.public.f()"));
    }

    @Test
    public void aGrantPassedOnThroughAGrantOptionIsRecordedInTheGrantingRolesName() {
        as("ua", "a", "GRANT SELECT ON TABLE bgg.public.ta TO ROLE gr WITH GRANT OPTION");
        as("ua", "a", "GRANT SELECT ON TABLE bgg.public.tb TO ROLE gr WITH GRANT OPTION");
        assertEquals("Statement executed successfully. 2 objects affected.",
            as("ug", "gr", "GRANT SELECT ON ALL TABLES IN SCHEMA bgg.public TO ROLE r"));
        assertEquals("OWNERSHIP, A, A | SELECT, GR, A | SELECT, R, GR", grantsOn("TABLE bgg.public.ta"));
    }
}
