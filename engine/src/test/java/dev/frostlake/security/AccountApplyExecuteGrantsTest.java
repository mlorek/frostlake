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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The account's APPLY privileges for every policy kind, and EXECUTE ALERT, EXECUTE MANAGED TASK and EXECUTE MANAGED
 * ALERT, are granted and revoked ON ACCOUNT like the others (documented, GRANT privilege): they were read as privileges
 * on an object named ACCOUNT and refused as a missing table. Engine only: the live account allows no CREATE ROLE or
 * GRANT.
 */
public class AccountApplyExecuteGrantsTest extends BaseDatabaseTest {

    private static final String[] PRIVILEGES = {"APPLY AGGREGATION POLICY", "APPLY AUTHENTICATION POLICY",
        "APPLY JOIN POLICY", "APPLY PACKAGES POLICY", "APPLY PASSWORD POLICY", "APPLY PROJECTION POLICY",
        "EXECUTE ALERT", "EXECUTE MANAGED ALERT", "EXECUTE MANAGED TASK"};

    private List<String> accountGrants() {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO ROLE aae_r");
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < rs.getRows().size(); i++) {
            if ("ACCOUNT".equals(cell(rs, rs.getRows().get(i), "granted_on"))) {
                out.add(cell(rs, rs.getRows().get(i), "privilege"));
            }
        }
        Collections.sort(out);
        return out;
    }

    @Test
    public void eachIsGrantedAndRevokedOnTheAccount() {
        assumeFalse(isLiveSnowflake(), "CREATE ROLE and GRANT are not run on the live account");
        engine.execute("CREATE ROLE aae_r");
        for (final String privilege : PRIVILEGES) {
            engine.execute("GRANT " + privilege + " ON ACCOUNT TO ROLE aae_r");
        }
        final List<String> expected = new ArrayList<>();
        Collections.addAll(expected, PRIVILEGES);
        Collections.sort(expected);
        assertEquals(expected, accountGrants());
        engine.execute("REVOKE EXECUTE ALERT, APPLY JOIN POLICY ON ACCOUNT FROM ROLE aae_r");
        expected.remove("EXECUTE ALERT");
        expected.remove("APPLY JOIN POLICY");
        assertEquals(expected, accountGrants());
    }
}
