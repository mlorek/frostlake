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
 * Demonstration of WHERE, GROUP BY, and ORDER BY operators
 */
public class QueryOperatorsDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new QueryOperatorsDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "SQL Operators Demo: WHERE, GROUP BY, ORDER BY";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("sales_db");

            // Create sales table
            engine.execute(
                "CREATE TABLE sales (" +
                "id INTEGER, " +
                "product VARCHAR, " +
                "category VARCHAR, " +
                "amount INTEGER, " +
                "region VARCHAR, " +
                "quantity INTEGER" +
                ")"
            );

            // Insert sample data
            engine.execute("INSERT INTO sales VALUES (1, 'Laptop', 'Electronics', 1200, 'North', 5)");
            engine.execute("INSERT INTO sales VALUES (2, 'Mouse', 'Electronics', 25, 'North', 50)");
            engine.execute("INSERT INTO sales VALUES (3, 'Desk', 'Furniture', 500, 'South', 3)");
            engine.execute("INSERT INTO sales VALUES (4, 'Chair', 'Furniture', 200, 'South', 8)");
            engine.execute("INSERT INTO sales VALUES (5, 'Monitor', 'Electronics', 300, 'East', 10)");
            engine.execute("INSERT INTO sales VALUES (6, 'Keyboard', 'Electronics', 75, 'West', 15)");
            engine.execute("INSERT INTO sales VALUES (7, 'Table', 'Furniture', 600, 'North', 2)");
            engine.execute("INSERT INTO sales VALUES (8, 'Lamp', 'Furniture', 45, 'East', 20)");
            engine.execute("INSERT INTO sales VALUES (9, 'Phone', 'Electronics', 800, 'South', 12)");
            engine.execute("INSERT INTO sales VALUES (10, 'Tablet', 'Electronics', 400, 'West', 7)");

            printSuccess("Sample data loaded\n");

            // Demo 1: Basic WHERE clause
            printSubsection("1. WHERE Clause - Filter by amount > 300");
            printQuery("SELECT product, amount, region FROM sales WHERE amount > 300");

            // Demo 2: WHERE with AND
            printSubsection("2. WHERE with AND - Electronics in North region");
            printQuery("SELECT product, category, region, amount FROM sales " +
                "WHERE category = 'Electronics' AND region = 'North'");

            // Demo 3: WHERE with OR
            printSubsection("3. WHERE with OR - Furniture OR amount < 100");
            printQuery("SELECT product, category, amount FROM sales " +
                "WHERE category = 'Furniture' OR amount < 100");

            // Demo 4: ORDER BY ascending
            printSubsection("4. ORDER BY ASC - Sort by amount (low to high)");
            printQuery("SELECT product, amount FROM sales ORDER BY amount ASC");

            // Demo 5: ORDER BY descending
            printSubsection("5. ORDER BY DESC - Sort by quantity (high to low)");
            printQuery("SELECT product, quantity FROM sales ORDER BY quantity DESC");

            // Demo 6: ORDER BY multiple columns
            printSubsection("6. ORDER BY Multiple - Sort by category, then amount");
            printQuery("SELECT product, category, amount FROM sales ORDER BY category ASC, amount DESC");

            // Demo 7: GROUP BY with COUNT
            printSubsection("7. GROUP BY with COUNT - Count products per category");
            printQuery("SELECT category, COUNT(*) FROM sales GROUP BY category");

            // Demo 8: GROUP BY with SUM
            printSubsection("8. GROUP BY with SUM - Total sales by region");
            printQuery("SELECT region, SUM(amount) FROM sales GROUP BY region");

            // Demo 9: GROUP BY with AVG
            printSubsection("9. GROUP BY with AVG - Average price by category");
            printQuery("SELECT category, AVG(amount) FROM sales GROUP BY category");

            // Demo 10: GROUP BY with MIN and MAX
            printSubsection("10. GROUP BY with MIN/MAX - Price range by category");
            printQuery("SELECT category, MIN(amount), MAX(amount) FROM sales GROUP BY category");

            // Demo 11: LIMIT clause
            printSubsection("11. LIMIT - Top 5 highest amounts");
            printQuery("SELECT product, amount FROM sales ORDER BY amount DESC LIMIT 5");

            // Demo 12: WHERE + ORDER BY
            printSubsection("12. Combined: WHERE + ORDER BY");
            System.out.println("Electronics products sorted by amount");
            printQuery("SELECT product, amount FROM sales " +
                "WHERE category = 'Electronics' " +
                "ORDER BY amount DESC");

            // Demo 13: WHERE + GROUP BY
            printSubsection("13. Combined: WHERE + GROUP BY");
            System.out.println("Count high-value items (>200) by region");
            printQuery("SELECT region, COUNT(*) FROM sales " +
                "WHERE amount > 200 " +
                "GROUP BY region");

            // Demo 14: GROUP BY + ORDER BY
            printSubsection("14. Combined: GROUP BY + ORDER BY");
            System.out.println("Total sales by category, sorted by total");
            printQuery("SELECT category, SUM(amount) FROM sales " +
                "GROUP BY category " +
                "ORDER BY SUM(amount) DESC");

            // Demo 15: All together
            printSubsection("15. Complex Query: WHERE + GROUP BY + ORDER BY + LIMIT");
            System.out.println("Top 2 regions by average sale amount (excluding low amounts)");
            printQuery("SELECT region, AVG(amount), COUNT(*) FROM sales " +
                "WHERE amount > 100 " +
                "GROUP BY region " +
                "ORDER BY AVG(amount) DESC " +
                "LIMIT 2");
    }
}
