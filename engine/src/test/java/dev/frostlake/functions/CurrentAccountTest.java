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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for CURRENT_ACCOUNT() function
 */
public class CurrentAccountTest {

    private static final Logger logger = LoggerFactory.getLogger(CurrentAccountTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @Test
    public void testCurrentAccountDefaultValue() {
        ResultSet rs = engine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("ABC12345", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_ACCOUNT() returned default value: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountWithCustomConfig() {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "XYZ98765");
        DatabaseEngine customEngine = new DatabaseEngine(config);

        ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("XYZ98765", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_ACCOUNT() returned custom value: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountInWhereClause() {
        engine.execute("CREATE TABLE accounts (id INTEGER, account_id VARCHAR)");
        engine.execute("INSERT INTO accounts VALUES (1, 'ABC12345')");
        engine.execute("INSERT INTO accounts VALUES (2, 'OTHER123')");

        ResultSet rs = engine.executeQuery("SELECT id FROM accounts WHERE account_id = CURRENT_ACCOUNT()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        logger.info("Found matching account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountWithAlias() {
        ResultSet rs = engine.executeQuery("SELECT CURRENT_ACCOUNT() AS account_identifier");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("ABC12345", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_ACCOUNT() with alias: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountInJoin() {
        engine.execute("CREATE TABLE user_accounts (user_id INTEGER, account_id VARCHAR)");
        engine.execute("INSERT INTO user_accounts VALUES (1, 'ABC12345')");
        engine.execute("INSERT INTO user_accounts VALUES (2, 'XYZ98765')");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");

        String sql = """
            SELECT u.name
            FROM users u
            JOIN user_accounts ua ON u.id = ua.user_id
            WHERE ua.account_id = CURRENT_ACCOUNT()
            """;

        ResultSet rs = engine.executeQuery(sql);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0));
        logger.info("Found user for current account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountMultipleInvocations() {
        ResultSet rs = engine.executeQuery("""
            SELECT CURRENT_ACCOUNT(), CURRENT_ACCOUNT(), CURRENT_ACCOUNT()
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("ABC12345", rs.getRows().get(0).getValue(0));
        assertEquals("ABC12345", rs.getRows().get(0).getValue(1));
        assertEquals("ABC12345", rs.getRows().get(0).getValue(2));
        logger.info("All invocations returned same value");
    }

    @Test
    public void testCurrentAccountCaseInsensitive() {
        ResultSet rs1 = engine.executeQuery("SELECT current_account()");
        ResultSet rs2 = engine.executeQuery("SELECT CURRENT_ACCOUNT()");
        ResultSet rs3 = engine.executeQuery("SELECT Current_Account()");

        assertEquals("ABC12345", rs1.getRows().get(0).getValue(0));
        assertEquals("ABC12345", rs2.getRows().get(0).getValue(0));
        assertEquals("ABC12345", rs3.getRows().get(0).getValue(0));
        logger.info("Function is case-insensitive");
    }

    @Test
    public void testCurrentAccountWithComplexQuery() {
        // DECIMAL(10,2), not bare DECIMAL: bare DECIMAL is NUMBER(38,0) and rounds fractional
        // writes (live-verified), which would turn these amounts into whole numbers.
        engine.execute("CREATE TABLE transactions (id INTEGER, account_id VARCHAR, amount DECIMAL(10,2))");
        engine.execute("INSERT INTO transactions VALUES (1, 'ABC12345', 100.50)");
        engine.execute("INSERT INTO transactions VALUES (2, 'ABC12345', 200.75)");
        engine.execute("INSERT INTO transactions VALUES (3, 'OTHER123', 150.00)");

        String sql = """
            SELECT SUM(amount) as total
            FROM transactions
            WHERE account_id = CURRENT_ACCOUNT()
            GROUP BY account_id
            """;

        ResultSet rs = engine.executeQuery(sql);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("301.25", rs.getRows().get(0).getValue(0).toString());
        logger.info("Total for current account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountAlphanumeric() {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "AB12CD34EF56");
        DatabaseEngine customEngine = new DatabaseEngine(config);

        ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertEquals("AB12CD34EF56", rs.getRows().get(0).getValue(0));
        logger.info("Alphanumeric account ID: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountUpperCase() {
        EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "MYACCOUNT123");
        DatabaseEngine customEngine = new DatabaseEngine(config);

        ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertEquals("MYACCOUNT123", rs.getRows().get(0).getValue(0));
        logger.info("Upper case account ID: {}", rs.getRows().get(0).getValue(0));
    }
}
