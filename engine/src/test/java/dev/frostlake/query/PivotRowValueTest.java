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
 * A PIVOT value naming the relation's columns: each row meets the value it computes from itself, the column named
 * as the plan prints the value, and one row may feed several columns (all live-verified).
 */
public class PivotRowValueTest extends MeasuredCellsTestSupport {

    @Test
    public void rowValues() {
        engine.execute("CREATE OR REPLACE TABLE fz2052 (id INT, b BOOLEAN, d DATE, s VARCHAR, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO fz2052 VALUES (1, TRUE, '2024-01-01', 'X', '2024-01-01 10:00'), (2, FALSE, "
            + "'2024-01-02', 'Y', '2024-01-02 10:00'), (3, NULL, '2024-01-03', 'Z', '2024-01-03 10:00')");
        assertAnswer("true|2024-01-01|X|2024-01-01T10:00|1 ;; false|2024-01-02|Y|2024-01-02T10:00|2 ;; null|"
                + "2024-01-03|Z|2024-01-03T10:00|3",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id)) ORDER BY s");
        assertAnswer("Table P2052 successfully created.",
            "CREATE OR REPLACE TABLE p2052 AS SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id))");
        assertAnswer("B|BOOLEAN ;; D|DATE ;; S|VARCHAR(16777216) ;; TS|TIMESTAMP_NTZ(9) ;; ID|NUMBER(38,0)",
            "DESCRIBE TABLE p2052 ->> SELECT \"name\", \"type\" FROM $1");
        assertAnswer("true|2024-01-01|X|2024-01-01T10:00|null ;; false|2024-01-02|Y|2024-01-02T10:00|null ;; null|"
                + "2024-01-03|Z|2024-01-03T10:00|null",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id + 1)) ORDER BY s");
        assertAnswer("Table P2052 successfully created.",
            "CREATE OR REPLACE TABLE p2052 AS SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id + 1))");
        assertAnswer("B|BOOLEAN ;; D|DATE ;; S|VARCHAR(16777216) ;; TS|TIMESTAMP_NTZ(9) ;; ID + 1|NUMBER(38,0)",
            "DESCRIBE TABLE p2052 ->> SELECT \"name\", \"type\" FROM $1");
        assertAnswer("true|2024-01-01|X|2024-01-01T10:00|1 ;; false|2024-01-02|Y|2024-01-02T10:00|null ;; null|"
                + "2024-01-03|Z|2024-01-03T10:00|null",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (b::INT)) ORDER BY s");
        assertAnswer("true|2024-01-01|X|2024-01-01T10:00|1|null ;; false|2024-01-02|Y|2024-01-02T10:00|2|2 ;; null|"
                + "2024-01-03|Z|2024-01-03T10:00|3|null",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id, 2)) ORDER BY s");
        assertAnswer("Table P2052 successfully created.",
            "CREATE OR REPLACE TABLE p2052 AS SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (id, 2))");
        assertAnswer("B|BOOLEAN ;; D|DATE ;; S|VARCHAR(16777216) ;; TS|TIMESTAMP_NTZ(9) ;; ID|NUMBER(38,0) ;; 2|"
                + "NUMBER(38,0)",
            "DESCRIBE TABLE p2052 ->> SELECT \"name\", \"type\" FROM $1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 61 | invalid identifier 'S'",
            "SELECT * FROM fz2052 PIVOT(COUNT(s) FOR id IN (id)) ORDER BY s");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'ID'",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR s IN (s)) ORDER BY id");
        assertAnswer("X|1 ;; Y|2 ;; Z|3", "SELECT s, \"ID\" FROM fz2052 PIVOT(SUM(id) FOR id IN (id)) ORDER BY s");
        assertAnswer("true|2024-01-01|X|2024-01-01T10:00|1 ;; false|2024-01-02|Y|2024-01-02T10:00|2 ;; null|"
                + "2024-01-03|Z|2024-01-03T10:00|3",
            "SELECT * FROM fz2052 PIVOT(SUM(id) FOR id IN (fz2052.id)) ORDER BY s");
    }
}
