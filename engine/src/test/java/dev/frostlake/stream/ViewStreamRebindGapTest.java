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
 * A view stream re-bound to a table that replaced its view's table, as its first read binds it (live-verified): it is
 * listed as reading that table under the name it has, renamed too; once the view's name holds no table its read fails
 * as the view's expansion does; and its read stays refused for the time the table tracked no change where a statement
 * had written the table before its tracking began - a CREATE TABLE AS, one of no row too, or an INSERT - whatever the
 * table takes later, an UPDATE, a DELETE, a TRUNCATE, an INSERT or a DDL change, while SYSTEM$STREAM_HAS_DATA answers
 * whether the table holds rows. A DDL change does not close the gap over a table nothing wrote either. A stream whose
 * table is gone with no table under its name answers SYSTEM$STREAM_HAS_DATA by its view's expansion.
 */
public class ViewStreamRebindGapTest extends MeasuredCellsTestSupport {

    @Test
    public void aDataRebindFollowsItsTablesName() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA uc927", "Schema UC927 successfully created."},
            {"CREATE TABLE uc927.t (a INT)", "Table T successfully created."},
            {"CREATE VIEW uc927.v AS SELECT a FROM uc927.t", "View V successfully created."},
            {"CREATE STREAM uc927.vs ON VIEW uc927.v", "Stream VS successfully created."},
            {"DROP TABLE uc927.t", "T successfully dropped."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA uc927");
        assertCells(new String[][] {
            {"SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "VS|1 inaccessible table(s)|true"},
            {"SELECT a FROM uc927.vs",
                "REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.UC927.V': SQL "
                + "compilation error: | Object 'TEST_DB.UC927.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.UC927.T."},
            {"CREATE TABLE uc927.t (a INT)", "Table T successfully created."},
            {"INSERT INTO uc927.t VALUES (2)", "1"},
            {"SELECT a FROM uc927.vs",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.UC927.T'."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA uc927");
        assertCells(new String[][] {
            {"SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "VS|TEST_DB.UC927.T|false"},
            {"ALTER TABLE uc927.t RENAME TO uc927.t_new", "Statement executed successfully."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA uc927");
        assertCells(new String[][] {
            {"SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "VS|TEST_DB.UC927.T_NEW|false"},
            {"SELECT a FROM uc927.vs ORDER BY 1",
                "REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.UC927.V': SQL "
                + "compilation error: | Object 'TEST_DB.UC927.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.UC927.T."},
            {"UNDROP TABLE uc927.t", "Table T successfully restored."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA uc927");
        assertCells(new String[][] {
            {"SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "VS|TEST_DB.UC927.T_NEW|false"},
            {"INSERT INTO uc927.t VALUES (3)", "1"},
            {"INSERT INTO uc927.t_new VALUES (4)", "1"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('uc927.vs')", "true"},
        });
    }

    @Test
    public void aGoneTablesStreams() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA hg927", "Schema HG927 successfully created."},
            {"CREATE TABLE hg927.t (a INT)", "Table T successfully created."},
            {"CREATE TABLE hg927.t2 (a INT)", "Table T2 successfully created."},
            {"CREATE VIEW hg927.v AS SELECT a FROM hg927.t", "View V successfully created."},
            {"CREATE STREAM hg927.vs ON VIEW hg927.v", "Stream VS successfully created."},
            {"CREATE STREAM hg927.ts ON TABLE hg927.t2", "Stream TS successfully created."},
            {"DROP TABLE hg927.t", "T successfully dropped."},
            {"DROP TABLE hg927.t2", "T2 successfully dropped."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('hg927.vs')",
                "REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.HG927.V': SQL "
                + "compilation error: | Object 'TEST_DB.HG927.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.HG927.T."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('hg927.ts')",
                "REFUSED Base table 'TEST_DB.HG927.T2' dropped, cannot read from stream 'TS' in line {3} position "
                + "{4}."},
            {"SELECT COUNT(*) FROM hg927.vs",
                "REFUSED SQL compilation error: | Failure during expansion of view 'TEST_DB.HG927.V': SQL "
                + "compilation error: | Object 'TEST_DB.HG927.T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TEST_DB.HG927.T."},
            {"SELECT COUNT(*) FROM hg927.ts",
                "REFUSED Base table 'TEST_DB.HG927.T2' dropped, cannot read from stream 'TS' in line 1 position 21."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA hg927");
        assertCells(new String[][] {
            {"SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "TS|No privilege or table dropped|true ;; VS|1 inaccessible table(s)|true"},
        });
    }

    @Test
    public void anUpdateAfterACreateTableAs() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gu927", "Schema GU927 successfully created."},
            {"CREATE TABLE gu927.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gu927.v AS SELECT id FROM gu927.raw", "View V successfully created."},
            {"CREATE STREAM gu927.s ON VIEW gu927.v", "Stream S successfully created."},
            {"DROP TABLE gu927.raw", "RAW successfully dropped."},
            {"CREATE TABLE gu927.raw AS SELECT 1 AS id", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM gu927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GU927.RAW'."},
            {"UPDATE gu927.raw SET id = 2", "1|0"},
            {"SELECT COUNT(*) FROM gu927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GU927.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gu927.s')", "true"},
            {"INSERT INTO gu927.raw (id) VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gu927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GU927.RAW'."},
        });
    }

    @Test
    public void aDeleteAfterACreateTableAs() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gd927", "Schema GD927 successfully created."},
            {"CREATE TABLE gd927.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gd927.v AS SELECT id FROM gd927.raw", "View V successfully created."},
            {"CREATE STREAM gd927.s ON VIEW gd927.v", "Stream S successfully created."},
            {"DROP TABLE gd927.raw", "RAW successfully dropped."},
            {"CREATE TABLE gd927.raw AS SELECT 1 AS id", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM gd927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GD927.RAW'."},
            {"DELETE FROM gd927.raw", "1"},
            {"SELECT COUNT(*) FROM gd927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GD927.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gd927.s')", "false"},
            {"INSERT INTO gd927.raw (id) VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gd927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GD927.RAW'."},
        });
    }

    @Test
    public void anAddedColumnAfterACreateTableAs() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA ga927", "Schema GA927 successfully created."},
            {"CREATE TABLE ga927.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW ga927.v AS SELECT id FROM ga927.raw", "View V successfully created."},
            {"CREATE STREAM ga927.s ON VIEW ga927.v", "Stream S successfully created."},
            {"DROP TABLE ga927.raw", "RAW successfully dropped."},
            {"CREATE TABLE ga927.raw AS SELECT 1 AS id", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM ga927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GA927.RAW'."},
            {"ALTER TABLE ga927.raw ADD COLUMN c INT", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ga927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GA927.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ga927.s')", "true"},
            {"INSERT INTO ga927.raw (id) VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM ga927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GA927.RAW'."},
        });
    }

    @Test
    public void aCommentAfterACreateTableAs() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gc927", "Schema GC927 successfully created."},
            {"CREATE TABLE gc927.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gc927.v AS SELECT id FROM gc927.raw", "View V successfully created."},
            {"CREATE STREAM gc927.s ON VIEW gc927.v", "Stream S successfully created."},
            {"DROP TABLE gc927.raw", "RAW successfully dropped."},
            {"CREATE TABLE gc927.raw AS SELECT 1 AS id", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM gc927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GC927.RAW'."},
            {"ALTER TABLE gc927.raw SET COMMENT = 'x'", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM gc927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GC927.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gc927.s')", "true"},
            {"INSERT INTO gc927.raw (id) VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gc927.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GC927.RAW'."},
        });
    }

    @Test
    public void anAddedColumnOverAnEmptyTable() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA ge929", "Schema GE929 successfully created."},
            {"CREATE TABLE ge929.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW ge929.v AS SELECT id FROM ge929.raw", "View V successfully created."},
            {"CREATE STREAM ge929.s ON VIEW ge929.v", "Stream S successfully created."},
            {"DROP TABLE ge929.raw", "RAW successfully dropped."},
            {"CREATE TABLE ge929.raw (id INT)", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM ge929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GE929.RAW'."},
            {"ALTER TABLE ge929.raw ADD COLUMN c INT", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ge929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GE929.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ge929.s')", "false"},
        });
    }

    @Test
    public void anInsertAfterACreateTableAsOfNoRow() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gz929", "Schema GZ929 successfully created."},
            {"CREATE TABLE gz929.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gz929.v AS SELECT id FROM gz929.raw", "View V successfully created."},
            {"CREATE STREAM gz929.s ON VIEW gz929.v", "Stream S successfully created."},
            {"DROP TABLE gz929.raw", "RAW successfully dropped."},
            {"CREATE TABLE gz929.raw AS SELECT 1 AS id WHERE FALSE", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM gz929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GZ929.RAW'."},
            {"INSERT INTO gz929.raw VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gz929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GZ929.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gz929.s')", "true"},
        });
    }

    @Test
    public void anInsertAfterAnEarlierInsert() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gi929", "Schema GI929 successfully created."},
            {"CREATE TABLE gi929.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gi929.v AS SELECT id FROM gi929.raw", "View V successfully created."},
            {"CREATE STREAM gi929.s ON VIEW gi929.v", "Stream S successfully created."},
            {"DROP TABLE gi929.raw", "RAW successfully dropped."},
            {"CREATE TABLE gi929.raw (id INT)", "Table RAW successfully created."},
            {"INSERT INTO gi929.raw VALUES (1)", "1"},
            {"SELECT COUNT(*) FROM gi929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GI929.RAW'."},
            {"INSERT INTO gi929.raw VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gi929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GI929.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gi929.s')", "true"},
        });
    }

    @Test
    public void anInsertAfterATruncate() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA gt929", "Schema GT929 successfully created."},
            {"CREATE TABLE gt929.raw (id INT)", "Table RAW successfully created."},
            {"CREATE VIEW gt929.v AS SELECT id FROM gt929.raw", "View V successfully created."},
            {"CREATE STREAM gt929.s ON VIEW gt929.v", "Stream S successfully created."},
            {"DROP TABLE gt929.raw", "RAW successfully dropped."},
            {"CREATE TABLE gt929.raw AS SELECT 1 AS id", "Table RAW successfully created."},
            {"SELECT COUNT(*) FROM gt929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GT929.RAW'."},
            {"TRUNCATE TABLE gt929.raw", "Statement executed successfully."},
            {"INSERT INTO gt929.raw VALUES (5)", "1"},
            {"SELECT COUNT(*) FROM gt929.s",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.GT929.RAW'."},
            {"SELECT SYSTEM$STREAM_HAS_DATA('gt929.s')", "true"},
        });
    }
}
