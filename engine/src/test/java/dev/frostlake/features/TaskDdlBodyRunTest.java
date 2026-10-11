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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task whose body is a DDL statement runs that statement when the task does, and an alert whose action is one
 * runs it when its condition holds: the body is stored as written and re-issued, whatever family it belongs to.
 */
public class TaskDdlBodyRunTest extends BaseDatabaseTest {

    private static final String TASK_EXECUTION =
        "runs a task and reads what it did straight away: on a real account EXECUTE TASK is asynchronous";

    @Test
    public void aCreateTableBodyCreatesTheTable() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_EXECUTION);
        engine.execute("CREATE OR REPLACE TASK tk_ddl AS CREATE OR REPLACE TABLE made_by_task (x INT)");
        engine.execute("EXECUTE TASK tk_ddl");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM made_by_task");
        assertTrue(rs.next());
        assertEquals("0", String.valueOf(rs.getValue(0)));
    }

    @Test
    public void aModifiedBodyRunsTheNewStatement() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_EXECUTION);
        engine.execute("CREATE OR REPLACE TABLE dropped_by_task (x INT)");
        engine.execute("CREATE OR REPLACE TASK tk_drop AS TRUNCATE TABLE dropped_by_task");
        engine.execute("ALTER TASK tk_drop MODIFY AS DROP TABLE dropped_by_task;");
        engine.execute("EXECUTE TASK tk_drop");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM dropped_by_task");
            }
        });
        assertTrue(refused.getMessage().contains("does not exist"), refused.getMessage());
    }

    @Test
    public void anAlertsDdlActionRuns() {
        Assumptions.assumeFalse(isLiveSnowflake(), TASK_EXECUTION);
        engine.execute("""
            CREATE OR REPLACE ALERT al_ddl SCHEDULE = '60 MINUTE'
              IF (EXISTS (SELECT 1)) THEN CREATE OR REPLACE TABLE made_by_alert (x INT)
            """);
        engine.execute("EXECUTE ALERT al_ddl");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM made_by_alert");
        assertTrue(rs.next());
        assertEquals("0", String.valueOf(rs.getValue(0)));
    }
}
