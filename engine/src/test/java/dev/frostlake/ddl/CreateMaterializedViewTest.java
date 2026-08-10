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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateMaterializedViewTest extends BaseDatabaseTest {


    private Schema getCurrentSchema() {
        final String dbName = engine.getCatalog().getCurrentDatabase();
        final String schemaName = engine.getCatalog().getCurrentSchema();
        return engine.getCatalog().getDatabase(dbName).getSchema(schemaName);
    }

    private static final String NO_SUSPENSION_SURFACE =
        "the suspended flag has no SQL read surface yet (SHOW MATERIALIZED VIEWS carries no such "
        + "cell) — measure live's spelling before adding one; asserted embedded meanwhile";

    private int mvCount(final String name) {
        return engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE '" + name + "'").getRowCount();
    }

    private String mvCell(final String name, final String column) {
        final ResultSet views = engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE '" + name + "'");
        return cell(views, soleRowWhere(views, "name", name.toUpperCase()), column);
    }

    @Test
    public void testCreateSimpleMaterializedView() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_users AS SELECT * FROM users");

        assertEquals(1, mvCount("mv_users"));
    }

    @Test
    public void testCreateMaterializedViewWithFilter() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100), (2, 'Gadget', 200)");

        engine.execute("CREATE MATERIALIZED VIEW mv_expensive AS SELECT * FROM products WHERE price > 150");

        assertEquals(1, mvCount("mv_expensive"));
        assertTrue(mvCell("mv_expensive", "text").toLowerCase().contains("price > 150"),
            mvCell("mv_expensive", "text"));
    }

    @Test
    public void testDropMaterializedView() {
        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_items AS SELECT * FROM items");

        assertEquals(1, mvCount("mv_items"));

        engine.execute("DROP MATERIALIZED VIEW mv_items");

        assertEquals(0, mvCount("mv_items"));
    }

    @Test
    public void testDropMaterializedViewIfExists() {
        engine.execute("DROP MATERIALIZED VIEW IF EXISTS nonexistent_mv");
    }

    @Test
    public void testAlterMaterializedViewSuspend() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_SUSPENSION_SURFACE);
        final MaterializedView mv = getCurrentSchema().getMaterializedView("MV_DATA");
        assertFalse(mv.isSuspended());

        engine.execute("ALTER MATERIALIZED VIEW mv_data SUSPEND");
        assertTrue(mv.isSuspended());
    }

    @Test
    public void testAlterMaterializedViewResume() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_SUSPENSION_SURFACE);
        final MaterializedView mv = getCurrentSchema().getMaterializedView("MV_DATA");

        engine.execute("ALTER MATERIALIZED VIEW mv_data SUSPEND");
        assertTrue(mv.isSuspended());

        engine.execute("ALTER MATERIALIZED VIEW mv_data RESUME");
        assertFalse(mv.isSuspended());
    }

    @Test
    public void testAlterMaterializedViewRefresh() {
        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE MATERIALIZED VIEW mv_data AS SELECT * FROM data");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_SUSPENSION_SURFACE);
        final MaterializedView mv = getCurrentSchema().getMaterializedView("MV_DATA");

        engine.execute("ALTER MATERIALIZED VIEW mv_data REFRESH");
        assertNotNull(mv.getLastRefreshedTime());
    }

    @Test
    public void testCreateMaterializedViewWithComment() {
        engine.execute("CREATE TABLE sales (id INTEGER, amount INTEGER)");
        // Live-verified: the COMMENT property goes BEFORE AS (after the query it is a syntax error).
        engine.execute("CREATE MATERIALIZED VIEW mv_sales COMMENT = 'Sales data' AS SELECT * FROM sales");

        assertEquals("Sales data", mvCell("mv_sales", "comment"));
    }

    @Test
    public void testAlterMaterializedViewSetComment() {
        engine.execute("CREATE TABLE orders (id INTEGER, total INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW mv_orders AS SELECT * FROM orders");

        engine.execute("ALTER MATERIALIZED VIEW mv_orders SET COMMENT = 'Order summary'");
        assertEquals("Order summary", mvCell("mv_orders", "comment"));
    }
}
