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

/**
 * SHOW GRANTS OF ROLE over the built-in roles: PUBLIC, which every user and role holds, lists no one, and the
 * account's own role hierarchy - SYSADMIN and SECURITYADMIN to ACCOUNTADMIN, USERADMIN to SECURITYADMIN - names
 * no grantor (all live-verified).
 */
public class ShowGrantsOfRoleBuiltInsTest extends MeasuredCellsTestSupport {

    @Test
    public void builtInRoleGrants() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("0", "SHOW GRANTS OF ROLE PUBLIC ->> SELECT COUNT(*) FROM $1");
        assertAnswer("ROLE|ACCOUNTADMIN|",
            "SHOW GRANTS OF ROLE SYSADMIN ->> SELECT \"granted_to\", \"grantee_name\", \"granted_by\" FROM $1 WHERE "
                + "\"granted_to\" = 'ROLE'");
        assertAnswer("ROLE|ACCOUNTADMIN|",
            "SHOW GRANTS OF ROLE SECURITYADMIN ->> SELECT \"granted_to\", \"grantee_name\", \"granted_by\" FROM $1 "
                + "WHERE \"granted_to\" = 'ROLE'");
        assertAnswer("ROLE|SECURITYADMIN|",
            "SHOW GRANTS OF ROLE USERADMIN ->> SELECT \"granted_to\", \"grantee_name\", \"granted_by\" FROM $1 WHERE "
                + "\"granted_to\" = 'ROLE'");
        assertAnswer("",
            "SHOW GRANTS OF ROLE ACCOUNTADMIN ->> SELECT \"granted_to\", \"grantee_name\", \"granted_by\" FROM $1 "
                + "WHERE \"granted_to\" = 'ROLE'");
        assertAnswer("0", "SHOW GRANTS OF ROLE IDENTIFIER('PUBLIC') ->> SELECT COUNT(*) FROM $1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
