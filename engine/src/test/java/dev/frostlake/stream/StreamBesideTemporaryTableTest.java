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

package dev.frostlake.stream;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A stream keeps its own table while a temporary table of the same name hides it: SYSTEM$STREAM_HAS_DATA answers,
 * SHOW STREAMS lists the stream as not stale, the changes made to the temporary table are not the stream's, and once
 * the temporary table is dropped the stream reads its own table's changes again. A stream created on the temporary
 * table is stale once that table is dropped. A view stream read where a temporary table that tracks no changes
 * hides a table its view reads is refused as the view reads that table (all live-verified).
 */
public class StreamBesideTemporaryTableTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableStreamKeepsItsTableBesideATemporaryTable() {
        assertAnswer("Table T successfully created.", "CREATE OR REPLACE TABLE t (id INT, v STRING)");
        assertAnswer("Stream ST successfully created.", "CREATE OR REPLACE STREAM st ON TABLE t");
        assertAnswer("1", "INSERT INTO t VALUES (2, 'perm2')");
        assertAnswer("Table T successfully created.", "CREATE TEMPORARY TABLE t (id INT, v STRING)");
        answer("SELECT id, METADATA$ACTION FROM st ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('st')");
        engine.execute("SHOW STREAMS LIKE 'ST'");
        assertAnswer("ST|TEST_DB.TEST_SCHEMA.T|false|DEFAULT",
            "SELECT \"name\", \"table_name\", \"stale\", \"mode\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("1", "INSERT INTO t VALUES (3, 'temp3')");
        answer("SELECT id, METADATA$ACTION FROM st ORDER BY 1");
        assertAnswer("Table U successfully created.", "CREATE OR REPLACE TABLE u (id INT, v STRING)");
        answer("SELECT COUNT(*) FROM st");
        assertAnswer("3|temp3", "SELECT id, v FROM t ORDER BY 1");
        assertAnswer("T successfully dropped.", "DROP TABLE t");
        assertAnswer("1", "INSERT INTO t VALUES (4, 'perm4')");
        answer("SELECT id, METADATA$ACTION FROM st ORDER BY 1");
        answer("SELECT id FROM u ORDER BY 1");
        assertAnswer("Table T successfully created.", "CREATE TEMPORARY TABLE t (id INT, v STRING)");
        assertAnswer("Stream STT successfully created.", "CREATE OR REPLACE STREAM stt ON TABLE t");
        assertAnswer("1", "INSERT INTO t VALUES (5, 'temp5')");
        assertAnswer("5|INSERT", "SELECT id, METADATA$ACTION FROM stt ORDER BY 1");
        engine.execute("SHOW STREAMS LIKE 'ST%'");
        assertAnswer("ST|TEST_DB.TEST_SCHEMA.T|false|DEFAULT ;; STT|TEST_DB.TEST_SCHEMA.T|false|DEFAULT",
            "SELECT \"name\", \"table_name\", \"stale\", \"mode\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("T successfully dropped.", "DROP TABLE t");
        assertAnswer("REFUSED Base table 'TEST_DB.TEST_SCHEMA.T' dropped, cannot read from stream 'STT' in line 1 "
                + "position 21.",
            "SELECT COUNT(*) FROM stt");
        assertAnswer("REFUSED Base table 'TEST_DB.TEST_SCHEMA.T' dropped, cannot read from stream 'STT' in line {3} "
                + "position {4}.",
            "SELECT SYSTEM$STREAM_HAS_DATA('stt')");
        answer("SELECT id, METADATA$ACTION FROM st ORDER BY 1");
    }

    @Test
    public void aViewStreamReadThroughAnUntrackedTemporaryTableIsRefused() {
        assertAnswer("Table TV successfully created.", "CREATE OR REPLACE TABLE tv (id INT, v STRING)");
        assertAnswer("View VTV successfully created.", "CREATE OR REPLACE VIEW vtv AS SELECT id, v FROM tv");
        assertAnswer("Stream SVT successfully created.", "CREATE OR REPLACE STREAM svt ON VIEW vtv");
        assertAnswer("1", "INSERT INTO tv VALUES (2, 'perm2')");
        assertAnswer("Table TV successfully created.", "CREATE TEMPORARY TABLE tv (id INT, v STRING)");
        assertAnswer("1", "INSERT INTO tv VALUES (3, 'temp3')");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.TEST_SCHEMA.TV'.",
            "SELECT id, v, METADATA$ACTION FROM svt ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('svt')");
        assertAnswer("3|temp3", "SELECT id, v FROM vtv ORDER BY 1");
        engine.execute("SHOW STREAMS LIKE 'SVT'");
        assertAnswer("SVT|TEST_DB.TEST_SCHEMA.VTV|TEST_DB.TEST_SCHEMA.TV|false",
            "SELECT \"name\", \"table_name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1");
        assertAnswer("TV successfully dropped.", "DROP TABLE tv");
        assertAnswer("2|perm2|INSERT", "SELECT id, v, METADATA$ACTION FROM svt ORDER BY 1");
        assertAnswer("1", "INSERT INTO tv VALUES (4, 'perm4')");
        assertAnswer("2|perm2|INSERT ;; 4|perm4|INSERT", "SELECT id, v, METADATA$ACTION FROM svt ORDER BY 1");
    }

    @Test
    public void aTrackedTemporaryTableKeepsAViewStreamReadable() {
        assertAnswer("Table TW successfully created.", "CREATE OR REPLACE TABLE tw (id INT)");
        assertAnswer("View VTW successfully created.", "CREATE OR REPLACE VIEW vtw AS SELECT id FROM tw");
        assertAnswer("Stream SVW successfully created.", "CREATE OR REPLACE STREAM svw ON VIEW vtw");
        assertAnswer("1", "INSERT INTO tw VALUES (1)");
        assertAnswer("Table TW2 successfully created.", "CREATE OR REPLACE TABLE tw2 (id INT)");
        assertAnswer("Table TW successfully created.", "CREATE TEMPORARY TABLE tw (id INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tw SET CHANGE_TRACKING = TRUE");
        assertAnswer("1", "INSERT INTO tw VALUES (2)");
        answer("SELECT id, METADATA$ACTION FROM svw ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('svw')");
        assertAnswer("1", "INSERT INTO tw2 SELECT id FROM svw");
        assertAnswer("TW successfully dropped.", "DROP TABLE tw");
        assertAnswer("", "SELECT id, METADATA$ACTION FROM svw ORDER BY 1");
        answer("SELECT id FROM tw2 ORDER BY 1");
        assertAnswer("Table TX successfully created.", "CREATE OR REPLACE TABLE tx (id INT)");
        assertAnswer("Stream SX successfully created.", "CREATE OR REPLACE STREAM sx ON TABLE tx");
        assertAnswer("1", "INSERT INTO tx VALUES (1)");
        assertAnswer("Table TX successfully created.", "CREATE TEMPORARY TABLE tx (id INT) CHANGE_TRACKING = TRUE");
        assertAnswer("1", "INSERT INTO tx VALUES (2)");
        assertAnswer("TX successfully dropped.", "DROP TABLE tx");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM sx ORDER BY 1");
    }
}
