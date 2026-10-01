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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RESOURCE_CONSTRAINT, WAIT_FOR_COMPLETION, adaptive warehouses and ALTER WAREHOUSE … ENABLE | DISABLE. */
public class WarehouseResourceConstraintTest extends BaseDatabaseTest {

    private String show(final String warehouse, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW WAREHOUSES LIKE '" + warehouse + "'");
        return cell(rs, soleRowWhere(rs, "name", warehouse), column);
    }

    @Test
    public void theResourceConstraintFollowsTheGenerationUnlessSet() {
        engine.execute("CREATE WAREHOUSE rc_std");
        assertEquals("STANDARD_GEN_2", show("RC_STD", "resource_constraint"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE rc_std SET RESOURCE_CONSTRAINT = STANDARD_GEN_1");
            }
        });
        assertEquals("Cannot set resource constraint to 'STANDARD_GEN_1'. Use the GENERATION property to set"
            + " warehouse hardware generation.\n", refused.getMessage());
        engine.execute("ALTER WAREHOUSE rc_std SET GENERATION = '1'");
        assertEquals("STANDARD_GEN_1", show("RC_STD", "resource_constraint"));
        assertEquals("1", show("RC_STD", "generation"));
        engine.execute("ALTER WAREHOUSE rc_std UNSET GENERATION");
        assertEquals("STANDARD_GEN_2", show("RC_STD", "resource_constraint"));
        engine.execute("CREATE WAREHOUSE rc_sp WAREHOUSE_TYPE = 'SNOWPARK-OPTIMIZED' RESOURCE_CONSTRAINT = 'memory_1x_x86'");
        assertEquals("MEMORY_1X_x86", show("RC_SP", "resource_constraint"));
        assertEquals("X-Small", show("RC_SP", "size"), "a 1X constraint keeps the X-Small default");
        assertEquals(null, show("RC_SP", "generation"));
        final RuntimeException tooSmall = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE rc_sp UNSET RESOURCE_CONSTRAINT");
            }
        });
        assertTrue(tooSmall.getMessage().contains("invalid property combination 'RESOURCE_CONSTRAINT'='MEMORY_16X'"
            + " and 'WAREHOUSE_SIZE'='X-Small'"), tooSmall.getMessage());
        engine.execute("CREATE WAREHOUSE rc_sp16 WAREHOUSE_TYPE = 'SNOWPARK-OPTIMIZED'");
        assertEquals("Medium", show("RC_SP16", "size"), "the 16X default makes a MEDIUM warehouse");
        final RuntimeException mismatch = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE rc_std SET RESOURCE_CONSTRAINT = MEMORY_1X");
            }
        });
        assertTrue(mismatch.getMessage().contains("invalid property combination 'WAREHOUSE_TYPE'='STANDARD' and"
            + " 'RESOURCE_CONSTRAINT'='MEMORY_1X'"), mismatch.getMessage());
    }

    @Test
    public void anUndocumentedConstraintIsRefusedAndCreatesNothing() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE WAREHOUSE rc_bad RESOURCE_CONSTRAINT = MEMORY_2X");
            }
        });
        assertTrue(refused.getMessage().contains("RESOURCE_CONSTRAINT"), refused.getMessage());
        assertEquals(0, engine.executeQuery("SHOW WAREHOUSES LIKE 'RC_BAD'").getRows().size());
    }

    @Test
    public void waitForCompletionIsAnAlterProperty() {
        engine.execute("CREATE WAREHOUSE rc_wait");
        engine.execute("ALTER WAREHOUSE rc_wait SET WAREHOUSE_SIZE = SMALL WAIT_FOR_COMPLETION = TRUE");
        assertEquals("Small", show("RC_WAIT", "size"));
        final RuntimeException alone = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE rc_wait SET WAIT_FOR_COMPLETION = TRUE");
            }
        });
        assertEquals("Property WAREHOUSE_SIZE must be specified when using WAIT_FOR_COMPLETION", alone.getMessage());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE WAREHOUSE rc_wait2 WAIT_FOR_COMPLETION = TRUE");
            }
        });
        assertEquals(0, engine.executeQuery("SHOW WAREHOUSES LIKE 'RC_WAIT2'").getRows().size());
    }

    @Test
    public void onlyAnAdaptiveWarehouseIsEnabledOrDisabled() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE WAREHOUSE rc_adaptive WAREHOUSE_TYPE = ADAPTIVE INITIALLY_SUSPENDED = TRUE");
            }
        });
        engine.execute("CREATE WAREHOUSE rc_adaptive WAREHOUSE_TYPE = ADAPTIVE");
        assertEquals("ADAPTIVE", show("RC_ADAPTIVE", "type"));
        engine.execute("ALTER WAREHOUSE rc_adaptive DISABLE");
        engine.execute("ALTER WAREHOUSE rc_adaptive ENABLE");
        engine.execute("CREATE WAREHOUSE rc_standard");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER WAREHOUSE rc_standard DISABLE");
            }
        });
    }
}
