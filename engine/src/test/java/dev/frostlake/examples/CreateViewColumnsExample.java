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

package dev.frostlake.examples;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateViewColumnsExample {
    private static final Logger logger = LoggerFactory.getLogger(CreateViewColumnsExample.class);

    @Test
    public void demonstrateViewColumnNames() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== CREATE VIEW with Explicit Column Names ===");

            // Example 1: Rename columns in a view
            logger.info("\n1. Rename columns in a view:");
            engine.execute("CREATE TABLE employees (emp_id INTEGER, emp_name VARCHAR, emp_salary INTEGER)");
            engine.execute("INSERT INTO employees VALUES (1, 'Alice', 75000)");
            engine.execute("INSERT INTO employees VALUES (2, 'Bob', 65000)");
            engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 85000)");

            engine.execute("CREATE VIEW employee_info (id, name, salary) AS SELECT emp_id, emp_name, emp_salary FROM employees");

            final ResultSet rs1 = engine.executeQuery("SELECT id, name, salary FROM employee_info WHERE salary > 70000 ORDER BY id");
            logger.info("Employees with salary > 70000:");
            for (int i = 0; i < rs1.getRowCount(); i++) {
                logger.info("  ID {}: {} - ${}",
                    rs1.getRows().get(i).getValue(0),
                    rs1.getRows().get(i).getValue(1),
                    rs1.getRows().get(i).getValue(2)
                );
            }

            // Example 2: Name computed columns
            logger.info("\n2. Name computed columns:");
            engine.execute("CREATE TABLE products (product_id INTEGER, price INTEGER, quantity INTEGER)");
            engine.execute("INSERT INTO products VALUES (1, 100, 10)");
            engine.execute("INSERT INTO products VALUES (2, 50, 20)");
            engine.execute("INSERT INTO products VALUES (3, 75, 15)");

            engine.execute("CREATE VIEW product_values (id, total_value, avg_unit_price) AS SELECT product_id, price * quantity, price FROM products");

            final ResultSet rs2 = engine.executeQuery("SELECT id, total_value, avg_unit_price FROM product_values ORDER BY id");
            logger.info("Product values:");
            for (int i = 0; i < rs2.getRowCount(); i++) {
                logger.info("  Product {}: Total Value = ${}, Unit Price = ${}",
                    rs2.getRows().get(i).getValue(0),
                    rs2.getRows().get(i).getValue(1),
                    rs2.getRows().get(i).getValue(2)
                );
            }

            // Example 3: Aggregate views with meaningful names
            logger.info("\n3. Aggregate views with meaningful names:");
            engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, revenue INTEGER)");
            engine.execute("INSERT INTO sales VALUES ('North', 'Widget', 1000)");
            engine.execute("INSERT INTO sales VALUES ('North', 'Gadget', 1500)");
            engine.execute("INSERT INTO sales VALUES ('South', 'Widget', 800)");
            engine.execute("INSERT INTO sales VALUES ('South', 'Gadget', 1200)");

            engine.execute("CREATE VIEW regional_sales (region_name, total_revenue, product_count) AS SELECT region, SUM(revenue), COUNT(*) FROM sales GROUP BY region");

            final ResultSet rs3 = engine.executeQuery("SELECT region_name, total_revenue, product_count FROM regional_sales ORDER BY region_name");
            logger.info("Regional sales summary:");
            for (int i = 0; i < rs3.getRowCount(); i++) {
                logger.info("  {}: ${} revenue, {} products",
                    rs3.getRows().get(i).getValue(0),
                    rs3.getRows().get(i).getValue(1),
                    rs3.getRows().get(i).getValue(2)
                );
            }

            // Example 4: CREATE OR REPLACE VIEW with different column names
            logger.info("\n4. CREATE OR REPLACE VIEW with different column names:");
            engine.execute("CREATE TABLE customers (cust_id INTEGER, cust_name VARCHAR, cust_email VARCHAR)");
            engine.execute("INSERT INTO customers VALUES (1, 'John Doe', 'john@example.com')");

            engine.execute("CREATE VIEW customer_view (id, full_name, email) AS SELECT cust_id, cust_name, cust_email FROM customers");

            final ResultSet rs4 = engine.executeQuery("SELECT id, full_name FROM customer_view");
            logger.info("Original view: ID = {}, Name = {}",
                rs4.getRows().get(0).getValue(0),
                rs4.getRows().get(0).getValue(1)
            );

            engine.execute("CREATE OR REPLACE VIEW customer_view (customer_number, name) AS SELECT cust_id, cust_name FROM customers");

            final ResultSet rs5 = engine.executeQuery("SELECT customer_number, name FROM customer_view");
            logger.info("Replaced view: Number = {}, Name = {}",
                rs5.getRows().get(0).getValue(0),
                rs5.getRows().get(0).getValue(1)
            );

            // Example 5: View with SELECT * and explicit names
            logger.info("\n5. View with SELECT * and explicit column names:");
            engine.execute("CREATE TABLE orders (order_id INTEGER, amount INTEGER)");
            engine.execute("INSERT INTO orders VALUES (100, 500)");

            engine.execute("CREATE VIEW order_view (id, total) AS SELECT * FROM orders");

            final ResultSet rs6 = engine.executeQuery("SELECT id, total FROM order_view");
            logger.info("Order: ID = {}, Total = ${}",
                rs6.getRows().get(0).getValue(0),
                rs6.getRows().get(0).getValue(1)
            );

            // Example 6: Complex view with joins and renamed columns
            logger.info("\n6. Complex view with joins:");
            engine.execute("CREATE TABLE authors (author_id INTEGER, author_name VARCHAR)");
            engine.execute("CREATE TABLE books (book_id INTEGER, book_title VARCHAR, author_ref INTEGER)");

            engine.execute("INSERT INTO authors VALUES (1, 'Jane Austen')");
            engine.execute("INSERT INTO authors VALUES (2, 'Charles Dickens')");

            engine.execute("INSERT INTO books VALUES (101, 'Pride and Prejudice', 1)");
            engine.execute("INSERT INTO books VALUES (102, 'Oliver Twist', 2)");

            engine.execute("CREATE VIEW book_catalog (book_number, title, author) AS SELECT b.book_id, b.book_title, a.author_name FROM books b JOIN authors a ON b.author_ref = a.author_id");

            final ResultSet rs7 = engine.executeQuery("SELECT book_number, title, author FROM book_catalog ORDER BY book_number");
            logger.info("Book catalog:");
            for (int i = 0; i < rs7.getRowCount(); i++) {
                logger.info("  Book {}: '{}' by {}",
                    rs7.getRows().get(i).getValue(0),
                    rs7.getRows().get(i).getValue(1),
                    rs7.getRows().get(i).getValue(2)
                );
            }

        } finally {
            engine.shutdown();
        }
    }
}
