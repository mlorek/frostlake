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
import dev.frostlake.TaskRunWait;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS: one row per completed graph run, built from the run history. The
 * history outlives the database a task ran in, by the task's name, so each test names its tasks apart and reads
 * the runs of its own roots.
 */
public class CompleteTaskGraphsFunctionTest extends BaseDatabaseTest {

    private String root;
    private String child;
    private String ok;

    @Override
    protected void setupTest() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        root = "G_ROOT_" + suffix;
        child = "G_CHILD_" + suffix;
        ok = "G_OK_" + suffix;
        engine.execute("CREATE TASK " + root + " AS SELECT 1");
        engine.execute("CREATE TASK " + child + " AFTER " + root + " AS SELECT 1/0");
        engine.execute("ALTER TASK " + child + " RESUME");
        engine.execute("CREATE TASK " + ok + " AS SELECT 1");
    }

    private String graphsOf(final String task, final String more) {
        return "SELECT * FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => '" + task + "'" + more
            + "))";
    }

    private ResultSet graphs(final String task, final String more) {
        return engine.executeQuery(graphsOf(task, more));
    }

    private Object cell(final ResultSet rs, final int row, final String column) {
        return rs.getRows().get(row).getValue(rs.getColumnIndex(column));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aGraphRunFailsWhenAChildFails() {
        assertEquals(0, graphs(root, "").getRowCount());
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, graphsOf(root, ""), 1);
        final ResultSet rs = graphs(root, "");
        assertEquals(1, rs.getRowCount());
        assertEquals(root, cell(rs, 0, "ROOT_TASK_NAME"));
        assertEquals("TEST_DB", cell(rs, 0, "DATABASE_NAME"));
        assertEquals("FAILED", cell(rs, 0, "STATE"));
        assertEquals("EXECUTE TASK", cell(rs, 0, "SCHEDULED_FROM"));
        assertEquals(child, cell(rs, 0, "FIRST_ERROR_TASK_NAME"));
        assertTrue(String.valueOf(cell(rs, 0, "FIRST_ERROR_MESSAGE")).contains("Division by zero"));
        assertEquals(1L, ((Number) cell(rs, 0, "GRAPH_VERSION")).longValue());
        assertEquals(1L, ((Number) cell(rs, 0, "ATTEMPT_NUMBER")).longValue());
        assertTrue(cell(rs, 0, "COMPLETED_TIME") != null);
        assertNull(cell(rs, 0, "BACKFILL_INFO"));
        assertEquals(1, graphs(root.toLowerCase(Locale.ROOT), "").getRowCount(), "the root's name in any case");
    }

    @Test
    public void errorOnlyKeepsTheFailedRuns() {
        engine.execute("EXECUTE TASK " + root);
        engine.execute("EXECUTE TASK " + ok);
        TaskRunWait.untilRows(engine, graphsOf(root, ""), 1);
        TaskRunWait.untilRows(engine, graphsOf(ok, ""), 1);
        assertEquals(1, graphs(root, ", ERROR_ONLY => TRUE").getRowCount());
        assertEquals(0, graphs(ok, ", ERROR_ONLY => TRUE").getRowCount());
        final ResultSet succeeded = graphs(ok, "");
        assertEquals("SUCCEEDED", cell(succeeded, 0, "STATE"));
        assertNull(cell(succeeded, 0, "FIRST_ERROR_TASK_NAME"));
    }

    @Test
    public void aResultLimitIsANumberOfNoneToTenThousand() {
        engine.execute("EXECUTE TASK " + ok);
        TaskRunWait.untilRows(engine, graphsOf(ok, ""), 1);
        for (final String function : new String[] {"COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME", "CURRENT_TASK_GRAPHS("
            + "ROOT_TASK_NAME", "TASK_HISTORY(TASK_NAME"}) {
            final String head = "SELECT * FROM TABLE(INFORMATION_SCHEMA." + function + " => '" + ok
                + "', RESULT_LIMIT => ";
            assertTrue(refusal(head + "10001))").contains(
                "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."), function);
            assertTrue(refusal(head + "-1))").contains("invalid value [-1] for parameter 'RESULT_LIMIT'"), function);
            assertTrue(refusal(head + "'5'))").contains("invalid type [VARCHAR(1)] for parameter 'RESULT_LIMIT'"),
                function);
            engine.executeQuery(head + "NULL))");
            engine.executeQuery(head + "1.5))");
            engine.executeQuery(head + "10000))");
        }
        assertEquals(1, graphs(ok, ", RESULT_LIMIT => 1").getRowCount());
        assertEquals(1, graphs(ok, ", RESULT_LIMIT => 0").getRowCount(), "a limit of none still answers the latest run");
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => '" + ok
            + "', RESULT_LIMIT => 0))").getRowCount(), "where the run history answers no row");
    }
}
