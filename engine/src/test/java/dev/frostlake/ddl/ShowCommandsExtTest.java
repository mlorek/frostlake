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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShowCommandsExtTest extends BaseDatabaseTest {

    /** SHOW ORGANIZATION ACCOUNTS returns nothing without the ORGADMIN role, which the test account lacks. */
    private static final String NEEDS_ORGADMIN =
        "SHOW ORGANIZATION ACCOUNTS needs the ORGADMIN role to return any row";

    // ── SHOW PARAMETERS ───────────────────────────────────────────────────────

    @Test
    public void testShowParameters() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0, "SHOW PARAMETERS should return rows");
        assertNotNull(rs.getColumnIndex("key"));
        assertNotNull(rs.getColumnIndex("value"));
        assertNotNull(rs.getColumnIndex("default"));
        assertNotNull(rs.getColumnIndex("level"));
    }

    @Test
    public void testShowParametersContainsTimezone() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        boolean found = false;
        final int keyIdx = rs.getColumnIndex("key");
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
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS");
        boolean found = false;
        final int keyIdx = rs.getColumnIndex("key");
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
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE 'TIMEZONE%'");
        assertNotNull(rs);
        final int keyIdx = rs.getColumnIndex("key");
        for (int i = 0; i < rs.getRowCount(); i++) {
            final String key = rs.getRows().get(i).getValue(keyIdx).toString().toUpperCase();
            assertTrue(key.startsWith("TIMEZONE"),
                "LIKE filter should only return TIMEZONE* params, got: " + key);
        }
    }

    @Test
    public void testShowParametersInSession() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN SESSION");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 0);
    }

    @Test
    public void testShowParametersInAccount() {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS IN ACCOUNT");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0);
    }

    // ── SHOW SESSIONS ─────────────────────────────────────────────────────────

    /**
     * There is no SHOW SESSIONS on Snowflake. Measured in every spelling — plain, TERSE, IN ACCOUNT and
     * LIKE all answer {@code Object type or Class 'SESSIONS' does not exist or not authorized}, and
     * {@code FOR USER} is a syntax error — while SHOW TRANSACTIONS and SHOW LOCKS beside it both work,
     * so this is SESSIONS specifically and not a whole family being absent. An account exposes its
     * sessions through the {@code SNOWFLAKE.ACCOUNT_USAGE.SESSIONS} view instead.
     *
     * <p>Frostlake used to answer one. Accepting a statement the account rejects is a fidelity bug in
     * its own right, so the command is gone and the parser refuses it.
     */
    @Test
    public void showSessionsIsNotAStatement() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW SESSIONS");
            }
        });
    }

    /** SESSIONS survives as an ordinary identifier, which it was before and still is. */
    @Test
    public void sessionsIsStillAUsableName() {
        engine.execute("CREATE TABLE sessions (id INTEGER)");
        engine.execute("INSERT INTO sessions VALUES (1)");
        assertEquals(1, engine.executeQuery("SELECT id FROM sessions").getRowCount());
    }

    // ── SHOW OBJECTS ──────────────────────────────────────────────────────────

    @Test
    public void testShowObjects() {
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("CREATE VIEW v1 AS SELECT * FROM t1");

        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 2);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("kind"));
    }

    @Test
    public void testShowObjectsContainsTable() {
        engine.execute("CREATE TABLE my_table (id INTEGER)");
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        final int nameIdx = rs.getColumnIndex("name");
        final int kindIdx = rs.getColumnIndex("kind");
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
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        final int nameIdx = rs.getColumnIndex("name");
        final int kindIdx = rs.getColumnIndex("kind");
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
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        final int nameIdx = rs.getColumnIndex("name");
        final int kindIdx = rs.getColumnIndex("kind");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("MY_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())
                && "PROCEDURE".equalsIgnoreCase(rs.getRows().get(i).getValue(kindIdx).toString())) {
                found = true;
            }
        }
        assertFalse(found, "SHOW OBJECTS lists tables and views; SHOW PROCEDURES lists procedures");
    }

    @Test
    public void testShowObjectsInSchema() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("USE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_table (id INTEGER)");
        engine.execute("USE SCHEMA public");

        final ResultSet rs = engine.executeQuery("SHOW OBJECTS IN SCHEMA other_schema");
        assertNotNull(rs);
        final int nameIdx = rs.getColumnIndex("name");
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
        // Live's exact column set, in order; an idle session holds nothing.
        final ResultSet rs = engine.executeQuery("SHOW LOCKS");
        assertNotNull(rs);
        assertEquals(0, rs.getRowCount(), "No active transactions → no locks");
        final String[] expected = {"resource", "type", "transaction", "transaction_started_on",
            "status", "acquired_on", "query_id"};
        assertEquals(expected.length, rs.getColumns().size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], rs.getColumns().get(i).getName());
        }
    }

    @Test
    public void testShowLocksWithActiveTransaction() {
        // Live-verified: partition-rewriting DML — UPDATE, DELETE (even matching no rows), MERGE —
        // holds one PARTITIONS lock per (transaction, table), however many statements rewrite the
        // table; an append-only INSERT holds NO lock; COMMIT releases them.
        engine.execute("CREATE TABLE lock_test (id INTEGER)");
        engine.execute("CREATE TABLE lock_test2 (id INTEGER)");
        engine.execute("INSERT INTO lock_test2 VALUES (0)");
        engine.setAutoCommit(false);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO lock_test VALUES (1)");
        assertEquals(0, engine.executeQuery("SHOW LOCKS").getRowCount(),
            "an append-only INSERT holds no lock");

        engine.execute("UPDATE lock_test2 SET id = 1");
        final ResultSet rs = engine.executeQuery("SHOW LOCKS");
        assertEquals(1, rs.getRowCount(), "an UPDATE holds a lock on its table");
        assertEquals("TEST_DB.TEST_SCHEMA.LOCK_TEST2",
            rs.getRows().get(0).getValue(rs.getColumnIndex("resource")));
        assertEquals("PARTITIONS", rs.getRows().get(0).getValue(rs.getColumnIndex("type")));
        assertEquals("HOLDING", rs.getRows().get(0).getValue(rs.getColumnIndex("status")));
        assertNotNull(rs.getRows().get(0).getValue(rs.getColumnIndex("query_id")));
        assertNotNull(rs.getRows().get(0).getValue(rs.getColumnIndex("acquired_on")));

        // A second rewrite of the SAME table stays one row; a DELETE on another table adds one.
        engine.execute("UPDATE lock_test2 SET id = 9");
        assertEquals(1, engine.executeQuery("SHOW LOCKS").getRowCount());
        engine.execute("DELETE FROM lock_test WHERE id = 1");
        assertEquals(2, engine.executeQuery("SHOW LOCKS").getRowCount());

        // IN ACCOUNT prepends a session column; the rows are otherwise the same.
        final ResultSet account = engine.executeQuery("SHOW LOCKS IN ACCOUNT");
        assertEquals("session", account.getColumns().get(0).getName());
        assertEquals("resource", account.getColumns().get(1).getName());
        assertEquals(2, account.getRowCount());
        assertNotNull(account.getRows().get(0).getValue(0));

        engine.execute("COMMIT");
        engine.setAutoCommit(true);
        assertEquals(0, engine.executeQuery("SHOW LOCKS").getRowCount(),
            "COMMIT releases every lock");
    }

    // ── SHOW TRANSACTIONS ─────────────────────────────────────────────────────

    @Test
    public void testShowTransactionsEmpty() {
        // Live's exact column set, in order; only OPEN transactions are listed, so idle is empty.
        final ResultSet rs = engine.executeQuery("SHOW TRANSACTIONS");
        assertNotNull(rs);
        assertEquals(0, rs.getRowCount(), "no open transaction → no rows");
        final String[] expected = {"id", "user", "session", "name", "started_on", "state", "scope"};
        assertEquals(expected.length, rs.getColumns().size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], rs.getColumns().get(i).getName());
        }
    }

    @Test
    public void testShowTransactionsActive() {
        engine.execute("CREATE TABLE txn_test (id INTEGER)");
        engine.execute("INSERT INTO txn_test VALUES (0)");
        engine.setAutoCommit(false);
        engine.execute("BEGIN");
        engine.execute("INSERT INTO txn_test VALUES (1)");
        final ResultSet rs = engine.executeQuery("SHOW TRANSACTIONS");
        assertEquals(1, rs.getRowCount());
        // state is lowercase "running", scope is 0, the name is a system-generated UUID, and the
        // id carries the start instant at nanosecond scale (all live-verified shapes).
        assertEquals("running", rs.getRows().get(0).getValue(rs.getColumnIndex("state")));
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(rs.getColumnIndex("scope"))).longValue());
        final String name = String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("name")));
        assertEquals(36, name.length(), "system-generated transaction name is a UUID: " + name);
        final long id = ((Number) rs.getRows().get(0).getValue(rs.getColumnIndex("id"))).longValue();
        assertTrue(id / 1_000_000L > 1_500_000_000_000L, "id is the start instant in epoch nanos: " + id);

        // The lock a rewrite takes references the SAME transaction id (an INSERT alone holds none).
        engine.execute("UPDATE txn_test SET id = 9 WHERE id = 0");
        final ResultSet locks = engine.executeQuery("SHOW LOCKS");
        assertEquals(id, ((Number) locks.getRows().get(0)
            .getValue(locks.getColumnIndex("transaction"))).longValue());

        engine.execute("ROLLBACK");
        engine.setAutoCommit(true);
        assertEquals(0, engine.executeQuery("SHOW TRANSACTIONS").getRowCount(),
            "ROLLBACK ends the listed transaction");
    }

    // ── SHOW VARIABLES ────────────────────────────────────────────────────────

    @Test
    public void testShowVariablesEmpty() {
        final ResultSet rs = engine.executeQuery("SHOW VARIABLES");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("value"));
    }

    // ── SHOW ACCOUNTS / SHOW ORGANIZATION ACCOUNTS ───────────────────────────

    @Test
    public void testShowAccounts() {
        final ResultSet rs = engine.executeQuery("SHOW ACCOUNTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Should return at least one account");
        final int orgIdx = rs.getColumnIndex("organization_name");
        final int accIdx = rs.getColumnIndex("account_name");
        final int editionIdx = rs.getColumnIndex("edition");
        final int regionIdx = rs.getColumnIndex("snowflake_region");
        assertNotNull(rs.getRows().get(0).getValue(orgIdx));
        assertNotNull(rs.getRows().get(0).getValue(accIdx));
        assertNotNull(rs.getRows().get(0).getValue(editionIdx));
        assertNotNull(rs.getRows().get(0).getValue(regionIdx));
    }

    @Test
    public void testShowOrganizationAccounts() {
        Assumptions.assumeFalse(isLiveSnowflake(), NEEDS_ORGADMIN);
        final ResultSet rs = engine.executeQuery("SHOW ORGANIZATION ACCOUNTS");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Should return at least one account");
        final int orgIdx = rs.getColumnIndex("organization_name");
        final int accIdx = rs.getColumnIndex("account_name");
        final int editionIdx = rs.getColumnIndex("edition");
        final int isOrgAdminIdx = rs.getColumnIndex("is_org_admin");
        assertNotNull(rs.getRows().get(0).getValue(orgIdx), "organization_name must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(accIdx), "account_name must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(editionIdx), "edition must be non-null");
        assertNotNull(rs.getRows().get(0).getValue(isOrgAdminIdx), "is_org_admin must be non-null");
    }
}
