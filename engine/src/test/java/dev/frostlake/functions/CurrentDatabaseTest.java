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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class CurrentDatabaseTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(CurrentDatabaseTest.class);

    @Test
    public void testCurrentDatabase() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() returns current database");

        statement.execute("CREATE DATABASE IF NOT EXISTS test_db");
        statement.execute("USE DATABASE test_db");

        ResultSet rs = statement.executeQuery("SELECT CURRENT_DATABASE()");
        assertTrue(rs.next());
        assertEquals("TEST_DB", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseWithParentheses() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() with explicit parentheses");

        statement.execute("CREATE DATABASE IF NOT EXISTS my_database");
        statement.execute("USE DATABASE my_database");

        ResultSet rs = statement.executeQuery("SELECT CURRENT_DATABASE() AS db_name");
        assertTrue(rs.next());
        assertEquals("MY_DATABASE", rs.getString("db_name"));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseInWhereClause() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() in WHERE clause");

        statement.execute("CREATE DATABASE IF NOT EXISTS analytics_db");
        statement.execute("USE DATABASE analytics_db");

        statement.execute("CREATE TABLE metadata (db_name VARCHAR, description VARCHAR)");
        statement.execute("INSERT INTO metadata VALUES ('ANALYTICS_DB', 'Main analytics database')");
        statement.execute("INSERT INTO metadata VALUES ('OTHER_DB', 'Other database')");

        ResultSet rs = statement.executeQuery(
            "SELECT description FROM metadata WHERE db_name = CURRENT_DATABASE()"
        );
        assertTrue(rs.next());
        assertEquals("Main analytics database", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseAfterSwitching() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() after switching databases");

        statement.execute("CREATE DATABASE IF NOT EXISTS db1");
        statement.execute("CREATE DATABASE IF NOT EXISTS db2");

        statement.execute("USE DATABASE db1");
        ResultSet rs = statement.executeQuery("SELECT CURRENT_DATABASE()");
        assertTrue(rs.next());
        assertEquals("DB1", rs.getString(1));

        statement.execute("USE DATABASE db2");
        rs = statement.executeQuery("SELECT CURRENT_DATABASE()");
        assertTrue(rs.next());
        assertEquals("DB2", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseInInsert() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() in INSERT statement");

        statement.execute("CREATE DATABASE IF NOT EXISTS tracker_db");
        statement.execute("USE DATABASE tracker_db");

        statement.execute("CREATE TABLE operations (id INTEGER, db_name VARCHAR)");
        statement.execute("INSERT INTO operations VALUES (1, CURRENT_DATABASE())");
        statement.execute("INSERT INTO operations VALUES (2, CURRENT_DATABASE())");

        ResultSet rs = statement.executeQuery("SELECT DISTINCT db_name FROM operations");
        assertTrue(rs.next());
        assertEquals("TRACKER_DB", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseInSubquery() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() in subquery");

        statement.execute("CREATE DATABASE IF NOT EXISTS subquery_db");
        statement.execute("USE DATABASE subquery_db");

        statement.execute("CREATE TABLE logs (id INTEGER, context VARCHAR)");
        statement.execute("INSERT INTO logs VALUES (1, 'test')");

        ResultSet rs = statement.executeQuery(
            "SELECT id FROM logs WHERE context = 'test' AND 'SUBQUERY_DB' = (SELECT CURRENT_DATABASE())"
        );
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseMultipleCalls() throws SQLException {
        logger.info("Testing multiple CURRENT_DATABASE() calls in same query");

        statement.execute("CREATE DATABASE IF NOT EXISTS multi_db");
        statement.execute("USE DATABASE multi_db");

        ResultSet rs = statement.executeQuery(
            "SELECT CURRENT_DATABASE() AS db1, CURRENT_DATABASE() AS db2"
        );
        assertTrue(rs.next());
        assertEquals("MULTI_DB", rs.getString("db1"));
        assertEquals("MULTI_DB", rs.getString("db2"));
        assertEquals(rs.getString("db1"), rs.getString("db2"));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseWithJoin() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() with JOIN");

        statement.execute("CREATE DATABASE IF NOT EXISTS join_db");
        statement.execute("USE DATABASE join_db");

        statement.execute("CREATE TABLE databases (name VARCHAR, active INTEGER)");
        statement.execute("CREATE TABLE settings (db_name VARCHAR, config VARCHAR)");

        statement.execute("INSERT INTO databases VALUES ('JOIN_DB', 1)");
        statement.execute("INSERT INTO databases VALUES ('OTHER', 0)");

        statement.execute("INSERT INTO settings VALUES ('JOIN_DB', 'enabled')");

        ResultSet rs = statement.executeQuery(
            "SELECT s.config FROM databases d " +
            "JOIN settings s ON d.name = s.db_name " +
            "WHERE d.name = CURRENT_DATABASE()"
        );
        assertTrue(rs.next());
        assertEquals("enabled", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseCreateTableAsSelect() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() in CREATE TABLE AS SELECT");

        statement.execute("CREATE DATABASE IF NOT EXISTS ctas_db");
        statement.execute("USE DATABASE ctas_db");

        statement.execute(
            "CREATE TABLE db_snapshot AS SELECT CURRENT_DATABASE() AS snapshot_db"
        );

        ResultSet rs = statement.executeQuery("SELECT snapshot_db FROM db_snapshot");
        assertTrue(rs.next());
        assertEquals("CTAS_DB", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testCurrentDatabaseWithConcat() throws SQLException {
        logger.info("Testing CURRENT_DATABASE() with string concatenation");

        statement.execute("CREATE DATABASE IF NOT EXISTS concat_db");
        statement.execute("USE DATABASE concat_db");

        ResultSet rs = statement.executeQuery(
            "SELECT 'Database: ' || CURRENT_DATABASE() AS info"
        );
        assertTrue(rs.next());
        assertEquals("Database: CONCAT_DB", rs.getString(1));
        assertFalse(rs.next());
    }
}
