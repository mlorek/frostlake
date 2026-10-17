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
 * SYSTEM$CLUSTERING_DEPTH's third argument is a predicate the rows are filtered by, compiled after the table's name
 * and the key list: a name in it that the table has not is an invalid identifier placed fifty-six characters in,
 * after both as written, a fault of its syntax is placed the same way, an aggregate and a value that is no condition
 * are refused as a WHERE refuses them, and a predicate no row meets gives the depth 0 (live-verified).
 */
public class ClusteringDepthPredicateTest extends MeasuredCellsTestSupport {

    @Test
    public void thePredicateIsCompiledAfterTheTableAndTheKeys() {
        engine.execute("CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
        engine.execute("INSERT INTO tsrc VALUES ('a', 1)");
        engine.execute("CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
        engine.execute("CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
        engine.execute("INSERT INTO tcl VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE OR REPLACE TABLE tem (i INT)");
        assertCells(new String[][] {
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = 1')", "1.0"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'v = ''x''')", "1.0"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(v)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i, v)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 65 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = 1 AND nosuch = 2')",
                "REFUSED SQL compilation error: error line 1 at position 72 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)',  'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT  SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i =')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected '<EOF>'."},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i > 100')", "0.0"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', '  nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = nosuch')",
                "REFUSED SQL compilation error: error line 1 at position 66 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'nosuch')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('test_db.test_schema.tcl', '(i)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 82 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'tcl.nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'TCL.NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'i = 1', 'x')", "1.0"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', 'SUM(i) = 1')",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause [SUM(TCL.I)]"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', '1')",
                "REFUSED SQL compilation error: | Invalid data type [NUMBER(1,0)] for predicate [1]"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(i)', NULL)", "1.0"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', NULL, 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 62 | invalid identifier 'NOSUCH'"},
            {"SELECT SYSTEM$CLUSTERING_DEPTH('tcl', '(nosuch)', 'nosuch = 1')",
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH'"},
        });
    }
}
