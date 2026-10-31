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
 * A stream on a view whose table was dropped and created again is stale until it is read or SYSTEM$STREAM_HAS_DATA
 * asks of it, which re-bind it to the table the view's name reads now: a stream holding no change it has not read
 * reads that table from then on, refused until the table next changes where its change tracking began only with
 * the re-bind; one holding changes keeps reading its own table, and reads it again once UNDROP brings it back, while
 * SHOW STREAMS lists the table that replaced it. A view stream whose table is gone with no table under its name
 * fails as the view's expansion does (all live-verified).
 */
public class ViewStreamRebindTest extends MeasuredCellsTestSupport {

    @Test
    public void aStaleStreamRebindsOnItsFirstReadOrHasData() {
        assertAnswer("Schema PG successfully created.", "CREATE OR REPLACE SCHEMA pg");
        assertAnswer("Table RAW successfully created.", "CREATE TABLE pg.raw (id INT, v STRING)");
        assertAnswer("Schema PS successfully created.", "CREATE OR REPLACE SCHEMA ps");
        assertAnswer("View RAWV successfully created.", "CREATE VIEW ps.rawv AS SELECT id, v FROM pg.raw");
        assertAnswer("Stream SV successfully created.", "CREATE STREAM ps.sv ON VIEW ps.rawv");
        assertAnswer("Stream SV2 successfully created.", "CREATE STREAM ps.sv2 ON VIEW ps.rawv");
        assertAnswer("RAW successfully dropped.", "DROP TABLE pg.raw");
        assertAnswer("Table RAW successfully created.", "CREATE TABLE pg.raw (id INT, v STRING)");
        assertAnswer("Schema PA successfully created.", "CREATE OR REPLACE SCHEMA pa CLONE ps");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'.",
            "SELECT COUNT(*) FROM ps.sv");
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertAnswer("SV|TEST_DB.PG.RAW|false ;; SV2|1 inaccessible table(s)|true",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'.",
            "SELECT COUNT(*) FROM ps.sv");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('ps.sv2')");
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertAnswer("SV|TEST_DB.PG.RAW|false ;; SV2|TEST_DB.PG.RAW|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'.",
            "SELECT COUNT(*) FROM pa.sv");
        assertAnswer("Schema PB successfully created.", "CREATE OR REPLACE SCHEMA pb CLONE pa");
        engine.execute("SHOW STREAMS IN SCHEMA pb");
        assertAnswer("SV|TEST_DB.PG.RAW|false ;; SV2|1 inaccessible table(s)|true",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("0", "SELECT COUNT(*) FROM pb.sv");
        assertAnswer("0", "SELECT COUNT(*) FROM pb.sv2");
        assertAnswer("1", "INSERT INTO pg.raw VALUES (1, 'a')");
        assertAnswer("1", "SELECT COUNT(*) FROM pb.sv");
        assertAnswer("1", "SELECT COUNT(*) FROM pb.sv2");
        assertAnswer("1", "SELECT COUNT(*) FROM ps.sv");
        assertAnswer("1", "SELECT COUNT(*) FROM ps.sv2");
        assertAnswer("Stream SV2_C successfully created.", "CREATE OR REPLACE STREAM ps.sv2_c CLONE ps.sv2");
        assertAnswer("1", "SELECT COUNT(*) FROM ps.sv2_c");
        assertAnswer("1|a|INSERT", "SELECT id, v, METADATA$ACTION FROM ps.sv ORDER BY 1");
    }

    @Test
    public void aStreamHoldingChangesKeepsItsOwnTable() {
        assertAnswer("Schema PQ successfully created.", "CREATE OR REPLACE SCHEMA pq");
        assertAnswer("Table RAW2 successfully created.", "CREATE TABLE pq.raw2 (id INT)");
        assertAnswer("View V2 successfully created.", "CREATE VIEW pq.v2 AS SELECT id FROM pq.raw2");
        assertAnswer("Stream S3 successfully created.", "CREATE STREAM pq.s3 ON VIEW pq.v2");
        assertAnswer("Stream S4 successfully created.", "CREATE STREAM pq.s4 ON VIEW pq.v2");
        assertAnswer("1", "INSERT INTO pq.raw2 VALUES (1)");
        assertAnswer("1", "SELECT COUNT(*) FROM pq.s3");
        assertAnswer("RAW2 successfully dropped.", "DROP TABLE pq.raw2");
        assertAnswer("Table RAW2 successfully created.", "CREATE TABLE pq.raw2 (id INT)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('pq.s3')");
        engine.execute("SHOW STREAMS IN SCHEMA pq");
        assertAnswer("S3|TEST_DB.PQ.RAW2|false ;; S4|1 inaccessible table(s)|true",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PQ.RAW2'.",
            "SELECT COUNT(*) FROM pq.s3");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PQ.RAW2'.",
            "SELECT COUNT(*) FROM pq.s4");
        assertAnswer("1", "INSERT INTO pq.raw2 VALUES (2)");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PQ.RAW2'.",
            "SELECT COUNT(*) FROM pq.s3");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PQ.RAW2'.",
            "SELECT COUNT(*) FROM pq.s4");
        assertAnswer("RAW2 successfully dropped.", "DROP TABLE pq.raw2");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.PQ.V2': SQL "
                + "compilation error: | Object 'TEST_DB.PQ.RAW2' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.PQ.RAW2.",
            "SELECT COUNT(*) FROM pq.s3");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.PQ.V2': SQL "
                + "compilation error: | Object 'TEST_DB.PQ.RAW2' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.PQ.RAW2.",
            "SELECT SYSTEM$STREAM_HAS_DATA('pq.s4')");
    }

    @Test
    public void anUndroppedTableIsReadAgainAfterARebind() {
        assertAnswer("Schema UA successfully created.", "CREATE OR REPLACE SCHEMA ua");
        assertAnswer("Table T successfully created.", "CREATE TABLE ua.t (a INT)");
        assertAnswer("View V successfully created.", "CREATE VIEW ua.v AS SELECT a FROM ua.t");
        assertAnswer("Stream VS successfully created.", "CREATE STREAM ua.vs ON VIEW ua.v");
        assertAnswer("1", "INSERT INTO ua.t VALUES (1)");
        assertAnswer("T successfully dropped.", "DROP TABLE ua.t");
        engine.execute("SHOW STREAMS IN SCHEMA ua");
        assertAnswer("VS|1 inaccessible table(s)|true",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.UA.V': SQL "
                + "compilation error: | Object 'TEST_DB.UA.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.UA.T.",
            "SELECT a FROM ua.vs");
        assertAnswer("Table T successfully created.", "CREATE TABLE ua.t (a INT)");
        assertAnswer("1", "INSERT INTO ua.t VALUES (2)");
        assertAnswer("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.UA.T'.",
            "SELECT a FROM ua.vs");
        engine.execute("SHOW STREAMS IN SCHEMA ua");
        assertAnswer("VS|TEST_DB.UA.T|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ua.t RENAME TO ua.t_new");
        assertAnswer("Table T successfully restored.", "UNDROP TABLE ua.t");
        engine.execute("SHOW STREAMS IN SCHEMA ua");
        assertAnswer("VS|TEST_DB.UA.T_NEW|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("1", "SELECT a FROM ua.vs ORDER BY 1");
        assertAnswer("1", "INSERT INTO ua.t VALUES (3)");
        assertAnswer("1 ;; 3", "SELECT a FROM ua.vs ORDER BY 1");
        assertAnswer("1", "INSERT INTO ua.t_new VALUES (4)");
        assertAnswer("1 ;; 3", "SELECT a FROM ua.vs ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('ua.vs')");
    }

    @Test
    public void anUndroppedTableIsReadAgainWithoutARebind() {
        assertAnswer("Schema UB successfully created.", "CREATE OR REPLACE SCHEMA ub");
        assertAnswer("Table T successfully created.", "CREATE TABLE ub.t (a INT)");
        assertAnswer("View V successfully created.", "CREATE VIEW ub.v AS SELECT a FROM ub.t");
        assertAnswer("Stream VS successfully created.", "CREATE STREAM ub.vs ON VIEW ub.v");
        assertAnswer("1", "INSERT INTO ub.t VALUES (1)");
        assertAnswer("T successfully dropped.", "DROP TABLE ub.t");
        engine.execute("SHOW STREAMS IN SCHEMA ub");
        assertAnswer("VS|1 inaccessible table(s)|true",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.UB.V': SQL "
                + "compilation error: | Object 'TEST_DB.UB.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.UB.T.",
            "SELECT a FROM ub.vs");
        assertAnswer("Table T successfully created.", "CREATE TABLE ub.t (a INT)");
        assertAnswer("1", "INSERT INTO ub.t VALUES (2)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ub.t RENAME TO ub.t_new");
        assertAnswer("Table T successfully restored.", "UNDROP TABLE ub.t");
        engine.execute("SHOW STREAMS IN SCHEMA ub");
        assertAnswer("VS|TEST_DB.UB.T|false",
            "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer("1", "SELECT a FROM ub.vs ORDER BY 1");
        assertAnswer("1", "INSERT INTO ub.t VALUES (3)");
        assertAnswer("1 ;; 3", "SELECT a FROM ub.vs ORDER BY 1");
        assertAnswer("1", "INSERT INTO ub.t_new VALUES (4)");
        assertAnswer("1 ;; 3", "SELECT a FROM ub.vs ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('ub.vs')");
    }
}
