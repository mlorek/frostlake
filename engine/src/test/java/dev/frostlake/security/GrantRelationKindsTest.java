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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * GRANT and REVOKE on a dynamic table and on an event table, named by their own kinds, and the kind a relation's
 * grant is recorded under: a grant ON TABLE or ON VIEW over a materialized view or a dynamic table is that
 * relation's, listed by SHOW GRANTS ON under its kind and by the privilege views, and an event table's is listed
 * under EVENT_TABLE; GRANT ALL on a dynamic table is its three privileges (documented). INFORMATION_SCHEMA's
 * privilege views list only the objects the current role reaches (documented). Engine only: GRANT and USE ROLE are
 * not run on the live account.
 */
public class GrantRelationKindsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        engine.execute("CREATE TABLE gr_base (a INT)");
        engine.execute("CREATE MATERIALIZED VIEW gr_mv AS SELECT a FROM gr_base");
        final String warehouse = String.valueOf(engine.executeQuery("SELECT COALESCE(CURRENT_WAREHOUSE(),"
            + " 'COMPUTE_WH')").getRows().get(0).getValue(0));
        engine.execute("CREATE DYNAMIC TABLE gr_dt TARGET_LAG = '1 day' WAREHOUSE = " + warehouse
            + " AS SELECT a FROM gr_base");
        engine.execute("CREATE EVENT TABLE gr_et");
        engine.execute("CREATE ROLE IF NOT EXISTS gr_role");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** SHOW GRANTS ON's rows held by GR_ROLE, each as {@code privilege|granted_on|name}, in order. */
    private List<String> granted(final String target) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SHOW GRANTS ON " + target).getRows()) {
            if ("GR_ROLE".equals(row.getValue(5))) {
                out.add(row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3));
            }
        }
        out.sort(null);
        return out;
    }

    private List<String> cells(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    @Test
    public void aDynamicTableIsGrantedOnUnderItsOwnKind() {
        engine.execute("GRANT SELECT ON DYNAMIC TABLE gr_dt TO ROLE gr_role");
        assertEquals("[SELECT|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.GR_DT]", granted("DYNAMIC TABLE gr_dt").toString());
        engine.execute("REVOKE SELECT ON DYNAMIC TABLE gr_dt FROM ROLE gr_role");
        assertEquals("[]", granted("DYNAMIC TABLE gr_dt").toString());
        engine.execute("GRANT ALL ON DYNAMIC TABLE gr_dt TO ROLE gr_role");
        assertEquals("[MONITOR|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.GR_DT, OPERATE|DYNAMIC_TABLE|"
            + "TEST_DB.TEST_SCHEMA.GR_DT, SELECT|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.GR_DT]",
            granted("DYNAMIC TABLE gr_dt").toString());
        assertTrue(refusal("GRANT SELECT ON DYNAMIC TABLE nosuch_dt TO ROLE gr_role")
            .contains("Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH_DT' does not exist or not authorized."));
        assertTrue(refusal("GRANT SELECT ON DYNAMIC TABLE gr_mv TO ROLE gr_role")
            .contains("Dynamic table 'TEST_DB.TEST_SCHEMA.GR_MV' does not exist or not authorized."));
    }

    @Test
    public void aGrantOnTableOverAMaterializedViewOrADynamicTableIsTheirs() {
        engine.execute("GRANT SELECT ON TABLE gr_mv TO ROLE gr_role");
        assertEquals("[SELECT|MATERIALIZED_VIEW|TEST_DB.TEST_SCHEMA.GR_MV]",
            granted("MATERIALIZED VIEW gr_mv").toString());
        assertEquals("[SELECT]", cells("SELECT privilege_type FROM information_schema.table_privileges"
            + " WHERE table_name = 'GR_MV' AND grantee = 'GR_ROLE'").toString());
        engine.execute("REVOKE SELECT ON TABLE gr_mv FROM ROLE gr_role");
        assertEquals("[]", granted("MATERIALIZED VIEW gr_mv").toString());
        engine.execute("GRANT SELECT ON VIEW gr_dt TO ROLE gr_role");
        assertEquals("[SELECT|DYNAMIC_TABLE|TEST_DB.TEST_SCHEMA.GR_DT]", granted("DYNAMIC TABLE gr_dt").toString());
        assertEquals("[SELECT]", cells("SELECT privilege_type FROM information_schema.table_privileges"
            + " WHERE table_name = 'GR_DT' AND grantee = 'GR_ROLE'").toString());
    }

    @Test
    public void anEventTableIsGrantedOnUnderItsOwnKind() {
        engine.execute("GRANT SELECT ON EVENT TABLE gr_et TO ROLE gr_role");
        assertEquals("[SELECT|EVENT_TABLE|TEST_DB.TEST_SCHEMA.GR_ET]", granted("EVENT TABLE gr_et").toString());
        engine.execute("REVOKE SELECT ON EVENT TABLE gr_et FROM ROLE gr_role");
        assertEquals("[]", granted("EVENT TABLE gr_et").toString());
        assertTrue(refusal("GRANT SELECT ON EVENT TABLE gr_base TO ROLE gr_role")
            .contains("Event table 'TEST_DB.TEST_SCHEMA.GR_BASE' does not exist or not authorized."));
    }

    @Test
    public void thePrivilegeViewsListWhatTheCurrentRoleReaches() {
        final String opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE TABLE gr_hidden (a INT)");
        engine.execute("CREATE SEQUENCE gr_seen_seq");
        engine.execute("CREATE SEQUENCE gr_hidden_seq");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE gr_role");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE gr_role");
        engine.execute("GRANT SELECT ON TABLE gr_base TO ROLE gr_role");
        engine.execute("GRANT USAGE ON SEQUENCE gr_seen_seq TO ROLE gr_role");
        engine.execute("GRANT ROLE gr_role TO ROLE " + opener);
        assertTrue(cells("SELECT DISTINCT table_name FROM information_schema.table_privileges")
            .contains("GR_HIDDEN"));
        engine.execute("USE ROLE gr_role");
        try {
            assertEquals("[GR_BASE]", cells("SELECT DISTINCT table_name FROM information_schema.table_privileges"
                + " ORDER BY 1").toString());
            assertEquals("[GR_SEEN_SEQ]", cells("SELECT DISTINCT object_name FROM information_schema.usage_privileges"
                + " ORDER BY 1").toString());
            final List<String> objects = cells("SELECT DISTINCT object_name FROM information_schema.object_privileges"
                + " WHERE object_schema = 'TEST_SCHEMA' ORDER BY 1");
            assertTrue(objects.contains("GR_BASE") && objects.contains("GR_SEEN_SEQ"), objects.toString());
            assertTrue(!objects.contains("GR_HIDDEN") && !objects.contains("GR_HIDDEN_SEQ"), objects.toString());
        } finally {
            engine.execute("USE ROLE " + opener);
        }
    }
}
