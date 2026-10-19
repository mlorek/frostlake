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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A privilege granted to a user WITH GRANT OPTION carries the option, as one granted to a role does: SHOW GRANTS TO
 * USER says so (live-verified), and REVOKE GRANT OPTION FOR takes the option back and leaves the privilege.
 */
public class UserGrantOptionTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates a user and grants to it, which the account this suite is checked against is not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
        engine.execute("CREATE USER IF NOT EXISTS ug_u");
        engine.execute("CREATE TABLE ug_t (a INT)");
    }

    /** The privilege, object kind, object, grantee kind and grant option of each row SHOW GRANTS TO USER lists. */
    private String granted() {
        final StringBuilder out = new StringBuilder();
        for (final String row : answer("SHOW GRANTS TO USER ug_u").split(" ;; ")) {
            final String[] cells = row.split("\\|", -1);
            out.append(out.length() > 0 ? " ;; " : "").append(cells[1]).append('|').append(cells[2]).append('|')
                .append(cells[3]).append('|').append(cells[5]).append('|').append(cells[7]);
        }
        return out.toString();
    }

    @Test
    public void theGrantOptionIsKeptAndRevokedAlone() {
        engine.execute("GRANT SELECT ON TABLE ug_t TO USER ug_u WITH GRANT OPTION");
        assertEquals("SELECT|TABLE|TEST_DB.TEST_SCHEMA.UG_T|USER|true", granted());
        engine.execute("REVOKE GRANT OPTION FOR SELECT ON TABLE ug_t FROM USER ug_u");
        assertEquals("SELECT|TABLE|TEST_DB.TEST_SCHEMA.UG_T|USER|false", granted());
        engine.execute("GRANT INSERT ON TABLE ug_t TO USER ug_u");
        engine.execute("REVOKE SELECT ON TABLE ug_t FROM USER ug_u");
        assertEquals("INSERT|TABLE|TEST_DB.TEST_SCHEMA.UG_T|USER|false", granted());
    }
}
