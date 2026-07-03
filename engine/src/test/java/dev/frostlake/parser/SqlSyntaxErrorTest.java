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

package dev.frostlake.parser;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SQL syntax error detection and logging
 */
public class SqlSyntaxErrorTest {

    private static final Logger logger = LoggerFactory.getLogger(SqlSyntaxErrorTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @Test
    public void testInvalidSelectSyntax() {
        String sql = "SELECT * FORM users";  // FORM instead of FROM
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        assertEquals(sql, ex.getFailedSql());
        assertFalse(ex.getSyntaxErrors().isEmpty());
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testMissingFromClause() {
        // With WHERE allowed without FROM, this now parses but fails at runtime
        String sql = "SELECT name WHERE age > 18";
        assertThrows(RuntimeException.class, () -> {
            engine.execute(sql);
        });
    }

    @Test
    public void testInvalidWhereClause() {
        String sql = "SELECT * FROM users WHERE";  // Incomplete WHERE
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        assertFalse(ex.getSyntaxErrors().isEmpty());
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidInsertSyntax() {
        String sql = "INSERT users VALUES (1, 'Alice')";  // Missing INTO
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains("Syntax error"));
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testUnclosedParenthesis() {
        String sql = "SELECT * FROM users WHERE (age > 18";  // Missing )
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidColumnName() {
        String sql = "SELECT * FROM users ORDER BY 123abc";  // Invalid identifier
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains("Syntax error"));
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testMultipleSyntaxErrors() {
        String sql = "SELECT * FORM users WERE age > 18";  // Multiple errors
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getSyntaxErrors());
        assertFalse(ex.getSyntaxErrors().isEmpty());
        logger.info("Caught {} syntax error(s)", ex.getSyntaxErrors().size());
    }

    @Test
    public void testInvalidCreateTable() {
        String sql = "CREATE TABLE users (id INT name VARCHAR)";  // Missing comma
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains("Syntax error"));
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidUpdate() {
        String sql = "UPDATE users age = 25 WHERE id = 1";  // Missing SET
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidDelete() {
        String sql = "DELETE users WHERE id = 1";  // Missing FROM
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains("Syntax error"));
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testValidQueryDoesNotThrow() {
        // Valid queries should not throw syntax errors
        assertDoesNotThrow(() -> {
            engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
            engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");
            engine.execute("SELECT * FROM test_table WHERE id = 1");
            engine.execute("UPDATE test_table SET name = 'Bob' WHERE id = 1");
            engine.execute("DELETE FROM test_table WHERE id = 1");
        });
        logger.info("Valid queries executed successfully");
    }

    @Test
    public void testErrorMessageContainsQuery() {
        String sql = "SELEKT * FROM users";  // Typo in SELECT
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains(sql) || ex.getFailedSql().equals(sql));
        logger.info("Error message contains failed query");
    }

    @Test
    public void testComplexInvalidQuery() {
        String sql = """
            SELECT u.name, o.amount
            FROM users u
            JOIN orders o u.id = o.user_id
            WHERE o.amount > 100
            """;  // Missing ON in JOIN

        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        assertTrue(ex.getFailedSql().contains("JOIN"));
        logger.info("Caught expected syntax error in complex query");
    }

    @Test
    public void testInvalidAggregateFunction() {
        String sql = "SELECT COUNT(*) FORM users";  // FORM instead of FROM
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertFalse(ex.getSyntaxErrors().isEmpty());
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidGroupBy() {
        String sql = "SELECT age FROM users GROUP age";  // Missing BY
        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertTrue(ex.getMessage().contains("Syntax error"));
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testPartiallyValidScript() {
        // First statement is valid, second is invalid
        String sql = """
            CREATE TABLE test (id INTEGER);
            SELEKT * FROM test
            """;

        SqlSyntaxException ex = assertThrows(SqlSyntaxException.class, () -> {
            engine.execute(sql);
        });

        assertNotNull(ex.getFailedSql());
        logger.info("Caught syntax error in multi-statement script");
    }
}
