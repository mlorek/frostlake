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

/** INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS: one row per completed graph run, built from the run history. */
public class CompleteTaskGraphsFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TASK g_root AS SELECT 1");
        engine.execute("CREATE TASK g_child AFTER g_root AS SELECT 1/0");
        engine.execute("ALTER TASK g_child RESUME");
        engine.execute("CREATE TASK g_ok AS SELECT 1");
    }

    private ResultSet graphs(final String args) {
        return engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(" + args + "))");
    }

    private Object cell(final ResultSet rs, final int row, final String column) {
        return rs.getRows().get(row).getValue(rs.getColumnIndex(column));
    }

    @Test
    public void aGraphRunFailsWhenAChildFails() {
        assertEquals(0, graphs("").getRowCount());
        engine.execute("EXECUTE TASK g_root");
        final ResultSet rs = graphs("ROOT_TASK_NAME => 'g_root'");
        assertEquals(1, rs.getRowCount());
        assertEquals("G_ROOT", cell(rs, 0, "ROOT_TASK_NAME"));
        assertEquals("TEST_DB", cell(rs, 0, "DATABASE_NAME"));
        assertEquals("FAILED", cell(rs, 0, "STATE"));
        assertEquals("EXECUTE TASK", cell(rs, 0, "SCHEDULED_FROM"));
        assertEquals("G_CHILD", cell(rs, 0, "FIRST_ERROR_TASK_NAME"));
        assertTrue(String.valueOf(cell(rs, 0, "FIRST_ERROR_MESSAGE")).contains("Division by zero"));
        assertEquals(1L, ((Number) cell(rs, 0, "GRAPH_VERSION")).longValue());
        assertEquals(1L, ((Number) cell(rs, 0, "ATTEMPT_NUMBER")).longValue());
        assertTrue(cell(rs, 0, "COMPLETED_TIME") != null);
        assertNull(cell(rs, 0, "BACKFILL_INFO"));
    }

    @Test
    public void errorOnlyKeepsTheFailedRunsAndTheLimitCuts() {
        engine.execute("EXECUTE TASK g_root");
        engine.execute("EXECUTE TASK g_ok");
        engine.execute("EXECUTE TASK g_ok");
        assertEquals(3, graphs("").getRowCount());
        final ResultSet failed = graphs("ERROR_ONLY => TRUE");
        assertEquals(1, failed.getRowCount());
        assertEquals("G_ROOT", cell(failed, 0, "ROOT_TASK_NAME"));
        assertEquals(1, graphs("RESULT_LIMIT => 1").getRowCount());
        assertEquals("SUCCEEDED", cell(graphs("ROOT_TASK_NAME => 'G_OK'"), 0, "STATE"));
    }

    @Test
    public void aResultLimitAboveTheMaximumIsRefusedAndOneBelowOneIsNoLimit() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                graphs("RESULT_LIMIT => 10001");
            }
        });
        assertTrue(refused.getMessage().contains(
            "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."), refused.getMessage());
        engine.execute("EXECUTE TASK g_ok");
        assertEquals(1, graphs("RESULT_LIMIT => 0").getRowCount());
        assertEquals(1, graphs("RESULT_LIMIT => -1").getRowCount());
    }
}
