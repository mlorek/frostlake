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

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;

/**
 * Demonstration of PRIMARY KEY functionality in Frostlake SQL Engine
 */
public class PrimaryKeyDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new PrimaryKeyDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "PRIMARY KEY Support Demo";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("demo_db");

        // Demo 1: Single-column primary key
        demo1SingleColumnPrimaryKey();

        // Demo 2: Composite primary key
        demo2CompositePrimaryKey();

        // Demo 3: Primary key with auto-increment
        demo3AutoIncrementPrimaryKey();

        // Demo 4: Querying primary key information
        demo4QueryPrimaryKeys();
    }

    private void demo1SingleColumnPrimaryKey() {
        printSubsection("Demo 1: Single-Column Primary Key");

        engine.execute(
            "CREATE TABLE users (" +
            "    id INTEGER PRIMARY KEY," +
            "    username VARCHAR," +
            "    email VARCHAR" +
            ")"
        );
        printSuccess("Created table 'users' with single-column primary key on 'id'");

        engine.execute("INSERT INTO users VALUES (1, 'alice', 'alice@example.com')");
        engine.execute("INSERT INTO users VALUES (2, 'bob', 'bob@example.com')");
        engine.execute("INSERT INTO users VALUES (3, 'charlie', 'charlie@example.com')");
        printSuccess("Inserted 3 users");

        System.out.println("\nUsers table:");
        printQuery("SELECT * FROM users ORDER BY id");

        // Show primary key info
        Schema schema = engine.getCatalog().getDatabase("demo_db").getSchema("PUBLIC");
        Table table = schema.getTable("users");
        System.out.println("Primary key columns: " + table.getPrimaryKeys());
    }

    private void demo2CompositePrimaryKey() {
        printSubsection("Demo 2: Composite Primary Key");

        engine.execute(
            "CREATE TABLE order_items (" +
            "    order_id INTEGER," +
            "    item_id INTEGER," +
            "    product_name VARCHAR," +
            "    quantity INTEGER," +
            "    price DECIMAL," +
            "    PRIMARY KEY (order_id, item_id)" +
            ")"
        );
        printSuccess("Created table 'order_items' with composite primary key (order_id, item_id)");

        engine.execute("INSERT INTO order_items VALUES (1001, 1, 'Widget', 2, 19.99)");
        engine.execute("INSERT INTO order_items VALUES (1001, 2, 'Gadget', 1, 29.99)");
        engine.execute("INSERT INTO order_items VALUES (1002, 1, 'Widget', 5, 19.99)");
        engine.execute("INSERT INTO order_items VALUES (1002, 2, 'Doohickey', 3, 9.99)");
        printSuccess("Inserted 4 order items");

        System.out.println("\nOrder items table:");
        printQuery("SELECT * FROM order_items ORDER BY order_id, item_id");

        // Show primary key info
        Schema schema = engine.getCatalog().getDatabase("demo_db").getSchema("PUBLIC");
        Table table = schema.getTable("order_items");
        System.out.println("Primary key columns: " + table.getPrimaryKeys());
    }

    private void demo3AutoIncrementPrimaryKey() {
        printSubsection("Demo 3: Primary Key with Auto-Increment");

        engine.execute(
            "CREATE TABLE products (" +
            "    product_id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    name VARCHAR," +
            "    category VARCHAR," +
            "    price DECIMAL" +
            ")"
        );
        printSuccess("Created table 'products' with auto-increment primary key");

        // Note: Auto-increment is defined but actual auto-generation would need additional implementation
        engine.execute("INSERT INTO products VALUES (1, 'Laptop', 'Electronics', 999.99)");
        engine.execute("INSERT INTO products VALUES (2, 'Mouse', 'Electronics', 24.99)");
        engine.execute("INSERT INTO products VALUES (3, 'Desk', 'Furniture', 299.99)");
        printSuccess("Inserted 3 products");

        System.out.println("\nProducts table:");
        printQuery("SELECT * FROM products ORDER BY product_id");

        Schema schema = engine.getCatalog().getDatabase("demo_db").getSchema("PUBLIC");
        Table table = schema.getTable("products");
        System.out.println("Primary key columns: " + table.getPrimaryKeys());
        System.out.println("product_id is auto-increment: " +
            table.getColumn("product_id").isAutoIncrement());
    }

    private void demo4QueryPrimaryKeys() {
        printSubsection("Demo 4: Query Primary Key Information");

        engine.execute(
            "CREATE TABLE employees (" +
            "    emp_id INTEGER," +
            "    dept_id INTEGER," +
            "    name VARCHAR," +
            "    salary DECIMAL," +
            "    hire_date DATE," +
            "    PRIMARY KEY (emp_id, dept_id)" +
            ")"
        );
        printSuccess("Created table 'employees' with composite key (emp_id, dept_id)");

        Schema schema = engine.getCatalog().getDatabase("demo_db").getSchema("PUBLIC");
        Table table = schema.getTable("employees");

        System.out.println("\nPrimary Key Analysis:");
        System.out.println("  Total columns: " + table.getColumns().size());
        System.out.println("  Primary key columns: " + table.getPrimaryKeys());

        System.out.println("\n  Column Details:");
        for (final var col : table.getColumns()) {
            System.out.printf("    - %s: %s%s%n",
                col.getName(),
                col.getDataType().getName(),
                col.isPrimaryKey() ? " [PRIMARY KEY]" : "");
        }
    }
}
