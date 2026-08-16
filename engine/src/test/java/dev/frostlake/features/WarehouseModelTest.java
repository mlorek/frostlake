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
import dev.frostlake.metastore.QueryExecution;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the {@link Warehouse} MODEL — size properties, execution accounting, cluster and
 * scaling setters, and the default-warehouse drop guard, none of which have a SQL surface.
 *
 * <p>Deliberately NOT on the live surface: every assertion drives model objects directly, so this
 * class runs its own embedded engine. The SQL-visible warehouse behavior lives in
 * {@code WarehousesTest}.
 */
public class WarehouseModelTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void testWarehouseSizeProperties() {
        final WarehouseSize small = WarehouseSize.SMALL;
        assertEquals("Small", small.getDisplayName());
        assertEquals(2, small.getServers());
        assertEquals(16, small.getCreditsPerHour());

        final WarehouseSize large = WarehouseSize.LARGE;
        assertEquals("Large", large.getDisplayName());
        assertEquals(8, large.getServers());
        assertEquals(64, large.getCreditsPerHour());
    }

    @Test
    public void testCannotDropDefaultWarehouse() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.getCatalog().dropWarehouse("COMPUTE_WH");
            }
        });
    }

    @Test
    public void testQueryExecutionTracking() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        final Warehouse wh = engine.getCatalog().getWarehouse("test_wh");

        // Create a query execution record
        final QueryExecution execution = new QueryExecution(
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

    @Test
    public void testMultiClusterSettings() {
        engine.execute("CREATE WAREHOUSE cluster_wh WITH WAREHOUSE_SIZE = 'LARGE'");
        final Warehouse wh = engine.getCatalog().getWarehouse("cluster_wh");

        // Set cluster counts
        wh.setMinClusterCount(2);
        wh.setMaxClusterCount(10);

        assertEquals(2, wh.getMinClusterCount());
        assertEquals(10, wh.getMaxClusterCount());
    }

    @Test
    public void testScalingPolicy() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'MEDIUM'");
        final Warehouse wh = engine.getCatalog().getWarehouse("test_wh");

        assertEquals(ScalingPolicy.STANDARD, wh.getScalingPolicy());

        wh.setScalingPolicy(ScalingPolicy.ECONOMY);
        assertEquals(ScalingPolicy.ECONOMY, wh.getScalingPolicy());
    }
}
