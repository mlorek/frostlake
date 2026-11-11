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
 * EXPLAIN prints a window as the plan reads it (live-verified): a ROWS frame that ends after the current row and does
 * not start at the partition's first row prints its order reversed - {@code ORDER BY ET58.B DESC NULLS FIRST} for
 * {@code ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING} - a ROWS frame of the whole partition prints no order and its scan
 * reads nothing, and a RANGE frame of the whole partition keeps its order but lists no column for it.
 */
public class ExplainWindowFrameOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void framesAsThePlanReadsThem() {
        engine.execute("CREATE OR REPLACE TABLE et58 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et58 VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        assertCells(new String[][] {
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                + "FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|null|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN 1 FOLLOWING AND 2 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                + "FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*) OVER "
                + "()|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(*) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|null|1|0|0"},
            {"EXPLAIN SELECT SUM(a) OVER (ORDER BY b ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(ET58.A) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET58.A) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT SUM(a) OVER (ORDER BY b RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) "
                + "FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(ET58.A) OVER "
                + "(ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET58.A) "
                + "OVER (ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY a RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.A ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN 1 PRECEDING AND 2 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN 2 PRECEDING AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) "
                + "OVER (ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b DESC ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER "
                + "(ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (PARTITION BY a ORDER BY b ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) "
                + "FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(PARTITION BY ET58.A ORDER BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (PARTITION BY ET58.A ORDER BY ET58.B DESC NULLS "
                + "FIRST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
        });
    }
}
