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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CURRENT_ACCOUNT() — live-tolerant: the value cells compare against the session's own answer
 * (a real run rides whatever account it connects to), while the engine's config default
 * ({@code ABC12345}) is pinned on the embedded engine only. The custom-config cells build their
 * own throwaway engines and are transport-independent.
 */
public class CurrentAccountTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(CurrentAccountTest.class);

    private String currentAccount() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertEquals(1, rs.getRowCount());
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountAnswersTheSessionAccount() {
        final String account = currentAccount();
        assertNotNull(account);
        assertFalse(account.isEmpty());
        if (!isLiveSnowflake()) {
            assertEquals("ABC12345", account);
        }
        logger.info("CURRENT_ACCOUNT() returned: {}", account);
    }

    @Test
    public void testCurrentAccountWithCustomConfig() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "XYZ98765");
        final DatabaseEngine customEngine = new DatabaseEngine(config);

        final ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("XYZ98765", rs.getRows().get(0).getValue(0));
        logger.info("CURRENT_ACCOUNT() returned custom value: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }

    @Test
    public void testCurrentAccountInWhereClause() {
        final String account = currentAccount();
        engine.execute("CREATE TABLE accounts (id INTEGER, account_id VARCHAR)");
        engine.execute("INSERT INTO accounts VALUES (1, '" + account + "')");
        engine.execute("INSERT INTO accounts VALUES (2, 'SOME_OTHER_ACCT')");

        final ResultSet rs = engine.executeQuery(
            "SELECT id FROM accounts WHERE account_id = CURRENT_ACCOUNT()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        logger.info("Found matching account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountWithAlias() {
        final String account = currentAccount();
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_ACCOUNT() AS account_identifier");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(account, String.valueOf(rs.getRows().get(0).getValue(0)));
        logger.info("CURRENT_ACCOUNT() with alias: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountInJoin() {
        final String account = currentAccount();
        engine.execute("CREATE TABLE user_accounts (user_id INTEGER, account_id VARCHAR)");
        engine.execute("INSERT INTO user_accounts VALUES (1, '" + account + "')");
        engine.execute("INSERT INTO user_accounts VALUES (2, 'SOME_OTHER_ACCT')");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");

        final String sql = """
            SELECT u.name
            FROM users u
            JOIN user_accounts ua ON u.id = ua.user_id
            WHERE ua.account_id = CURRENT_ACCOUNT()
            """;

        final ResultSet rs = engine.executeQuery(sql);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0));
        logger.info("Found user for current account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountMultipleInvocations() {
        final String account = currentAccount();
        final ResultSet rs = engine.executeQuery("""
            SELECT CURRENT_ACCOUNT(), CURRENT_ACCOUNT(), CURRENT_ACCOUNT()
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(account, String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals(account, String.valueOf(rs.getRows().get(0).getValue(1)));
        assertEquals(account, String.valueOf(rs.getRows().get(0).getValue(2)));
        logger.info("All invocations returned same value");
    }

    @Test
    public void testCurrentAccountCaseInsensitive() {
        final String account = currentAccount();
        final ResultSet rs1 = engine.executeQuery("SELECT current_account()");
        final ResultSet rs2 = engine.executeQuery("SELECT Current_Account()");

        assertEquals(account, String.valueOf(rs1.getRows().get(0).getValue(0)));
        assertEquals(account, String.valueOf(rs2.getRows().get(0).getValue(0)));
        logger.info("Function is case-insensitive");
    }

    @Test
    public void testCurrentAccountWithComplexQuery() {
        final String account = currentAccount();
        // DECIMAL(10,2), not bare DECIMAL: bare DECIMAL is NUMBER(38,0) and rounds fractional
        // writes (live-verified), which would turn these amounts into whole numbers.
        engine.execute("CREATE TABLE transactions (id INTEGER, account_id VARCHAR, amount DECIMAL(10,2))");
        engine.execute("INSERT INTO transactions VALUES (1, '" + account + "', 100.50)");
        engine.execute("INSERT INTO transactions VALUES (2, '" + account + "', 200.75)");
        engine.execute("INSERT INTO transactions VALUES (3, 'SOME_OTHER_ACCT', 150.00)");

        final String sql = """
            SELECT SUM(amount) as total
            FROM transactions
            WHERE account_id = CURRENT_ACCOUNT()
            GROUP BY account_id
            """;

        final ResultSet rs = engine.executeQuery(sql);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("301.25", rs.getRows().get(0).getValue(0).toString());
        logger.info("Total for current account: {}", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrentAccountAlphanumeric() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "AB12CD34EF56");
        final DatabaseEngine customEngine = new DatabaseEngine(config);

        final ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertEquals("AB12CD34EF56", rs.getRows().get(0).getValue(0));
        logger.info("Alphanumeric account id: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }

    @Test
    public void testCurrentAccountUpperCase() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_ACCOUNT_ID, "MYACCOUNT123");
        final DatabaseEngine customEngine = new DatabaseEngine(config);

        final ResultSet rs = customEngine.executeQuery("SELECT CURRENT_ACCOUNT()");
        assertEquals("MYACCOUNT123", rs.getRows().get(0).getValue(0));
        logger.info("Upper case account id: {}", rs.getRows().get(0).getValue(0));
        customEngine.shutdown();
    }
}
