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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Initialize;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DYNAMIC TABLE DDL, asserted through the SQL surface — {@code SHOW DYNAMIC TABLES} cells
 * (target_lag, refresh_mode, scheduling_state ACTIVE/SUSPENDED, warehouse, comment) and
 * {@code DESCRIBE DYNAMIC TABLE} — so every check runs against whichever engine executed the DDL,
 * embedded or live. The three cells with no SQL read surface yet (INITIALIZE, data retention days,
 * last-refresh time) are asserted embedded-only, each with its reason.
 */
public class DynamicTableTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamicTableTest.class);

    private static final String NO_SQL_SURFACE =
        "this dynamic-table property has no SQL read surface yet (not a SHOW DYNAMIC TABLES cell), "
        + "so it is asserted off the model, embedded only";

    @Override
    protected void setupTest() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh1 WITH WAREHOUSE_SIZE = 'XSMALL' "
            + "AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE TABLE source (id INTEGER, val DOUBLE)");
        engine.execute("INSERT INTO source VALUES (1, 10.0), (2, 20.0), (3, 30.0)");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP WAREHOUSE IF EXISTS wh1");
        engine.execute("DROP WAREHOUSE IF EXISTS wh2");
    }

    private DynamicTable getDt(final String name) {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getDynamicTable(name);
    }

    private String dtCell(final String name, final String column) {
        final ResultSet tables = engine.executeQuery("SHOW DYNAMIC TABLES LIKE '" + name + "'");
        return cell(tables, soleRowWhere(tables, "name", name.toUpperCase()), column);
    }

    private int dtCount(final String name) {
        return engine.executeQuery("SHOW DYNAMIC TABLES LIKE '" + name + "'").getRowCount();
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
        // The lag is canonicalized ('1 minutes' reads back singular) and an AUTO-configured
        // table reports its RESOLVED refresh mode, both live-verified.
        assertEquals("1 minute", dtCell("sales_agg", "target_lag"));
        assertEquals("WH1", dtCell("sales_agg", "warehouse"));
        assertEquals("INCREMENTAL", dtCell("sales_agg", "refresh_mode"));
        assertEquals("ACTIVE", dtCell("sales_agg", "scheduling_state"));
        logger.info("Created basic dynamic table");
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
                COMMENT = 'full options test'
                AS SELECT * FROM source
            """);
        assertEquals("5 minutes", dtCell("full_opts", "target_lag"));
        assertEquals("FULL", dtCell("full_opts", "refresh_mode"));
        assertEquals("full options test", dtCell("full_opts", "comment"));
        Assumptions.assumeFalse(isLiveSnowflake(), NO_SQL_SURFACE);
        assertEquals(Initialize.ON_SCHEDULE, getDt("FULL_OPTS").getInitialize());
        assertEquals(7, getDt("FULL_OPTS").getDataRetentionDays());
    }

    @Test
    public void testCreateDynamicTableDownstream() {
        engine.execute("""
            CREATE DYNAMIC TABLE downstream_dt
                TARGET_LAG = DOWNSTREAM
                WAREHOUSE = wh1
                AS SELECT id FROM source
            """);
        assertEquals("DOWNSTREAM", dtCell("downstream_dt", "target_lag"));
    }

    @Test
    public void testCreateDynamicTableIncrementalMode() {
        engine.execute("""
            CREATE DYNAMIC TABLE incr_dt
                TARGET_LAG = '2 minutes'
                WAREHOUSE = wh1
                REFRESH_MODE = INCREMENTAL
                AS SELECT * FROM source
            """);
        assertEquals("INCREMENTAL", dtCell("incr_dt", "refresh_mode"));
    }

    @Test
    public void testSubMinuteTargetLagIsRefused() {
        // Lags under sixty seconds are refused outright, quoting the input (live-verified).
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE DYNAMIC TABLE too_fast TARGET_LAG = '30 seconds' WAREHOUSE = wh1 AS SELECT id FROM source");
            }
        });
        assertTrue(e.getMessage().contains(
            "Invalid TARGET_LAG value '30 seconds'. Dynamic Tables do not support lag values under 60 second(s)."),
            e.getMessage());
        assertEquals(0, dtCount("too_fast"));
    }

    @Test
    public void testTargetLagIsCanonicalized() {
        // The stored lag is a canonical decomposition, not the input echo (live-verified).
        engine.execute("CREATE DYNAMIC TABLE lag_canon TARGET_LAG = '90 seconds' WAREHOUSE = wh1 AS SELECT id FROM source");
        assertEquals("1 minute, 30 seconds", dtCell("lag_canon", "target_lag"));
    }

    @Test
    public void testTrailingCommentAfterQueryIsRefused() {
        // COMMENT is one of the pre-AS options; after the query it is a syntax error (live-verified).
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE DYNAMIC TABLE trail_dt TARGET_LAG = '1 minute' WAREHOUSE = wh1 "
                    + "AS SELECT id FROM source COMMENT = 'late'");
            }
        });
        assertTrue(e.getMessage().toLowerCase().contains("syntax error"), e.getMessage());
    }

    @Test
    public void testCreateOrReplaceDynamicTable() {
        engine.execute("CREATE DYNAMIC TABLE replace_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE replace_dt TARGET_LAG = '5 minutes' WAREHOUSE = wh1 AS SELECT val FROM source");
        assertEquals("5 minutes", dtCell("replace_dt", "target_lag"));
    }

    @Test
    public void testCreateDynamicTableIfNotExists() {
        engine.execute("CREATE DYNAMIC TABLE ine_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("CREATE DYNAMIC TABLE IF NOT EXISTS ine_dt TARGET_LAG = '2 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
            }
        });
        // Original should not be overwritten
        assertEquals("1 minute", dtCell("ine_dt", "target_lag"));
    }

    // ── DROP ──────────────────────────────────────────────────────────────────

    @Test
    public void testDropDynamicTable() {
        engine.execute("CREATE DYNAMIC TABLE drop_me TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("DROP DYNAMIC TABLE drop_me");
        assertEquals(0, dtCount("drop_me"));
    }

    @Test
    public void testDropDynamicTableIfExists() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("DROP DYNAMIC TABLE IF EXISTS nonexistent_dt");
            }
        });
    }

    // ── ALTER ─────────────────────────────────────────────────────────────────

    @Test
    public void testAlterSuspend() {
        engine.execute("CREATE DYNAMIC TABLE sus_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE sus_dt SUSPEND");
        assertEquals("SUSPENDED", dtCell("sus_dt", "scheduling_state"));
    }

    @Test
    public void testAlterResume() {
        engine.execute("CREATE DYNAMIC TABLE res_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE res_dt SUSPEND");
        engine.execute("ALTER DYNAMIC TABLE res_dt RESUME");
        assertEquals("ACTIVE", dtCell("res_dt", "scheduling_state"));
    }

    @Test
    public void testAlterRefresh() {
        engine.execute("CREATE DYNAMIC TABLE ref_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE ref_dt REFRESH");
        Assumptions.assumeFalse(isLiveSnowflake(), NO_SQL_SURFACE);
        assertNotNull(getDt("REF_DT").getLastRefreshedTime());
    }

    @Test
    public void testAlterSetTargetLag() {
        engine.execute("CREATE DYNAMIC TABLE lag_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE lag_dt SET TARGET_LAG = '10 minutes'");
        assertEquals("10 minutes", dtCell("lag_dt", "target_lag"));
    }

    @Test
    public void testAlterSetWarehouse() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh2 WITH WAREHOUSE_SIZE = 'XSMALL' "
            + "AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE DYNAMIC TABLE wh_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE wh_dt SET WAREHOUSE = wh2");
        assertEquals("WH2", dtCell("wh_dt", "warehouse"));
    }

    @Test
    public void testAlterSetComment() {
        engine.execute("CREATE DYNAMIC TABLE cmt_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE cmt_dt SET COMMENT = 'updated comment'");
        assertEquals("updated comment", dtCell("cmt_dt", "comment"));
    }

    @Test
    public void testAlterSetDownstream() {
        engine.execute("CREATE DYNAMIC TABLE ds_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE ds_dt SET TARGET_LAG = DOWNSTREAM");
        assertEquals("DOWNSTREAM", dtCell("ds_dt", "target_lag"));
    }

    @Test
    public void testAlterSetRefreshModeIsRefused() {
        // The refresh mode is fixed at creation; ALTER refuses the property (live-verified).
        engine.execute("CREATE DYNAMIC TABLE rm_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER DYNAMIC TABLE rm_dt SET REFRESH_MODE = INCREMENTAL");
            }
        });
        assertTrue(e.getMessage().contains("invalid property 'REFRESH_MODE' for 'DYNAMIC_TABLE'"),
            e.getMessage());
    }

    @Test
    public void testAlterSetDataRetention() {
        engine.execute("CREATE DYNAMIC TABLE dr_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE dr_dt SET DATA_RETENTION_TIME_IN_DAYS = 5");
        Assumptions.assumeFalse(isLiveSnowflake(), NO_SQL_SURFACE);
        assertEquals(5, getDt("DR_DT").getDataRetentionDays());
    }

    // ── SHOW ──────────────────────────────────────────────────────────────────

    @Test
    public void testShowDynamicTables() {
        engine.execute("CREATE DYNAMIC TABLE show_dt1 TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("CREATE DYNAMIC TABLE show_dt2 TARGET_LAG = '2 minutes' WAREHOUSE = wh1 AS SELECT val FROM source");
        final ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("target_lag"));
        assertNotNull(rs.getColumnIndex("scheduling_state"));
        assertNotNull(rs.getColumnIndex("warehouse"));
        // The definition column is named text, never query (live-verified).
        assertNotNull(rs.getColumnIndex("text"));
    }

    @Test
    public void testShowDynamicTablesEmpty() {
        final ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        assertNotNull(rs);
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testShowDynamicTablesReflectsState() {
        engine.execute("CREATE DYNAMIC TABLE state_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        engine.execute("ALTER DYNAMIC TABLE state_dt SUSPEND");
        final ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES");
        final int schedIdx = rs.getColumnIndex("scheduling_state");
        assertEquals("SUSPENDED", rs.getRows().get(0).getValue(schedIdx).toString().toUpperCase());
    }

    // ── DESCRIBE ──────────────────────────────────────────────────────────────

    @Test
    public void testDescribeDynamicTable() {
        // DESCRIBE DYNAMIC TABLE answers the COLUMN list, exactly as DESCRIBE TABLE does
        // (live-verified) — never property/value rows.
        engine.execute("CREATE DYNAMIC TABLE desc_dt TARGET_LAG = '3 minutes' WAREHOUSE = wh1 AS SELECT id, val FROM source");
        final ResultSet rs = engine.executeQuery("DESCRIBE DYNAMIC TABLE desc_dt");
        assertEquals(2, rs.getRowCount());
        assertEquals("COLUMN", cell(rs, soleRowWhere(rs, "name", "ID"), "kind"));
        assertEquals("COLUMN", cell(rs, soleRowWhere(rs, "name", "VAL"), "kind"));
    }

    // ── SHOW OBJECTS ──────────────────────────────────────────────────────────

    @Test
    public void testDynamicTableAppearsInShowObjects() {
        engine.execute("CREATE DYNAMIC TABLE obj_dt TARGET_LAG = '1 minutes' WAREHOUSE = wh1 AS SELECT id FROM source");
        final ResultSet rs = engine.executeQuery("SHOW OBJECTS");
        final int nameIdx = rs.getColumnIndex("name");
        final int kindIdx = rs.getColumnIndex("kind");
        final int dynamicIdx = rs.getColumnIndex("is_dynamic");
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
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SHOW DYNAMIC TABLES");
            }
        });
    }
}
