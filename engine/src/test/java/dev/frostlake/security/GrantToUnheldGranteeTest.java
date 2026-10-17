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
 * GRANT reads a share and an application role as grantees, and ROLE written alone as the name of a role: a grant to
 * one that is not there is refused for the grantee, not as a syntax error (live-verified; the account's grant
 * statements are not run there, so this is the engine's own check of that measurement).
 */
public class GrantToUnheldGranteeTest extends MeasuredCellsTestSupport {

    @Test
    public void aGrantToAShareOrAnApplicationRoleThatIsNotThere() {
        assumeFalse(isLiveSnowflake(), "grants are not made on the account");
        answer("CREATE OR REPLACE TABLE t1434 (a INT)");
        assertCells(new String[][] {
            {"GRANT SELECT ON TABLE t1434 TO SHARE nosuchshare", "REFUSED SQL compilation error: | Share "
                + "'\"<ACCOUNT>.NOSUCHSHARE\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on SHARE \"<ACCOUNT>.NOSUCHSHARE\"."},
            {"GRANT SELECT ON TABLE t1434 TO APPLICATION ROLE nosuchapprole", "REFUSED SQL compilation error: | "
                + "Application role 'TEST_DB.NOSUCHAPPROLE' does not exist or not authorized. Your primary role <ROLE> "
                + "must have at least one privilege granted on APPLICATION ROLE TEST_DB.NOSUCHAPPROLE."},
            {"GRANT SELECT ON TABLE t1434 TO ROLE", "REFUSED SQL compilation error: | Role 'ROLE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE ROLE."},
        });
    }

    @Test
    public void theObjectIsJudgedFirstAndARoleCalledRoleTakesTheGrant() {
        assumeFalse(isLiveSnowflake(), "roles are not created on the account");
        answer("CREATE OR REPLACE TABLE t1434b (a INT)");
        answer("CREATE ROLE IF NOT EXISTS \"ROLE\"");
        try {
            assertCells(new String[][] {
                {"GRANT SELECT ON TABLE nosuch1434 TO SHARE nosuchshare", "REFUSED SQL compilation error: | Table "
                    + "'NOSUCH1434' does not exist or not authorized. Your primary role <ROLE> must have at least one "
                    + "privilege granted on TABLE NOSUCH1434."},
                {"GRANT SELECT ON TABLE t1434b TO ROLE", "Statement executed successfully."},
            });
            answer("SHOW GRANTS ON TABLE t1434b");
            assertAnswer("SELECT|ROLE", "SELECT \"privilege\", \"grantee_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
                + " WHERE \"privilege\" = 'SELECT'");
        } finally {
            answer("DROP ROLE IF EXISTS \"ROLE\"");
        }
    }
}
