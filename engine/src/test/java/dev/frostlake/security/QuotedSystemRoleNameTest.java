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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A quoted role name is that name alone, a system role's included: {@code "sysadmin"} is a role of its own beside
 * SYSADMIN, created and dropped by its spelling, while SYSADMIN however written unquoted stays the system role. A
 * user's DEFAULT_ROLE written as a quoted name keeps it as written, a bare one is upper-cased, and a string is folded
 * as before. Engine only: the live account creates no role or user.
 */
public class QuotedSystemRoleNameTest extends MeasuredCellsTestSupport {

    private static final String NAMES = "SHOW ROLES LIKE 'sysadmin' ->> SELECT \"name\" FROM $1 ORDER BY 1";
    private static final String DEFAULT_ROLE =
        "DESC USER u1989 ->> SELECT \"value\" FROM $1 WHERE \"property\" = 'DEFAULT_ROLE'";

    @Test
    public void aQuotedLowerCaseSystemRoleNameIsARoleOfItsOwn() {
        assumeFalse(isLiveSnowflake());
        engine.execute("CREATE ROLE \"sysadmin\"");
        assertAnswer("SYSADMIN ;; sysadmin", NAMES);
        engine.execute("DROP ROLE \"sysadmin\"");
        assertAnswer("SYSADMIN", NAMES);
        assertAnswer("REFUSED Cannot create system role: SYSADMIN", "CREATE ROLE sysadmin");
        assertAnswer("REFUSED Cannot drop system role: SYSADMIN", "DROP ROLE \"SYSADMIN\"");
    }

    @Test
    public void aQuotedDefaultRoleKeepsItsCase() {
        assumeFalse(isLiveSnowflake());
        engine.execute("CREATE USER u1989 DEFAULT_ROLE = \"low\"");
        assertAnswer("low", DEFAULT_ROLE);
        engine.execute("ALTER USER u1989 SET DEFAULT_ROLE = low");
        assertAnswer("LOW", DEFAULT_ROLE);
        engine.execute("ALTER USER u1989 SET DEFAULT_ROLE = 'Mixed'");
        assertAnswer("MIXED", DEFAULT_ROLE);
        engine.execute("ALTER USER u1989 SET DEFAULT_ROLE = \"Mixed\"");
        assertAnswer("Mixed", DEFAULT_ROLE);
    }
}
