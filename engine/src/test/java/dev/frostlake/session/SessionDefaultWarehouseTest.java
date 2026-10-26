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

package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.storage.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A new session starts in the warehouse its user's DEFAULT_WAREHOUSE names, an HTTP session and a direct JDBC
 * connection alike; a default that names no warehouse leaves the session where it would start without one, and an
 * ALTER USER reaches the sessions that start after it, not one already running.
 */
public class SessionDefaultWarehouseTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        final SessionContext setup = engine.createSession();
        run(setup, "CREATE WAREHOUSE dw_one INITIALLY_SUSPENDED = TRUE");
        run(setup, "CREATE WAREHOUSE dw_two INITIALLY_SUSPENDED = TRUE");
        run(setup, "CREATE USER dw_user DEFAULT_WAREHOUSE = dw_one");
        engine.removeSession(setup.getSessionId());
    }

    @AfterEach
    public void tearDown() {
        engine.getEngine().setCurrentUser("SYSTEM");
        engine.shutdown();
    }

    private ExecutionResult run(final SessionContext session, final String sql) {
        final ExecutionResult result = engine.execute(sql, session);
        if (!result.isSuccess()) {
            throw new IllegalStateException(sql + " failed: " + result.getErrorMessage());
        }
        return result;
    }

    private Object warehouse(final SessionContext session) {
        final List<ResultSet> sets = run(session, "SELECT CURRENT_WAREHOUSE()").getResultSets();
        final ResultSet rs = sets.get(sets.size() - 1);
        rs.next();
        return rs.getValue(0);
    }

    private static String warehouse(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery("SELECT CURRENT_WAREHOUSE()")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void aSessionStartsInItsUsersDefaultWarehouse() {
        final SessionContext before = engine.createSession();
        assertNull(warehouse(before));
        engine.getEngine().setCurrentUser("dw_user");
        final SessionContext session = engine.createSession();
        assertEquals("DW_ONE", warehouse(session));
        run(session, "USE WAREHOUSE dw_two");
        assertEquals("DW_TWO", warehouse(session));
        assertEquals("DW_ONE", warehouse(engine.createSession()));
    }

    @Test
    public void anAlterUserReachesTheSessionsThatStartAfterIt() {
        engine.getEngine().setCurrentUser("dw_user");
        final SessionContext running = engine.createSession();
        assertEquals("DW_ONE", warehouse(running));
        run(running, "ALTER USER dw_user SET DEFAULT_WAREHOUSE = dw_two");
        assertEquals("DW_ONE", warehouse(running));
        assertEquals("DW_TWO", warehouse(engine.createSession()));
        run(running, "ALTER USER dw_user UNSET DEFAULT_WAREHOUSE");
        assertNull(warehouse(engine.createSession()));
    }

    @Test
    public void aDefaultThatNamesNoWarehouseLeavesTheSessionWithoutOne() {
        engine.getEngine().setCurrentUser("dw_user");
        final SessionContext setup = engine.createSession();
        run(setup, "ALTER USER dw_user SET DEFAULT_WAREHOUSE = dw_nosuch");
        assertNull(warehouse(engine.createSession()));
        run(setup, "ALTER USER dw_user SET DEFAULT_WAREHOUSE = dw_two");
        assertEquals("DW_TWO", warehouse(engine.createSession()));
        // The warehouse's owner drops it, and the user's default goes on naming it.
        engine.getEngine().setCurrentUser("SYSTEM");
        final SessionContext owner = engine.createSession();
        run(owner, "USE ROLE SYSADMIN");
        run(owner, "DROP WAREHOUSE dw_two");
        engine.getEngine().setCurrentUser("dw_user");
        assertNull(warehouse(engine.createSession()));
    }

    @Test
    public void aDirectConnectionStartsInItsUsersDefaultWarehouse() throws SQLException {
        try (Connection before = new DirectConnection(engine.getEngine())) {
            assertNull(warehouse(before));
        }
        engine.getEngine().setCurrentUser("dw_user");
        try (Connection connection = new DirectConnection(engine.getEngine())) {
            assertEquals("DW_ONE", warehouse(connection));
            try (Statement statement = connection.createStatement()) {
                statement.execute("USE WAREHOUSE dw_two");
            }
            assertEquals("DW_TWO", warehouse(connection));
        }
        try (Connection next = new DirectConnection(engine.getEngine())) {
            assertEquals("DW_ONE", warehouse(next));
        }
    }
}
