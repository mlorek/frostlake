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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a role sees of the account's network policies (live-verified): SHOW NETWORK POLICIES lists a policy only to a
 * role that owns it or holds a privilege on it, or holds MANAGE GRANTS, and DESCRIBE NETWORK POLICY refuses any other
 * role as it refuses a policy that does not exist. PUBLIC, with no secondary roles in force, sees none.
 */
public class NetworkPolicyVisibilityTest extends MeasuredCellsTestSupport {

    @Test
    public void aRoleWithNoPrivilegeOnAPolicyNeitherListsNorDescribesIt() {
        final String opener = answer("SELECT CURRENT_ROLE()");
        engine.execute("CREATE OR REPLACE NETWORK POLICY np47v ALLOWED_IP_LIST = ('10.0.0.1')");
        try {
            assertEquals(1, engine.executeQuery("SHOW NETWORK POLICIES LIKE 'NP47V'").getRows().size());
            engine.execute("USE ROLE PUBLIC");
            engine.execute("USE SECONDARY ROLES NONE");
            assertAnswer("PUBLIC", "SELECT CURRENT_ROLE()");
            assertEquals(0, engine.executeQuery("SHOW NETWORK POLICIES LIKE 'NP47V'").getRows().size());
            assertAnswer("REFUSED SQL compilation error: | Network policy 'NP47V' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on NETWORK POLICY NP47V.",
                "DESCRIBE NETWORK POLICY np47v");
        } finally {
            engine.execute("USE ROLE " + opener);
            engine.execute("USE SECONDARY ROLES ALL");
            engine.execute("DROP NETWORK POLICY IF EXISTS np47v");
        }
    }
}
