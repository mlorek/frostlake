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

package dev.frostlake.demo;

import dev.frostlake.metastore.model.*;
import dev.frostlake.storage.ResultSet;

/**
 * Demonstration of STREAMS and TASKS - Change Data Capture and Scheduled Execution
 */
public class StreamsAndTasksDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new StreamsAndTasksDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "STREAMS and TASKS Demo: CDC and Automated Processing";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("analytics_db");

            // ==================== STREAMS - Change Data Capture ====================
            printSectionHeader("STREAMS - Change Data Capture (CDC)");

            printSubsection("1. Create Table and Stream");
            engine.execute(
                "CREATE TABLE customers (" +
                "customer_id INTEGER, " +
                "name VARCHAR, " +
                "email VARCHAR, " +
                "status VARCHAR, " +
                "balance INTEGER" +
                ")"
            );

            engine.execute("CREATE STREAM customer_changes ON TABLE customers");
            printSuccess("Created table 'customers' and stream 'customer_changes'\n");

            printSubsection("2. Initial Data Load");
            engine.execute(
                "INSERT INTO customers VALUES " +
                "(1, 'Alice Johnson', 'alice@example.com', 'active', 1000), " +
                "(2, 'Bob Smith', 'bob@example.com', 'active', 1500), " +
                "(3, 'Charlie Brown', 'charlie@example.com', 'active', 2000)"
            );
            printSuccess("Inserted 3 customers\n");

            // Check stream
            Schema schema = engine.getCatalog().getDatabase("analytics_db").getSchema("PUBLIC");
            Stream stream = schema.getStream("customer_changes");

            System.out.println("Stream status:");
            System.out.println("  Unconsumed records: " + stream.getUnconsumedCount());
            System.out.println("  Recent changes:");
            for (final StreamRecord record : stream.getUnconsumedRecords()) {
                System.out.println("    - " + record.getChangeType() + ": " + record.getValues());
            }

            printSubsection("3. Make Changes to Track");
            System.out.println("Updating Bob's balance and status...");
            engine.execute("UPDATE customers SET balance = 1800, status = 'premium' WHERE customer_id = 2");

            System.out.println("Deleting Charlie's account...");
            engine.execute("DELETE FROM customers WHERE customer_id = 3");

            System.out.println("\nStream now contains:");
            System.out.println("  Unconsumed records: " + stream.getUnconsumedCount());
            System.out.println("  Change types:");
            for (final StreamRecord record : stream.getUnconsumedRecords()) {
                String type = record.getChangeType().toString();
                String update = record.isUpdate() ? " (from UPDATE)" : "";
                System.out.println("    - " + type + update);
            }

            printSubsection("4. Append-Only Stream");
            engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, amount INTEGER)");
            engine.execute("CREATE STREAM order_stream ON TABLE orders APPEND_ONLY = TRUE");
            printSuccess("Created append-only stream on orders table");

            engine.execute("INSERT INTO orders VALUES (1, 1, 500), (2, 2, 750)");
            engine.execute("UPDATE orders SET amount = 800 WHERE order_id = 2");
            engine.execute("DELETE FROM orders WHERE order_id = 1");

            Stream orderStream = schema.getStream("order_stream");
            System.out.println("\nAppend-only stream status:");
            System.out.println("  Unconsumed records: " + orderStream.getUnconsumedCount());
            System.out.println("  (Only tracks INSERTs, ignores UPDATEs and DELETEs)");

            // ==================== TASKS - Scheduled Execution ====================
            printSectionHeader("TASKS - Scheduled SQL Execution");

            printSubsection("5. Create Task with Schedule");
            engine.execute("CREATE TABLE daily_summary (date VARCHAR, total_customers INTEGER, total_balance INTEGER)");

            engine.execute(
                "CREATE TASK daily_summary_task " +
                "WAREHOUSE = 'COMPUTE_WH' " +
                "SCHEDULE = '60 MINUTES' " +
                "AS INSERT INTO daily_summary SELECT '2024-01-01', COUNT(*), SUM(balance) FROM customers"
            );
            printSuccess("Created task 'daily_summary_task' (runs every 60 minutes)");

            Task task = schema.getTask("daily_summary_task");
            System.out.println("\nTask details:");
            System.out.println("  Name: " + task.getName());
            System.out.println("  Schedule: " + task.getSchedule());
            System.out.println("  State: " + task.getState());
            System.out.println("  SQL: " + task.getSqlStatement());

            printSubsection("6. Resume Task (Start Execution)");
            engine.execute("ALTER TASK daily_summary_task RESUME");
            printSuccess("Task resumed and will run on schedule");
            System.out.println("  State: " + task.getState());

            printSubsection("7. Manual Task Execution (For Demo)");
            String qualifiedName = "analytics_db.PUBLIC.daily_summary_task";
            engine.getTaskScheduler().executeTaskNow(qualifiedName, task);
            printSuccess("Task executed manually");

            // Check results
            ResultSet summary = engine.executeQuery("SELECT * FROM daily_summary");
            System.out.println("\nTask execution results:");
            printResultSet(summary);

            System.out.println("\nTask execution history:");
            System.out.println("  Total executions: " + task.getExecutionHistory().size());
            System.out.println("  Successful: " + task.getSuccessCount());
            System.out.println("  Failed: " + task.getFailureCount());

            if (!task.getExecutionHistory().isEmpty()) {
                TaskExecution lastExecution = task.getExecutionHistory().get(0);
                System.out.println("  Last execution:");
                System.out.println("    State: " + lastExecution.getState());
                System.out.println("    Rows affected: " + lastExecution.getRowsAffected());
            }

            printSubsection("8. Suspend Task");
            engine.execute("ALTER TASK daily_summary_task SUSPEND");
            printSuccess("Task suspended (won't run on schedule)");
            System.out.println("  State: " + task.getState());

            // ==================== REAL-WORLD SCENARIO ====================
            printSectionHeader("REAL-WORLD: Stream + Task Integration");

            printSubsection("9. CDC Pipeline: Capture Changes & Process");
            System.out.println("Scenario: Track product inventory changes and update warehouse summary\n");

            // Create tables
            engine.execute(
                "CREATE TABLE inventory (" +
                "product_id INTEGER, " +
                "product_name VARCHAR, " +
                "quantity INTEGER, " +
                "last_updated VARCHAR" +
                ")"
            );

            engine.execute(
                "CREATE TABLE inventory_audit (" +
                "audit_id INTEGER, " +
                "change_type VARCHAR, " +
                "product_id INTEGER, " +
                "old_quantity INTEGER, " +
                "new_quantity INTEGER, " +
                "timestamp VARCHAR" +
                ")"
            );

            // Create stream to track changes
            engine.execute("CREATE STREAM inventory_stream ON TABLE inventory");
            printSuccess("Created inventory tracking stream");

            // Load initial data
            engine.execute(
                "INSERT INTO inventory VALUES " +
                "(1, 'Widget', 100, '2024-01-01'), " +
                "(2, 'Gadget', 50, '2024-01-01')"
            );
            printSuccess("Loaded initial inventory");

            // Create task to process stream changes
            engine.execute(
                "CREATE TASK process_inventory_changes " +
                "WAREHOUSE = 'COMPUTE_WH' " +
                "SCHEDULE = '5 MINUTES' " +
                "AS INSERT INTO inventory_audit SELECT 1, 'INSERT', 1, 0, 100, '2024-01-01'"
            );
            printSuccess("Created task to process inventory changes");

            Task inventoryTask = schema.getTask("process_inventory_changes");
            System.out.println("\nTask configuration:");
            System.out.println("  Schedule: Every 5 minutes");
            System.out.println("  Purpose: Process stream changes and write to audit table");

            // Make changes
            System.out.println("\nMaking inventory changes...");
            engine.execute("UPDATE inventory SET quantity = 120 WHERE product_id = 1");
            engine.execute("INSERT INTO inventory VALUES (3, 'Doohickey', 75, '2024-01-02')");

            Stream inventoryStream = schema.getStream("inventory_stream");
            System.out.println("\nStream captured " + inventoryStream.getUnconsumedCount() + " change records:");
            for (final StreamRecord record : inventoryStream.getUnconsumedRecords()) {
                System.out.println("  - " + record.getChangeType() +
                    (record.isUpdate() ? " (UPDATE)" : ""));
            }

            printSubsection("10. Stream Consumption");
            System.out.println("Before consumption: " + inventoryStream.getUnconsumedCount() + " records");

            // In real scenario, task would consume the stream
            inventoryStream.consume();

            System.out.println("After consumption: " + inventoryStream.getUnconsumedCount() + " records");
            printSuccess("Stream offset advanced (changes marked as processed)");

            // ==================== BENEFITS SUMMARY ====================
            printSectionHeader("STREAMS & TASKS BENEFITS");

            System.out.println("STREAMS Benefits:");
            System.out.println("  ✓ Automatic change tracking (CDC)");
            System.out.println("  ✓ Captures INSERT, UPDATE, DELETE operations");
            System.out.println("  ✓ Append-only mode for insert-only tracking");
            System.out.println("  ✓ Offset-based consumption (process once)");
            System.out.println("  ✓ Enables incremental data processing\n");

            System.out.println("TASKS Benefits:");
            System.out.println("  ✓ Schedule SQL execution (CRON or interval)");
            System.out.println("  ✓ Automate data pipelines");
            System.out.println("  ✓ Built-in execution history");
            System.out.println("  ✓ RESUME/SUSPEND control");
            System.out.println("  ✓ No external orchestration needed\n");

            System.out.println("COMBINED Use Cases:");
            System.out.println("  • Real-time data warehousing (CDC → staging → DW)");
            System.out.println("  • Audit logging and compliance");
            System.out.println("  • Incremental ETL pipelines");
            System.out.println("  • Data replication and synchronization");
            System.out.println("  • Event-driven processing");
            System.out.println("  • Automated data quality checks");
    }
}
