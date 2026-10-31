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
 * How EXPLAIN spells a count in a window (live-verified): COUNT(*) over an ordered window counts the constant 1, and
 * over a window without an order stays COUNT(*); COUNT_IF is the sum of an IFF of its condition, in a window and in an
 * aggregate alike; and a number written beside a window's sum, mean or extreme of a FLOAT is the FLOAT literal it
 * becomes.
 */
public class ExplainWindowCountTextTest extends MeasuredCellsTestSupport {

    /** COUNT(*), COUNT(1), COUNT(a) and COUNT_IF over ordered, partitioned and empty windows, and under QUALIFY. */
    @Test
    public void aCountOverAnOrderedWindowCountsAConstant() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE et57 (a INT, b VARCHAR)",
                "Table ET57 successfully created."},
            {"INSERT INTO et57 VALUES (1, 'x'), (2, 'y'), (3, 'z')",
                "3"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b) FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER (ORDER "
                + "BY ET57.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (ORDER "
                + "BY ET57.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (PARTITION BY a) FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(*) OVER "
                + "(PARTITION BY ET57.A)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(*) OVER (PARTITION "
                + "BY ET57.A)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER () FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*) OVER "
                + "()|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(*) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|null|1|0|0"},
            {"EXPLAIN SELECT a, COUNT(*) FROM et57 GROUP BY a",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET57.A, "
                + "COUNT(*)|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)], groupKeys: "
                + "[ET57.A]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT(1) OVER (ORDER BY b) FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER (ORDER "
                + "BY ET57.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (ORDER "
                + "BY ET57.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(a) OVER (ORDER BY b) FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(ET57.A) OVER "
                + "(ORDER BY ET57.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(ET57.A) "
                + "OVER (ORDER BY ET57.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT COUNT_IF(a > 1) OVER (ORDER BY b) FROM et57",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(IFF(ET57.A > 1, "
                + "1, 0)) OVER (ORDER BY ET57.B ASC NULLS LAST)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|SUM(IFF(ET57.A > 1, 1, 0)) OVER (ORDER BY ET57.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT a FROM et57 QUALIFY COUNT(*) OVER (PARTITION BY b) = 1",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET57.A|null|null|null "
                + ";; 1|1|[0]|Filter|null|null|COUNT(*) OVER (PARTITION BY ET57.B) = 1|null|null|null ;; "
                + "1|2|[1]|WindowFunction|null|null|COUNT(*) OVER (PARTITION BY ET57.B)|null|null|null ;; "
                + "1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET57|null|A, B|1|1|1024"},
        });
    }

    /** COUNT and COUNT_IF over partitions, frames, a descending order and DISTINCT. */
    @Test
    public void countIfIsASumOfIffsInEveryWindow() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE et58 (a INT, b VARCHAR)",
                "Table ET58 successfully created."},
            {"INSERT INTO et58 VALUES (1, 'x'), (2, 'y'), (3, 'z')",
                "3"},
            {"EXPLAIN SELECT COUNT(*) OVER (PARTITION BY a ORDER BY b) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER "
                + "(PARTITION BY ET58.A ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (PARTITION BY ET58.A ORDER BY ET58.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER (ORDER "
                + "BY ET58.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (ORDER "
                + "BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b DESC) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(1) OVER (ORDER "
                + "BY ET58.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (ORDER "
                + "BY ET58.B DESC NULLS FIRST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|B|1|1|1024"},
            {"EXPLAIN SELECT COUNT_IF(a > 1) OVER () FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(IFF(ET58.A > 1, "
                + "1, 0)) OVER ()|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(IFF(ET58.A > 1, 1, 0)) OVER "
                + "()|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT_IF(a > 1) OVER (PARTITION BY b) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(IFF(ET58.A > 1, "
                + "1, 0)) OVER (PARTITION BY ET58.B)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(IFF(ET58.A "
                + "> 1, 1, 0)) OVER (PARTITION BY ET58.B)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT COUNT_IF(a > 1) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(IFF(ET58.A > 1, "
                + "1, 0))|null|null|null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [SUM(IFF(ET58.A > 1, 1, "
                + "0))]|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A|1|1|1024"},
            {"EXPLAIN SELECT COUNT_IF(a > 1) OVER (PARTITION BY b ORDER BY a) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|SUM(IFF(ET58.A > 1, "
                + "1, 0)) OVER (PARTITION BY ET58.B ORDER BY ET58.A ASC NULLS LAST)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|SUM(IFF(ET58.A > 1, 1, 0)) OVER (PARTITION BY ET58.B ORDER BY "
                + "ET58.A ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, "
                + "B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(DISTINCT a) OVER (PARTITION BY b) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(DISTINCT "
                + "ET58.A) OVER (PARTITION BY ET58.B)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|COUNT(DISTINCT ET58.A) OVER (PARTITION BY ET58.B)|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT COUNT(a) OVER (PARTITION BY b ORDER BY a), COUNT(*) OVER (ORDER BY a) FROM et58",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|COUNT(ET58.A) OVER "
                + "(PARTITION BY ET58.B ORDER BY ET58.A ASC NULLS LAST), COUNT(1) OVER (ORDER BY ET58.A ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(ET58.A) OVER (PARTITION BY ET58.B "
                + "ORDER BY ET58.A ASC NULLS LAST), COUNT(1) OVER (ORDER BY ET58.A ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
            {"EXPLAIN SELECT a FROM et58 QUALIFY COUNT(*) OVER (ORDER BY b) = 1",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET58.A|null|null|null "
                + ";; 1|1|[0]|Filter|null|null|COUNT(1) OVER (ORDER BY ET58.B ASC NULLS LAST) = 1|null|null|null ;; "
                + "1|2|[1]|WindowFunction|null|null|COUNT(1) OVER (ORDER BY ET58.B ASC NULLS LAST)|null|null|null ;; "
                + "1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET58|null|A, B|1|1|1024"},
        });
    }

    /** Windows and aggregates as operands of an operation, a concatenation, a negation and a call. */
    @Test
    public void windowOperandsOfAnOperation() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE et59 (a INT, b VARCHAR, c NUMBER(10,2), d FLOAT)",
                "Table ET59 successfully created."},
            {"EXPLAIN SELECT SUM(a) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|SUM(ET59.A) OVER () + "
                + "1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET59.A) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
            {"EXPLAIN SELECT COUNT(*) OVER (ORDER BY b) > 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|COUNT(1) OVER (ORDER BY "
                + "ET59.B ASC NULLS LAST) > 1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|COUNT(1) OVER (ORDER "
                + "BY ET59.B ASC NULLS LAST)|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|B|0|0|0"},
            {"EXPLAIN SELECT MAX(a) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|MAX(ET59.A) OVER () + "
                + "1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|MAX(ET59.A) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
            {"EXPLAIN SELECT a FROM et59 QUALIFY SUM(a) OVER () + 1 > 2",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|ET59.A|null|null|null ;; "
                + "1|1|[0]|Filter|null|null|(SUM(ET59.A) OVER () + 1) > 2|null|null|null ;; "
                + "1|2|[1]|WindowFunction|null|null|SUM(ET59.A) OVER ()|null|null|null ;; "
                + "1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
            {"EXPLAIN SELECT SUM(c) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|SUM(ET59.C) OVER () + "
                + "1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET59.C) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|C|0|0|0"},
            {"EXPLAIN SELECT MAX(b) OVER () || 'x' FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|MAX(ET59.B) OVER () || "
                + "'x'|null|null|null ;; 1|1|[0]|WindowFunction|null|null|MAX(ET59.B) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|B|0|0|0"},
            {"EXPLAIN SELECT -SUM(a) OVER () FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|NEGATE(SUM(ET59.A) OVER "
                + "())|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET59.A) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
            {"EXPLAIN SELECT UPPER(MAX(b) OVER ()) FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|UPPER(MAX(ET59.B) OVER "
                + "())|null|null|null ;; 1|1|[0]|WindowFunction|null|null|MAX(ET59.B) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|B|0|0|0"},
            {"EXPLAIN SELECT COUNT(*) OVER () = COUNT(*) OVER (ORDER BY b) FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|COUNT(*) OVER () = "
                + "COUNT(1) OVER (ORDER BY ET59.B ASC NULLS LAST)|null|null|null ;; "
                + "1|1|[0]|WindowFunction|null|null|COUNT(*) OVER (), COUNT(1) OVER (ORDER BY ET59.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|B|0|0|0"},
            {"EXPLAIN SELECT SUM(d) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|SUM(ET59.D) OVER () + "
                + "1.0|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET59.D) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|D|0|0|0"},
            {"EXPLAIN SELECT MIN(a) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|MIN(ET59.A) OVER () + "
                + "1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|MIN(ET59.A) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
            {"EXPLAIN SELECT AVG(a) OVER () + 1 FROM et59",
                "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|AVG(ET59.A) OVER () + "
                + "1|null|null|null ;; 1|1|[0]|WindowFunction|null|null|AVG(ET59.A) OVER ()|null|null|null ;; "
                + "1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET59|null|A|0|0|0"},
        });
    }
}
