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
 * RATIO_TO_REPORT is typed and rounded from the column it reads in each statement: a NUMBER(p, s) argument declares
 * a NUMBER of scale s + 6, a DECFLOAT one an exact DECFLOAT, a FLOAT one a FLOAT, and a table replaced with
 * another column type, or read through a view, is typed anew (all live-verified).
 */
public class RatioToReportScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void eachStatementReadsItsColumnsType() {
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v FLOAT)");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        answer("SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY "
            + "1");
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v NUMBER(10, 0))");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        assertAnswer("0.333333|NUMBER(16,6)[SB8] ;; 0.666667|NUMBER(16,6)[SB8]",
            "SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY 1");
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v NUMBER(10, 2))");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        assertAnswer("0.33333333|NUMBER(18,8)[SB8] ;; 0.66666667|NUMBER(18,8)[SB8]",
            "SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY 1");
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v FLOAT)");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        answer("SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY "
            + "1");
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v DECFLOAT)");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        assertAnswer("0.33333333333333333333333333333333333333|DECFLOAT(38)[DECFLOAT18] ;; "
                + "0.66666666666666666666666666666666666667|DECFLOAT(38)[DECFLOAT18]",
            "SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY 1");
        assertAnswer("Table T735 successfully created.", "CREATE OR REPLACE TABLE t735 (v NUMBER(38, 0))");
        assertAnswer("2", "INSERT INTO t735 VALUES (1), (2)");
        assertAnswer("0.333333|NUMBER(38,6)[SB8] ;; 0.666667|NUMBER(38,6)[SB8]",
            "SELECT RATIO_TO_REPORT(v) OVER () r, SYSTEM$TYPEOF(RATIO_TO_REPORT(v) OVER ()) ty FROM t735 ORDER BY 1");
    }

    @Test
    public void aViewReadsItsTablesNewType() {
        assertAnswer("Table T736 successfully created.", "CREATE OR REPLACE TABLE t736 (v FLOAT)");
        assertAnswer("2", "INSERT INTO t736 VALUES (1), (2)");
        assertAnswer("View V736 successfully created.",
            "CREATE OR REPLACE VIEW v736 AS SELECT RATIO_TO_REPORT(v) OVER () r FROM t736");
        answer("SELECT r, SYSTEM$TYPEOF(r) FROM v736 ORDER BY 1");
        assertAnswer("Table T736 successfully created.", "CREATE OR REPLACE TABLE t736 (v NUMBER(10, 0))");
        assertAnswer("2", "INSERT INTO t736 VALUES (1), (2)");
        assertAnswer("0.333333|NUMBER(16,6)[SB8] ;; 0.666667|NUMBER(16,6)[SB8]",
            "SELECT r, SYSTEM$TYPEOF(r) FROM v736 ORDER BY 1");
        answer("SELECT RATIO_TO_REPORT(v) OVER () a, RATIO_TO_REPORT(v::FLOAT) OVER () b FROM t736 ORDER BY 1");
    }
}
