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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.User;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CurrentUserTest {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserTest.class);
    // The default connected user is now sourced from the OS login (upper-cased, as the engine stores users).
    private static final String OS_USER = System.getProperty("user.name", "ADMIN").toUpperCase();
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCurrentUserDefaultValue() {
        ResultSet rs = engine.executeQuery("SELECT CURRENT_USER()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(OS_USER, rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_USER() returned: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentUserWithCustomConfig() {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DEFAULT_USER, "JOHN");
        DatabaseEngine customEngine = new DatabaseEngine(config);

        ResultSet rs = customEngine.executeQuery("SELECT CURRENT_USER()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("JOHN", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_USER() with custom config: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }

    @Test
    public void testCurrentUserCaseInsensitive() {
        ResultSet rs1 = engine.executeQuery("SELECT current_user()");
        ResultSet rs2 = engine.executeQuery("SELECT CURRENT_USER()");

        assertEquals(OS_USER, rs1.getRows().get(0).getValue(0));
        assertEquals(OS_USER, rs2.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentUserInWhereClause() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE user_data (id INTEGER, username VARCHAR)");
        engine.execute("INSERT INTO user_data VALUES (1, '" + OS_USER + "')");
        engine.execute("INSERT INTO user_data VALUES (2, 'OTHER')");

        ResultSet rs = engine.executeQuery("SELECT id FROM user_data WHERE username = CURRENT_USER()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_USER() in WHERE clause works");
    }

    @Test
    public void testDefaultUserExistsInCatalog() {
        assertNotNull(engine.getCatalog().getUser(OS_USER));
        logger.info("Default user {} exists in catalog", OS_USER);
    }

    @Test
    public void testDefaultUserHasAdminRoles() {
        User admin = engine.getCatalog().getUser(OS_USER);
        assertTrue(admin.getGrantedRoles().contains("ORGADMIN"),
            "Default user should have ORGADMIN role");
        assertTrue(admin.getGrantedRoles().contains("ACCOUNTADMIN"),
            "Default user should have ACCOUNTADMIN role");
        logger.info("Default user roles: {}", admin.getGrantedRoles());
    }

    @Test
    public void testCurrentUserUpperCasedFromConfig() {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DEFAULT_USER, "alice");
        DatabaseEngine customEngine = new DatabaseEngine(config);

        ResultSet rs = customEngine.executeQuery("SELECT CURRENT_USER()");
        assertEquals("ALICE", rs.getRows().get(0).getValue(0));
        logger.info("User name is uppercased: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }
}
