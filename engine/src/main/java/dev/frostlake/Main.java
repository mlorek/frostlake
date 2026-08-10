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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

/**
 * Example usage of the Frostlake SQL Engine
 */
public final class Main {

    /** Static helpers only — never instantiated. */
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println("=== Frostlake SQL Engine Demo ===\n");

        // Create and initialize the engine
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            // Example 1: Create a database and schema
            System.out.println("1. Creating database and schema...");
            engine.execute("CREATE DATABASE sales_db");
            engine.execute("USE DATABASE sales_db");
            engine.execute("CREATE SCHEMA analytics");
            engine.execute("USE SCHEMA analytics");
            System.out.println("   ✓ Database and schema created\n");

            // Example 2: Create a table
            System.out.println("2. Creating customers table...");
            engine.execute(
                "CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name VARCHAR, " +
                "email VARCHAR, " +
                "age INTEGER, " +
                "created_date DATE" +
                ")"
            );
            System.out.println("   ✓ Table created\n");

            // Example 3: Insert data
            System.out.println("3. Inserting customer data...");
            engine.execute("INSERT INTO customers VALUES (1, 'John Doe', 'john@example.com', 30, '2024-01-15')");
            engine.execute("INSERT INTO customers VALUES (2, 'Jane Smith', 'jane@example.com', 28, '2024-01-16')");
            engine.execute("INSERT INTO customers VALUES (3, 'Bob Johnson', 'bob@example.com', 35, '2024-01-17')");
            System.out.println("   ✓ 3 rows inserted\n");

            // Example 4: Query data
            System.out.println("4. Querying customer data...");
            final ResultSet result = engine.executeQuery("SELECT * FROM customers");
            printResultSet(result);

            // Example 5: Create a view
            System.out.println("\n5. Creating a view...");
            engine.execute("CREATE VIEW young_customers AS SELECT * FROM customers");
            System.out.println("   ✓ View created\n");

            // Example 6: Show system information
            System.out.println("6. Showing databases...");
            final ResultSet databases = engine.showDatabases();
            printResultSet(databases);

            System.out.println("\n7. Showing tables...");
            final ResultSet tables = engine.showTables();
            printResultSet(tables);

            System.out.println("\n8. Showing columns for customers table...");
            final ResultSet columns = engine.showColumns("customers");
            printResultSet(columns);

            // Example 9: Transaction example
            System.out.println("\n9. Testing transactions...");
            engine.setAutoCommit(false);
            engine.beginTransaction();
            engine.execute("INSERT INTO customers VALUES (4, 'Alice Brown', 'alice@example.com', 32, '2024-01-18')");
            engine.commit();
            System.out.println("   ✓ Transaction committed\n");

            // Example 10: Create another database
            System.out.println("10. Creating inventory database...");
            engine.execute("CREATE DATABASE inventory_db");
            engine.execute("USE DATABASE inventory_db");
            engine.execute("USE SCHEMA PUBLIC");
            engine.execute("CREATE TABLE products (final id INTEGER, final name VARCHAR, final price DECIMAL)");
            engine.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99)");
            engine.execute("INSERT INTO products VALUES (2, 'Mouse', 29.99)");
            System.out.println("   ✓ Inventory database setup complete\n");

            final ResultSet products = engine.executeQuery("SELECT * FROM products");
            printResultSet(products);

            System.out.println("\n=== Demo Complete ===");

        } catch (final Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            engine.shutdown();
        }
    }

    private static void printResultSet(final ResultSet result) {
        if (result.getRowCount() == 0) {
            System.out.println("   (No rows)");
            return;
        }

        // Print column headers
        System.out.print("   ");
        for (final ResultSetColumn col : result.getColumns()) {
            System.out.printf("%-20s", col.getName());
        }
        System.out.println();

        // Print separator
        System.out.print("   ");
        for (int i = 0; i < result.getColumnCount(); i++) {
            System.out.print("--------------------");
        }
        System.out.println();

        // Print rows
        for (final Row row : result.getRows()) {
            System.out.print("   ");
            for (final Object value : row.getValues()) {
                final String displayValue = value != null ? value.toString() : "NULL";
                System.out.printf("%-20s", displayValue);
            }
            System.out.println();
        }

        System.out.println("   (" + result.getRowCount() + " rows)");
    }
}
