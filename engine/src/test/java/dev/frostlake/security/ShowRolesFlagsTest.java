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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW ROLES answers is_default 'Y' for the session user's DEFAULT_ROLE and is_inherited 'Y' for each role the
 * current role inherits, directly or through another (documented: "'Y' if the role is the default role of the
 * current user", "'Y' if the role is inherited by the current role"); both read 'N' on every row. That a role reached
 * through two levels counts is unmeasured on live. Engine only: the live account allows no CREATE ROLE or USER.
 */
public class ShowRolesFlagsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        for (final String sql : new String[] {"CREATE ROLE r1", "CREATE ROLE r2", "CREATE ROLE r3", "CREATE ROLE r4",
            "GRANT ROLE r2 TO ROLE r1", "GRANT ROLE r3 TO ROLE r2", "CREATE USER u1 DEFAULT_ROLE = r1",
            "GRANT ROLE r1 TO USER u1", "GRANT ROLE r4 TO USER u1"}) {
            engine.execute(sql);
        }
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("U1");
        engine.setCurrentRole("R1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** The name, is_default, is_current and is_inherited of each listed role of the given names. */
    private List<String> flags(final String... names) {
        final List<String> rows = new ArrayList<>();
        for (final String name : names) {
            for (final Row row : engine.executeQuery("SHOW ROLES LIKE '" + name + "'").getRows()) {
                rows.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|" + row.getValue(4));
            }
        }
        return rows;
    }

    @Test
    public void theDefaultAndTheInheritedRolesAreMarked() {
        assertEquals(List.of("R1|Y|Y|N", "R2|N|N|Y", "R3|N|N|Y", "R4|N|N|N"), flags("R1", "R2", "R3", "R4"));
        engine.setCurrentRole("R4");
        assertEquals(List.of("R1|Y|N|N", "R2|N|N|N", "R4|N|Y|N"), flags("R1", "R2", "R4"));
    }
}
