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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * EXPLAIN of a query over one table names what a star stands for (live-verified): the Result row's expressions
 * are the table's columns named by the relation — {@code PQ1.N, PQ1.M}, {@code T.N, T.M} under an alias, less
 * the columns an EXCLUDE names, a RENAME changing none — and the TableScan row lists the columns it reads. TABLE t
 * is planned as SELECT * FROM t.
 */
public class ExplainStarColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void aStarStandsForItsColumns() {
        engine.execute("CREATE OR REPLACE TABLE pq1 (n INT, m VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE pq2 (k INT)");
        assertCells(new String[][] {
            {"EXPLAIN SELECT * FROM pq1",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|PQ1.N, PQ1.M|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.PQ1|null|N, M|0|0|0"},
            {"EXPLAIN TABLE pq1",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|PQ1.N, PQ1.M|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.PQ1|null|N, M|0|0|0"},
            {"EXPLAIN SELECT t.* FROM pq1 t",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|T.N, T.M|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.PQ1|T|N, M|0|0|0"},
            {"EXPLAIN SELECT * EXCLUDE m FROM pq1",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|PQ1.N|null|null|null "
                + ";; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.PQ1|null|N|0|0|0"},
            {"EXPLAIN SELECT * RENAME n AS nn FROM pq1",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|PQ1.N, PQ1.M|null|null|"
                + "null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.PQ1|null|N, M|0|0|0"},
        });
    }
}
