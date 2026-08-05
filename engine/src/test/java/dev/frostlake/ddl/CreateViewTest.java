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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CREATE VIEW command.
 *
 * <p>{@code SHOW VIEWS} is probed for the view the test itself created rather than counted: a real
 * account's schema carries views of its own, so a total row count is not a portable assertion.
 */
public class CreateViewTest extends BaseDatabaseTest {

    /** Whether {@code SHOW VIEWS} carries a row whose {@code name} column is this view. */
    private boolean viewListed(final String name) {
        final ResultSet views = engine.executeQuery("SHOW VIEWS");
        final int nameColumn = views.getColumnIndex("name");
        for (final Row row : views.getRows()) {
            if (name.equalsIgnoreCase(String.valueOf(row.getValue(nameColumn)))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testCreateSimpleView() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");

        assertTrue(viewListed("ACTIVE_USERS"), "the created view should be listed by SHOW VIEWS");
    }

    @Test
    public void testCreateViewWithFilter() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 20)");

        engine.execute("CREATE VIEW adult_users AS SELECT * FROM users WHERE age >= 21");

        assertTrue(viewListed("ADULT_USERS"), "the created view should be listed by SHOW VIEWS");
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

        // Live-verified: an expression select item without an alias fails view creation.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW product_totals AS SELECT product, SUM(amount) FROM sales GROUP BY product");
            }
        });
        assertTrue(e.getMessage().contains("Missing column specification"), "unexpected message: " + e.getMessage());

        // With an alias on the aggregate the view is created.
        engine.execute("CREATE VIEW product_totals AS SELECT product, SUM(amount) AS total FROM sales GROUP BY product");

        assertTrue(viewListed("PRODUCT_TOTALS"), "the created view should be listed by SHOW VIEWS");
    }
}
