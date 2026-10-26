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

package dev.frostlake.udf;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A driver's query timeout and its cancel() end a statement whatever its handler does meanwhile: a Python function
 * that sleeps and a JavaScript function that spins fail with "SQL execution canceled", SQLSTATE 57014, code 604, as
 * SYSTEM$WAIT does, and the statement object runs the next statement as usual.
 */
public class GuestDriverTimeoutTest extends BaseJdbcTest {

    private static final Logger logger = LoggerFactory.getLogger(GuestDriverTimeoutTest.class);

    private static final String CANCELED = "SQL execution canceled";

    private static final String SLEEP = """
        CREATE OR REPLACE FUNCTION gd_sleep(n INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.12'
        HANDLER = 'h' AS $$
        import time
        def h(n):
            time.sleep(n)
            return n
        $$""";

    private static final String SPIN = """
        CREATE OR REPLACE FUNCTION gd_spin(n FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$
        var e = Date.now() + N * 1000; while (Date.now() < e) {} return N;
        $$""";

    private static void refused(final Statement st, final String sql) {
        try {
            st.execute(sql);
            fail("ran: " + sql);
        } catch (final SQLException e) {
            assertEquals(CANCELED, e.getMessage());
            assertEquals("57014", e.getSQLState());
            assertEquals(604, e.getErrorCode());
        }
    }

    private static String one(final Statement st, final String sql) throws SQLException {
        final ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    public void theQueryTimeoutEndsAHandler() throws SQLException {
        statement.execute(SLEEP);
        statement.execute(SPIN);
        assertEquals("0", one(statement, "SELECT gd_sleep(0)"));
        final Statement timed = connection.createStatement();
        try {
            timed.setQueryTimeout(2);
            refused(timed, "SELECT gd_sleep(6)");
            refused(timed, "SELECT gd_spin(6)");
            timed.setQueryTimeout(0);
            assertEquals("1", one(timed, "SELECT gd_sleep(1)"));
        } finally {
            timed.close();
        }
    }

    @Test
    public void cancelEndsAHandler() throws Exception {
        statement.execute(SLEEP);
        assertEquals("0", one(statement, "SELECT gd_sleep(0)"));
        final Statement canceled = connection.createStatement();
        try {
            final Thread canceller = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                        canceled.cancel();
                    } catch (final InterruptedException | SQLException e) {
                        logger.debug("The cancel was not sent: {}", e.getMessage());
                    }
                }
            });
            canceller.start();
            refused(canceled, "SELECT gd_sleep(8)");
            canceller.join(10000);
            assertEquals("3", one(canceled, "SELECT 3"));
        } finally {
            canceled.close();
        }
    }
}
