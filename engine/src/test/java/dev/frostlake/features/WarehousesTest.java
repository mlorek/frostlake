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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.metastore.model.WarehouseState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WarehousesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    // ==================== WAREHOUSE CREATION TESTS ====================

    @Test
    public void testDefaultWarehouseExists() {
        Warehouse wh = engine.getCatalog().getWarehouse("COMPUTE_WH");
        assertNotNull(wh);
        assertEquals("COMPUTE_WH", wh.getName());
        assertEquals(WarehouseSize.X_SMALL, wh.getSize());
    }

    @Test
    public void testCreateWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertNotNull(wh);
        assertEquals("test_wh", wh.getName());
        assertEquals(WarehouseSize.SMALL, wh.getSize());
        assertEquals(WarehouseState.SUSPENDED, wh.getState());
    }

    @Test
    public void testCreateWarehouseWithOptions() {
        engine.execute("""
            CREATE WAREHOUSE analytics_wh
            WITH WAREHOUSE_SIZE = 'LARGE'
            AUTO_SUSPEND = 300
            AUTO_RESUME = true
            """);

        Warehouse wh = engine.getCatalog().getWarehouse("analytics_wh");
        assertEquals(WarehouseSize.LARGE, wh.getSize());
        assertEquals(300, wh.getAutoSuspendSeconds());
        assertTrue(wh.isAutoResume());
    }

    @Test
    public void testCreateWarehouseVariousSizes() {
        engine.execute("CREATE WAREHOUSE wh_xs WITH WAREHOUSE_SIZE = 'X-SMALL'");
        engine.execute("CREATE WAREHOUSE wh_s WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh_m WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("CREATE WAREHOUSE wh_l WITH WAREHOUSE_SIZE = 'LARGE'");

        assertEquals(WarehouseSize.X_SMALL,
            engine.getCatalog().getWarehouse("wh_xs").getSize());
        assertEquals(WarehouseSize.SMALL,
            engine.getCatalog().getWarehouse("wh_s").getSize());
        assertEquals(WarehouseSize.MEDIUM,
            engine.getCatalog().getWarehouse("wh_m").getSize());
        assertEquals(WarehouseSize.LARGE,
            engine.getCatalog().getWarehouse("wh_l").getSize());
    }

    // ==================== WAREHOUSE STATE TESTS ====================

    @Test
    public void testResumeWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh RESUME");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertEquals(WarehouseState.STARTED, wh.getState());
        assertTrue(wh.isActive());
        assertTrue(wh.canExecuteQueries());
    }

    @Test
    public void testSuspendWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh RESUME");
        engine.execute("ALTER WAREHOUSE test_wh SUSPEND");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertEquals(WarehouseState.SUSPENDED, wh.getState());
        assertFalse(wh.isActive());
    }

    @Test
    public void testResumeSuspendCycle() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertEquals(WarehouseState.SUSPENDED, wh.getState());

        engine.execute("ALTER WAREHOUSE test_wh RESUME");
        assertEquals(WarehouseState.STARTED, wh.getState());

        engine.execute("ALTER WAREHOUSE test_wh SUSPEND");
        assertEquals(WarehouseState.SUSPENDED, wh.getState());

        engine.execute("ALTER WAREHOUSE test_wh RESUME");
        assertEquals(WarehouseState.STARTED, wh.getState());
    }

    // ==================== ALTER WAREHOUSE TESTS ====================

    @Test
    public void testAlterWarehouseSize() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET WAREHOUSE_SIZE = 'LARGE'");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertEquals(WarehouseSize.LARGE, wh.getSize());
    }

    @Test
    public void testAlterWarehouseAutoSuspend() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET AUTO_SUSPEND = 120");

        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");
        assertEquals(120, wh.getAutoSuspendSeconds());
    }

    // ==================== USE WAREHOUSE TESTS ====================

    @Test
    public void testUseWarehouse() {
        engine.execute("CREATE WAREHOUSE analytics_wh WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("USE WAREHOUSE analytics_wh");

        assertEquals("ANALYTICS_WH", engine.getCatalog().getCurrentWarehouse());
    }

    @Test
    public void testSwitchWarehouses() {
        engine.execute("CREATE WAREHOUSE wh1 WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh2 WITH WAREHOUSE_SIZE = 'LARGE'");

        engine.execute("USE WAREHOUSE wh1");
        assertEquals("WH1", engine.getCatalog().getCurrentWarehouse());

        engine.execute("USE WAREHOUSE wh2");
        assertEquals("WH2", engine.getCatalog().getCurrentWarehouse());
    }

    // ==================== DROP WAREHOUSE TESTS ====================

    @Test
    public void testDropWarehouse() {
        engine.execute("CREATE WAREHOUSE temp_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("DROP WAREHOUSE temp_wh");

        assertThrows(RuntimeException.class, () -> {
            engine.getCatalog().getWarehouse("temp_wh");
        });
    }

    @Test
    public void testCannotDropDefaultWarehouse() {
        assertThrows(RuntimeException.class, () -> {
            engine.getCatalog().dropWarehouse("COMPUTE_WH");
        });
    }

    // ==================== WAREHOUSE PROPERTIES TESTS ====================

    @Test
    public void testWarehouseSizeProperties() {
        WarehouseSize small = WarehouseSize.SMALL;
        assertEquals("Small", small.getDisplayName());
        assertEquals(2, small.getServers());
        assertEquals(16, small.getCreditsPerHour());

        WarehouseSize large = WarehouseSize.LARGE;
        assertEquals("Large", large.getDisplayName());
        assertEquals(8, large.getServers());
        assertEquals(64, large.getCreditsPerHour());
    }

    @Test
    public void testQueryExecutionTracking() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");

        // Create a query execution record
        QueryExecution execution = new QueryExecution(
            "query-123",
            "SELECT * FROM test_table",
            LocalDateTime.now(),
            LocalDateTime.now().plusSeconds(5),
            5000,
            1000,
            "SUCCESS"
        );

        wh.recordQueryExecution(execution);

        assertEquals(1, wh.getTotalQueriesExecuted());
        assertEquals(1, wh.getQueryHistory().size());
        assertTrue(wh.getTotalCreditsUsed() > 0);
    }

    // ==================== MULTI-CLUSTER TESTS ====================

    @Test
    public void testMultiClusterSettings() {
        engine.execute("CREATE WAREHOUSE cluster_wh WITH WAREHOUSE_SIZE = 'LARGE'");
        Warehouse wh = engine.getCatalog().getWarehouse("cluster_wh");

        // Set cluster counts
        wh.setMinClusterCount(2);
        wh.setMaxClusterCount(10);

        assertEquals(2, wh.getMinClusterCount());
        assertEquals(10, wh.getMaxClusterCount());
    }

    @Test
    public void testScalingPolicy() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'MEDIUM'");
        Warehouse wh = engine.getCatalog().getWarehouse("test_wh");

        assertEquals(ScalingPolicy.STANDARD, wh.getScalingPolicy());

        wh.setScalingPolicy(ScalingPolicy.ECONOMY);
        assertEquals(ScalingPolicy.ECONOMY, wh.getScalingPolicy());
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void testWarehouseWithQueryExecution() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");

        engine.execute("CREATE WAREHOUSE query_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("USE WAREHOUSE query_wh");
        engine.execute("ALTER WAREHOUSE query_wh RESUME");

        // Execute a query
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Test')");

        Warehouse wh = engine.getCatalog().getWarehouse("query_wh");
        assertTrue(wh.isActive());
    }

    @Test
    public void testListWarehouses() {
        engine.execute("CREATE WAREHOUSE wh1 WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh2 WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("CREATE WAREHOUSE wh3 WITH WAREHOUSE_SIZE = 'LARGE'");

        var warehouses = engine.getCatalog().getAllWarehouses();
        assertTrue(warehouses.size() >= 4); // Including COMPUTE_WH
    }
}
