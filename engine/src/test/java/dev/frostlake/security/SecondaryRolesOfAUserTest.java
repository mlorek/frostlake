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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * USE SECONDARY ROLES ALL for a user that was granted roles: CURRENT_SECONDARY_ROLES() lists the roles granted to
 * the user but the primary one, in alphabetical order, and hears a USE ROLE written after it. The account the
 * suite measures against answers the same shape for its own user's roles.
 */
public class SecondaryRolesOfAUserTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE ROLE sr_b");
        engine.execute("CREATE ROLE sr_a");
        engine.execute("CREATE ROLE sr_unheld");
        engine.execute("CREATE USER sr_user");
        engine.execute("GRANT ROLE sr_b TO USER sr_user");
        engine.execute("GRANT ROLE sr_a TO USER sr_user");
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("sr_user");
        engine.setCurrentRole("sr_a");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String secondaryRoles() {
        final ResultSet answer = engine.executeQuery("SELECT CURRENT_SECONDARY_ROLES()");
        return String.valueOf(answer.getRows().get(0).getValue(0));
    }

    @Test
    public void allStandsForTheRolesGrantedToTheUserButThePrimaryOne() {
        // A user whose DEFAULT_SECONDARY_ROLES was never written starts with the default, ALL.
        assertEquals("{\"roles\":\"SR_B\",\"value\":\"ALL\"}", secondaryRoles());
        engine.execute("USE SECONDARY ROLES NONE");
        assertEquals("{\"roles\":\"\",\"value\":\"\"}", secondaryRoles());
        engine.execute("USE SECONDARY ROLES ALL");
        assertEquals("{\"roles\":\"SR_B\",\"value\":\"ALL\"}", secondaryRoles());
        engine.execute("USE ROLE sr_b");
        assertEquals("{\"roles\":\"SR_A\",\"value\":\"ALL\"}", secondaryRoles());
        engine.execute("USE SECONDARY ROLES NONE");
        assertEquals("{\"roles\":\"\",\"value\":\"\"}", secondaryRoles());
    }

    @Test
    public void theRolesNamedReplaceTheOnesInUse() {
        engine.execute("USE SECONDARY ROLES sr_b, sr_a, sr_b");
        assertEquals("{\"roles\":\"SR_A,SR_B\",\"value\":\"SR_A,SR_B\"}", secondaryRoles());
        // A role the user was not granted is one it may not activate, and the roles in use stay.
        final RuntimeException unheld = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("USE SECONDARY ROLES sr_unheld");
            }
        });
        assertTrue(unheld.getMessage().contains("Requested role 'SR_UNHELD' is not assigned to the executing user."),
            unheld.getMessage());
        assertEquals("{\"roles\":\"SR_A,SR_B\",\"value\":\"SR_A,SR_B\"}", secondaryRoles());
        engine.execute("USE SECONDARY ROLES sr_b");
        assertEquals("{\"roles\":\"SR_B\",\"value\":\"SR_B\"}", secondaryRoles());
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("USE SECONDARY ROLES sr_a, nosuch_sr");
            }
        });
        assertTrue(refused.getMessage().contains("Object does not exist, or operation cannot be performed."),
            refused.getMessage());
        assertEquals("{\"roles\":\"SR_B\",\"value\":\"SR_B\"}", secondaryRoles());
    }
}
