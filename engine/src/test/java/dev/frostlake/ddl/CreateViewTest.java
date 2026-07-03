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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for CREATE VIEW command
 */
public class CreateViewTest extends BaseDatabaseTest {

    @Test
    public void testCreateSimpleView() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");

        ResultSet views = engine.showViews();
        assertEquals(1, views.getRowCount(), "Should have one view");
    }

    @Test
    public void testCreateViewWithFilter() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 20)");

        engine.execute("CREATE VIEW adult_users AS SELECT * FROM users WHERE age >= 21");

        ResultSet views = engine.showViews();
        assertEquals(1, views.getRowCount(), "Should have one view");
    }

    @Test
    public void testQueryView() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        engine.execute("CREATE VIEW expensive_products AS SELECT * FROM products WHERE price > 150");

        ResultSet result = engine.executeQuery("SELECT * FROM expensive_products");
        assertEquals(1, result.getRowCount(), "View should filter to 1 product");
    }

    @Test
    public void testCreateViewWithAggregation() {
        engine.execute("CREATE TABLE sales (id INTEGER, product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES (1, 'Widget', 100), (2, 'Widget', 150), (3, 'Gadget', 200)");

        engine.execute("CREATE VIEW product_totals AS SELECT product, SUM(amount) FROM sales GROUP BY product");

        ResultSet views = engine.showViews();
        assertEquals(1, views.getRowCount(), "Should have one view");
    }
}
