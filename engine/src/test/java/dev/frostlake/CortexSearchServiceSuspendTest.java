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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ALTER CORTEX SEARCH SERVICE … SUSPEND | RESUME [INDEXING | SERVING], read back through SHOW. */
public class CortexSearchServiceSuspendTest extends BaseDatabaseTest {

    @BeforeEach
    public void createService() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS fl_css_suspend_wh WAREHOUSE_SIZE = XSMALL "
            + "INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE TABLE docs (id INT, body STRING)");
        engine.execute("CREATE CORTEX SEARCH SERVICE svc ON body WAREHOUSE = fl_css_suspend_wh "
            + "TARGET_LAG = '1 hour' AS SELECT id, body FROM docs");
    }

    @AfterEach
    public void dropWarehouse() {
        engine.execute("DROP WAREHOUSE IF EXISTS fl_css_suspend_wh");
    }

    private String state(final String column) {
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES LIKE 'SVC'");
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column)).toString();
    }

    @Test
    public void aNewServiceIndexesAndServes() {
        assertEquals("ACTIVE", state("indexing_state"));
        assertEquals("RUNNING", state("serving_state"));
    }

    @Test
    public void suspendWithoutATargetStopsBothLayers() {
        engine.execute("ALTER CORTEX SEARCH SERVICE svc SUSPEND");
        assertEquals("SUSPENDED", state("indexing_state"));
        assertEquals("SUSPENDED", state("serving_state"));
        engine.execute("ALTER CORTEX SEARCH SERVICE svc RESUME");
        assertEquals("ACTIVE", state("indexing_state"));
        assertEquals("RUNNING", state("serving_state"));
    }

    @Test
    public void aTargetNamesTheOneLayer() {
        engine.execute("ALTER CORTEX SEARCH SERVICE svc SUSPEND INDEXING");
        assertEquals("SUSPENDED", state("indexing_state"));
        assertEquals("RUNNING", state("serving_state"));
        engine.execute("ALTER CORTEX SEARCH SERVICE svc SUSPEND SERVING");
        engine.execute("ALTER CORTEX SEARCH SERVICE svc RESUME INDEXING");
        assertEquals("ACTIVE", state("indexing_state"));
        assertEquals("SUSPENDED", state("serving_state"));
    }

    @Test
    public void aMissingServiceIsRefusedUnlessIfExists() {
        final Throwable refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER CORTEX SEARCH SERVICE nosuch SUSPEND");
            }
        });
        assertTrue(refused.getMessage().contains("does not exist or not authorized"), refused.getMessage());
        engine.execute("ALTER CORTEX SEARCH SERVICE IF EXISTS nosuch RESUME SERVING");
    }

    @Test
    public void theNewWordsStillNameColumns() {
        engine.execute("CREATE TABLE words (indexing INT, serving INT)");
        engine.execute("INSERT INTO words VALUES (1, 2)");
        final ResultSet rs = engine.executeQuery("SELECT indexing + serving AS total FROM words");
        assertEquals("3", rs.getRows().get(0).getValue(0).toString());
    }
}
