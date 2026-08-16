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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskHistoryFunctionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS test_wh");
        engine.execute(
            "CREATE OR REPLACE TASK my_task " +
            "WAREHOUSE = test_wh " +
            "SCHEDULE = '5 MINUTE' " +
            "AS SELECT 1"
        );
    }

    @Test
    public void testTaskHistoryNoArgs() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY())");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("state"));
        assertNotNull(rs.getColumnIndex("scheduled_time"));
    }

    @Test
    public void testTaskHistoryWithTaskName() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'my_task'))");
        assertNotNull(rs);
        // no executions yet — should return empty
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testTaskHistoryWithResultLimit() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 10))");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() <= 10);
    }

    /**
     * TASK_HISTORY lives ONLY under INFORMATION_SCHEMA — the bare name resolves to nothing. This test
     * asserted the opposite, which is a spelling no account runs; the database-qualified form is the
     * other one that works.
     */
    @Test
    public void theBareNameIsNotAFunction() {
        final RuntimeException thrown = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(TASK_HISTORY())");
            }
        });
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertEquals("SQL compilation error:\nInvalid identifier TASK_HISTORY", root.getMessage());

        final ResultSet qualified = engine.executeQuery(
            "SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 1))");
        assertNotNull(qualified);
    }
}
