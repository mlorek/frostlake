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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JDBC driver executes several {@code ;}-separated statements in one call (the engine parses and runs
 * each), and exposes every produced result set through the standard {@code getResultSet()} /
 * {@code getMoreResults()} iteration — not only the first.
 */
public class JdbcMultiStatementTest extends BaseJdbcTest {

    /** The gate's exact wording (live-verified — state 0A000, code 8, exact-count both ways). */
    private void assertPackRefused(final String sql, final int actual, final int desired) {
        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        assertEquals("Actual statement count " + actual + " did not match the desired statement count "
            + desired + ".", e.getMessage());
        assertEquals("0A000", e.getSQLState());
        assertEquals(8, e.getErrorCode());
    }

    private void allowAnyCount() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
    }

    @AfterEach
    public void restoreSingleStatementDefault() throws SQLException {
        // A live session is shared beyond this class — never leak the parameter. The statement
        // parameter overrides whatever session count a test left behind, so the UNSET always runs.
        setStatementCount(1);
        statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
    }

    @Test
    public void aPackIsRefusedByDefault() {
        assertPackRefused("SELECT 1; SELECT 2", 2, 1);
    }

    @Test
    public void aTrailingSemicolonIsStillOneStatement() throws SQLException {
        try (final ResultSet rs = statement.executeQuery("SELECT 1 AS x;")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    /** The per-statement parameter, through each transport's own unwrap surface. */
    private void setStatementCount(final int n) throws SQLException {
        if (isLiveSnowflake()) {
            statement.unwrap(net.snowflake.client.api.statement.SnowflakeStatement.class)
                .setParameter("MULTI_STATEMENT_COUNT", n);
        } else {
            statement.unwrap(DirectStatement.class).setParameter("MULTI_STATEMENT_COUNT", n);
        }
    }

    @Test
    public void theCountMustMatchExactlyInBothDirections() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 2");
        assertPackRefused("SELECT 1; SELECT 2; SELECT 3", 3, 2);
        assertPackRefused("SELECT 1", 1, 2);
        // The ALTER SESSION statement is gated like any other — under a count of 2 even the UNSET
        // refuses as one statement; the per-statement parameter is the escape hatch.
        assertPackRefused("ALTER SESSION UNSET MULTI_STATEMENT_COUNT", 1, 2);
        setStatementCount(1);
        statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        assertPackRefused("SELECT 1; SELECT 2", 2, 1);
    }

    @Test
    public void multipleStatementsInOneExecuteAllRun() throws SQLException {
        allowAnyCount();
        // DDL + several DML in a single execute — every statement takes effect.
        statement.execute(
            "CREATE TABLE mt (a NUMBER); INSERT INTO mt VALUES (1); INSERT INTO mt VALUES (2); INSERT INTO mt VALUES (3)");
        try (final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM mt")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    public void iterateMultipleResultSets() throws SQLException {
        allowAnyCount();
        final boolean firstIsResultSet = statement.execute("SELECT 1 AS x; SELECT 2 AS y; SELECT 3 AS z");
        assertTrue(firstIsResultSet, "first statement produced a result set");

        final ResultSet rs1 = statement.getResultSet();
        assertTrue(rs1.next());
        assertEquals(1, rs1.getInt(1));

        assertTrue(statement.getMoreResults(), "should advance to the 2nd result set");
        final ResultSet rs2 = statement.getResultSet();
        assertTrue(rs2.next());
        assertEquals(2, rs2.getInt(1));

        assertTrue(statement.getMoreResults(), "should advance to the 3rd result set");
        final ResultSet rs3 = statement.getResultSet();
        assertTrue(rs3.next());
        assertEquals(3, rs3.getInt(1));

        assertFalse(statement.getMoreResults(), "no more result sets");
    }

    @Test
    public void mixedDmlAndSelectBatch() throws SQLException {
        allowAnyCount();
        statement.execute("CREATE TABLE mt2 (a NUMBER)");
        // A batch that inserts then selects — the SELECT's result set is reachable.
        final boolean firstIsResultSet = statement.execute(
            "INSERT INTO mt2 VALUES (10); INSERT INTO mt2 VALUES (20); SELECT SUM(a) AS total FROM mt2");
        // The first produced result set in the batch is the SELECT (DML rows-affected aside), reachable
        // by walking getMoreResults() until a result set appears.
        boolean sawTotal = false;
        boolean hasResult = firstIsResultSet;
        do {
            if (hasResult) {
                final ResultSet rs = statement.getResultSet();
                if (rs != null && rs.next()) {
                    if (rs.getInt(1) == 30) {
                        sawTotal = true;
                    }
                }
            }
            hasResult = statement.getMoreResults();
        } while (hasResult);
        assertTrue(sawTotal, "the SELECT SUM(a)=30 result set is reachable in the batch");
    }
}
