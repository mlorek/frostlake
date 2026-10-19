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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The session's current warehouse — what makes a warehouse current and what leaves the session without one — and
 * the cells SHOW WAREHOUSES tells of a warehouse's health and query acceleration.
 */
public class SessionWarehouseTest extends BaseDatabaseTest {

    private static final String[] WAREHOUSES = {"sw_a", "sw_a2", "sw_b", "sw_run", "sw_sp", "sw_off", "sw_scale"};

    private Object initial;

    @BeforeEach
    public void rememberTheWarehouse() {
        initial = current();
    }

    /** The warehouses are the account's, not the test database's: none is left behind, nor the session without one. */
    @AfterEach
    public void dropWarehouses() {
        for (final String name : WAREHOUSES) {
            engine.execute("DROP WAREHOUSE IF EXISTS " + name);
        }
        if (initial != null) {
            engine.execute("USE WAREHOUSE " + initial);
        }
    }

    private Object current() {
        return engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String shown(final String name, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE '" + name + "'");
        final Row row = soleRowWhere(rs, "name", name);
        return cell(rs, row, column);
    }

    @Test
    public void aCreatedWarehouseBecomesCurrentEvenOneThatWasAlreadyThere() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS sw_a WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        assertEquals("SW_A", current());
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_b WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        assertEquals("SW_B", current());
        assertEquals("SW_A already exists, statement succeeded.", engine.executeQuery(
            "CREATE WAREHOUSE IF NOT EXISTS sw_a WAREHOUSE_SIZE = XSMALL").getRows().get(0).getValue(0));
        assertEquals("SW_A", current());
        assertEquals("Y", shown("SW_A", "is_current"));
        assertEquals("N", shown("SW_B", "is_current"));
    }

    @Test
    public void aDroppedOrRenamedCurrentWarehouseLeavesTheSessionWithoutOne() {
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_a WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_b WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        engine.execute("USE WAREHOUSE sw_a");
        engine.execute("DROP WAREHOUSE sw_b");
        assertEquals("SW_A", current(), "dropping another warehouse changes nothing");
        engine.execute("ALTER WAREHOUSE sw_a RENAME TO sw_a2");
        assertNull(current());
        engine.execute("USE WAREHOUSE sw_a2");
        assertEquals("SW_A2", current());
        engine.execute("DROP WAREHOUSE sw_a2");
        assertNull(current());
    }

    @Test
    public void aWarehouseThatIsNotThereIsRefusedWithoutItsName() {
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_a WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("USE WAREHOUSE sw_nosuch_zz"));
        assertEquals("SQL compilation error:\nObject does not exist, or operation cannot be performed.",
            refusal("USE WAREHOUSE \"sw_lower\""));
        assertEquals("SW_A", current());
    }

    @Test
    public void aSuspendedWarehouseTellsNothingOfItsHealthAndAStartedOneAllOfIt() {
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_a WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        for (final String column : new String[] {"available", "provisioning", "quiescing", "other"}) {
            assertEquals("", shown("SW_A", column), column);
        }
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_run WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60");
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE 'SW_RUN'");
        final Row row = soleRowWhere(rs, "name", "SW_RUN");
        // A live warehouse may still be starting when it is listed; its health is told once it has started.
        if ("STARTED".equals(cell(rs, row, "state"))) {
            assertEquals(" 100", cell(rs, row, "available"));
            assertEquals("0", cell(rs, row, "provisioning"));
            assertEquals("0", cell(rs, row, "quiescing"));
            assertEquals("0", cell(rs, row, "other"));
        }
    }

    @Test
    public void queryAccelerationIsOnForAStandardWarehouseUnlessTurnedOff() {
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_a WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_sp WAREHOUSE_TYPE = 'SNOWPARK-OPTIMIZED' WAREHOUSE_SIZE = MEDIUM"
            + " INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_off WAREHOUSE_SIZE = XSMALL ENABLE_QUERY_ACCELERATION = FALSE"
            + " INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE OR REPLACE WAREHOUSE sw_scale WAREHOUSE_SIZE = XSMALL"
            + " QUERY_ACCELERATION_MAX_SCALE_FACTOR = 3 INITIALLY_SUSPENDED = TRUE");
        assertEquals("true", shown("SW_A", "enable_query_acceleration"));
        assertEquals("8", shown("SW_A", "query_acceleration_max_scale_factor"));
        assertEquals("false", shown("SW_SP", "enable_query_acceleration"));
        assertEquals("false", shown("SW_OFF", "enable_query_acceleration"));
        assertEquals("true", shown("SW_SCALE", "enable_query_acceleration"));
        assertEquals("3", shown("SW_SCALE", "query_acceleration_max_scale_factor"));
        engine.execute("ALTER WAREHOUSE sw_off SET ENABLE_QUERY_ACCELERATION = TRUE");
        assertEquals("true", shown("SW_OFF", "enable_query_acceleration"));
        engine.execute("ALTER WAREHOUSE sw_off SET ENABLE_QUERY_ACCELERATION = FALSE");
        engine.execute("ALTER WAREHOUSE sw_off UNSET ENABLE_QUERY_ACCELERATION");
        assertEquals("true", shown("SW_OFF", "enable_query_acceleration"), "unset puts the default back");
        engine.execute("ALTER WAREHOUSE sw_scale UNSET QUERY_ACCELERATION_MAX_SCALE_FACTOR");
        assertEquals("8", shown("SW_SCALE", "query_acceleration_max_scale_factor"));
    }
}
