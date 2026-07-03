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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShowCommandsExtTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    // ── SHOW PARAMETERS ───────────────────────────────────────────────────────

    @Test
    public void testShowParameters() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0, "SHOW PARAMETERS should return rows");
        assertNotNull(rs.getColumnIndex("key"));
        assertNotNull(rs.getColumnIndex("value"));
        assertNotNull(rs.getColumnIndex("default"));
        assertNotNull(rs.getColumnIndex("level"));
    }

    @Test
    public void testShowParametersContainsTimezone() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        boolean found = false;
        int keyIdx = rs.getColumnIndex("key");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("TIMEZONE".equalsIgnoreCase(rs.getRows().get(i).getValue(keyIdx).toString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "SHOW PARAMETERS should include TIMEZONE");
    }

    @Test
    public void testShowParametersContainsAutocommit() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        boolean found = false;
        int keyIdx = rs.getColumnIndex("key");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("AUTOCOMMIT".equalsIgnoreCase(rs.getRows().get(i).getValue(keyIdx).toString())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "SHOW PARAMETERS should include AUTOCOMMIT");
    }

    @Test
    public void testShowParametersLikeFilter() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'TIMEZONE%'");
        assertNotNull(rs);
        int keyIdx = rs.getColumnIndex("key");
        for (int i = 0; i < rs.getRowCount(); i++) {
            String key = rs.getRows().get(i).getValue(keyIdx).toString().toUpperCase();
            assertTrue(key.startsWith("TIMEZONE"),
                "LIKE filter should only return TIMEZONE* params, got: " + key);
        }
    }

    @Test
    public void testShowParametersInSession() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN SESSION");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 0);
    }

    @Test
    public void testShowParametersInAccount() {
        ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN ACCOUNT");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0);
    }

    // ── SHOW SESSIONS ─────────────────────────────────────────────────────────

    @Test
    public void testShowSessions() {
        ResultSet rs = engine.executeQuery("SHOW SESSIONS");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount(), "Should show current session");
        assertNotNull(rs.getColumnIndex("session_id"));
        assertNotNull(rs.getColumnIndex("user_name"));
        assertNotNull(rs.getColumnIndex("created_on"));
    }

    @Test
    public void testShowSessionsHasUserName() {
        ResultSet rs = engine.executeQuery("SHOW SESSIONS");
        int userIdx = rs.getColumnIndex("user_name");
        assertNotNull(rs.getRows().get(0).getValue(userIdx));
    }

    @Test
    public void testShowSessionsHasSessionId() {
        ResultSet rs = engine.executeQuery("SHOW SESSIONS");
        int sidIdx = rs.getColumnIndex("session_id");
        assertNotNull(rs.getRows().get(0).getValue(sidIdx));
    }

    @Test
    public void testShowSessionsHasDatabaseAndSchema() {
        ResultSet rs = engine.executeQuery("SHOW SESSIONS");
        int dbIdx = rs.getColumnIndex("database_name");
        int scIdx = rs.getColumnIndex("schema_name");
        assertEquals("TEST_DB", rs.getRows().get(0).getValue(dbIdx).toString().toUpperCase());
        assertEquals("PUBLIC", rs.getRows().get(0).getValue(scIdx).toString().toUpperCase());
    }

    // ── SHOW OBJECTS ──────────────────────────────────────────────────────────

    @Test
    public void testShowObjects() {
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE VIEW v1 AS SELECT * FROM t1");
        engine.execute("CREATE FUNCTION f1(x INTEGER) RETURNS INTEGER AS $$ SELECT x $$");

        ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 3);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("kind"));
    }

    @Test
    public void testShowObjectsContainsTable() {
        engine.execute("CREATE TABLE my_table (id INTEGER)");
        ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        int nameIdx = rs.getColumnIndex("name");
        int kindIdx = rs.getColumnIndex("kind");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_TABLE".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())
                && rs.getRows().get(i).getValue(kindIdx).toString().contains("TABLE")) {
                found = true;
            }
        }
        assertTrue(found, "SHOW OBJECTS should include MY_TABLE");
    }

    @Test
    public void testShowObjectsContainsView() {
        engine.execute("CREATE TABLE base (id INTEGER)");
        engine.execute("CREATE VIEW my_view AS SELECT * FROM base");
        ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        int nameIdx = rs.getColumnIndex("name");
        int kindIdx = rs.getColumnIndex("kind");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_VIEW".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())
                && "VIEW".equalsIgnoreCase(rs.getRows().get(i).getValue(kindIdx).toString())) {
                found = true;
            }
        }
        assertTrue(found, "SHOW OBJECTS should include MY_VIEW");
    }

    @Test
    public void testShowObjectsContainsProcedure() {
        engine.execute("CREATE PROCEDURE my_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x'; END $$");
        ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        int nameIdx = rs.getColumnIndex("name");
        int kindIdx = rs.getColumnIndex("kind");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())
                && "PROCEDURE".equalsIgnoreCase(rs.getRows().get(i).getValue(kindIdx).toString())) {
                found = true;
            }
        }
        assertTrue(found, "SHOW OBJECTS should include MY_PROC");
    }

    @Test
    public void testShowObjectsInSchema() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("USE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_table (id INTEGER)");
        engine.execute("USE SCHEMA public");

        ResultSet rs = engine.executeQuery("SHOW OBJECTS IN SCHEMA other_schema");
        assertNotNull(rs);
        int nameIdx = rs.getColumnIndex("name");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("OTHER_TABLE".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())) {
                found = true;
            }
        }
        assertTrue(found, "SHOW OBJECTS IN SCHEMA should show objects in that schema");
    }

    // ── SHOW ORGANIZATION ACCOUNTS ────────────────────────────────────────────

    // ── SHOW LOCKS ────────────────────────────────────────────────────────────

    @Test
    public void testShowLocksEmpty() {
        ResultSet rs = engine.executeQuery("SHOW LOCKS");
        assertNotNull(rs);
        assertEquals(0, rs.getRowCount(), "No active transactions → no locks");
        assertNotNull(rs.getColumnIndex("transaction"));
        assertNotNull(rs.getColumnIndex("status"));
    }

    @Test
    public void testShowLocksWithActiveTransaction() {
        engine.execute("CREATE TABLE lock_test (id INTEGER)");
        engine.setAutoCommit(false);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO lock_test VALUES (1)");
        ResultSet rs = engine.executeQuery("SHOW LOCKS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Active transaction should produce a lock row");
        engine.execute("COMMIT");
        engine.setAutoCommit(true);
    }

    // ── SHOW TRANSACTIONS ─────────────────────────────────────────────────────

    @Test
    public void testShowTransactionsEmpty() {
        ResultSet rs = engine.executeQuery("SHOW TRANSACTIONS");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("id"));
        assertNotNull(rs.getColumnIndex("status"));
    }

    @Test
    public void testShowTransactionsActive() {
        engine.execute("CREATE TABLE txn_test (id INTEGER)");
        engine.setAutoCommit(false);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO txn_test VALUES (1)");
        ResultSet rs = engine.executeQuery("SHOW TRANSACTIONS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1);
        engine.execute("ROLLBACK");
        engine.setAutoCommit(true);
    }

    // ── SHOW VARIABLES ────────────────────────────────────────────────────────

    @Test
    public void testShowVariablesEmpty() {
        ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("value"));
    }

    // ── SHOW ACCOUNTS / SHOW ORGANIZATION ACCOUNTS ───────────────────────────

    @Test
    public void testShowAccounts() {
        ResultSet rs = engine.executeQuery("SHOW ACCOUNTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Should return at least one account");
        int orgIdx = rs.getColumnIndex("organization_name");
        int accIdx = rs.getColumnIndex("account_name");
        int editionIdx = rs.getColumnIndex("edition");
        int regionIdx = rs.getColumnIndex("snowflake_region");
        assertNotNull(rs.getRows().get(0).getValue(orgIdx));
        assertNotNull(rs.getRows().get(0).getValue(accIdx));
        assertNotNull(rs.getRows().get(0).getValue(editionIdx));
        assertNotNull(rs.getRows().get(0).getValue(regionIdx));
    }

    @Test
    public void testShowOrganizationAccounts() {
        ResultSet rs = engine.executeQuery("SHOW ORGANIZATION ACCOUNTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Should return at least one account");
        int orgIdx = rs.getColumnIndex("organization_name");
        int accIdx = rs.getColumnIndex("account_name");
        int editionIdx = rs.getColumnIndex("edition");
        int isOrgAdminIdx = rs.getColumnIndex("is_org_admin");
        assertNotNull(rs.getRows().get(0).getValue(orgIdx), "organization_name must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(accIdx), "account_name must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(editionIdx), "edition must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(isOrgAdminIdx), "is_org_admin must be non-null");
    }
}
