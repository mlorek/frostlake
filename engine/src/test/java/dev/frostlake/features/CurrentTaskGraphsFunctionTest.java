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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** INFORMATION_SCHEMA.CURRENT_TASK_GRAPHS: the graph runs a resumed scheduled root task has coming up. */
public class CurrentTaskGraphsFunctionTest extends BaseDatabaseTest {

    @Test
    public void aResumedScheduledRootIsScheduled() {
        engine.execute("CREATE TASK c_hourly SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c_manual AS SELECT 1");
        final String sql = "SELECT * FROM TABLE(INFORMATION_SCHEMA.CURRENT_TASK_GRAPHS())";
        assertEquals(0, engine.executeQuery(sql).getRowCount(), "a suspended task has no graph run coming");
        engine.execute("ALTER TASK c_hourly RESUME");
        try {
            final ResultSet rs = engine.executeQuery(sql);
            assertEquals(1, rs.getRowCount());
            assertEquals("C_HOURLY", rs.getRows().get(0).getValue(rs.getColumnIndex("ROOT_TASK_NAME")));
            assertEquals("SCHEDULED", rs.getRows().get(0).getValue(rs.getColumnIndex("STATE")));
            assertEquals("SCHEDULE", rs.getRows().get(0).getValue(rs.getColumnIndex("SCHEDULED_FROM")));
            assertTrue(rs.getRows().get(0).getValue(rs.getColumnIndex("SCHEDULED_TIME")) != null);
            assertEquals(19, rs.getColumns().size());
            assertEquals("SCHEDULED_FROM", rs.getColumns().get(14).getName(), "after ATTEMPT_NUMBER");
            assertEquals("SCHEDULED_BY_USER", rs.getColumns().get(18).getName());
            assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.CURRENT_TASK_GRAPHS("
                + "ROOT_TASK_NAME => 'c_manual'))").getRowCount());
        } finally {
            engine.execute("ALTER TASK c_hourly SUSPEND");
        }
    }
}
