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

package dev.frostlake.transaction;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request of several statements runs each as a statement of its own. Under autocommit a statement commits as it
 * completes, so a later statement's failure keeps what came before it and stops the request there; inside an
 * explicit transaction the failure undoes only the failing statement and leaves the transaction open for COMMIT or
 * ROLLBACK. The requests go over JDBC as one call each, as a client sends them. Every cell is live-verified.
 */
public class MultiStatementCommitTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
    }

    @Override
    protected void teardownTest() throws SQLException {
        statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
    }

    /** The first row's first cell as text. */
    private String answer(final String sql) throws SQLException {
        try (final ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    /** Send a request that must fail. */
    private void fails(final String sql) {
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        }, sql);
    }

    @Test
    public void aLaterFailureKeepsWhatCameBeforeIt() throws SQLException {
        fails("CREATE TABLE t1 (n INT); INSERT INTO t1 VALUES (1); SELECT * FROM nowhere_xyz");
        assertEquals("1", answer("SELECT COUNT(*) FROM t1"));

        statement.execute("CREATE TABLE t2 (n INT)");
        fails("INSERT INTO t2 VALUES (1); INSERT INTO t2 VALUES (2); SELECT * FROM nowhere_xyz");
        assertEquals("2", answer("SELECT COUNT(*) FROM t2"));

        statement.execute("CREATE TABLE t6 (n INT); INSERT INTO t6 VALUES (1)");
        fails("UPDATE t6 SET n = n + 10; SELECT 1/0");
        assertEquals("11", answer("SELECT SUM(n) FROM t6"));

        statement.execute("CREATE TABLE t9 (n INT)");
        fails("INSERT INTO t9 VALUES (1); DELETE FROM t9; SELECT 1/0");
        assertEquals("0", answer("SELECT COUNT(*) FROM t9"));
    }

    @Test
    public void theRequestStopsAtTheFailureAndTheFailingStatementLeavesNothing() throws SQLException {
        statement.execute("CREATE TABLE t3 (n INT)");
        fails("INSERT INTO t3 VALUES (1); SELECT * FROM nowhere_xyz; INSERT INTO t3 VALUES (2)");
        assertEquals("1", answer("SELECT COUNT(*) FROM t3"));

        statement.execute("CREATE TABLE t7 (n INT)");
        fails("INSERT INTO t7 VALUES (1); INSERT INTO t7 VALUES (2), ('x')");
        assertEquals("1", answer("SELECT COUNT(*) FROM t7"));
    }

    @Test
    public void insideAnExplicitTransactionOnlyTheFailingStatementIsUndone() throws SQLException {
        statement.execute("CREATE TABLE t4 (n INT)");
        fails("BEGIN; INSERT INTO t4 VALUES (1); SELECT * FROM nowhere_xyz");
        assertEquals("1", answer("SELECT COUNT(*) FROM t4"));
        statement.execute("ROLLBACK");
        assertEquals("0", answer("SELECT COUNT(*) FROM t4"));

        statement.execute("CREATE TABLE t5 (n INT)");
        fails("BEGIN; INSERT INTO t5 VALUES (1); SELECT * FROM nowhere_xyz");
        statement.execute("COMMIT");
        assertEquals("1", answer("SELECT COUNT(*) FROM t5"));

        statement.execute("CREATE TABLE t8 (n INT)");
        fails("INSERT INTO t8 VALUES (1); BEGIN; INSERT INTO t8 VALUES (2); SELECT * FROM nowhere_xyz");
        assertEquals("2", answer("SELECT COUNT(*) FROM t8"));
        statement.execute("ROLLBACK");
        assertEquals("1", answer("SELECT COUNT(*) FROM t8"));
    }

    @Test
    public void aTransactionOpenedByAnEarlierRequestKeepsTheStatementsBeforeTheFailure() throws SQLException {
        statement.execute("CREATE TABLE t10 (n INT)");
        statement.execute("BEGIN");
        fails("INSERT INTO t10 VALUES (1); INSERT INTO t10 VALUES (2), ('x')");
        assertEquals("1", answer("SELECT COUNT(*) FROM t10"));
        statement.execute("COMMIT");
        assertEquals("1", answer("SELECT COUNT(*) FROM t10"));
    }
}
