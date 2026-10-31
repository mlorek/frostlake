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
 * A result read back through RESULT_SCAN keeps no statistics of its values: SYSTEM$TYPEOF tags a scanned number
 * by its declared width - NUMBER(38,0)[SB16] for a column, an expression over one or a scalar subquery, correlated
 * or not, and NUMBER(1,0)[SB1] for a literal - where the query's own column was tagged by its values. A correlated
 * subquery over a common table expression of one row is answered, as over a derived table (all live-verified).
 */
public class ResultScanStorageTagTest extends MeasuredCellsTestSupport {

    @Test
    public void scannedNumbersByDeclaredWidth() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE o (k INT)");
        engine.execute("INSERT INTO o VALUES (1), (2)");
        assertAnswer("1|2 ;; 2|3", "SELECT k, (SELECT o.k + 1) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|2.5 ;; NUMBER(38,0)[SB16]|3.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|2 ;; 2|3", "SELECT k, (SELECT c + o.k FROM (SELECT 1 AS c)) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|2.5 ;; NUMBER(38,0)[SB16]|3.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|2 ;; 2|3",
            "WITH t AS (SELECT 1 AS c) SELECT k, (SELECT c + o.k FROM t) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|2.5 ;; NUMBER(38,0)[SB16]|3.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|4.0 ;; 2|5.0", "SELECT k, (SELECT c * 1.5 + o.k FROM (SELECT 2 AS c)) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,1)[SB16]|4.5 ;; NUMBER(38,1)[SB16]|5.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|2 ;; 2|2", "SELECT k, (SELECT 1 + 1) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(2,0)[SB1]|2.5 ;; NUMBER(2,0)[SB1]|2.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|2 ;; 2|2", "SELECT k, (SELECT MAX(k) FROM o) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|2.5 ;; NUMBER(38,0)[SB16]|2.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|1 ;; 2|2", "SELECT k, (SELECT MAX(o2.k) FROM o o2 WHERE o2.k = o.k) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|1.5 ;; NUMBER(38,0)[SB16]|2.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|2 ;; 2|3", "SELECT k, k + 1 AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|2.5 ;; NUMBER(38,0)[SB16]|3.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|1 ;; 2|2", "SELECT k, k AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|1.5 ;; NUMBER(38,0)[SB16]|2.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|1 ;; 2|1", "SELECT k, 1 AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(1,0)[SB1]|1.5 ;; NUMBER(1,0)[SB1]|1.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|1.5 ;; 2|3.0", "SELECT k, k * 1.5 AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,1)[SB16]|2.0 ;; NUMBER(38,1)[SB16]|3.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("1|1 ;; 2|2", "SELECT k, (SELECT o.k) AS x FROM o ORDER BY k");
        assertAnswer("NUMBER(38,0)[SB16]|1.5 ;; NUMBER(38,0)[SB16]|2.5",
            "SELECT SYSTEM$TYPEOF(x), x + 0.5 FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2");
        assertAnswer("NUMBER(38,0)[SB1] ;; NUMBER(38,0)[SB1]",
            "SELECT SYSTEM$TYPEOF((SELECT o.k + 1)) FROM o ORDER BY 1");
        assertAnswer("NUMBER(38,0)[SB1] ;; NUMBER(38,0)[SB1]", "SELECT SYSTEM$TYPEOF(k + 1) FROM o ORDER BY 1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
