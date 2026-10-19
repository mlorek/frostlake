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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER WAREHOUSE … UNSET puts each property back on its default — MAX_CONCURRENCY_LEVEL 8, the queued timeout 0,
 * the statement timeout 172800, the acceleration scale factor 8, the type STANDARD — and blanks the level SHOW
 * PARAMETERS tells; a warehouse whose type is altered keeps the acceleration setting it has. The size cannot be
 * unset, INITIALLY_SUSPENDED is no property to unset, and a property named twice is a duplicate (live-verified).
 * The warehouse the cells alter is made suspended and never runs: every statement here is a SHOW or an ALTER.
 */
public class WarehouseUnsetTest extends BaseDatabaseTest {

    private static final String WAREHOUSE = "FL_UNSET_WH";

    @Override
    protected void setupTest() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS " + WAREHOUSE
            + " WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE AUTO_RESUME = FALSE");
    }

    @AfterEach
    public void dropTheWarehouse() {
        try {
            engine.execute("DROP WAREHOUSE IF EXISTS " + WAREHOUSE);
        } catch (final RuntimeException alreadyGone) {
            // Best effort: the base's teardown drops what the run created, too.
        }
    }

    /** A parameter's value and the level it is set at, as SHOW PARAMETERS IN WAREHOUSE tells them. */
    private String parameter(final String key) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + key + "' IN WAREHOUSE " + WAREHOUSE);
        final Row row = soleRowWhere(rs, "key", key);
        return cell(rs, row, "value") + "@" + cell(rs, row, "level");
    }

    private String shown(final String column) {
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE '" + WAREHOUSE + "'");
        return String.valueOf(cell(rs, soleRowWhere(rs, "name", WAREHOUSE), column)).toLowerCase(Locale.ROOT);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void theParametersGoBackToTheirDefaults() {
        assertEquals("8@", parameter("MAX_CONCURRENCY_LEVEL"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " SET MAX_CONCURRENCY_LEVEL = 4"
            + " STATEMENT_QUEUED_TIMEOUT_IN_SECONDS = 100 STATEMENT_TIMEOUT_IN_SECONDS = 200");
        assertEquals("4@WAREHOUSE", parameter("MAX_CONCURRENCY_LEVEL"));
        assertEquals("100@WAREHOUSE", parameter("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS"));
        assertEquals("200@WAREHOUSE", parameter("STATEMENT_TIMEOUT_IN_SECONDS"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " UNSET MAX_CONCURRENCY_LEVEL");
        assertEquals("8@", parameter("MAX_CONCURRENCY_LEVEL"));
        assertEquals("100@WAREHOUSE", parameter("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE
            + " UNSET STATEMENT_QUEUED_TIMEOUT_IN_SECONDS, STATEMENT_TIMEOUT_IN_SECONDS");
        assertEquals("0@", parameter("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS"));
        assertEquals("172800@", parameter("STATEMENT_TIMEOUT_IN_SECONDS"));
    }

    @Test
    public void theAccelerationSettingsAndTheTypeGoBack() {
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE
            + " SET ENABLE_QUERY_ACCELERATION = TRUE QUERY_ACCELERATION_MAX_SCALE_FACTOR = 3");
        assertEquals("true", shown("enable_query_acceleration"));
        assertEquals("3", shown("query_acceleration_max_scale_factor"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE
            + " UNSET ENABLE_QUERY_ACCELERATION, QUERY_ACCELERATION_MAX_SCALE_FACTOR");
        assertEquals("true", shown("enable_query_acceleration"));
        assertEquals("8", shown("query_acceleration_max_scale_factor"));
        // A warehouse altered to another type keeps the acceleration setting it has.
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE
            + " SET WAREHOUSE_SIZE = MEDIUM WAREHOUSE_TYPE = 'SNOWPARK-OPTIMIZED'");
        assertEquals("snowpark-optimized", shown("type"));
        assertEquals("true", shown("enable_query_acceleration"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " UNSET WAREHOUSE_TYPE");
        assertEquals("standard", shown("type"));
        assertEquals("medium", shown("size"));
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " SET COMMENT = 'c' AUTO_SUSPEND = 120");
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " UNSET COMMENT, AUTO_SUSPEND");
        assertEquals("null", shown("auto_suspend"));
    }

    @Test
    public void whatAnUnsetListRefuses() {
        assertTrue(refusal("ALTER WAREHOUSE " + WAREHOUSE + " UNSET WAREHOUSE_SIZE")
            .contains("invalid type of property 'null' for 'WAREHOUSE_SIZE'"));
        assertTrue(refusal("ALTER WAREHOUSE " + WAREHOUSE + " UNSET INITIALLY_SUSPENDED")
            .contains("invalid property 'INITIALLY_SUSPENDED' for 'WAREHOUSE'"));
        assertTrue(refusal("ALTER WAREHOUSE " + WAREHOUSE + " UNSET MAX_CONCURRENCY_LEVEL, NOSUCH_PROPERTY")
            .contains("invalid property 'NOSUCH_PROPERTY' for 'WAREHOUSE'"));
        assertTrue(refusal("ALTER WAREHOUSE " + WAREHOUSE + " UNSET MAX_CONCURRENCY_LEVEL, MAX_CONCURRENCY_LEVEL")
            .contains("duplicate property 'MAX_CONCURRENCY_LEVEL';"));
        assertTrue(refusal("ALTER WAREHOUSE nosuch_wh_p1306 UNSET MAX_CONCURRENCY_LEVEL")
            .contains("Warehouse 'NOSUCH_WH_P1306' does not exist or not authorized."));
        engine.execute("ALTER WAREHOUSE IF EXISTS nosuch_wh_p1306 UNSET WAREHOUSE_TYPE");
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " UNSET MAX_CLUSTER_COUNT, MIN_CLUSTER_COUNT, SCALING_POLICY");
        engine.execute("ALTER WAREHOUSE " + WAREHOUSE + " UNSET RESOURCE_MONITOR");
    }
}
