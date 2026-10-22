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
 * EXPLAIN of a query sorted with no row limit lists the scan's table twice, the sort keys' columns before the
 * columns it reads, and counts its partitions twice; a missing stage or file format under EXPLAIN DROP is refused,
 * and a CREATE TABLE AS VALUES names the VALUES alone (all live-verified).
 */
public class ExplainSortedScanTest extends MeasuredCellsTestSupport {

    @Test
    public void sortedScans() {
        engine.execute("CREATE OR REPLACE TABLE et (a INT, b VARCHAR)");
        engine.execute("INSERT INTO et VALUES (1, 'x'), (2, 'y')");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET, "
                + "TEST_DB.TEST_SCHEMA.ET|null|A, A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, A, B|1|1|1024",
            "EXPLAIN TABLE et ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'VALUES'.",
            "EXPLAIN CREATE TABLE ex AS VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'VALUES'.",
            "CREATE TABLE ex AS VALUES (1)");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, A|1|1|1024",
            "EXPLAIN SELECT a FROM et ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, A, B|1|1|1024",
            "EXPLAIN SELECT * FROM et ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|ET.B ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|B, A, B|1|1|1024",
            "EXPLAIN SELECT a FROM et ORDER BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|SortWithLimit|null|null|sortKey: [ET.A ASC NULLS LAST], rowCount: 1|null|null|null "
                + ";; 1|2|[1]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN SELECT a FROM et ORDER BY a LIMIT 1");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|ET.A DESC NULLS FIRST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, A|1|1|1024",
            "EXPLAIN SELECT a FROM et ORDER BY a DESC");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Sort|null|null|ET.B ASC NULLS LAST, ET.A ASC NULLS LAST|null|null|null ;; 1|2|"
                + "[1]|TableScan|TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, B, A, B|1|1|1024",
            "EXPLAIN SELECT a, b FROM et ORDER BY b, a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.ET|null|A, B|1|1|1024",
            "EXPLAIN TABLE et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A, ET.B|null|"
                + "null|null ;; 1|1|[0]|Sort|null|null|ET.B ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|B, A, B|1|1|1024",
            "EXPLAIN TABLE et ORDER BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "TableScan|TEST_DB.TEST_SCHEMA.ET|null|A|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "Sort|null|null|ET.B ASC NULLS LAST|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET, "
                + "TEST_DB.TEST_SCHEMA.ET|null|B, A, B|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT a FROM et ORDER BY b");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|null|null|null|"
                + "null ;; 1|1|[0]|CreateTableAsSelect|null|null|TEST_DB.TEST_SCHEMA.EX|null|null|null ;; 1|2|[1]|"
                + "Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.ET, "
                + "TEST_DB.TEST_SCHEMA.ET|null|A, A, B|1|1|1024",
            "EXPLAIN CREATE TABLE ex AS SELECT * FROM et ORDER BY a");
        assertAnswer("null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|null|null|null|null",
            "EXPLAIN CREATE TASK tk AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.STX' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.STX.",
            "EXPLAIN DROP STAGE stx");
        assertAnswer("REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.STX' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.STX.",
            "EXPLAIN DESCRIBE STAGE stx");
        assertAnswer("REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.FFX' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.FFX.",
            "EXPLAIN DROP FILE FORMAT ffx");
        assertAnswer("null|null|null|GlobalStats|null|null|null|2|2|2048 ;; 1|0|null|Result|null|null|ET.A|null|null|"
                + "null ;; 1|1|[0]|Sort|null|null|ET.A ASC NULLS LAST|null|null|null ;; 1|2|[1]|TableScan|"
                + "TEST_DB.TEST_SCHEMA.ET, TEST_DB.TEST_SCHEMA.ET|null|A, A|1|1|1024",
            "EXPLAIN SELECT a FROM (SELECT a FROM et ORDER BY a)");
    }
}
