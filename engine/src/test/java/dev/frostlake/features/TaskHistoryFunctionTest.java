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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskHistoryFunctionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS test_wh");
        engine.execute(
            "CREATE OR REPLACE TASK my_task " +
            "WAREHOUSE = test_wh " +
            "SCHEDULE = '5 MINUTE' " +
            "AS SELECT 1"
        );
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testTaskHistoryNoArgs() {
        ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY())");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("name"));
        assertNotNull(rs.getColumnIndex("state"));
        assertNotNull(rs.getColumnIndex("scheduled_time"));
    }

    @Test
    public void testTaskHistoryWithTaskName() {
        ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'my_task'))");
        assertNotNull(rs);
        // no executions yet — should return empty
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void testTaskHistoryWithResultLimit() {
        ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 10))");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() <= 10);
    }

    @Test
    public void testTaskHistoryUnqualified() {
        // Also callable without schema prefix
        ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(TASK_HISTORY())");
        assertNotNull(rs);
        assertNotNull(rs.getColumnIndex("name"));
    }
}
