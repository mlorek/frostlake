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
 * DESCRIBE USER of another user needs OWNERSHIP of that user (documented: "Individual users can see their own
 * properties ... To view the properties of another user, you must use a role that has ... OWNERSHIP"); a user
 * describes themselves. Any role read every user's e-mail, keys and lock state. The refusal's words are unmeasured on
 * live. Engine only: the live account allows no CREATE ROLE or CREATE USER.
 */
public class DescribeUserOwnershipTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE low");
        engine.execute("CREATE USER boss EMAIL = 'boss@example.com'");
        engine.execute("CREATE USER ulow");
        engine.execute("GRANT ROLE low TO USER ulow");
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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String property(final String user, final String name) {
        for (final Row row : engine.executeQuery("DESCRIBE USER " + user).getRows()) {
            if (name.equals(row.getValue(0))) {
                return String.valueOf(row.getValue(1));
            }
        }
        return null;
    }

    @Test
    public void anotherUserNeedsItsOwnership() {
        as("ULOW", "LOW");
        final String refused = refusal("DESCRIBE USER boss");
        assertTrue(refused.contains("Insufficient privileges to operate on user 'BOSS'"), refused);
        assertEquals("ULOW", property("ulow", "NAME"));
        as("SYSTEM", systemRole);
        engine.execute("GRANT OWNERSHIP ON USER boss TO ROLE low");
        as("ULOW", "LOW");
        assertEquals("boss@example.com", property("boss", "EMAIL"));
    }
}
