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
 * The bare SHOW GRANTS lists a row for each role granted to the user the session runs for (live-verified): USAGE on
 * the ROLE, granted to the USER, with no grant option, and the roles the account's first user held from the start name
 * no grantor.
 */
public class ShowGrantsSessionUserTest extends MeasuredCellsTestSupport {

    @Test
    public void theSessionUsersRolesAreListed() {
        assertCells(new String[][] {
            {"SHOW GRANTS ->> SELECT COUNT_IF(\"name\" = 'PUBLIC') FROM $1", "0"},
            {"SHOW GRANTS ->> SELECT COUNT_IF(\"granted_on\" = 'ROLE' AND \"role\" IS NULL), "
                + "COUNT_IF(\"granted_on\" <> 'ROLE' AND \"role\" IS NOT NULL) FROM $1",
                "0|0"},
            // Every role listed is one the session may use: the count is the account's own, so it is compared.
            {"SHOW GRANTS ->> SELECT COUNT_IF(\"name\" IN (SELECT VALUE::VARCHAR FROM TABLE(FLATTEN(PARSE_JSON("
                + "CURRENT_AVAILABLE_ROLES()))))) = COUNT(*) FROM $1 WHERE \"granted_on\" = 'ROLE'",
                "true"},
            {"SHOW GRANTS ->> SELECT COUNT(*) > 0, COUNT(DISTINCT \"grantee_name\"), MAX(\"grantee_name\" = "
                + "CURRENT_USER()), MIN(\"privilege\"), MIN(\"granted_to\"), MIN(\"grant_option\"), "
                + "MIN(\"granted_by\" = ''), MIN(\"name\" = \"role\"), COUNT_IF(\"expires_on\" IS NULL) = COUNT(*), "
                + "COUNT_IF(\"created_on\" IS NULL) FROM $1 WHERE \"granted_on\" = 'ROLE'",
                "true|1|true|USAGE|USER|false|true|true|true|0"},
            {"SHOW GRANTS ->> SELECT COUNT(*) FROM $1 WHERE \"granted_on\" = 'ROLE' AND \"name\" = 'PUBLIC'", "0"},
            {"SHOW GRANTS ->> SELECT COUNT(*) > 0, COUNT(DISTINCT \"granted_to\") FROM $1", "true|1"},
            {"SHOW GRANTS ->> SELECT * FROM $1 WHERE \"granted_on\" <> 'ROLE' LIMIT 0", ""},
        });
    }
}
