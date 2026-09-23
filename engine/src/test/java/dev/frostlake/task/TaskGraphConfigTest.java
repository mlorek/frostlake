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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A task graph's configuration read inside its tasks through SYSTEM$GET_TASK_GRAPH_CONFIG, and the EXECUTE TASK
 * forms that start a run with one merged over it (USING CONFIG) or retry a named graph run (RETRY GRAPH RUN
 * GROUP). The function answers text: the whole configuration as compact JSON in the order its keys were
 * written, or the value a path names — a string's content, a number or boolean as written, an object as compact
 * JSON — and NULL for a path that names nothing or a graph with no configuration.
 */
public class TaskGraphConfigTest extends BaseDatabaseTest {

    /** A real account runs an EXECUTE TASK later, on serverless compute; the embedded engine runs it in place. */
    private static final String RUNS_LATER =
        "a real account runs EXECUTE TASK asynchronously, so the run's rows are read only on the embedded engine";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    @Test
    public void theFunctionAnswersOnlyInsideATask() {
        final String outside = "Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.";
        assertEquals(outside, refusalOf("SELECT SYSTEM$GET_TASK_GRAPH_CONFIG()"));
        assertEquals(outside, refusalOf("SELECT SYSTEM$GET_TASK_GRAPH_CONFIG('env')"));
    }

    @Test
    public void executeTaskRefusesABadConfigurationAChildAndAnUnknownGraphRun() {
        engine.execute("CREATE TASK root SCHEDULE = '60 MINUTE' CONFIG = $${\"env\": \"prod\"}$$ AS SELECT 1");
        engine.execute("CREATE TASK child AFTER root AS SELECT 1");
        final String invalid = "Invalid config. Must be a string representation of a valid JSON Object.";
        assertEquals(invalid, refusalOf("EXECUTE TASK root USING CONFIG = 'bad'"));
        assertEquals(invalid, refusalOf("EXECUTE TASK root USING CONFIG = '[1]'"));
        assertEquals("Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.CHILD. Call EXECUTE TASK "
            + "on the root task of its graph instead.", refusalOf("EXECUTE TASK child USING CONFIG = '{\"a\": 1}'"));
        assertEquals("Cannot perform retry: Graph run group with ID 33af30ab-f960-4ba0-a5c8-d132e5623468 not found.",
            refusalOf("EXECUTE TASK root RETRY GRAPH RUN GROUP '33af30ab-f960-4ba0-a5c8-d132e5623468'"));
        assertEquals("Cannot perform retry: Graph run group with ID nope not found.",
            refusalOf("EXECUTE TASK root RETRY GRAPH RUN GROUP 'nope'"));
    }

    @Test
    public void aRunReadsTheConfigurationMergedWithTheOneItWasGiven() {
        Assumptions.assumeFalse(isLiveSnowflake(), RUNS_LATER);
        engine.execute("CREATE TABLE out (full_cfg VARCHAR, env VARCHAR, res VARCHAR, missing VARCHAR, nested VARCHAR, "
            + "n VARCHAR, flag VARCHAR)");
        engine.execute("CREATE TASK root SCHEDULE = '60 MINUTE' CONFIG = $${\"env\": \"prod\", \"paths\": {\"logs\": "
            + "\"/p/l\", \"results\": \"/p/r\"}, \"n\": 7}$$ AS INSERT INTO out SELECT SYSTEM$GET_TASK_GRAPH_CONFIG(), "
            + "SYSTEM$GET_TASK_GRAPH_CONFIG('env'), SYSTEM$GET_TASK_GRAPH_CONFIG('paths.results'), "
            + "SYSTEM$GET_TASK_GRAPH_CONFIG('missing'), SYSTEM$GET_TASK_GRAPH_CONFIG('paths'), "
            + "SYSTEM$GET_TASK_GRAPH_CONFIG('n'), SYSTEM$GET_TASK_GRAPH_CONFIG('extra')");
        engine.execute("EXECUTE TASK root USING CONFIG = $${\"paths\": {\"results\": \"/t\"}, \"extra\": true}$$");
        final ResultSet out = engine.executeQuery("SELECT * FROM out");
        assertEquals(1, out.getRowCount());
        final Row row = out.getRows().get(0);
        assertEquals(Arrays.<Object>asList(
            "{\"env\":\"prod\",\"paths\":{\"logs\":\"/p/l\",\"results\":\"/t\"},\"n\":7,\"extra\":true}",
            "prod", "/t", null, "{\"logs\":\"/p/l\",\"results\":\"/t\"}", "7", "true"), row.getValues());
        assertEquals("{\"env\": \"prod\", \"paths\": {\"logs\": \"/p/l\", \"results\": \"/p/r\"}, \"n\": 7}",
            configOf("ROOT"), "USING CONFIG leaves the task's own configuration as it was");
    }

    @Test
    public void aGraphWithNoConfigurationReadsNull() {
        Assumptions.assumeFalse(isLiveSnowflake(), RUNS_LATER);
        engine.execute("CREATE TABLE out (v VARCHAR, w VARCHAR)");
        engine.execute("CREATE TASK r0 SCHEDULE = '60 MINUTE' AS INSERT INTO out SELECT SYSTEM$GET_TASK_GRAPH_CONFIG(), "
            + "SYSTEM$GET_TASK_GRAPH_CONFIG('a')");
        engine.execute("EXECUTE TASK r0");
        final List<Object> row = engine.executeQuery("SELECT v, w FROM out").getRows().get(0).getValues();
        assertEquals(Arrays.asList(null, null), row);
    }

    @Test
    public void aResumedFinalizerRunsAfterItsGraphAndAChildReadsTheRootsConfiguration() {
        Assumptions.assumeFalse(isLiveSnowflake(), RUNS_LATER);
        engine.execute("CREATE TABLE log (who VARCHAR, cfg VARCHAR)");
        engine.execute("CREATE TASK root SCHEDULE = '60 MINUTE' CONFIG = '{\"stage\": \"one\"}' AS "
            + "INSERT INTO log SELECT 'root', SYSTEM$GET_TASK_GRAPH_CONFIG('stage')");
        engine.execute("CREATE TASK child AFTER root AS INSERT INTO log SELECT 'child', SYSTEM$GET_TASK_GRAPH_CONFIG('stage')");
        engine.execute("CREATE TASK fin FINALIZE = root AS INSERT INTO log SELECT 'fin', SYSTEM$GET_TASK_GRAPH_CONFIG()");
        engine.execute("ALTER TASK child RESUME");
        engine.execute("ALTER TASK fin RESUME");
        engine.execute("EXECUTE TASK root USING CONFIG = '{\"stage\": \"two\"}'");
        final ResultSet log = engine.executeQuery("SELECT who || '=' || cfg FROM log ORDER BY who");
        assertEquals("child=two|fin={\"stage\":\"two\"}|root=two", joined(log));
        engine.execute("ALTER TASK fin SUSPEND");
        engine.execute("ALTER TASK child SUSPEND");
    }

    private String configOf(final String task) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, soleRowWhere(tasks, "name", task), "config");
    }

    private static String joined(final ResultSet rows) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : rows.getRows()) {
            if (text.length() > 0) {
                text.append('|');
            }
            text.append(row.getValue(0));
        }
        return text.toString();
    }
}
