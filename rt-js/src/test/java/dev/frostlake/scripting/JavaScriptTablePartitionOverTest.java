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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript table function partitioned OVER the rows to its left (live-verified): a bare call after LATERAL takes
 * the OVER clause as TABLE(...) does, FLATTEN takes it and reads no partitions, OVER () is one partition, a call
 * joined with an ON is the lateral form the account refuses, and LATERAL takes no TABLE(...) wrapper. The handler
 * object is made once for the statement's call and reads every partition in turn, so what it keeps carries from one
 * partition to the next: the last partition's total is every row's. Which partition comes first is the account's
 * choice, so only what the order cannot change is asserted.
 */
public class JavaScriptTablePartitionOverTest extends MeasuredCellsTestSupport {

    /** The OVER forms, the refusals and the totals the order cannot change. */
    @Test
    public void aPartitionedCall() {
        engine.execute("CREATE OR REPLACE TABLE jev (g VARCHAR, v FLOAT)");
        engine.execute("INSERT INTO jev VALUES ('a', 1), ('a', 2), (NULL, 5), ('b', 10)");
        engine.execute("SET jsv = 2.5");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION js_sum(v FLOAT) RETURNS TABLE (total FLOAT) LANGUAGE JAVASCRIPT AS "
                + "$${processRow: function (r, w, c) { this.t = (this.t || 0) + r.V; }, finalize: function (w, c) { "
                + "w.writeRow({TOTAL: this.t}); }}$$",
                "Function JS_SUM successfully created."},
            {"SELECT f.total FROM jev, LATERAL js_sum(jev.v) f ORDER BY 1",
                "18.0"},
            {"SELECT f.total FROM jev, LATERAL js_sum(jev.v) OVER () f ORDER BY 1",
                "18.0"},
            {"SELECT f.total FROM jev JOIN TABLE(js_sum(jev.v) OVER (PARTITION BY jev.g)) f ON TRUE ORDER BY 1",
                "REFUSED Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join "
                + "predicate (ON clause)'."},
            {"SELECT f.total FROM jev JOIN TABLE(js_sum(jev.v) OVER (PARTITION BY jev.g)) f ON f.total > 4 ORDER BY 1",
                "REFUSED Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join "
                + "predicate (ON clause)'."},
            {"SELECT f.value FROM jev, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(jev.v)) OVER (PARTITION BY jev.g) f "
                + "ORDER BY 1",
                "1.0 ;; 2.0 ;; 5.0 ;; 10.0"},
            {"SELECT f.total FROM LATERAL js_sum(1::FLOAT) OVER (PARTITION BY 1) f",
                "1.0"},
            {"SELECT f.total FROM jev, LATERAL js_sum(jev.v) OVER (PARTITION BY jev.g) f, LATERAL js_sum(f.total) g "
                + "ORDER BY 1",
                "null"},
            {"SELECT f.total FROM jev, LATERAL TABLE(js_sum(jev.v) OVER (PARTITION BY jev.g)) f ORDER BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'TABLE'."},
            {"SELECT f.total FROM jev, LATERAL js_sum(jev.v) OVER (PARTITION BY jev.g) ORDER BY 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'F.TOTAL'"},
            {"SELECT * FROM jev, LATERAL js_sum(jev.v) OVER",
                "null|null|18.0"},
            {"SELECT * FROM jev, LATERAL js_sum(jev.v) OVER (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"SELECT COUNT(*), MAX(f.total) FROM jev, LATERAL js_sum(jev.v) OVER (PARTITION BY jev.g) f",
                "3|18.0"},
            {"SELECT COUNT(*), MAX(f.total) FROM jev, TABLE(js_sum(jev.v) OVER (PARTITION BY jev.g)) f",
                "3|18.0"},
            {"SELECT COUNT(*), MAX(f.total), MIN(f.total) >= 3 FROM jev, LATERAL js_sum(jev.v) OVER (PARTITION BY "
                + "jev.g ORDER BY jev.v) f",
                "3|18.0|true"},
        });
    }
}
