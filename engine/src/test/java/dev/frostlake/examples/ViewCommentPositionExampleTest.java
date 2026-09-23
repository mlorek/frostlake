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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a view's COMMENT is written: after the view's name, or after its column list, and before AS. Written
 * after the query, COMMENT reads as the table's alias and its '=' is a syntax error. INFORMATION_SCHEMA reads
 * the comment back, and CREATE OR REPLACE replaces it with the view. Every cell is live-verified.
 */
public class ViewCommentPositionExampleTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Every row, its cells joined by a space, the rows by " | ". */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            text.append(text.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                text.append(i > 0 ? " " : "").append(row.getValue(i));
            }
        }
        return text.toString();
    }

    private String comment(final String view) {
        return answer("SELECT comment FROM test_db.information_schema.views"
            + " WHERE table_schema = 'TEST_SCHEMA' AND table_name = '" + view + "'");
    }

    @Test
    public void demonstrateViewCommentPositions() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1200), (2, 'Mouse', 25)");
        assertEquals("SQL compilation error:|syntax error line 1 at position 74 unexpected '='.",
            answer("CREATE VIEW products_view AS SELECT id, name, price FROM products COMMENT = 'All products catalog'"));
        engine.execute("CREATE VIEW products_view COMMENT = 'All products catalog' AS SELECT id, name, price FROM products");
        assertEquals("1 Laptop 1200 | 2 Mouse 25", rows("SELECT id, name, price FROM products_view ORDER BY id"));
        assertEquals("All products catalog", comment("PRODUCTS_VIEW"));

        // After a column list.
        engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, total INTEGER)");
        engine.execute("INSERT INTO orders VALUES (100, 1, 500), (101, 2, 750)");
        engine.execute("CREATE VIEW order_summary (id, customer, amount) COMMENT = 'Order details summary'"
            + " AS SELECT order_id, customer_id, total FROM orders");
        assertEquals("101 2 750", rows("SELECT id, customer, amount FROM order_summary WHERE amount > 600"));
        assertEquals("Order details summary", comment("ORDER_SUMMARY"));

        // CREATE OR REPLACE replaces the comment with the view.
        engine.execute("CREATE TABLE inventory (item VARCHAR, quantity INTEGER)");
        engine.execute("INSERT INTO inventory VALUES ('Widget', 100), ('Gadget', 50)");
        engine.execute("CREATE VIEW inventory_view COMMENT = 'Current inventory levels' AS SELECT item, quantity FROM inventory");
        assertEquals("Current inventory levels", comment("INVENTORY_VIEW"));
        engine.execute("CREATE OR REPLACE VIEW inventory_view COMMENT = 'Updated inventory snapshot'"
            + " AS SELECT item, quantity FROM inventory WHERE quantity > 60");
        assertEquals("Widget 100", rows("SELECT item, quantity FROM inventory_view"));
        assertEquals("Updated inventory snapshot", comment("INVENTORY_VIEW"));

        // An aggregating view with a column list.
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, revenue INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('North', 'Widget', 1000), ('North', 'Gadget', 1500),"
            + " ('South', 'Widget', 800), ('South', 'Gadget', 1200)");
        engine.execute("CREATE VIEW regional_revenue (region, total) COMMENT = 'Revenue by region summary'"
            + " AS SELECT region, SUM(revenue) FROM sales GROUP BY region");
        assertEquals("North 2500 | South 2000", rows("SELECT region, total FROM regional_revenue ORDER BY region"));
        assertEquals("Revenue by region summary", comment("REGIONAL_REVENUE"));
    }
}
