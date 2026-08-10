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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.User;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT_USER() — live-tolerant: the value cells compare against the session's own answer
 * (whatever account the run rides), while the engine-default pin (the OS login, upper-cased as
 * the engine stores users) and the catalog cells hold on the embedded engine. The custom-config
 * cells build their own throwaway engines and are transport-independent.
 */
public class CurrentUserTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserTest.class);
    // The default connected user is sourced from the OS login (upper-cased, as the engine stores users).
    private static final String OS_USER = System.getProperty("user.name", "ADMIN").toUpperCase();

    private String currentUser() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_USER()");
        assertEquals(1, rs.getRowCount());
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentUserAnswersTheSessionUser() {
        final String user = currentUser();
        assertNotNull(user);
        assertFalse(user.isEmpty());
        if (!isLiveSnowflake()) {
            assertEquals(OS_USER, user);
        }
        logger.info("CURRENT_USER() returned: {}", user);
    }

    @Test
    public void testCurrentUserWithCustomConfig() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DEFAULT_USER, "JOHN");
        final DatabaseEngine customEngine = new DatabaseEngine(config);

        final ResultSet rs = customEngine.executeQuery("SELECT CURRENT_USER()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("JOHN", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_USER() with custom config: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }

    @Test
    public void testCurrentUserCaseInsensitive() {
        final String upper = currentUser();
        final ResultSet rs1 = engine.executeQuery("SELECT current_user()");
        assertEquals(upper, String.valueOf(rs1.getRows().get(0).getValue(0)));
    }

    @Test
    public void testCurrentUserInWhereClause() {
        final String user = currentUser();
        engine.execute("CREATE TABLE user_data (id INTEGER, username VARCHAR)");
        engine.execute("INSERT INTO user_data VALUES (1, '" + user + "')");
        engine.execute("INSERT INTO user_data VALUES (2, 'SOMEBODY_ELSE')");

        final ResultSet rs = engine.executeQuery(
            "SELECT id FROM user_data WHERE username = CURRENT_USER()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        logger.info("CURRENT_USER() in WHERE clause works");
    }

    @Test
    public void testDefaultUserExistsInCatalog() {
        assertNotNull(engine.getCatalog().getUser(OS_USER));
        logger.info("Default user {} exists in catalog", OS_USER);
    }

    @Test
    public void testDefaultUserHasAdminRoles() {
        final User admin = engine.getCatalog().getUser(OS_USER);
        assertTrue(admin.getGrantedRoles().contains("ORGADMIN"),
            "Default user should have ORGADMIN role");
        assertTrue(admin.getGrantedRoles().contains("ACCOUNTADMIN"),
            "Default user should have ACCOUNTADMIN role");
        logger.info("Default user roles: {}", admin.getGrantedRoles());
    }

    @Test
    public void testCurrentUserUpperCasedFromConfig() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DEFAULT_USER, "alice");
        final DatabaseEngine customEngine = new DatabaseEngine(config);

        final ResultSet rs = customEngine.executeQuery("SELECT CURRENT_USER()");
        assertEquals("ALICE", rs.getRows().get(0).getValue(0));
        logger.info("User name is uppercased: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }
}
