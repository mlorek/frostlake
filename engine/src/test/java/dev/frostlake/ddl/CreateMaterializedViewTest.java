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
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateMaterializedViewTest extends BaseDatabaseTest {

    private Schema getCurrentSchema() {
        String dbName = engine.getCatalog().getCurrentDatabase();
        String schemaName = engine.getCatalog().getCurrentSchema();
        return engine.getCatalog().getDatabase(dbName).getSchema(schemaName);
    }

    @Test
    public void testCreateSimpleMaterializedView() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_users AS SELECT * FROM users");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_USERS");
        assertNotNull(mv);
        assertFalse(mv.isSuspended());
    }

    @Test
    public void testCreateMaterializedViewWithFilter() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        engine.execute("CREATE MATERIALIZED VIEW mv_expensive AS SELECT * FROM products WHERE price > 150");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_EXPENSIVE");
        assertNotNull(mv);
    }

    @Test
    public void testDropMaterializedView() {
        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_items AS SELECT * FROM items");

        Schema schema = getCurrentSchema();
        assertNotNull(schema.getMaterializedView("MV_ITEMS"));

        engine.execute("DROP MATERIALIZED VIEW mv_items");

        assertThrows(RuntimeException.class, () -> getCurrentSchema().getMaterializedView("MV_ITEMS"));
    }

    @Test
    public void testDropMaterializedViewIfExists() {
        engine.execute("DROP MATERIALIZED VIEW IF EXISTS nonexistent_mv");
    }

    @Test
    public void testAlterMaterializedViewSuspend() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_DATA");
        assertFalse(mv.isSuspended());

        engine.execute("ALTER MATERIALIZED VIEW mv_data SUSPEND");
        assertTrue(mv.isSuspended());
    }

    @Test
    public void testAlterMaterializedViewResume() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_DATA");

        engine.execute("ALTER MATERIALIZED VIEW mv_data SUSPEND");
        assertTrue(mv.isSuspended());

        engine.execute("ALTER MATERIALIZED VIEW mv_data RESUME");
        assertFalse(mv.isSuspended());
    }

    @Test
    public void testAlterMaterializedViewRefresh() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_DATA");

        engine.execute("ALTER MATERIALIZED VIEW mv_data REFRESH");
        assertNotNull(mv.getLastRefreshedTime());
    }

    @Test
    public void testCreateMaterializedViewWithComment() {
        engine.execute("CREATE TABLE sales (id INTEGER, amount INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW mv_sales AS SELECT * FROM sales COMMENT = 'Sales data'");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_SALES");
        assertEquals("Sales data", mv.getComment());
    }

    @Test
    public void testAlterMaterializedViewSetComment() {
        engine.execute("CREATE TABLE orders (id INTEGER, total INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW mv_orders AS SELECT * FROM orders");

        Schema schema = getCurrentSchema();
        MaterializedView mv = schema.getMaterializedView("MV_ORDERS");

        engine.execute("ALTER MATERIALIZED VIEW mv_orders SET COMMENT = 'Order summary'");
        assertEquals("Order summary", mv.getComment());
    }
}
