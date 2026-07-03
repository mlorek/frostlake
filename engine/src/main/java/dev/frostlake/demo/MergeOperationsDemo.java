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

/**
 * Demonstration of MERGE (UPSERT) operations
 */
public class MergeOperationsDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new MergeOperationsDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "MERGE Operations Demo: INSERT + UPDATE in One Statement";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("warehouse_db");

            // ==================== BASIC MERGE ====================
            printSectionHeader("BASIC MERGE OPERATIONS");

            printSubsection("1. Setup: Initial Product Inventory");
            engine.execute(
                "CREATE TABLE products (" +
                "id INTEGER, " +
                "name VARCHAR, " +
                "price INTEGER, " +
                "stock INTEGER, " +
                "last_updated VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO products VALUES " +
                "(1, 'Widget', 100, 50, '2024-01-01'), " +
                "(2, 'Gadget', 200, 30, '2024-01-01'), " +
                "(3, 'Doohickey', 150, 40, '2024-01-01')"
            );
            System.out.println("Initial inventory:");
            printTable("products");

            printSubsection("2. MERGE - Update Existing Product");
            System.out.println("Merging price update for Widget (id=1)...");
            engine.execute(
                "MERGE INTO products USING (VALUES (1, 'Widget', 120, 55, '2024-02-01')) " +
                "ON id = 1 " +
                "WHEN MATCHED THEN UPDATE SET price = 120, stock = 55, last_updated = '2024-02-01'"
            );
            System.out.println("\nAfter merge:");
            printTable("products");

            printSubsection("3. MERGE - Insert New Product");
            System.out.println("Merging new product Thingamajig (id=4)...");
            engine.execute(
                "MERGE INTO products USING (VALUES (4, 'Thingamajig', 175, 25, '2024-02-01')) " +
                "ON id = 4 " +
                "WHEN NOT MATCHED THEN INSERT VALUES (4, 'Thingamajig', 175, 25, '2024-02-01')"
            );
            System.out.println("\nAfter merge:");
            printTable("products");

            printSubsection("4. MERGE - Combined Update and Insert");
            System.out.println("Processing multiple changes at once...");
            engine.execute(
                "MERGE INTO products USING (VALUES (2, 'Gadget', 220, 35, '2024-02-15')) " +
                "ON id = 2 " +
                "WHEN MATCHED THEN UPDATE SET price = 220, stock = 35, last_updated = '2024-02-15' " +
                "WHEN NOT MATCHED THEN INSERT VALUES (2, 'Gadget', 220, 35, '2024-02-15')"
            );
            System.out.println("\nAfter merge:");
            printTable("products");

            // ==================== TABLE-TO-TABLE MERGE ====================
            printSectionHeader("TABLE-TO-TABLE MERGE (UPSERT)");

            printSubsection("5. Bulk Updates from Staging Table");

            // Create staging table with updates
            engine.execute(
                "CREATE TABLE product_updates (" +
                "id INTEGER, " +
                "name VARCHAR, " +
                "price INTEGER, " +
                "stock INTEGER, " +
                "last_updated VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO product_updates VALUES " +
                "(1, 'Widget', 125, 60, '2024-03-01'), " +       // Update existing
                "(3, 'Doohickey', 155, 45, '2024-03-01'), " +    // Update existing
                "(5, 'Gizmo', 180, 20, '2024-03-01')"            // Insert new
            );

            System.out.println("Staging table (product_updates):");
            printTable("product_updates");

            System.out.println("\nExecuting MERGE from staging table...");
            engine.execute(
                "MERGE INTO products USING product_updates " +
                "ON products.id = product_updates.id " +
                "WHEN MATCHED THEN UPDATE SET price = 125, stock = 60, last_updated = '2024-03-01' " +
                "WHEN NOT MATCHED THEN INSERT VALUES (5, 'Gizmo', 180, 20, '2024-03-01')"
            );

            System.out.println("\nAfter bulk merge:");
            printTable("products");

            // ==================== REAL-WORLD SCENARIOS ====================
            printSectionHeader("REAL-WORLD MERGE SCENARIOS");

            printSubsection("6. Customer Dimension Table Update");
            engine.execute(
                "CREATE TABLE customers (" +
                "customer_id INTEGER, " +
                "name VARCHAR, " +
                "email VARCHAR, " +
                "status VARCHAR, " +
                "last_modified VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO customers VALUES " +
                "(101, 'Alice Johnson', 'alice@example.com', 'active', '2024-01-15'), " +
                "(102, 'Bob Smith', 'bob@example.com', 'active', '2024-01-15')"
            );

            System.out.println("Current customers:");
            printTable("customers");

            // Create daily updates
            engine.execute(
                "CREATE TABLE customer_changes (" +
                "customer_id INTEGER, " +
                "name VARCHAR, " +
                "email VARCHAR, " +
                "status VARCHAR, " +
                "last_modified VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO customer_changes VALUES " +
                "(102, 'Bob Smith', 'bob.smith@example.com', 'inactive', '2024-03-15'), " +  // Email change + status
                "(103, 'Charlie Brown', 'charlie@example.com', 'active', '2024-03-15')"       // New customer
            );

            System.out.println("\nDaily changes:");
            printTable("customer_changes");

            System.out.println("\nApplying customer updates via MERGE...");
            engine.execute(
                "MERGE INTO customers USING customer_changes " +
                "ON customers.customer_id = customer_changes.customer_id " +
                "WHEN MATCHED THEN UPDATE SET email = 'bob.smith@example.com', status = 'inactive', last_modified = '2024-03-15' " +
                "WHEN NOT MATCHED THEN INSERT VALUES (103, 'Charlie Brown', 'charlie@example.com', 'active', '2024-03-15')"
            );

            System.out.println("\nUpdated customer table:");
            printTable("customers");

            printSubsection("7. Daily Sales Aggregation");
            engine.execute(
                "CREATE TABLE sales_summary (" +
                "date VARCHAR, " +
                "product VARCHAR, " +
                "total_amount INTEGER, " +
                "transaction_count INTEGER" +
                ")"
            );
            engine.execute(
                "INSERT INTO sales_summary VALUES " +
                "('2024-03-01', 'Widget', 5000, 50), " +
                "('2024-03-01', 'Gadget', 8000, 40)"
            );

            System.out.println("Current sales summary:");
            printTable("sales_summary");

            // New day's transactions (aggregated)
            engine.execute(
                "CREATE TABLE daily_sales (" +
                "date VARCHAR, " +
                "product VARCHAR, " +
                "total_amount INTEGER, " +
                "transaction_count INTEGER" +
                ")"
            );
            engine.execute(
                "INSERT INTO daily_sales VALUES " +
                "('2024-03-01', 'Widget', 5500, 55), " +          // Correction for existing day
                "('2024-03-02', 'Widget', 6000, 60), " +          // New day
                "('2024-03-02', 'Gadget', 9000, 45)"              // New day
            );

            System.out.println("\nNew daily sales:");
            printTable("daily_sales");

            System.out.println("\nMerging sales data...");
            engine.execute(
                "MERGE INTO sales_summary USING daily_sales " +
                "ON sales_summary.date = daily_sales.date AND sales_summary.product = daily_sales.product " +
                "WHEN MATCHED THEN UPDATE SET total_amount = 5500, transaction_count = 55 " +
                "WHEN NOT MATCHED THEN INSERT VALUES ('2024-03-02', 'Widget', 6000, 60)"
            );

            System.out.println("\nUpdated sales summary:");
            printTable("sales_summary");

            printSubsection("8. Inventory Reconciliation");
            engine.execute(
                "CREATE TABLE warehouse_inventory (" +
                "sku INTEGER, " +
                "location VARCHAR, " +
                "quantity INTEGER, " +
                "last_count VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO warehouse_inventory VALUES " +
                "(1001, 'A1', 100, '2024-02-01'), " +
                "(1002, 'A2', 150, '2024-02-01'), " +
                "(1003, 'B1', 200, '2024-02-01')"
            );

            System.out.println("Current warehouse inventory:");
            printTable("warehouse_inventory");

            // Physical count results
            engine.execute(
                "CREATE TABLE physical_count (" +
                "sku INTEGER, " +
                "location VARCHAR, " +
                "quantity INTEGER, " +
                "last_count VARCHAR" +
                ")"
            );
            engine.execute(
                "INSERT INTO physical_count VALUES " +
                "(1001, 'A1', 95, '2024-03-15'), " +     // Shrinkage detected
                "(1002, 'A2', 150, '2024-03-15'), " +    // Matches
                "(1004, 'B2', 50, '2024-03-15')"         // New item found
            );

            System.out.println("\nPhysical count results:");
            printTable("physical_count");

            System.out.println("\nReconciling inventory...");
            engine.execute(
                "MERGE INTO warehouse_inventory USING physical_count " +
                "ON warehouse_inventory.sku = physical_count.sku " +
                "WHEN MATCHED THEN UPDATE SET quantity = 95, last_count = '2024-03-15' " +
                "WHEN NOT MATCHED THEN INSERT VALUES (1004, 'B2', 50, '2024-03-15')"
            );

            System.out.println("\nReconciled inventory:");
            printTable("warehouse_inventory");

            // ==================== MERGE STATISTICS ====================
            printSectionHeader("MERGE BENEFITS SUMMARY");

            System.out.println("MERGE Statement Benefits:");
            System.out.println("  ✓ Combines INSERT, UPDATE in single statement");
            System.out.println("  ✓ Atomic operation - all or nothing");
            System.out.println("  ✓ Efficient for bulk data synchronization");
            System.out.println("  ✓ Commonly used for:");
            System.out.println("    - Data warehouse ETL processes");
            System.out.println("    - Slowly Changing Dimensions (SCD Type 1)");
            System.out.println("    - Incremental data loading");
            System.out.println("    - Real-time data synchronization");
            System.out.println("    - Inventory reconciliation");
            System.out.println("    - Customer data deduplication\n");
    }
}
