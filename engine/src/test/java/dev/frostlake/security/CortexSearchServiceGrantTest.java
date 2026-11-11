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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A Cortex Search service is granted on as an object of its schema (documented): USAGE lets a role query it and see
 * it in SHOW and DESCRIBE, OPERATE suspends and resumes it, GRANT OWNERSHIP moves it, and SHOW GRANTS ON lists its
 * grants under the kind CORTEX_SEARCH_SERVICE by its full name. Engine only: GRANT and USE ROLE are not run on the
 * live account.
 */
public class CortexSearchServiceGrantTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS cs_reader");
        engine.execute("CREATE TABLE cs_docs (id INT, body VARCHAR)");
        engine.execute("CREATE CORTEX SEARCH SERVICE css ON body WAREHOUSE = compute_wh TARGET_LAG = '1 hour' "
            + "AS (SELECT id, body FROM cs_docs)");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        try {
            engine.execute("USE ROLE ACCOUNTADMIN");
            engine.execute("DROP ROLE IF EXISTS cs_reader");
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    /** The privilege, kind, name, grantee, grant option and grantor of each row SHOW GRANTS ON lists. */
    private List<String> grantsOn(final String kindAndName) {
        final ResultSet grants = engine.executeQuery("SHOW GRANTS ON " + kindAndName);
        final List<String> rows = new ArrayList<>();
        for (final Row row : grants.getRows()) {
            rows.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|" + row.getValue(5) + "|"
                + row.getValue(6) + "|" + row.getValue(7));
        }
        return rows;
    }

    private List<String> servicesShown() {
        final ResultSet listed = engine.executeQuery("SHOW CORTEX SEARCH SERVICES IN SCHEMA test_db.test_schema");
        final int name = listed.getColumnIndex("name");
        final List<String> names = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            names.add(String.valueOf(row.getValue(name)));
        }
        return names;
    }

    @Test
    public void usageIsGrantedListedAndRevoked() {
        engine.execute("GRANT USAGE ON CORTEX SEARCH SERVICE css TO ROLE cs_reader");
        assertEquals(List.of("OWNERSHIP|CORTEX_SEARCH_SERVICE|TEST_DB.TEST_SCHEMA.CSS|ACCOUNTADMIN|true|ACCOUNTADMIN",
            "USAGE|CORTEX_SEARCH_SERVICE|TEST_DB.TEST_SCHEMA.CSS|CS_READER|false|ACCOUNTADMIN"),
            grantsOn("CORTEX SEARCH SERVICE css"));
        engine.execute("GRANT OPERATE ON CORTEX SEARCH SERVICE test_db.test_schema.css TO ROLE cs_reader");
        engine.execute("REVOKE USAGE ON CORTEX SEARCH SERVICE css FROM ROLE cs_reader");
        assertEquals(List.of("OWNERSHIP|CORTEX_SEARCH_SERVICE|TEST_DB.TEST_SCHEMA.CSS|ACCOUNTADMIN|true|ACCOUNTADMIN",
            "OPERATE|CORTEX_SEARCH_SERVICE|TEST_DB.TEST_SCHEMA.CSS|CS_READER|false|ACCOUNTADMIN"),
            grantsOn("CORTEX SEARCH SERVICE css"));
    }

    @Test
    public void aRoleSeesTheServiceOnceItHoldsAPrivilegeOnIt() {
        engine.execute("GRANT ROLE cs_reader TO ROLE ACCOUNTADMIN");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE cs_reader");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE cs_reader");
        engine.execute("USE ROLE cs_reader");
        assertEquals(List.of(), servicesShown());
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("GRANT USAGE ON CORTEX SEARCH SERVICE css TO ROLE cs_reader");
        engine.execute("USE ROLE cs_reader");
        assertEquals(List.of("CSS"), servicesShown());
        assertEquals(1, engine.executeQuery("DESCRIBE CORTEX SEARCH SERVICE css").getRows().size());
    }

    @Test
    public void ownershipMovesTheService() {
        engine.execute("GRANT OWNERSHIP ON CORTEX SEARCH SERVICE css TO ROLE cs_reader");
        assertEquals(List.of("OWNERSHIP|CORTEX_SEARCH_SERVICE|TEST_DB.TEST_SCHEMA.CSS|CS_READER|true|CS_READER"),
            grantsOn("CORTEX SEARCH SERVICE css"));
    }

    @Test
    public void aServiceThatDoesNotExistIsRefused() {
        final String refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT USAGE ON CORTEX SEARCH SERVICE nosuch_css TO ROLE cs_reader");
            }
        }).getMessage();
        assertEquals(hinted("SQL compilation error:\nCortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH_CSS' does not "
            + "exist or not authorized."), refusal);
    }
}
