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

/**
 * What a driver's query timeout and its cancel() do to a request of several statements (live-verified): they end it
 * whole, while a later statement of it runs, with "SQL execution canceled", code 604 and SQLSTATE 57014, as they end a
 * statement alone - the request answers no multi-statement failure.
 */
public class RequestCancelTest extends BaseJdbcTest {

    private static final String CANCELED = "604 57014 SQL execution canceled";

    /** Every answer of a request of several statements, or how it failed. */
    private static String walk(final Statement st, final String sql) {
        final StringBuilder out = new StringBuilder();
        try {
            boolean hasRows = st.execute(sql);
            while (true) {
                if (hasRows) {
                    try (ResultSet rs = st.getResultSet()) {
                        out.append('[').append(rs.next() ? rs.getString(1) : "-").append(']');
                    }
                } else if (st.getUpdateCount() == -1) {
                    return out.toString();
                }
                hasRows = st.getMoreResults();
            }
        } catch (final SQLException e) {
            return e.getErrorCode() + " " + e.getSQLState() + " " + e.getMessage();
        }
    }

    @Test
    public void aQueryTimeoutEndsAWholeRequest() throws Exception {
        final Statement timed = connection.createStatement();
        try {
            statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
            timed.setQueryTimeout(5);
            assertEquals(CANCELED, walk(timed, "SELECT SYSTEM$WAIT(3); SELECT SYSTEM$WAIT(3); SELECT 'done'"));
        } finally {
            timed.close();
            statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    @Test
    public void aCancelEndsAWholeRequest() throws Exception {
        final Statement canceled = connection.createStatement();
        try {
            statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
            final String[] answered = new String[1];
            final Thread runner = new Thread(new Runnable() {
                @Override
                public void run() {
                    answered[0] = walk(canceled, "SELECT SYSTEM$WAIT(2); SELECT SYSTEM$WAIT(8); SELECT 'done'");
                }
            });
            runner.start();
            Thread.sleep(4000L);
            canceled.cancel();
            runner.join(30000L);
            assertEquals(CANCELED, answered[0]);
        } finally {
            canceled.close();
            statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }
}
