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

package dev.frostlake.task;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * How a task graph may change. A graph "starts with a scheduled root task and is linked together by dependencies"
 * (documented), so no statement makes a task its own ancestor (FL-438); and the root "must be suspended before any
 * task is recreated" and before "a child task is added (using CREATE TASK … AFTER or ALTER TASK … ADD AFTER) or
 * removed (using ALTER TASK … REMOVE AFTER)" (documented), which CREATE OR ALTER of a child follows too (FL-439,
 * #2340). The sentences of the cycle refusal are not measured, so the engine alone is asked.
 */
public class TaskGraphChangeTest extends BaseDatabaseTest {

    private static final String PREFIX = "TEST_DB.TEST_SCHEMA.";

    private static final String RUNNING = "Unable to update graph with root task " + PREFIX
        + "GC_R since that root task is not suspended.";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "the cycle refusal's sentence is not measured");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String taskCell(final String task, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, soleRowWhere(tasks, "name", task), column).replaceAll("\\s", "");
    }

    @Test
    public void noStatementMakesATaskItsOwnAncestor() {
        engine.execute("CREATE TASK gc_a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK gc_b AFTER gc_a AS SELECT 2");
        engine.execute("CREATE TASK gc_c AFTER gc_b AS SELECT 3");
        assertTrue(refusalOf("CREATE OR ALTER TASK gc_a AFTER gc_c AS SELECT 1")
            .contains("a task graph cannot have a cycle"));
        assertTrue(refusalOf("CREATE OR REPLACE TASK gc_a AFTER gc_b AS SELECT 1")
            .contains("a task graph cannot have a cycle"));
        assertTrue(refusalOf("ALTER TASK gc_b ADD AFTER gc_c").contains("a task graph cannot have a cycle"));
        // The graph is as it was: A the root, B after A, C after B.
        assertEquals("[]", taskCell("GC_A", "predecessors"));
        assertEquals("60MINUTE", taskCell("GC_A", "schedule"));
        assertEquals("[\"" + PREFIX + "GC_A\"]", taskCell("GC_B", "predecessors"));
        assertEquals("[\"" + PREFIX + "GC_B\"]", taskCell("GC_C", "predecessors"));
        // A task beside the chain may still follow its end.
        engine.execute("CREATE TASK gc_d AFTER gc_c AS SELECT 4");
        engine.execute("ALTER TASK gc_d ADD AFTER gc_b");
    }

    @Test
    public void theGraphOfARunningRootIsNotChanged() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("CREATE TASK gc_c AFTER gc_b AS SELECT 3");
        engine.execute("ALTER TASK gc_r RESUME");
        try {
            assertEquals(RUNNING, refusalOf("CREATE TASK gc_x AFTER gc_r AS SELECT 3"));
            assertEquals(RUNNING, refusalOf("CREATE TASK gc_x AFTER gc_c AS SELECT 3"));
            assertEquals(RUNNING, refusalOf("ALTER TASK gc_b REMOVE AFTER gc_r"));
            assertEquals(RUNNING, refusalOf("ALTER TASK gc_c ADD AFTER gc_r"));
            assertEquals(RUNNING, refusalOf("CREATE OR REPLACE TASK gc_b AFTER gc_r AS SELECT 97"));
            // #2340: CREATE OR ALTER of a child of the running root is refused as ALTER TASK … ADD AFTER is.
            assertEquals(RUNNING, refusalOf("CREATE OR ALTER TASK gc_b AFTER gc_r AS SELECT 99"));
            assertEquals(RUNNING, refusalOf("CREATE OR ALTER TASK gc_c AS SELECT 98"));
            assertEquals("[\"" + PREFIX + "GC_R\"]", taskCell("GC_B", "predecessors"));
            assertEquals("SELECT2", taskCell("GC_B", "definition"));
            assertEquals("[\"" + PREFIX + "GC_B\"]", taskCell("GC_C", "predecessors"));
            assertTrue(engine.executeQuery("SHOW TASKS LIKE 'GC_X'").getRows().isEmpty());
        } finally {
            engine.execute("ALTER TASK gc_r SUSPEND");
        }
        // Suspended, the root's graph takes each change.
        engine.execute("CREATE TASK gc_x AFTER gc_r AS SELECT 3");
        engine.execute("ALTER TASK gc_c ADD AFTER gc_r");
        engine.execute("ALTER TASK gc_c REMOVE AFTER gc_r");
        engine.execute("CREATE OR ALTER TASK gc_c AFTER gc_b AS SELECT 98");
        engine.execute("CREATE OR REPLACE TASK gc_b AFTER gc_r AS SELECT 97");
        assertEquals("SELECT97", taskCell("GC_B", "definition"));
    }

    @Test
    public void aStandaloneRunningTaskIsNotAGraphTheChangeTouches() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_o SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("ALTER TASK gc_o RESUME");
        try {
            // Another root runs; this graph's root is suspended.
            engine.execute("CREATE TASK gc_x AFTER gc_b AS SELECT 3");
            engine.execute("CREATE OR ALTER TASK gc_b AFTER gc_r AS SELECT 5");
        } finally {
            engine.execute("ALTER TASK gc_o SUSPEND");
        }
    }
}
