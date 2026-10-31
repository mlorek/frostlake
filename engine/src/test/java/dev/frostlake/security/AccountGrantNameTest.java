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

import dev.frostlake.LiveSnowflake;
import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * SHOW GRANTS TO ROLE names a grant on the account by the account's locator, as SHOW GRANTS ON ACCOUNT does: each of
 * SYSADMIN's rows on the account carries CURRENT_ACCOUNT() (live-verified), and so does a grant on the account a role
 * is given here.
 */
public class AccountGrantNameTest extends MeasuredCellsTestSupport {

    @Test
    public void theSystemRolesAccountGrants() {
        assertAnswer("true", "SHOW GRANTS TO ROLE SYSADMIN ->> SELECT COUNT(*) = COUNT_IF(\"name\" = "
            + "CURRENT_ACCOUNT()) FROM $1 WHERE \"granted_on\" = 'ACCOUNT'");
    }

    @Test
    public void aGrantOnTheAccountMadeHere() {
        assumeFalse(LiveSnowflake.enabled(), "the live suite grants nothing");
        engine.execute("CREATE ROLE account_name_role");
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO ROLE account_name_role");
        assertAnswer("CREATE DATABASE|ACCOUNT|true", "SHOW GRANTS TO ROLE account_name_role ->> SELECT \"privilege\", "
            + "\"granted_on\", \"name\" = CURRENT_ACCOUNT() FROM $1");
        assertAnswer("1|1", "SHOW GRANTS ON ACCOUNT ->> SELECT COUNT(*), COUNT_IF(\"name\" = CURRENT_ACCOUNT()) "
            + "FROM $1 WHERE \"grantee_name\" = 'ACCOUNT_NAME_ROLE'");
    }
}
