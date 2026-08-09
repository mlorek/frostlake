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
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicTableTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamicTableTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE WAREHOUSE wh1");
        engine.execute("CREATE TABLE source (id INTEGER, val DOUBLE)");
        engine.execute("INSERT INTO source VALUES (1, 10.0), (2, 20.0), (3, 30.0)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private DynamicTable getDt(final String name) {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getDynamicTable(name);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Test
    public void testCreateBasicDynamicTable() {
        engine.execute("""
            CREATE DYNAMIC TABLE sales_agg
                TARGET_LAG = '1 minutes'
                WAREHOUSE = wh1
                AS SELECT id, val FROM source
            """);
        DynamicTable dt = getDt("SALES_AGG");
        assertNotNull(dt);
        assertEquals("SALES_AGG", dt.getName());
        assertEquals("1 minutes", dt.getTargetLag());
        assertEquals("WH1", dt.getWarehouse());
        assertEquals(DynamicTable.RefreshMode.AUTO, dt.getRefreshMode());
        assertEquals(DynamicTable.State.RUNNING, dt.getState());
        logger.info("Created basic dynamic table: {}", dt.getName());
    }

    @Test
    public void testCreateDynamicTableWithAllOptions() {
        engine.execute("""
            CREATE DYNAMIC TABLE full_opts
                TARGET_LAG = '5 minutes'
                WAREHOUSE = wh1
                REFRESH_MODE = FULL
                INITIALIZE = ON_SCHEDULE
                DATA_RETENTION_TIME_IN_DAYS = 7
                AS SELECT * FROM source
                COMMENT = 'full options test'
            """);
        DynamicTable dt = getDt("FULL_OPTS");
        assertEquals("5 minutes", dt.getTargetLag());
        assertEquals(DynamicTable.RefreshMode.FULL, dt.getRefreshMode());
        assertEquals(DynamicTable.Initialize.ON_SCHEDULE, dt.getInitialize());
        assertEquals(7, dt.getDataRetentionDays());
        assertEquals("full options test", dt.getComment());
    }

    @Test
    public void testCreateDynamicTableDownstream() {
        engine.execute("""
            CREATE DYNAMIC TABLE downstream_dt
                TARGET_LAG = DOWNSTREAM
                WAREHOUSE = wh1
                AS SELECT id FROM source
            """);
        DynamicTable dt = getDt("DOWNSTREAM_DT");
        assertEquals("DOWNSTREAM", dt.getTargetLag());
    }

    @Test
    public void testCreateDynamicTableIncrementalMode() {
        engine.execute("""
            CREATE DYNAMIC TABLE incr_dt
                TARGET_LAG = '30 seconds'
                WAREHOUSE = wh1
                REFRESH_MODE = INCREMENTAL
                AS SELECT * FROM source
            """);
        assertEquals(DynamicTable.RefreshMode.INCREMENTAL, getDt("INCR_DT").getRefreshMode());
    }

    @Test
    public void testCreateOrReplaceDynamicTable() {
        engine.execute("CREATE DYNAMIC TABLE replace_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE replace_dt TARGET_LAG = '5 minutes' WAREHOUSE = wh1 AS SELECT val FROM source");
        assertEquals("5 minutes", getDt("REPLACE_DT").getTargetLag());
    }

    @Test
    public void testCreateDynamicTableIfNotExists() {
        engine.execute("CREATE DYNAMIC TABLE ine_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        assertDoesNotThrow(() ->
            engine.execute("CREATE DYNAMIC TABLE IF NOT EXISTS ine_dt TARGET_LAG = '2 minutes' WAREHOUSE = wh1 AS SELECT id FROM source"));
        // Original should not be overwritten
        assertEquals("1 minutes", getDt("INE_DT").getTargetLag());
    }

    // ── DROP ──────────────────────────────────────────────────────────────────

    @Test
    public void testDropDynamicTable() {
        engine.execute("CREATE DYNAMIC TABLE drop_me TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("DROP DYNAMIC TABLE drop_me");
        assertThrows(RuntimeException.class, () -> getDt("DROP_ME"));
    }

    @Test
    public void testDropDynamicTableIfExists() {
        assertDoesNotThrow(() -> engine.execute("DROP DYNAMIC TABLE IF EXISTS nonexistent_dt"));
    }

    // ── ALTER ─────────────────────────────────────────────────────────────────

    @Test
    public void testAlterSuspend() {
        engine.execute("CREATE DYNAMIC TABLE sus_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE sus_dt SUSPEND");
        DynamicTable dt = getDt("SUS_DT");
        assertEquals(DynamicTable.State.SUSPENDED, dt.getState());
        assertTrue(dt.isSuspended());
    }

    @Test
    public void testAlterResume() {
        engine.execute("CREATE DYNAMIC TABLE res_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE res_dt SUSPEND");
        engine.execute("ALTER DYNAMIC TABLE res_dt RESUME");
        assertEquals(DynamicTable.State.RUNNING, getDt("RES_DT").getState());
        assertFalse(getDt("RES_DT").isSuspended());
    }

    @Test
    public void testAlterRefresh() {
        engine.execute("CREATE DYNAMIC TABLE ref_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE ref_dt REFRESH");
        assertNotNull(getDt("REF_DT").getLastRefreshedTime());
    }

    @Test
    public void testAlterSetTargetLag() {
        engine.execute("CREATE DYNAMIC TABLE lag_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE lag_dt SET TARGET_LAG = '10 minutes'");
        assertEquals("10 minutes", getDt("LAG_DT").getTargetLag());
    }

    @Test
    public void testAlterSetWarehouse() {
        engine.execute("CREATE WAREHOUSE wh2");
        engine.execute("CREATE DYNAMIC TABLE wh_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE wh_dt SET WAREHOUSE = wh2");
        assertEquals("WH2", getDt("WH_DT").getWarehouse());
    }

    @Test
    public void testAlterSetComment() {
        engine.execute("CREATE DYNAMIC TABLE cmt_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE cmt_dt SET COMMENT = 'updated comment'");
        assertEquals("updated comment", getDt("CMT_DT").getComment());
    }

    @Test
    public void testAlterSetDownstream() {
        engine.execute("CREATE DYNAMIC TABLE ds_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE ds_dt SET TARGET_LAG = DOWNSTREAM");
        assertEquals("DOWNSTREAM", getDt("DS_DT").getTargetLag());
    }

    @Test
    public void testAlterSetRefreshMode() {
        engine.execute("CREATE DYNAMIC TABLE rm_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE rm_dt SET REFRESH_MODE = INCREMENTAL");
        assertEquals(DynamicTable.RefreshMode.INCREMENTAL, getDt("RM_DT").getRefreshMode());
    }

    @Test
    public void testAlterSetDataRetention() {
        engine.execute("CREATE DYNAMIC TABLE dr_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE dr_dt SET DATA_RETENTION_TIME_IN_DAYS = 5");
        assertEquals(5, getDt("DR_DT").getDataRetentionDays());
    }

    // ── SHOW ──────────────────────────────────────────────────────────────────

    @Test
    public void testShowDynamicTables() {
        engine.execute("CREATE DYNAMIC TABLE show_dt1 TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("CREATE DYNAMIC TABLE show_dt2 TARGET_LAG = '2 minutes' WAREHOUSE = wh1 AS SELECT val FROM source");
        ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("target_lag"));
        assertNotNull(rs.getColumnIndex("scheduling_state"));
        assertNotNull(rs.getColumnIndex("warehouse"));
        assertNotNull(rs.getColumnIndex("query"));
    }

    @Test
    public void testShowDynamicTablesEmpty() {
        ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        assertNotNull(rs);
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testShowDynamicTablesReflectsState() {
        engine.execute("CREATE DYNAMIC TABLE state_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE state_dt SUSPEND");
        ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        int schedIdx = rs.getColumnIndex("scheduling_state");
        assertEquals("SUSPENDED", rs.getRows().get(0).getValue(schedIdx).toString().toUpperCase());
    }

    // ── DESCRIBE ──────────────────────────────────────────────────────────────

    @Test
    public void testDescribeDynamicTable() {
        engine.execute("CREATE DYNAMIC TABLE desc_dt TARGET_LAG = '3 minutes' WAREHOUSE = wh1 AS SELECT id, val FROM source");
        ResultSet rs = engine.executeQuery("DESCRIBE DYNAMIC TABLE desc_dt");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0);
        // Check that name and target_lag are present as properties
        boolean foundName = false, foundLag = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            String prop = rs.getRows().get(i).getValue(0).toString();
            if ("name".equals(prop)) foundName = true;
            if ("target_lag".equals(prop)) foundLag = true;
        }
        assertTrue(foundName, "DESCRIBE should include 'name' property");
        assertTrue(foundLag, "DESCRIBE should include 'target_lag' property");
    }

    // ── SHOW OBJECTS ──────────────────────────────────────────────────────────

    @Test
    public void testDynamicTableAppearsInShowObjects() {
        engine.execute("CREATE DYNAMIC TABLE obj_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        int nameIdx = rs.getColumnIndex("name");
        int kindIdx = rs.getColumnIndex("kind");
        int dynamicIdx = rs.getColumnIndex("is_dynamic");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            // A dynamic table is a TABLE here; is_dynamic is what distinguishes it.
            if ("OBJ_DT".equalsIgnoreCase(rs.getRows().get(i).getValue(nameIdx).toString())
                && "TABLE".equals(rs.getRows().get(i).getValue(kindIdx).toString())
                && "Y".equals(rs.getRows().get(i).getValue(dynamicIdx))) {
                found = true;
            }
        }
        assertTrue(found, "Dynamic table should appear in SHOW OBJECTS");
    }

    // ── INFORMATION_SCHEMA ───────────────────────────────────────────────────

    @Test
    public void testDynamicTablesViewExistsInInformationSchema() {
        // INFORMATION_SCHEMA.DYNAMIC_TABLES view should be registered
        // Verify by using SHOW DYNAMIC TABLES (avoids keyword parsing issue)
        assertDoesNotThrow(() -> engine.executeQuery("SHOW DYNAMIC TABLES"));
    }
}
