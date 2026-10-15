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
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A statement ends before its answer when it outlives its session's STATEMENT_TIMEOUT_IN_SECONDS — "Statement reached
 * its statement or warehouse timeout of N second(s) and was canceled.", SQLSTATE 57014, code 630, a whole block with
 * it — or when the driver cancels it, by its query timeout or by cancel() from another thread: "SQL execution
 * canceled", 57014, 604. A transaction stays open past a canceled statement, a timeout of zero is the longest, and
 * SYSTEM$CANCEL_QUERY answers that a query ID names no running statement (live-verified).
 */
public class StatementTimeoutTest extends BaseJdbcTest {

    private static final String TIMED_OUT =
        "Statement reached its statement or warehouse timeout of 1 second(s) and was canceled.";

    private static void refused(final Statement st, final String sql, final String message, final int code) {
        try {
            st.execute(sql);
            fail("ran: " + sql);
        } catch (final SQLException e) {
            assertEquals(message, e.getMessage());
            assertEquals("57014", e.getSQLState());
            assertEquals(code, e.getErrorCode());
        }
    }

    private static String one(final Statement st, final String sql) throws SQLException {
        final ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    public void theSessionsTimeoutEndsAStatement() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 1");
            refused(statement, "SELECT SYSTEM$WAIT(3)", TIMED_OUT, 630);
            refused(statement, "SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 4))", TIMED_OUT, 630);
            refused(statement, "EXECUTE IMMEDIATE $$BEGIN CALL SYSTEM$WAIT(3); RETURN 1; END;$$", TIMED_OUT, 630);
            statement.execute("CREATE OR REPLACE TABLE tw (v INT)");
            statement.execute("BEGIN");
            statement.execute("INSERT INTO tw VALUES (1)");
            refused(statement, "SELECT SYSTEM$WAIT(3)", TIMED_OUT, 630);
            assertEquals("1", one(statement, "SELECT COUNT(*) FROM tw"));
            statement.execute("ROLLBACK");
            assertEquals("0", one(statement, "SELECT COUNT(*) FROM tw"));
            statement.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 0");
            assertEquals("waited 1 seconds", one(statement, "SELECT SYSTEM$WAIT(1)"));
        } finally {
            statement.execute("ROLLBACK");
            statement.execute("ALTER SESSION UNSET STATEMENT_TIMEOUT_IN_SECONDS");
            statement.execute("DROP TABLE IF EXISTS tw");
        }
    }

    @Test
    public void cancelQueryNamesNoRunningStatement() throws SQLException {
        assertEquals("Identified SQL statement is not currently executing.",
            one(statement, "SELECT SYSTEM$CANCEL_QUERY('01b2c3d4-0000-0000-0000-000000000000')"));
        one(statement, "SELECT 1");
        assertEquals("Identified SQL statement is not currently executing.",
            one(statement, "SELECT SYSTEM$CANCEL_QUERY(LAST_QUERY_ID())"));
        assertEquals("invalid UUID", one(statement, "SELECT SYSTEM$CANCEL_QUERY('not a query id')"));
    }

    @Test
    public void theDriverCancelsAStatement() throws Exception {
        final Statement timed = connection.createStatement();
        try {
            timed.setQueryTimeout(1);
            refused(timed, "SELECT SYSTEM$WAIT(3)", "SQL execution canceled", 604);
            timed.setQueryTimeout(0);
            assertEquals("2", one(timed, "SELECT 2"));
        } finally {
            timed.close();
        }
        final Statement canceled = connection.createStatement();
        try {
            final Thread canceller = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1500);
                        canceled.cancel();
                    } catch (final InterruptedException | SQLException ignored) {
                        // the assertion below reports a statement that ran to its end
                    }
                }
            });
            canceller.start();
            refused(canceled, "SELECT SYSTEM$WAIT(4)", "SQL execution canceled", 604);
            canceller.join(10000);
            assertEquals("3", one(canceled, "SELECT 3"));
        } finally {
            canceled.close();
        }
    }
}
