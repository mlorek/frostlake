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
 * An aggregate over an untyped NULL — the word itself, or a column holding nothing but one — declares what live
 * declares (live-verified): SUM, AVG and the variances read it as a NUMBER(18,0), so SUM(NULL) is NUMBER(30,0),
 * AVG(NULL) NUMBER(36,6) and over a window NUMBER(33,3); MEDIAN reads it as a text, NUMBER(12,3); MAX and MIN keep no
 * type, a VARCHAR(0) column; arithmetic over them widens from those types.
 */
public class UntypedNullAggregateTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregateOverAnUntypedNullDeclaresLivesType() {
        assertShape("S:NUMBER(30,0), MX:VARCHAR(0), MN:VARCHAR(0), A:NUMBER(36,6)",
            "SELECT SUM(NULL) AS s, MAX(NULL) AS mx, MIN(NULL) AS mn, AVG(NULL) AS a");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tn AS SELECT SUM(NULL) AS s, MAX(NULL) AS mx, MIN(NULL) AS mn, AVG(NULL) "
                + "AS a, MEDIAN(NULL) AS md, STDDEV(NULL) AS sd, VARIANCE(NULL) AS va, ANY_VALUE(NULL) AS av, "
                + "MODE(NULL) AS mo, COUNT(NULL) AS c, ABS(NULL) AS ab",
                "Table TN successfully created."},
        });
        engine.execute("DESCRIBE TABLE tn");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\", \"null?\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "S|NUMBER(30,0)|Y ;; MX|VARCHAR(16777216)|Y ;; MN|VARCHAR(16777216)|Y ;; A|NUMBER(36,6)|Y ;; "
                + "MD|NUMBER(12,3)|Y ;; SD|FLOAT|Y ;; VA|NUMBER(38,6)|Y ;; AV|VARCHAR(16777216)|Y ;; "
                + "MO|VARCHAR(16777216)|Y ;; C|NUMBER(18,0)|Y ;; AB|NUMBER(2,0)|Y"},
            {"CREATE OR REPLACE TABLE tn2 AS SELECT SUM(x) AS s, MAX(x) AS mx, AVG(x) AS a FROM (SELECT NULL AS x)",
                "Table TN2 successfully created."},
        });
        engine.execute("DESCRIBE TABLE tn2");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "S|NUMBER(30,0) ;; MX|VARCHAR(16777216) ;; A|NUMBER(36,6)"},
            {"CREATE OR REPLACE TABLE tn3 AS SELECT SUM(NULL) OVER () AS s, MAX(NULL) OVER () AS mx, AVG(NULL) "
                + "OVER () AS a",
                "Table TN3 successfully created."},
        });
        engine.execute("DESCRIBE TABLE tn3");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "S|NUMBER(30,0) ;; MX|VARCHAR(16777216) ;; A|NUMBER(33,3)"},
            {"SELECT SUM(NULL) + 1, MAX(NULL) || 'x', AVG(NULL) * 2", "null|null|null"},
            {"CREATE OR REPLACE TABLE tn4 AS SELECT SUM(NULL) + 1 AS s1, AVG(NULL) * 2 AS a2, MAX(NULL) || 'x' AS m2",
                "Table TN4 successfully created."},
        });
        engine.execute("DESCRIBE TABLE tn4");
        assertCells(new String[][] {
            {"SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "S1|NUMBER(31,0) ;; A2|NUMBER(37,6) ;; M2|VARCHAR(16777216)"},
        });
    }
}
