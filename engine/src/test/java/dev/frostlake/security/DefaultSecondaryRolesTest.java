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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session of a user starts with the secondary roles its DEFAULT_SECONDARY_ROLES names: ('ALL') puts every role
 * granted to the user in use, () none (documented). A user that sets none leaves the session's secondary roles as
 * they are. Engine only: the live account allows no CREATE USER or CREATE ROLE.
 */
public class DefaultSecondaryRolesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE ROLE dsr_r1");
        engine.execute("CREATE USER dsr_all DEFAULT_SECONDARY_ROLES = ('ALL')");
        engine.execute("CREATE USER dsr_none DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("CREATE USER dsr_unset");
        engine.execute("GRANT ROLE dsr_r1 TO USER dsr_all");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String secondaryRoles() {
        return String.valueOf(engine.executeQuery("SELECT CURRENT_SECONDARY_ROLES()").getRows().get(0).getValue(0));
    }

    @Test
    public void aSessionStartsFromItsUsersDefaultSecondaryRoles() {
        engine.setCurrentUser("DSR_ALL");
        assertTrue(secondaryRoles().contains("\"value\":\"ALL\""), secondaryRoles());
        engine.setCurrentUser("DSR_NONE");
        assertEquals("{\"roles\":\"\",\"value\":\"\"}", secondaryRoles());
    }

    @Test
    public void aUserWithoutDefaultsLeavesTheSecondaryRolesAsTheyAre() {
        engine.execute("USE SECONDARY ROLES ALL");
        engine.setCurrentUser("DSR_UNSET");
        assertTrue(secondaryRoles().contains("\"value\":\"ALL\""), secondaryRoles());
        engine.setCurrentUser("NOSUCH_DSR_USER");
        assertTrue(secondaryRoles().contains("\"value\":\"ALL\""), secondaryRoles());
    }
}
