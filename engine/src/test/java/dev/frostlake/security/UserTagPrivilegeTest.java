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
 * ALTER USER ... SET TAG and UNSET TAG take APPLY TAG on the account, or APPLY on each tag and OWNERSHIP of the user
 * (documented, "Tag privileges"); MODIFY on the user is not what they ask. They asked nothing, so any role tagged and
 * untagged any user. The refusal's wording is not measured. Engine only: the live account allows no CREATE
 * ROLE, CREATE USER or GRANT.
 */
public class UserTagPrivilegeTest {

    private static final String NO_APPLY = "SQL access control error:\nInsufficient privileges to operate on tag"
        + " 'T1'. Your primary role R1 must have APPLY granted on TAG P.PUBLIC.T1.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE r1");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("CREATE USER victim");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("CREATE TAG p.public.t1");
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


    private String tagOfVictim() {
        return String.valueOf(engine.executeQuery("SELECT SYSTEM$GET_TAG('P.PUBLIC.T1', 'VICTIM', 'USER')")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void aRoleWithNoPrivilegeNeitherTagsNorUntagsAUser() {
        engine.execute("ALTER USER victim SET TAG p.public.t1 = 'kept'");
        as("U1", "R1");
        assertEquals(NO_APPLY, refusal("ALTER USER victim SET TAG p.public.t1 = 'x'"));
        assertEquals(NO_APPLY, refusal("ALTER USER victim UNSET TAG p.public.t1"));
        assertEquals(NO_APPLY, refusal("ALTER USER u1 SET TAG p.public.t1 = 'x'"));
        asSystem();
        assertEquals("kept", tagOfVictim());
    }

    @Test
    public void applyOnTheTagTakesOwnershipOfTheUserToo() {
        engine.execute("GRANT APPLY ON TAG p.public.t1 TO ROLE r1");
        as("U1", "R1");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on user 'VICTIM'. Your primary"
            + " role R1 must have OWNERSHIP granted on USER VICTIM.", refusal("ALTER USER victim SET TAG p.public.t1"
            + " = 'x'"));
        // A user the role created is the role's own.
        asSystem();
        engine.execute("GRANT CREATE USER ON ACCOUNT TO ROLE r1");
        as("U1", "R1");
        engine.execute("CREATE USER mine");
        engine.execute("ALTER USER mine SET TAG p.public.t1 = 'x'");
        engine.execute("ALTER USER mine UNSET TAG p.public.t1");
    }

    @Test
    public void applyTagOnTheAccountTagsEveryUser() {
        engine.execute("GRANT APPLY TAG ON ACCOUNT TO ROLE r1");
        as("U1", "R1");
        engine.execute("ALTER USER victim SET TAG p.public.t1 = 'x'");
        engine.execute("ALTER USER victim UNSET TAG p.public.t1");
        asSystem();
        assertEquals("null", tagOfVictim());
    }
}
