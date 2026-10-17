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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$GET_TASK_GRAPH_CONFIG, SYSTEM$GET_PREDECESSOR_RETURN_VALUE and SYSTEM$TASK_RUNTIME_INFO run inside a task
 * alone: outside one each is refused while the statement compiles, under a WHERE, over a table with no row and
 * inside SYSTEM$TYPEOF alike, and so is SYSTEM$CLUSTERING_RATIO, a feature no longer supported (live-verified).
 */
public class TaskOnlyFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void theTaskOnlyFunctionsAreRefusedOutsideATask() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('a', 1)");
        engine.execute("CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
        engine.execute("CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
        engine.execute("INSERT INTO tcl VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE tem (i INT)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(SYSTEM$GET_TASK_GRAPH_CONFIG())",
                "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$GET_PREDECESSOR_RETURN_VALUE())",
                "REFUSED Function SYSTEM$GET_PREDECESSOR_RETURN_VALUE must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$TASK_RUNTIME_INFO('CURRENT_TASK_NAME'))",
                "REFUSED Function SYSTEM$TASK_RUNTIME_INFO must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_RATIO('tcl'))", "REFUSED Unsupported feature 'Clustering Ratio'."},
            {"SELECT SYSTEM$GET_PREDECESSOR_RETURN_VALUE()",
                "REFUSED Function SYSTEM$GET_PREDECESSOR_RETURN_VALUE must be called from within a task."},
            {"SELECT SYSTEM$TASK_RUNTIME_INFO('CURRENT_TASK_NAME')",
                "REFUSED Function SYSTEM$TASK_RUNTIME_INFO must be called from within a task."},
            {"SELECT SYSTEM$CLUSTERING_RATIO('tcl')", "REFUSED Unsupported feature 'Clustering Ratio'."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$GET_TASK_GRAPH_CONFIG()) FROM tem",
                "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task."},
            {"SELECT SYSTEM$GET_TASK_GRAPH_CONFIG() FROM tem",
                "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task."},
            {"SELECT SYSTEM$GET_PREDECESSOR_RETURN_VALUE() FROM tem",
                "REFUSED Function SYSTEM$GET_PREDECESSOR_RETURN_VALUE must be called from within a task."},
            {"SELECT SYSTEM$TASK_RUNTIME_INFO('CURRENT_TASK_NAME') FROM tem",
                "REFUSED Function SYSTEM$TASK_RUNTIME_INFO must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$TASK_RUNTIME_INFO('CURRENT_TASK_GRAPH_RUN_GROUP_ID'))",
                "REFUSED Function SYSTEM$TASK_RUNTIME_INFO must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_RATIO('tcl')) FROM tem",
                "REFUSED Unsupported feature 'Clustering Ratio'."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$GET_TASK_GRAPH_CONFIG('x'))",
                "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task."},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$CURRENT_USER_TASK_NAME())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_DEPTH('tcl'))", "FLOAT[DOUBLE]"},
            {"SELECT 1 FROM tem WHERE SYSTEM$GET_PREDECESSOR_RETURN_VALUE() IS NULL",
                "REFUSED Function SYSTEM$GET_PREDECESSOR_RETURN_VALUE must be called from within a task."},
        });
    }
}
