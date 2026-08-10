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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.FrostlakeJdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL syntax error detection over malformed statement shapes — each cell refuses on every
 * transport with live's {@code syntax error} wording somewhere in the message. The typed
 * {@code SqlSyntaxException} API ({@code getFailedSql}, {@code getSyntaxErrors}) exists only on
 * the plain embedded engine — JDBC flattens it to a message — so those asserts are guarded.
 * Exact wording and positions are pinned in {@code SyntaxErrorShapeTest}.
 */
public class SqlSyntaxErrorTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SqlSyntaxErrorTest.class);

    /** Whether the typed engine-side exception object survives to the test (no JDBC in between). */
    private static boolean typedSurface() {
        return !isLiveSnowflake() && !FrostlakeJdbc.enabled();
    }

    private RuntimeException refusal(final String sql) {
        RuntimeException caught = null;
        try {
            engine.execute(sql);
        } catch (final RuntimeException e) {
            caught = e;
        }
        assertTrue(caught != null, "expected a refusal for: " + sql);
        return caught;
    }

    /** Refuses everywhere; the message reads as a syntax error; typed API checked embedded. */
    private void assertSyntaxRefusal(final String sql) {
        final RuntimeException ex = refusal(sql);
        assertTrue(ex.getMessage().contains("syntax error"),
            "not a syntax refusal: " + ex.getMessage());
        if (typedSurface()) {
            assertTrue(ex instanceof SqlSyntaxException, "expected SqlSyntaxException, got " + ex);
            final SqlSyntaxException typed = (SqlSyntaxException) ex;
            assertEquals(sql, typed.getFailedSql());
            assertFalse(typed.getSyntaxErrors().isEmpty());
        }
        logger.info("Caught expected syntax error: {}", ex.getMessage());
    }

    @Test
    public void testInvalidSelectSyntax() {
        assertSyntaxRefusal("SELECT * FORM users");  // FORM instead of FROM
    }

    @Test
    public void testMissingFromClause() {
        // WHERE is allowed without FROM, so this parses and fails resolving the identifier.
        refusal("SELECT name WHERE age > 18");
    }

    @Test
    public void testInvalidWhereClause() {
        assertSyntaxRefusal("SELECT * FROM users WHERE");  // Incomplete WHERE
    }

    @Test
    public void testInvalidInsertSyntax() {
        assertSyntaxRefusal("INSERT users VALUES (1, 'Alice')");  // Missing INTO
    }

    @Test
    public void testUnclosedParenthesis() {
        assertSyntaxRefusal("SELECT * FROM users WHERE (age > 18");  // Missing )
    }

    @Test
    public void testInvalidColumnName() {
        assertSyntaxRefusal("SELECT * FROM users ORDER BY 123abc");  // Invalid identifier
    }

    @Test
    public void testMultipleSyntaxErrors() {
        assertSyntaxRefusal("SELECT * FORM users WERE age > 18");  // Multiple errors
    }

    @Test
    public void testInvalidCreateTable() {
        assertSyntaxRefusal("CREATE TABLE users (id INT name VARCHAR)");  // Missing comma
    }

    @Test
    public void testInvalidUpdate() {
        assertSyntaxRefusal("UPDATE users age = 25 WHERE id = 1");  // Missing SET
    }

    @Test
    public void testInvalidDelete() {
        assertSyntaxRefusal("DELETE users WHERE id = 1");  // Missing FROM
    }

    @Test
    public void testValidQueryDoesNotThrow() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
                engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");
                engine.execute("SELECT * FROM test_table WHERE id = 1");
                engine.execute("UPDATE test_table SET name = 'Bob' WHERE id = 1");
                engine.execute("DELETE FROM test_table WHERE id = 1");
            }
        });
        logger.info("Valid queries executed successfully");
    }

    @Test
    public void testErrorCarriesTheFailedStatement() {
        // SELEKT reads as a bare identifier expression, so the unexpected token is the '*'.
        assertSyntaxRefusal("SELEKT * FROM users");
    }

    @Test
    public void testComplexInvalidQuery() {
        assertSyntaxRefusal("""
            SELECT u.name, o.amount
            FROM users u
            JOIN orders o u.id = o.user_id
            WHERE o.amount > 100
            """);  // Missing ON in JOIN
    }

    @Test
    public void testInvalidAggregateFunction() {
        assertSyntaxRefusal("SELECT COUNT(*) FORM users");  // FORM instead of FROM
    }

    @Test
    public void testInvalidGroupBy() {
        assertSyntaxRefusal("SELECT age FROM users GROUP age");  // Missing BY
    }

    @Test
    public void testPartiallyValidScript() {
        // First statement is valid, second is invalid.
        assertSyntaxRefusal("""
            CREATE TABLE test (id INTEGER);
            SELEKT * FROM test
            """);
    }
}
