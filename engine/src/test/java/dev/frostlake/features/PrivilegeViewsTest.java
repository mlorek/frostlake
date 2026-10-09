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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's privilege views list the privileges held in their database. The engine answered all three
 * with no rows.
 *
 * <p>On the account TABLE_PRIVILEGES holds a row per privilege on a table, a view, a materialized view or a dynamic
 * table, the owner's OWNERSHIP included; OBJECT_PRIVILEGES holds the same under OBJECT_TYPE TABLE beside the
 * database's schemas, functions, procedures, sequences, streams and tasks and every database; USAGE_PRIVILEGES
 * holds the sequences alone, whose owner holds USAGE. A stage, a file format, a tag and a policy are in none. Every
 * cell is live-verified.
 */
public class PrivilegeViewsTest extends BaseDatabaseTest {

    private static final String ME = "IFF(grantor = CURRENT_ROLE(), 'me', grantor) || '>' || IFF(grantee ="
        + " CURRENT_ROLE(), 'me', grantee) || ' ' || granted_to || ' '";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE pv_t (a INT)");
        engine.execute("CREATE VIEW pv_v AS SELECT a FROM pv_t");
        engine.execute("CREATE SEQUENCE pv_s");
        engine.execute("CREATE STAGE pv_st");
        engine.execute("CREATE FILE FORMAT pv_ff TYPE = CSV");
        engine.execute("CREATE FUNCTION pv_f(x INT, y VARCHAR) RETURNS INT AS 'x'");
        engine.execute("CREATE PROCEDURE pv_p(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE STREAM pv_str ON TABLE pv_t");
        engine.execute("CREATE TASK pv_task SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TRANSIENT TABLE pv_tr (a INT)");
        engine.execute("CREATE ROLE IF NOT EXISTS pv_role");
        engine.execute("CREATE DATABASE ROLE pv_dbrole");
    }

    private List<String> cells(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        return out;
    }

    private static List<String> rows(final String... rows) {
        final List<String> out = new ArrayList<>();
        for (final String row : rows) {
            out.add(row);
        }
        return out;
    }

    @Test
    public void tablePrivilegesListsTheOwnerAndEveryGrant() {
        engine.execute("GRANT SELECT, INSERT ON TABLE pv_t TO ROLE pv_role");
        engine.execute("GRANT SELECT ON TABLE pv_t TO ROLE pv_role WITH GRANT OPTION");
        engine.execute("GRANT SELECT ON TABLE pv_t TO DATABASE ROLE pv_dbrole");
        assertEquals(rows(
            "me>PV_DBROLE DATABASE_ROLE TEST_DB.TEST_SCHEMA.PV_T SELECT NO NO",
            "me>PV_ROLE ROLE TEST_DB.TEST_SCHEMA.PV_T INSERT NO NO",
            "me>PV_ROLE ROLE TEST_DB.TEST_SCHEMA.PV_T SELECT YES NO",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_T OWNERSHIP YES NO",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_TR OWNERSHIP YES NO",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_V OWNERSHIP YES NO"),
            cells("SELECT " + ME + "|| table_catalog || '.' || table_schema || '.' || table_name || ' ' || privilege_type"
                + " || ' ' || is_grantable || ' ' || with_hierarchy FROM information_schema.table_privileges"
                + " WHERE created IS NOT NULL ORDER BY table_name, 1"));
        engine.execute("REVOKE SELECT ON TABLE pv_t FROM DATABASE ROLE pv_dbrole");
        assertEquals(rows("PV_ROLE INSERT", "PV_ROLE SELECT"), cells("SELECT grantee || ' ' || privilege_type"
            + " FROM information_schema.table_privileges WHERE table_name = 'PV_T' AND grantee <> CURRENT_ROLE()"
            + " ORDER BY 1"));
    }

    @Test
    public void grantAllIsARowPerPrivilege() {
        engine.execute("GRANT ALL ON TABLE pv_tr TO ROLE pv_role");
        assertEquals(rows("APPLYBUDGET", "DELETE", "DELETE ERROR TABLE", "EVOLVE SCHEMA", "INSERT", "REBUILD",
            "REFERENCES", "SELECT", "SELECT ERROR TABLE", "TRUNCATE", "UPDATE"),
            cells("SELECT privilege_type FROM information_schema.table_privileges WHERE table_name = 'PV_TR'"
                + " AND grantee = 'PV_ROLE' ORDER BY 1"));
    }

    @Test
    public void objectPrivilegesListsTheSchemasObjects() {
        engine.execute("GRANT USAGE ON PROCEDURE pv_p(INT) TO ROLE pv_role");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA test_schema TO ROLE pv_role");
        engine.execute("GRANT USAGE ON FILE FORMAT pv_ff TO ROLE pv_role");
        engine.execute("GRANT READ ON STAGE pv_st TO ROLE pv_role");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA test_schema TO ROLE pv_role");
        assertEquals(rows(
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_F(X NUMBER, Y VARCHAR) FUNCTION OWNERSHIP YES",
            "me>PV_ROLE ROLE TEST_DB.TEST_SCHEMA.PV_P(X NUMBER) PROCEDURE USAGE NO",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_P(X NUMBER) PROCEDURE OWNERSHIP YES",
            "me>me ROLE TEST_DB.-.PUBLIC SCHEMA OWNERSHIP YES",
            "me>PV_ROLE ROLE TEST_DB.-.TEST_SCHEMA SCHEMA CREATE TABLE NO",
            "me>PV_ROLE ROLE TEST_DB.-.TEST_SCHEMA SCHEMA USAGE NO",
            "me>me ROLE TEST_DB.-.TEST_SCHEMA SCHEMA OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_S SEQUENCE OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_STR STREAM OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_T TABLE OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_TR TABLE OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_V TABLE OWNERSHIP YES",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_TASK TASK OWNERSHIP YES"),
            cells("SELECT " + ME + "|| object_catalog || '.' || COALESCE(object_schema, '-') || '.' || object_name || ' '"
                + " || object_type || ' ' || privilege_type || ' ' || is_grantable"
                + " FROM information_schema.object_privileges WHERE object_type <> 'DATABASE'"
                + " ORDER BY object_type, object_name, IFF(grantee = CURRENT_ROLE(), 1, 0), privilege_type"));
    }

    @Test
    public void everyDatabaseIsListedWithoutACatalog() {
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE pv_role");
        assertEquals(rows("me>PV_ROLE ROLE null.null.TEST_DB USAGE NO", "me>me ROLE null.null.TEST_DB OWNERSHIP YES"),
            cells("SELECT " + ME + "|| COALESCE(object_catalog, 'null') || '.' || COALESCE(object_schema, 'null') || '.'"
                + " || object_name || ' ' || privilege_type || ' ' || is_grantable"
                + " FROM information_schema.object_privileges WHERE object_type = 'DATABASE' AND object_name = 'TEST_DB'"
                + " ORDER BY IFF(grantee = CURRENT_ROLE(), 1, 0)"));
    }

    @Test
    public void usagePrivilegesListsTheSequences() {
        engine.execute("GRANT USAGE ON SEQUENCE pv_s TO ROLE pv_role");
        assertEquals(rows("me>PV_ROLE ROLE TEST_DB.TEST_SCHEMA.PV_S SEQUENCE USAGE NO",
            "me>me ROLE TEST_DB.TEST_SCHEMA.PV_S SEQUENCE USAGE YES"),
            cells("SELECT " + ME + "|| object_catalog || '.' || object_schema || '.' || object_name || ' ' || object_type"
                + " || ' ' || privilege_type || ' ' || is_grantable FROM information_schema.usage_privileges"
                + " ORDER BY IFF(grantee = CURRENT_ROLE(), 1, 0)"));
    }

    @Test
    public void aNullOnlyNumberIsStoredNarrow() {
        assertEquals(rows("NUMBER(38,0)[SB1]"), cells("SELECT DISTINCT SYSTEM$TYPEOF(interval_precision)"
            + " FROM information_schema.columns WHERE table_name = 'PV_T'"));
    }
}
