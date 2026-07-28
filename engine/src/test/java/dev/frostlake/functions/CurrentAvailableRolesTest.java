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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT_AVAILABLE_ROLES() — a JSON array string of the roles the session can use: active roles,
 * the current user's granted roles, and the closure over roles granted to roles, alphabetically.
 */
public class CurrentAvailableRolesTest extends BaseDatabaseTest {

    private String q(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void includesCurrentRoleAsJsonArray() {
        final String roles = q("SELECT CURRENT_AVAILABLE_ROLES()");
        final String current = q("SELECT CURRENT_ROLE()");
        assertTrue(roles.startsWith("[") && roles.endsWith("]"), "JSON array text: " + roles);
        assertTrue(roles.contains("\"" + current + "\""), current + " must appear in " + roles);
    }

    @Test
    public void includesRolesGrantedThroughTheHierarchy() {
        engine.execute("CREATE ROLE outer_r");
        engine.execute("CREATE ROLE inner_r");
        engine.execute("GRANT ROLE inner_r TO ROLE outer_r");
        engine.execute("GRANT ROLE outer_r TO ROLE SYSADMIN");
        final String roles = q("SELECT CURRENT_AVAILABLE_ROLES()");
        assertTrue(roles.contains("\"OUTER_R\""), "direct grant missing: " + roles);
        assertTrue(roles.contains("\"INNER_R\""), "hierarchy grant missing: " + roles);
        // Alphabetical: INNER_R sorts before OUTER_R.
        assertTrue(roles.indexOf("INNER_R") < roles.indexOf("OUTER_R"), roles);
        assertEquals("[", roles.substring(0, 1));
    }
}
