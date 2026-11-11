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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
 * GRANT OWNERSHIP moves a warehouse to the role it names, as it moves a schema's objects: SHOW WAREHOUSES names the
 * new owner, SHOW GRANTS lists its OWNERSHIP alone, and the role that owned it before acts on it as any other role
 * does. A join, an aggregation and a projection policy are altered by their owner alone: a rename asks OWNERSHIP and
 * every other change MODIFY, as a masking policy's do (documented). Engine only: GRANT and USE ROLE are not run on the
 * live account.
 */
public class WarehousePolicyOwnershipTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS wo_other");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE wo_other TO ROLE ACCOUNTADMIN");
        quietly("DROP WAREHOUSE IF EXISTS wo_wh");
        quietly("DROP WAREHOUSE IF EXISTS wo_wh2");
        quietly("DROP ROLE IF EXISTS wo_other");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void runs(final String sql) {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    private static String refused(final String kind, final String name, final String privilege,
                                  final String grantKind, final String full) {
        return "SQL access control error:\nInsufficient privileges to operate on " + kind + " '" + name
            + "'. Your primary role ACCOUNTADMIN must have " + privilege + " granted on " + grantKind + " " + full
            + ".";
    }

    private static String inSchema(final String name) {
        return "TEST_DB.TEST_SCHEMA." + name;
    }

    /** The privilege, grantee, grant option and grantor of each row SHOW GRANTS ON lists, one text per row. */
    private List<String> grantsOn(final String kindAndName) {
        final ResultSet grants = engine.executeQuery("SHOW GRANTS ON " + kindAndName);
        final List<String> rows = new ArrayList<>();
        for (final Row row : grants.getRows()) {
            rows.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|" + row.getValue(5) + "|"
                + row.getValue(6) + "|" + row.getValue(7));
        }
        return rows;
    }

    private String ownerShown(final String warehouse) {
        final ResultSet listed = engine.executeQuery("SHOW WAREHOUSES LIKE '" + warehouse + "'");
        final int owner = listed.getColumnIndex("owner");
        return String.valueOf(listed.getRows().get(0).getValue(owner));
    }

    @Test
    public void aWarehouseMovesToTheRoleGrantedItsOwnership() {
        engine.execute("CREATE WAREHOUSE wo_wh INITIALLY_SUSPENDED = TRUE");
        assertEquals("ACCOUNTADMIN", ownerShown("WO_WH"));
        engine.execute("GRANT OWNERSHIP ON WAREHOUSE wo_wh TO ROLE wo_other");
        assertEquals("WO_OTHER", ownerShown("WO_WH"));
        assertEquals(List.of("OWNERSHIP|WAREHOUSE|WO_WH|WO_OTHER|true|WO_OTHER"), grantsOn("WAREHOUSE wo_wh"));
        // The role that owned it before holds nothing on it now.
        assertEquals(refused("warehouse", "WO_WH", "MODIFY", "WAREHOUSE", "WO_WH"),
            refusal("ALTER WAREHOUSE wo_wh SET COMMENT = 'x'"));
        assertEquals(refused("warehouse", "WO_WH", "OPERATE", "WAREHOUSE", "WO_WH"),
            refusal("ALTER WAREHOUSE wo_wh RESUME"));
        final String ownership = refused("warehouse", "WO_WH", "OWNERSHIP", "WAREHOUSE", "WO_WH");
        assertEquals(ownership, refusal("ALTER WAREHOUSE wo_wh RENAME TO wo_wh2"));
        assertEquals(ownership, refusal("DROP WAREHOUSE wo_wh"));
        // A grant on it is the new owner's to give, and is recorded in its name.
        engine.execute("GRANT ROLE wo_other TO ROLE ACCOUNTADMIN");
        engine.execute("USE ROLE wo_other");
        runs("GRANT USAGE ON WAREHOUSE wo_wh TO ROLE PUBLIC");
        assertEquals(List.of("USAGE|WAREHOUSE|WO_WH|PUBLIC|false|WO_OTHER",
            "OWNERSHIP|WAREHOUSE|WO_WH|WO_OTHER|true|WO_OTHER"), grantsOn("WAREHOUSE wo_wh"));
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES ALL");
        runs("ALTER WAREHOUSE wo_wh SET COMMENT = 'x'");
        runs("ALTER WAREHOUSE wo_wh RENAME TO wo_wh2");
        assertEquals("WO_OTHER", ownerShown("WO_WH2"));
        runs("DROP WAREHOUSE wo_wh2");
    }

    @Test
    public void aJoinAnAggregationAndAProjectionPolicyAreAlteredByTheirOwnerAlone() {
        engine.execute("CREATE JOIN POLICY wo_jp AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE AGGREGATION POLICY wo_ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE PROJECTION POLICY wo_pp AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        for (final String policy : new String[] {"JOIN POLICY wo_jp", "AGGREGATION POLICY wo_ap",
            "PROJECTION POLICY wo_pp"}) {
            engine.execute("GRANT OWNERSHIP ON " + policy + " TO ROLE wo_other");
        }
        assertEquals(refused("join_policy", "WO_JP", "MODIFY", "POLICY", inSchema("WO_JP")),
            refusal("ALTER JOIN POLICY wo_jp SET COMMENT = 'x'"));
        assertEquals(refused("join_policy", "WO_JP", "OWNERSHIP", "POLICY", inSchema("WO_JP")),
            refusal("ALTER JOIN POLICY wo_jp RENAME TO wo_jp2"));
        assertEquals(refused("aggregation_policy", "WO_AP", "MODIFY", "POLICY", inSchema("WO_AP")),
            refusal("ALTER AGGREGATION POLICY wo_ap SET BODY -> NO_AGGREGATION_CONSTRAINT()"));
        assertEquals(refused("aggregation_policy", "WO_AP", "OWNERSHIP", "POLICY", inSchema("WO_AP")),
            refusal("ALTER AGGREGATION POLICY wo_ap RENAME TO wo_ap2"));
        assertEquals(refused("projection_policy", "WO_PP", "MODIFY", "POLICY", inSchema("WO_PP")),
            refusal("ALTER PROJECTION POLICY wo_pp UNSET COMMENT"));
        assertEquals(refused("projection_policy", "WO_PP", "OWNERSHIP", "POLICY", inSchema("WO_PP")),
            refusal("ALTER PROJECTION POLICY wo_pp RENAME TO wo_pp2"));
        // The owner's role granted back is what lets the session change them.
        engine.execute("GRANT ROLE wo_other TO ROLE ACCOUNTADMIN");
        engine.execute("USE SECONDARY ROLES ALL");
        runs("ALTER JOIN POLICY wo_jp SET COMMENT = 'x'");
        runs("ALTER AGGREGATION POLICY wo_ap RENAME TO wo_ap2");
        runs("ALTER PROJECTION POLICY wo_pp UNSET COMMENT");
    }
}
