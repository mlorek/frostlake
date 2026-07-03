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

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for dollar-quoted string literals in TASK definitions
 */
public class TaskDollarQuotedTest extends BaseJdbcTest {

    @Test
    public void testCreateTaskWithDollarQuotedBody() throws SQLException {
        // Create task using dollar-quoted string for SQL body
        statement.execute("""
                CREATE TASK daily_summary
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 9 * * * UTC'
                AS $$INSERT INTO summary_table SELECT date, COUNT(*) as count, AVG(value) as avg_value FROM transactions WHERE status = 'completed' GROUP BY date$$
                """);

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("daily_summary", rs.getString("name"));
        rs.close();
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

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("simple_task", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testCreateTaskWithSingleQuotedBody() throws SQLException {
        // Create task using single-quoted string
        statement.execute("""
                CREATE TASK quoted_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 12 * * * UTC'
                AS 'DELETE FROM temp_data WHERE created_at < DATEADD(day, -7, CURRENT_DATE())'
                """);

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("quoted_task", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotedTaskWithComplexSQL() throws SQLException {
        // Test complex SQL with multiple statements and comments
        statement.execute("""
                CREATE TASK complex_etl
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 2 * * * UTC'
                AS $$
                -- Load data from staging
                INSERT INTO fact_sales
                SELECT\s
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

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("complex_etl", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotedTaskWithSingleQuotesInSQL() throws SQLException {
        // Dollar quotes make it easy to include single quotes without escaping
        statement.execute("""
                CREATE TASK filter_task
                WAREHOUSE = 'compute_wh'
                SCHEDULE = 'USING CRON 0 6 * * * UTC'
                AS $$UPDATE products SET status = 'archived' WHERE category = 'obsolete' AND last_sold < DATEADD(year, -2, CURRENT_DATE())$$
                """);

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("filter_task", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotedTaskWithMultilineFormatting() throws SQLException {
        // Test nicely formatted multi-line SQL
        statement.execute("""
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

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("weekly_report", rs.getString("name"));
        rs.close();
    }

    @Test
    public void testDollarQuotedTaskWithJSONFunctions() throws SQLException {
        // Test task with JSON/OBJECT_CONSTRUCT
        statement.execute("""
                CREATE TASK json_export
                WAREHOUSE = 'compute_wh'
                SCHEDULE = '60 MINUTES'
                AS $$INSERT INTO json_exports SELECT id, OBJECT_CONSTRUCT('name', name, 'email', email, 'status', status, 'created', created_at) as user_json FROM users WHERE modified_at >= DATEADD(hour, -1, CURRENT_TIMESTAMP())$$
                """);

        // Verify task was created
        ResultSet rs = statement.executeQuery("SHOW TASKS");
        assertTrue(rs.next());
        assertEquals("json_export", rs.getString("name"));
        rs.close();
    }
}
