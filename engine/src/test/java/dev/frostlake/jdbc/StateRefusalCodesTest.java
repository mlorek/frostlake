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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The refusals of a running object's state, an overflowing sum and a property's value carry the account driver's
 * code and state (live-verified): a non-root task's own retry parameter is 91449 / 55000, a graph changed under a
 * started root 91421 / 22000, a triggered serverless task run without its completion interval 91865 / 55000, an
 * alert's CONFIG that spells no JSON object 91461 / 42601, a change to a started alert 392005 / 55000, a SUM's
 * overflow 100058 / 22000, an ordered window's 100045 / 22003 and a retention past 90 days 1048 / 22023. They read
 * 0 and no state.
 */
public class StateRefusalCodesTest extends BaseJdbcTest {

    /** The code, the state and the sentence a statement is refused with. */
    private String refusal(final String sql) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        return refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage();
    }

    @Test
    public void aTaskGraphsState() throws SQLException {
        statement.execute("CREATE TASK sc_r SCHEDULE = '60 MINUTE' AS SELECT 1");
        statement.execute("CREATE TASK sc_c AFTER sc_r AS SELECT 1");
        assertEquals("91449|55000|Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task SC_C. Non-root tasks "
            + "use the parameter setting from their root task. Please set this on the root task for this task instead.",
            refusal("ALTER TASK sc_c SET TASK_AUTO_RETRY_ATTEMPTS = 2"));
        statement.execute("ALTER TASK sc_r RESUME");
        try {
            assertEquals("91421|22000|Unable to update graph with root task TEST_DB.PUBLIC.SC_R since that root task "
                + "is not suspended.", refusal("CREATE TASK sc_c2 AFTER sc_r AS SELECT 1"));
        } finally {
            statement.execute("ALTER TASK sc_r SUSPEND");
        }
        statement.execute("CREATE TABLE sc_src (id INT)");
        statement.execute("CREATE STREAM sc_st ON TABLE sc_src");
        statement.execute("CREATE TASK sc_t WHEN SYSTEM$STREAM_HAS_DATA('sc_st') AS SELECT 1");
        assertEquals("91865|55000|TARGET_COMPLETION_INTERVAL must be set for serverless triggered task "
            + "TEST_DB.PUBLIC.SC_T to resume or execute.", refusal("EXECUTE TASK sc_t"));
    }

    @Test
    public void anAlertsConfigAndState() throws SQLException {
        assertEquals("91461|42601|Invalid config. Must be a string representation of a valid JSON Object.",
            refusal("CREATE ALERT sc_a SCHEDULE = '60 MINUTE' CONFIG = 'x' IF (EXISTS (SELECT 1)) THEN SELECT 1"));
        statement.execute("CREATE ALERT sc_a SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        statement.execute("ALTER ALERT sc_a RESUME");
        try {
            assertEquals("392005|55000|Unable to alter property for alert SC_A since it is not suspended.",
                refusal("ALTER ALERT sc_a SET COMMENT = 'c'"));
        } finally {
            statement.execute("ALTER ALERT sc_a SUSPEND");
        }
    }

    @Test
    public void anOverflowingSumAndARetention() throws SQLException {
        statement.execute("CREATE TABLE sc_v (o INT, x NUMBER(38,0))");
        statement.execute("INSERT INTO sc_v VALUES (1, 10000000000000000000), (2, -10000000000000000000)");
        assertEquals("100058|22000|Value overflow in a SUM aggregate", refusal("SELECT VARIANCE(x) FROM sc_v"));
        assertEquals("100045|22003|Numeric value is out of range, error: Window function intermediate result too large",
            refusal("SELECT o, VARIANCE(x) OVER (ORDER BY o) FROM sc_v"));
        assertEquals("1048|22023|SQL compilation error:\nExceeds maximum allowable retention time (90 day(s)).",
            refusal("CREATE TEMPORARY TABLE sc_tt (a INT) DATA_RETENTION_TIME_IN_DAYS = 91"));
    }
}
