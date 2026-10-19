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
 * An external volume is a securable of its own: SHOW GRANTS ON EXTERNAL VOLUME, and ON VOLUME, lists its OWNERSHIP
 * row under the kind {@code EXTERNAL_VOLUME} (live-verified), GRANT and REVOKE take its one privilege, USAGE, and GRANT
 * OWNERSHIP moves it.
 */
public class ExternalVolumeGrantTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_OBJECTS =
        "creates an external volume and a role and grants on them, which the account this suite is checked against "
        + "is not altered by";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_OBJECTS);
        engine.execute("CREATE EXTERNAL VOLUME ice_pv STORAGE_LOCATIONS = ((NAME = 'loc1' STORAGE_PROVIDER = 'S3'"
            + " STORAGE_BASE_URL = 's3://bucket/path/' STORAGE_AWS_ROLE_ARN = 'arn:aws:iam::1:role/r'))");
        engine.execute("CREATE ROLE IF NOT EXISTS ev_r");
    }

    /** A statement's rows with each creation time masked. */
    private String granted(final String sql) {
        return answer(sql).replaceAll("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z", "<TS>");
    }

    @Test
    public void showGrantsOnAVolumeListsItsOwnership() {
        final String owned = "<TS>|OWNERSHIP|EXTERNAL_VOLUME|ICE_PV|ROLE|SYSADMIN|true|SYSADMIN|ROLE";
        assertEquals(owned, granted("SHOW GRANTS ON EXTERNAL VOLUME ice_pv"));
        assertEquals(owned, granted("SHOW GRANTS ON VOLUME ice_pv"));
        assertAnswer("REFUSED SQL compilation error: | External volume 'NOSUCH_PV' does not exist or not authorized."
            + " Your primary role <ROLE> must have at least one privilege granted on VOLUME NOSUCH_PV.",
            "SHOW GRANTS ON EXTERNAL VOLUME nosuch_pv");
    }

    @Test
    public void usageIsGrantedRevokedAndTheOwnershipMoved() {
        assertAnswer("Statement executed successfully.", "GRANT USAGE ON EXTERNAL VOLUME ice_pv TO ROLE ev_r");
        assertEquals("<TS>|USAGE|EXTERNAL_VOLUME|ICE_PV|ROLE|EV_R|false|SYSADMIN|ROLE ;; "
            + "<TS>|OWNERSHIP|EXTERNAL_VOLUME|ICE_PV|ROLE|SYSADMIN|true|SYSADMIN|ROLE",
            granted("SHOW GRANTS ON EXTERNAL VOLUME ice_pv"));
        assertAnswer("REFUSED SQL compilation error: | Invalid object type 'EXTERNAL_VOLUME' for privilege 'SELECT'.",
            "GRANT SELECT ON EXTERNAL VOLUME ice_pv TO ROLE ev_r");
        assertAnswer("Statement executed successfully. 1 objects affected.",
            "REVOKE USAGE ON EXTERNAL VOLUME ice_pv FROM ROLE ev_r");
        assertAnswer("Statement executed successfully.", "GRANT ALL ON EXTERNAL VOLUME ice_pv TO ROLE ev_r");
        engine.execute("GRANT OWNERSHIP ON EXTERNAL VOLUME ice_pv TO ROLE ev_r COPY CURRENT GRANTS");
        assertEquals("<TS>|OWNERSHIP|EXTERNAL_VOLUME|ICE_PV|ROLE|EV_R|true|EV_R|ROLE ;; "
            + "<TS>|USAGE|EXTERNAL_VOLUME|ICE_PV|ROLE|EV_R|false|EV_R|ROLE", granted("SHOW GRANTS ON VOLUME ice_pv"));
    }
}
