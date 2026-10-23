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

package dev.frostlake.executor;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * EXPLAIN of a WindowFunction straight over a TableScan: when the query's first window is a ROW_NUMBER, a RANK or
 * a DENSE_RANK, or a LAG or a LEAD with no PARTITION BY, the scan lists its table twice, the columns that window
 * partitions and orders by first, under GlobalStats counted twice; any other first window, and a window over a
 * Filter, read the scan once (all live-verified).
 */
public class ExplainWindowScanTest extends MeasuredCellsTestSupport {

    @Test
    public void windowsOverAScan() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table ET52 successfully created.", "CREATE OR REPLACE TABLE et52 (a INT, b VARCHAR)");
        assertAnswer("3", "INSERT INTO et52 VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, RANK() "
                + "OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|RANK() "
                + "OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET52,"
                + " TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, RANK() OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "DENSE_RANK() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|DENSE_RANK() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, DENSE_RANK() OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "LAG(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|LAG(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a) OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "LEAD(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|LEAD(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LEAD(a) OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET52.A, "
                + "NTILE(2) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|"
                + "NTILE(2) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, NTILE(2) OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET52.A, "
                + "SUM(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|SUM(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, SUM(a) OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET52.A, "
                + "FIRST_VALUE(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|"
                + "null|null|FIRST_VALUE(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET52|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, FIRST_VALUE(a) OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "ROW_NUMBER() OVER (PARTITION BY ET52.B ORDER BY ET52.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|ROW_NUMBER() OVER (PARTITION BY ET52.B ORDER BY ET52.A ASC NULLS LAST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|A, B, "
                + "A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (PARTITION BY b ORDER BY a) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET52.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET52.A ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|A, A|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY a) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ROW_NUMBER() "
                + "OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|"
                + "ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, B|1|1|1024",
            "EXPLAIN SELECT ROW_NUMBER() OVER (ORDER BY b) FROM et52");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET52.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|Filter|null|null|"
                + "ET52.A > 1|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET52|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b) FROM et52 WHERE a > 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET52.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET52.B ASC NULLS LAST), LAG(ET52.A) OVER (ORDER BY ET52.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET52.B ASC "
                + "NULLS LAST), LAG(ET52.A) OVER (ORDER BY ET52.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET52, TEST_DB.TEST_SCHEMA.ET52|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b), LAG(a) OVER (ORDER BY b) FROM et52");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void moreWindows() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table ET53 successfully created.", "CREATE OR REPLACE TABLE et53 (a INT, b VARCHAR)");
        assertAnswer("3", "INSERT INTO et53 VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST), SUM(ET53.A) OVER (ORDER BY ET53.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET53.B ASC "
                + "NULLS LAST), SUM(ET53.A) OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b), SUM(a) OVER (ORDER BY b) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET53.A, "
                + "SUM(ET53.A) OVER (ORDER BY ET53.B ASC NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET53.A ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET53.A) OVER (ORDER BY ET53.B ASC "
                + "NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET53.A ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET53|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, SUM(a) OVER (ORDER BY b), ROW_NUMBER() OVER (ORDER BY a) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET53.A, "
                + "PERCENT_RANK() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|PERCENT_RANK() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET53|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, PERCENT_RANK() OVER (ORDER BY b) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET53.A, "
                + "CUME_DIST() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|CUME_DIST() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET53|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, CUME_DIST() OVER (ORDER BY b) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A, "
                + "ROW_NUMBER() OVER (PARTITION BY ET53.B ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|ROW_NUMBER() OVER (PARTITION BY ET53.B ORDER BY ET53.B ASC NULLS LAST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|null|B, A, "
                + "B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (PARTITION BY b ORDER BY b) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A|null|"
                + "null|null ;; 1|1|[0]|Filter|null|null|ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST) = 1|null|"
                + "null|null ;; 1|2|[1]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST)|"
                + "null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|null|B, A, "
                + "B|1|1|1024",
            "EXPLAIN SELECT a FROM et53 QUALIFY ROW_NUMBER() OVER (ORDER BY b) = 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b) FROM (SELECT a, b FROM et53)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET53.A, "
                + "LAG(ET53.A, 2, 0) OVER (PARTITION BY ET53.B ORDER BY ET53.A DESC NULLS FIRST)|null|null|null ;; 1|"
                + "1|[0]|WindowFunction|null|null|LAG(ET53.A, 2, 0) OVER (PARTITION BY ET53.B ORDER BY ET53.A DESC "
                + "NULLS FIRST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET53|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a, 2, 0) OVER (PARTITION BY b ORDER BY a DESC) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|E.A, "
                + "ROW_NUMBER() OVER (ORDER BY E.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|"
                + "ROW_NUMBER() OVER (ORDER BY E.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|E|B, A, B|1|1|1024",
            "EXPLAIN SELECT e.a, ROW_NUMBER() OVER (ORDER BY e.b) FROM et53 e");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A, "
                + "ROW_NUMBER() OVER (ORDER BY UPPER(ET53.B) ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|"
                + "null|null|ROW_NUMBER() OVER (ORDER BY UPPER(ET53.B) ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET53, TEST_DB.TEST_SCHEMA.ET53|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY UPPER(b)) FROM et53");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET53.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET53.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|Sort|null|null|ET53.A "
                + "ASC NULLS LAST|null|null|null ;; 1|2|[1]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY "
                + "ET53.B ASC NULLS LAST)|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET53, "
                + "TEST_DB.TEST_SCHEMA.ET53|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b) FROM et53 ORDER BY a");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void firstWindowDecides() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE et54 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et54 VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET54.B DESC NULLS FIRST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b DESC) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LAG(ET54.A, 2, 0) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|"
                + "null|null|LAG(ET54.A, 2, 0) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a, 2, 0) OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LAG(ET54.A) OVER (PARTITION BY ET54.B ORDER BY ET54.A DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|LAG(ET54.A) OVER (PARTITION BY ET54.B ORDER BY ET54.A DESC NULLS FIRST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a) OVER (PARTITION BY b ORDER BY a DESC) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (PARTITION BY ET54.B ORDER BY ET54.A DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|ROW_NUMBER() OVER (PARTITION BY ET54.B ORDER BY ET54.A DESC NULLS FIRST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|A, B, "
                + "A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (PARTITION BY b ORDER BY a DESC) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET54.A, "
                + "SUM(ET54.A) OVER (ORDER BY ET54.B ASC NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|SUM(ET54.A) OVER (ORDER BY ET54.B ASC "
                + "NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET54|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, SUM(a) OVER (ORDER BY b), ROW_NUMBER() OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.A ASC NULLS LAST), SUM(ET54.A) OVER (ORDER BY ET54.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET54.A ASC "
                + "NULLS LAST), SUM(ET54.A) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|A, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY a), SUM(a) OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS FIRST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b NULLS FIRST) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, RANK() "
                + "OVER (ORDER BY ET54.B DESC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|RANK() "
                + "OVER (ORDER BY ET54.B DESC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, RANK() OVER (ORDER BY b DESC NULLS LAST) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LAG(ET54.A, 2) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|LAG(ET54.A, 2) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a, 2) OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LAG(ET54.A) OVER (PARTITION BY ET54.B ORDER BY ET54.A ASC NULLS LAST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|LAG(ET54.A) OVER (PARTITION BY ET54.B ORDER BY ET54.A ASC NULLS LAST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a) OVER (PARTITION BY b ORDER BY a) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.A ASC NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET54.A ASC "
                + "NULLS LAST), ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|A, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY a), ROW_NUMBER() OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS LAST, ET54.A ASC NULLS LAST)|null|null|null ;; 1|1|"
                + "[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET54.B ASC NULLS LAST, ET54.A ASC NULLS "
                + "LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|"
                + "A, B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY b, a) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "ROW_NUMBER() OVER (ORDER BY ET54.A DESC NULLS FIRST, ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|"
                + "[0]|WindowFunction|null|null|ROW_NUMBER() OVER (ORDER BY ET54.A DESC NULLS FIRST, ET54.B ASC NULLS "
                + "LAST)|null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|"
                + "A, B, A, B|1|1|1024",
            "EXPLAIN SELECT a, ROW_NUMBER() OVER (ORDER BY a DESC, b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET54.A, "
                + "MIN(ET54.A) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|MIN(ET54.A) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54|null|A, B|1|1|1024",
            "EXPLAIN SELECT a, MIN(a) OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LAG(ET54.A, 1, 0) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|WindowFunction|"
                + "null|null|LAG(ET54.A, 1, 0) OVER (ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LAG(a, 1, 0) OVER (ORDER BY b) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "LEAD(ET54.A) OVER (ORDER BY ET54.B DESC NULLS FIRST)|null|null|null ;; 1|1|[0]|WindowFunction|null|"
                + "null|LEAD(ET54.A) OVER (ORDER BY ET54.B DESC NULLS FIRST)|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a, LEAD(a) OVER (ORDER BY b DESC) FROM et54");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET54.A, "
                + "DENSE_RANK() OVER (PARTITION BY ET54.A ORDER BY ET54.B ASC NULLS LAST)|null|null|null ;; 1|1|[0]|"
                + "WindowFunction|null|null|DENSE_RANK() OVER (PARTITION BY ET54.A ORDER BY ET54.B ASC NULLS LAST)|"
                + "null|null|null ;; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET54, TEST_DB.TEST_SCHEMA.ET54|null|A, B, "
                + "A, B|1|1|1024",
            "EXPLAIN SELECT a, DENSE_RANK() OVER (PARTITION BY a ORDER BY b) FROM et54");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
