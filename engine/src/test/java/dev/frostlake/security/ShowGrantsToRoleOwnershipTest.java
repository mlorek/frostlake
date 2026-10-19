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
 * SHOW GRANTS TO ROLE lists an OWNERSHIP row for every object the role owns, beside the privileges granted to it:
 * the object's kind and name, the grant option, and the role itself as the grantor (live-verified for a table and a
 * function the role was given). The account's role and grant statements are not run there, so this is the engine's
 * own check of that measurement.
 */
public class ShowGrantsToRoleOwnershipTest extends MeasuredCellsTestSupport {

    private static final String LISTED = "SELECT \"privilege\", \"granted_on\", \"name\", \"grant_option\", "
        + "\"granted_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    @Test
    public void aRoleListsWhatItOwns() {
        assumeFalse(isLiveSnowflake(), "roles and grants are not made on the account");
        answer("CREATE ROLE r1432");
        try {
            answer("CREATE TABLE own1432 (a INT)");
            answer("CREATE FUNCTION ownf1432(x INT) RETURNS INT AS '1'");
            answer("GRANT SELECT ON TABLE own1432 TO ROLE r1432");
            answer("GRANT OWNERSHIP ON TABLE own1432 TO ROLE r1432 COPY CURRENT GRANTS");
            answer("GRANT OWNERSHIP ON FUNCTION ownf1432(INT) TO ROLE r1432");
            answer("SHOW GRANTS TO ROLE r1432");
            assertAnswer("OWNERSHIP|FUNCTION|TEST_DB.TEST_SCHEMA.OWNF1432(NUMBER)|true|R1432 ;; "
                + "OWNERSHIP|TABLE|TEST_DB.TEST_SCHEMA.OWN1432|true|R1432 ;; "
                + "SELECT|TABLE|TEST_DB.TEST_SCHEMA.OWN1432|false|R1432", LISTED);
        } finally {
            answer("DROP ROLE IF EXISTS r1432");
        }
    }

    @Test
    public void aRoleListsWhatItCreated() {
        assumeFalse(isLiveSnowflake(), "roles and grants are not made on the account");
        answer("CREATE ROLE r1432c");
        try {
            answer("GRANT OWNERSHIP ON SCHEMA test_db.test_schema TO ROLE r1432c COPY CURRENT GRANTS");
            answer("SHOW GRANTS TO ROLE r1432c");
            assertAnswer("OWNERSHIP|SCHEMA|TEST_DB.TEST_SCHEMA|true|R1432C", LISTED);
        } finally {
            answer("DROP ROLE IF EXISTS r1432c");
        }
    }
}
