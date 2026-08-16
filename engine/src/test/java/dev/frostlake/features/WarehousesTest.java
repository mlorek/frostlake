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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAREHOUSE DDL and state, asserted through the SQL surface — {@code SHOW WAREHOUSES} cells
 * (state {@code STARTED}/{@code SUSPENDED}, size as its display name, auto_suspend/auto_resume)
 * and {@code CURRENT_WAREHOUSE()} — so every check runs against whichever engine executed the
 * DDL, embedded or live. Model-level warehouse accounting stays in {@code WarehouseModelTest}.
 */
public class WarehousesTest extends BaseDatabaseTest {

    private String warehouseCell(final String name, final String column) {
        final ResultSet warehouses = engine.executeQuery("SHOW WAREHOUSES LIKE '" + name + "'");
        return cell(warehouses, soleRowWhere(warehouses, "name", name.toUpperCase()), column);
    }

    /**
     * Live suspends and resumes settle ASYNCHRONOUSLY: right after the statement, the state cell
     * may still read the transitional spelling (SUSPENDING, STARTING, RESUMING). The assert
     * accepts the transition toward the expected state — never a state in the other direction.
     */
    private void assertStateSettlingTo(final String name, final String expected) {
        final String state = warehouseCell(name, "state");
        final boolean acceptable = "SUSPENDED".equals(expected)
            ? "SUSPENDED".equals(state) || "SUSPENDING".equals(state)
            : "STARTED".equals(state) || "STARTING".equals(state) || "RESUMING".equals(state);
        assertTrue(acceptable, name + " should settle to " + expected + " but reads " + state);
    }

    // ==================== WAREHOUSE CREATION TESTS ====================

    @Test
    public void testDefaultWarehouseExists() {
        // The engine ships COMPUTE_WH; on a live account the harness itself relies on it.
        assertEquals(1, engine.executeQuery("SHOW WAREHOUSES LIKE 'COMPUTE_WH'").getRowCount());
    }

    @Test
    public void testCreateWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        assertEquals("Small", warehouseCell("test_wh", "size"));
    }

    @Test
    public void testCreateWarehouseWithUnquotedSize() {
        // WAREHOUSE_SIZE = MEDIUM (unquoted identifier) — Snowflake accepts it; it previously threw an NPE.
        engine.execute("CREATE WAREHOUSE uq_wh WAREHOUSE_SIZE = MEDIUM");
        assertEquals("Medium", warehouseCell("uq_wh", "size"));
    }

    @Test
    public void testCreateWarehouseWithSessionVariableSize() {
        // WAREHOUSE_SIZE = $var — the size comes from a session variable. Previously this threw an NPE
        // that IF NOT EXISTS silently swallowed, leaving the warehouse uncreated.
        engine.execute("SET whsz = (SELECT 'LARGE')");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS sv_wh WAREHOUSE_SIZE = $whsz MAX_CLUSTER_COUNT = 2");
        assertEquals("Large", warehouseCell("sv_wh", "size"));
    }

    @Test
    public void testCreateWarehouseWithOptions() {
        engine.execute("""
            CREATE WAREHOUSE analytics_wh
            WITH WAREHOUSE_SIZE = 'LARGE'
            AUTO_SUSPEND = 300
            AUTO_RESUME = true
            """);

        assertEquals("Large", warehouseCell("analytics_wh", "size"));
        assertEquals("300", warehouseCell("analytics_wh", "auto_suspend"));
        assertEquals("true", warehouseCell("analytics_wh", "auto_resume"));
    }

    @Test
    public void testCreateWarehouseVariousSizes() {
        engine.execute("CREATE WAREHOUSE wh_xs WITH WAREHOUSE_SIZE = 'X-SMALL'");
        engine.execute("CREATE WAREHOUSE wh_s WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh_m WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("CREATE WAREHOUSE wh_l WITH WAREHOUSE_SIZE = 'LARGE'");

        assertEquals("X-Small", warehouseCell("wh_xs", "size"));
        assertEquals("Small", warehouseCell("wh_s", "size"));
        assertEquals("Medium", warehouseCell("wh_m", "size"));
        assertEquals("Large", warehouseCell("wh_l", "size"));
    }

    // ==================== WAREHOUSE STATE TESTS ====================

    @Test
    public void testNewWarehouseIsRunning() {
        // A new warehouse starts running unless INITIALLY_SUSPENDED says otherwise (live-verified).
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        assertStateSettlingTo("test_wh", "STARTED");
    }

    @Test
    public void testInitiallySuspendedWarehouseIsSuspended() {
        engine.execute("CREATE WAREHOUSE susp_wh WITH WAREHOUSE_SIZE = 'SMALL' INITIALLY_SUSPENDED = TRUE");

        assertEquals("SUSPENDED", warehouseCell("susp_wh", "state"));
    }

    @Test
    public void testResumeWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL' INITIALLY_SUSPENDED = TRUE");
        engine.execute("ALTER WAREHOUSE test_wh RESUME");

        assertStateSettlingTo("test_wh", "STARTED");
    }

    @Test
    public void testResumingARunningWarehouseIsRefused() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE test_wh RESUME");
            }
        });
        assertTrue(e.getMessage().contains(
            "cannot be resumed since it is not suspended"), e.getMessage());

        // IF SUSPENDED makes the same statement a no-op instead — and IF EXISTS does NOT forgive
        // the invalid state, only a missing warehouse.
        engine.execute("ALTER WAREHOUSE test_wh RESUME IF SUSPENDED");
        assertStateSettlingTo("test_wh", "STARTED");

        final RuntimeException underIfExists = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE IF EXISTS test_wh RESUME");
            }
        });
        assertTrue(underIfExists.getMessage().contains(
            "cannot be resumed since it is not suspended"), underIfExists.getMessage());
    }

    @Test
    public void testResumeIfSuspendedResumesASuspendedWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL' INITIALLY_SUSPENDED = TRUE");

        engine.execute("ALTER WAREHOUSE test_wh RESUME IF SUSPENDED");

        assertStateSettlingTo("test_wh", "STARTED");
    }

    @Test
    public void testSuspendingASuspendedWarehouseIsRefused() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL' INITIALLY_SUSPENDED = TRUE");

        // Unlike RESUME, SUSPEND has no IF form: repeating it is an error (live-verified).
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE test_wh SUSPEND");
            }
        });
        assertTrue(e.getMessage().contains("cannot be suspended"), e.getMessage());
    }

    @Test
    public void testUnsetCommentAndAutoSuspend() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL' AUTO_SUSPEND = 120");
        engine.execute("ALTER WAREHOUSE test_wh SET COMMENT = 'to be removed'");

        assertEquals("120", warehouseCell("test_wh", "auto_suspend"));
        assertEquals("to be removed", warehouseCell("test_wh", "comment"));

        // UNSET leaves auto_suspend EMPTY — not back at its creation default (live-verified).
        engine.execute("ALTER WAREHOUSE test_wh UNSET AUTO_SUSPEND, COMMENT");

        assertNull(warehouseCell("test_wh", "auto_suspend"));
        assertEquals("", warehouseCell("test_wh", "comment"));
    }

    @Test
    public void testUnsetUnknownPropertyIsRefused() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE test_wh UNSET NO_SUCH_PROP");
            }
        });
        assertTrue(e.getMessage().contains("invalid property 'NO_SUCH_PROP' for 'WAREHOUSE'"),
            e.getMessage());
    }

    @Test
    public void testAbortAllQueriesIsAccepted() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        engine.execute("ALTER WAREHOUSE test_wh ABORT ALL QUERIES");

        assertStateSettlingTo("test_wh", "STARTED");
    }

    @Test
    public void testIfExistsForgivesOnlyAbsence() {
        engine.execute("ALTER WAREHOUSE IF EXISTS no_such_wh SUSPEND");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE no_such_wh SUSPEND");
            }
        });
        assertTrue(e.getMessage().contains("does not exist or not authorized"), e.getMessage());
    }

    @Test
    public void testSuspendWarehouse() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SUSPEND");

        assertStateSettlingTo("test_wh", "SUSPENDED");
    }

    @Test
    public void testResumeSuspendCycle() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");

        assertStateSettlingTo("test_wh", "STARTED");

        engine.execute("ALTER WAREHOUSE test_wh SUSPEND");
        assertStateSettlingTo("test_wh", "SUSPENDED");

        engine.execute("ALTER WAREHOUSE test_wh RESUME");
        assertStateSettlingTo("test_wh", "STARTED");
    }

    // ==================== ALTER WAREHOUSE TESTS ====================

    @Test
    public void testAlterWarehouseSize() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET WAREHOUSE_SIZE = 'LARGE'");

        assertEquals("Large", warehouseCell("test_wh", "size"));
    }

    @Test
    public void testAlterWarehouseAutoSuspend() {
        engine.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("ALTER WAREHOUSE test_wh SET AUTO_SUSPEND = 120");

        assertEquals("120", warehouseCell("test_wh", "auto_suspend"));
    }

    // ==================== USE WAREHOUSE TESTS ====================

    @Test
    public void testUseWarehouse() {
        engine.execute("CREATE WAREHOUSE analytics_wh WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("USE WAREHOUSE analytics_wh");

        assertEquals("ANALYTICS_WH",
            engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0));
    }

    @Test
    public void testSwitchWarehouses() {
        engine.execute("CREATE WAREHOUSE wh1 WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh2 WITH WAREHOUSE_SIZE = 'LARGE'");

        engine.execute("USE WAREHOUSE wh1");
        assertEquals("WH1",
            engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0));

        engine.execute("USE WAREHOUSE wh2");
        assertEquals("WH2",
            engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0));
    }

    // ==================== DROP WAREHOUSE TESTS ====================

    @Test
    public void testDropWarehouse() {
        engine.execute("CREATE WAREHOUSE temp_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("DROP WAREHOUSE temp_wh");

        assertEquals(0, engine.executeQuery("SHOW WAREHOUSES LIKE 'temp_wh'").getRowCount());
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    public void testWarehouseWithQueryExecution() {
        engine.execute("CREATE WAREHOUSE query_wh WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("USE WAREHOUSE query_wh");

        // Execute a query
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Test')");

        assertStateSettlingTo("query_wh", "STARTED");
    }

    @Test
    public void testListWarehouses() {
        engine.execute("CREATE WAREHOUSE wh1 WITH WAREHOUSE_SIZE = 'SMALL'");
        engine.execute("CREATE WAREHOUSE wh2 WITH WAREHOUSE_SIZE = 'MEDIUM'");
        engine.execute("CREATE WAREHOUSE wh3 WITH WAREHOUSE_SIZE = 'LARGE'");

        assertTrue(engine.executeQuery("SHOW WAREHOUSES").getRowCount() >= 4,
            "the three created warehouses plus COMPUTE_WH should be listed");
    }

    @Test
    public void createWarehouseWithGenerationClause() {
        // GENERATION (a Snowflake warehouse-generation property) must parse and be retained.
        engine.execute("""
            CREATE WAREHOUSE IF NOT EXISTS gen_wh
                WAREHOUSE_SIZE                      = 'MEDIUM'
                ENABLE_QUERY_ACCELERATION           = TRUE
                QUERY_ACCELERATION_MAX_SCALE_FACTOR = 4
                STATEMENT_TIMEOUT_IN_SECONDS        = 21600
                GENERATION                          = '1'
            """);
        assertEquals("Medium", warehouseCell("gen_wh", "size"));
    }
}
