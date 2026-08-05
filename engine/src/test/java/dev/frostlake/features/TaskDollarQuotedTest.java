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

package dev.frostlake.features;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE TASK body forms. Live-verified: the body after {@code AS} must be a BARE statement (or CALL) —
 * a dollar-quoted ({@code AS $$...$$}) or string-quoted ({@code AS '...'}) body is a syntax error.
 * Each rejected quoted form is paired with its bare-body equivalent, which creates successfully and
 * shows up in SHOW TASKS.
 */
public class TaskDollarQuotedTest extends BaseJdbcTest {

    private void assertTaskRejected(final String sql) {
        final SQLException e = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(sql);
            }
        });
        assertTrue(String.valueOf(e.getMessage()).contains("syntax"),
            "expected a syntax error, got: " + e.getMessage());
    }

    private void assertTaskShown(final String taskName) throws SQLException {
        final ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals(taskName, rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotedTaskBodyIsRejected() throws SQLException {
        // A dollar-quoted string body is a syntax error
        assertTaskRejected("""
                CREATE TASK daily_summary
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 9 * * * UTC'
                AS $$INSERT INTO summary_table SELECT date, COUNT(*) as count, AVG(value) as avg_value FROM transactions WHERE status = 'completed' GROUP BY date$$
                """);

        // The equivalent bare statement body works
        statement.execute("""
                CREATE TASK daily_summary
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 9 * * * UTC'
                AS INSERT INTO summary_table SELECT date, COUNT(*) as count, AVG(value) as avg_value FROM transactions WHERE status = 'completed' GROUP BY date
                """);
        assertTaskShown("DAILY_SUMMARY");
    }

    @Test
    public void testCreateTaskWithTraditionalBody() throws SQLException {
        // Verify traditional syntax still works (raw SQL)
        statement.execute("""
                CREATE TASK simple_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 * * * * UTC'
                AS INSERT INTO logs VALUES (CURRENT_TIMESTAMP())
                """);
        assertTaskShown("SIMPLE_TASK");
    }

    @Test
    public void testSingleQuotedTaskBodyIsRejected() throws SQLException {
        // A single-quoted string body is a syntax error
        assertTaskRejected("""
                CREATE TASK quoted_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 12 * * * UTC'
                AS 'DELETE FROM temp_data WHERE created_at < DATEADD(day, -7, CURRENT_DATE())'
                """);

        // The equivalent bare statement body works
        statement.execute("""
                CREATE TASK quoted_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 12 * * * UTC'
                AS DELETE FROM temp_data WHERE created_at < DATEADD(day, -7, CURRENT_DATE())
                """);
        assertTaskShown("QUOTED_TASK");
    }

    @Test
    public void testDollarQuotedComplexSqlBodyIsRejected() throws SQLException {
        // Complex multi-line SQL with comments: still rejected when dollar-quoted
        assertTaskRejected("""
                CREATE TASK complex_etl
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 2 * * * UTC'
                AS $$
                -- Load data from staging
                INSERT INTO fact_sales
                SELECT
                  s.id,
                  s.amount,
                  s.date,
                  c.region
                FROM staging_sales s
                JOIN customers c ON s.customer_id = c.id
                WHERE s.status = 'verified'
                  AND s.date >= CURRENT_DATE() - 1
                $$
                """);

        // The same SQL as a bare body works, comment line included
        statement.execute("""
                CREATE TASK complex_etl
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 2 * * * UTC'
                AS
                -- Load data from staging
                INSERT INTO fact_sales
                SELECT
                  s.id,
                  s.amount,
                  s.date,
                  c.region
                FROM staging_sales s
                JOIN customers c ON s.customer_id = c.id
                WHERE s.status = 'verified'
                  AND s.date >= CURRENT_DATE() - 1
                """);
        assertTaskShown("COMPLEX_ETL");
    }

    @Test
    public void testDollarQuotedBodyWithSingleQuotesIsRejected() throws SQLException {
        // Dollar quotes around a body containing single quotes: rejected all the same
        assertTaskRejected("""
                CREATE TASK filter_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 6 * * * UTC'
                AS $$UPDATE products SET status = 'archived' WHERE category = 'obsolete' AND last_sold < DATEADD(year, -2, CURRENT_DATE())$$
                """);

        // Bare body: the string literals inside need no special treatment
        statement.execute("""
                CREATE TASK filter_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 6 * * * UTC'
                AS UPDATE products SET status = 'archived' WHERE category = 'obsolete' AND last_sold < DATEADD(year, -2, CURRENT_DATE())
                """);
        assertTaskShown("FILTER_TASK");
    }

    @Test
    public void testDollarQuotedMultilineBodyIsRejected() throws SQLException {
        // Nicely formatted multi-line SQL: rejected when dollar-quoted
        assertTaskRejected("""
                CREATE TASK weekly_report
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 0 * * 0 UTC'
                AS $$
                INSERT INTO weekly_summary (
                  week_start,
                  total_orders,
                  total_revenue,
                  avg_order_value
                )
                SELECT
                  DATE_TRUNC('week', order_date) as week_start,
                  COUNT(*) as total_orders,
                  SUM(amount) as total_revenue,
                  AVG(amount) as avg_order_value
                FROM orders
                WHERE order_date >= DATEADD(week, -1, CURRENT_DATE())
                GROUP BY DATE_TRUNC('week', order_date)
                $$
                """);

        // The same formatting works as a bare body
        statement.execute("""
                CREATE TASK weekly_report
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 0 * * 0 UTC'
                AS
                INSERT INTO weekly_summary (
                  week_start,
                  total_orders,
                  total_revenue,
                  avg_order_value
                )
                SELECT
                  DATE_TRUNC('week', order_date) as week_start,
                  COUNT(*) as total_orders,
                  SUM(amount) as total_revenue,
                  AVG(amount) as avg_order_value
                FROM orders
                WHERE order_date >= DATEADD(week, -1, CURRENT_DATE())
                GROUP BY DATE_TRUNC('week', order_date)
                """);
        assertTaskShown("WEEKLY_REPORT");
    }

    @Test
    public void testDollarQuotedJsonBodyIsRejected() throws SQLException {
        // Task with JSON/OBJECT_CONSTRUCT: rejected when dollar-quoted
        assertTaskRejected("""
                CREATE TASK json_export
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS $$INSERT INTO json_exports SELECT id, OBJECT_CONSTRUCT('name', name, 'email', email, 'status', status, 'created', created_at) as user_json FROM users WHERE modified_at >= DATEADD(hour, -1, CURRENT_TIMESTAMP())$$
                """);

        // Bare body equivalent works
        statement.execute("""
                CREATE TASK json_export
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS INSERT INTO json_exports SELECT id, OBJECT_CONSTRUCT('name', name, 'email', email, 'status', status, 'created', created_at) as user_json FROM users WHERE modified_at >= DATEADD(hour, -1, CURRENT_TIMESTAMP())
                """);
        assertTaskShown("JSON_EXPORT");
    }
}
