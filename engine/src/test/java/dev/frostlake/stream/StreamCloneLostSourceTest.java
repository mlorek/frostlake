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
 * A schema clone keeps what each stream is bound to (live-verified). A table stream whose table went before the clone
 * is lost in the clone, whatever table now holds the name, a table in the cloned schema included: SHOW STREAMS and
 * DESCRIBE STREAM list it stale with {@code No privilege or table dropped} and {@code 1 inaccessible table(s)}, a read
 * is refused, {@code Table for stream 'ST_TAB' invalid due to loss of table before or during cloning of parent
 * entity}, GET_DDL names its table {@code CLONED_FROM_STREAM_WITH_DROPPED_SOURCE}, and a clone of the clone keeps it
 * lost. A stream cloned with CREATE STREAM … CLONE needs its table: {@code Table must be present to create/clone
 * stream}. A view stream any of whose view's tables went is as stale in the clone as in place.
 */
public class StreamCloneLostSourceTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableStreamWhoseTableWentIsLostInTheClone() {
        engine.execute("CREATE OR REPLACE SCHEMA pg");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        engine.execute("CREATE TABLE pg.gone (id INT)");
        engine.execute("CREATE TABLE pg.kept (id INT)");
        engine.execute("CREATE OR REPLACE SCHEMA ps");
        engine.execute("CREATE VIEW ps.rawv AS SELECT id, v FROM pg.raw");
        engine.execute("CREATE STREAM ps.st_view ON VIEW ps.rawv SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE STREAM ps.st_tab ON TABLE pg.raw");
        engine.execute("CREATE STREAM ps.st_app ON TABLE pg.raw APPEND_ONLY = TRUE COMMENT = 'cross'");
        engine.execute("CREATE STREAM ps.st_gone ON TABLE pg.gone");
        engine.execute("CREATE STREAM ps.st_kept ON TABLE pg.kept");
        engine.execute("CREATE TABLE ps.own (id INT)");
        engine.execute("CREATE STREAM ps.st_own ON TABLE ps.own");
        engine.execute("CREATE TABLE ps.own2 (id INT)");
        engine.execute("CREATE STREAM ps.st_own2 ON TABLE ps.own2");
        engine.execute("DROP TABLE pg.raw");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        engine.execute("DROP TABLE pg.gone");
        engine.execute("DROP TABLE ps.own");
        engine.execute("CREATE TABLE ps.own (id INT)");
        engine.execute("CREATE OR REPLACE SCHEMA ps2 CLONE ps");
        engine.execute("SHOW STREAMS IN SCHEMA ps2");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                "ST_APP|No privilege or table dropped|Table|1 inaccessible "
                + "table(s)|true|APPEND_ONLY|true|N/A|cross ;; ST_GONE|No privilege or table dropped|Table|1 "
                + "inaccessible table(s)|true|DEFAULT|true|N/A| ;; ST_KEPT|TEST_DB.PG.KEPT|Table|TEST_DB.PG.KEPT|fals"
                + "e|DEFAULT|false|N/A| ;; ST_OWN|No privilege or table dropped|Table|1 inaccessible "
                + "table(s)|true|DEFAULT|true|N/A| ;; ST_OWN2|TEST_DB.PS2.OWN2|Table|TEST_DB.PS2.OWN2|false|DEFAULT|f"
                + "alse|N/A| ;; ST_TAB|No privilege or table dropped|Table|1 inaccessible "
                + "table(s)|true|DEFAULT|true|N/A| ;; ST_VIEW|TEST_DB.PS2.RAWV|View|1 inaccessible "
                + "table(s)|true|DEFAULT|true|N/A|"},
        });
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                "ST_APP|No privilege or table dropped|Table|No privilege or table "
                + "dropped|true|APPEND_ONLY|true|N/A|cross ;; ST_GONE|No privilege or table dropped|Table|No "
                + "privilege or table dropped|true|DEFAULT|true|N/A| ;; "
                + "ST_KEPT|TEST_DB.PG.KEPT|Table|TEST_DB.PG.KEPT|false|DEFAULT|false|N/A| ;; ST_OWN|No privilege or "
                + "table dropped|Table|No privilege or table dropped|true|DEFAULT|true|N/A| ;; "
                + "ST_OWN2|TEST_DB.PS.OWN2|Table|TEST_DB.PS.OWN2|false|DEFAULT|false|N/A| ;; ST_TAB|No privilege or "
                + "table dropped|Table|No privilege or table dropped|true|DEFAULT|true|N/A| ;; "
                + "ST_VIEW|TEST_DB.PS.RAWV|View|1 inaccessible table(s)|true|DEFAULT|true|N/A|"},
            {"SELECT * FROM ps2.st_tab",
                "REFUSED Table for stream 'ST_TAB' invalid due to loss of table before or during cloning of parent "
                + "entity"},
            {"SELECT COUNT(*) FROM ps2.st_app",
                "REFUSED Table for stream 'ST_APP' invalid due to loss of table before or during cloning of parent "
                + "entity"},
            {"SELECT * FROM ps2.st_gone",
                "REFUSED Table for stream 'ST_GONE' invalid due to loss of table before or during cloning of "
                + "parent entity"},
            {"SELECT * FROM ps2.st_own",
                "REFUSED Table for stream 'ST_OWN' invalid due to loss of table before or during cloning of parent "
                + "entity"},
            {"SELECT COUNT(*) FROM ps2.st_kept", "0"},
            {"SELECT COUNT(*) FROM ps2.st_own2", "0"},
            {"SELECT * FROM ps.st_tab",
                "REFUSED Base table 'TEST_DB.PG.RAW' dropped, cannot read from stream 'ST_TAB' in line 1 position 14."},
            {"SELECT * FROM ps.st_own",
                "REFUSED Base table 'TEST_DB.PS.OWN' dropped, cannot read from stream 'ST_OWN' in line 1 position 14."},
            {"SELECT GET_DDL('STREAM', 'ps2.st_tab', TRUE)",
                "create or replace stream TEST_DB.PS2.ST_TAB on table CLONED_FROM_STREAM_WITH_DROPPED_SOURCE;"},
            {"SELECT GET_DDL('STREAM', 'ps2.st_tab')",
                "create or replace stream ST_TAB on table CLONED_FROM_STREAM_WITH_DROPPED_SOURCE;"},
            {"SELECT GET_DDL('STREAM', 'ps2.st_app', TRUE)",
                "create or replace stream TEST_DB.PS2.ST_APP on table CLONED_FROM_STREAM_WITH_DROPPED_SOURCE "
                + "append_only = true;"},
            {"SELECT GET_DDL('STREAM', 'ps2.st_own', TRUE)",
                "create or replace stream TEST_DB.PS2.ST_OWN on table CLONED_FROM_STREAM_WITH_DROPPED_SOURCE;"},
            {"SELECT GET_DDL('STREAM', 'ps.st_tab', TRUE)",
                "create or replace stream TEST_DB.PS.ST_TAB on table \"TEST_DB.PG.RAW\";"},
            {"SELECT GET_DDL('STREAM', 'ps2.st_kept', TRUE)",
                "create or replace stream TEST_DB.PS2.ST_KEPT on table KEPT;"},
            {"SELECT GET_DDL('STREAM', 'ps2.st_view', TRUE)",
                "create or replace stream TEST_DB.PS2.ST_VIEW on view RAWV;"},
        });
        engine.execute("DESCRIBE STREAM ps2.st_tab");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                "ST_TAB|No privilege or table dropped|Table|1 inaccessible table(s)|true|DEFAULT|true|N/A|"},
            {"SELECT SYSTEM$STREAM_HAS_DATA('ps2.st_tab')",
                "REFUSED Base table 'CLONED_FROM_STREAM_WITH_DROPPED_SOURCE' dropped, cannot read from stream "
                + "'ST_TAB' in line {3} position {4}."},
            {"INSERT INTO pg.raw VALUES (1, 'a')", "1"},
            {"SELECT COUNT(*) FROM ps2.st_view",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'."},
            {"SELECT * FROM ps2.st_tab",
                "REFUSED Table for stream 'ST_TAB' invalid due to loss of table before or during cloning of parent "
                + "entity"},
            {"CREATE STREAM ps.st_c CLONE ps.st_tab",
                "REFUSED SQL compilation error:  |  Table must be present to create/clone stream"},
            {"SHOW STREAMS LIKE 'ST_C' IN SCHEMA ps", ""},
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                ""},
            {"SELECT * FROM ps.st_c",
                "REFUSED SQL compilation error: | Object 'TEST_DB.PS.ST_C' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.PS.ST_C."},
            {"CREATE STREAM ps2.st_c2 CLONE ps2.st_tab",
                "REFUSED SQL compilation error:  |  Table must be present to create/clone stream"},
            {"SHOW STREAMS LIKE 'ST_C2' IN SCHEMA ps2", ""},
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                ""},
            {"SELECT * FROM ps2.st_c2",
                "REFUSED SQL compilation error: | Object 'TEST_DB.PS2.ST_C2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.PS2.ST_C2."},
            {"CREATE OR REPLACE SCHEMA ps3 CLONE ps2", "Schema PS3 successfully created."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA ps3");
        assertCells(new String[][] {
            {"SELECT * FROM ps3.st_tab",
                "REFUSED Table for stream 'ST_TAB' invalid due to loss of table before or during cloning of parent "
                + "entity"},
            {"ALTER STREAM ps2.st_tab SET COMMENT = 'x'", "Statement executed successfully."},
        });
        engine.execute("SHOW STREAMS LIKE 'ST_TAB' IN SCHEMA ps2");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"source_type\", \"base_tables\", \"stale\", \"mode\", "
                + "\"stale_after\" IS NULL, \"invalid_reason\", \"comment\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                + "ORDER BY 1",
                "ST_TAB|No privilege or table dropped|Table|1 inaccessible table(s)|true|DEFAULT|true|N/A|x"},
        });
        engine.execute("SHOW STREAMS LIKE 'ST_TAB' IN SCHEMA ps3");
        assertCells(new String[][] {
            {"SELECT \"table_name\", \"base_tables\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
                "No privilege or table dropped|1 inaccessible table(s)"},
        });
    }

    @Test
    public void aViewStreamKeepsItsTablesInTheClone() {
        engine.execute("CREATE OR REPLACE SCHEMA pg");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        engine.execute("CREATE OR REPLACE SCHEMA ps");
        engine.execute("CREATE VIEW ps.rawv AS SELECT id, v FROM pg.raw");
        engine.execute("CREATE STREAM ps.sv ON VIEW ps.rawv");
        engine.execute("CREATE STREAM ps.sv2 ON VIEW ps.rawv");
        engine.execute("CREATE STREAM ps.st ON TABLE pg.raw");
        engine.execute("DROP TABLE pg.raw");
        engine.execute("CREATE TABLE pg.raw (id INT, v STRING)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA pa CLONE ps", "Schema PA successfully created."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"base_tables\", \"stale\", \"stale_after\" IS NULL FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "ST|No privilege or table dropped|No privilege or table dropped|true|true ;; SV|TEST_DB.PS.RAWV|1 "
                + "inaccessible table(s)|true|true ;; SV2|TEST_DB.PS.RAWV|1 inaccessible table(s)|true|true"},
            {"SELECT COUNT(*) FROM ps.sv",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        assertCells(new String[][] {
            {"SELECT SYSTEM$STREAM_HAS_DATA('ps.sv2')", "false"},
        });
        engine.execute("SHOW STREAMS IN SCHEMA ps");
        engine.execute("SHOW STREAMS IN SCHEMA pa");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"base_tables\", \"stale\", \"stale_after\" IS NULL FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "ST|No privilege or table dropped|1 inaccessible table(s)|true|true ;; SV|TEST_DB.PA.RAWV|1 "
                + "inaccessible table(s)|true|true ;; SV2|TEST_DB.PA.RAWV|1 inaccessible table(s)|true|true"},
            {"SELECT COUNT(*) FROM pa.sv",
                "REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time "
                + "range requested on table 'TEST_DB.PG.RAW'."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA pa");
        assertCells(new String[][] {
            {"CREATE OR REPLACE SCHEMA pb CLONE pa", "Schema PB successfully created."},
        });
        engine.execute("SHOW STREAMS IN SCHEMA pb");
        assertCells(new String[][] {
            {"INSERT INTO pg.raw VALUES (1, 'a')", "1"},
            {"CREATE STREAM ps.sv_c CLONE ps.sv", "Stream SV_C successfully created."},
        });
        engine.execute("SHOW STREAMS LIKE 'SV_C' IN SCHEMA ps");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"base_tables\", \"stale\", \"stale_after\" IS NULL FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "SV_C|TEST_DB.PS.RAWV|TEST_DB.PG.RAW|false|false"},
            {"CREATE STREAM ps.sv2_c CLONE ps.sv2", "Stream SV2_C successfully created."},
        });
        engine.execute("SHOW STREAMS LIKE 'SV2_C' IN SCHEMA ps");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"base_tables\", \"stale\", \"stale_after\" IS NULL FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "SV2_C|TEST_DB.PS.RAWV|TEST_DB.PG.RAW|false|false"},
            {"SELECT GET_DDL('STREAM', 'pa.sv', TRUE)", "create or replace stream TEST_DB.PA.SV on view RAWV;"},
            {"SELECT GET_DDL('STREAM', 'pa.st', TRUE)",
                "create or replace stream TEST_DB.PA.ST on table CLONED_FROM_STREAM_WITH_DROPPED_SOURCE;"},
        });
        engine.execute("DESCRIBE STREAM pa.st");
        assertCells(new String[][] {
            {"SELECT \"name\", \"table_name\", \"base_tables\", \"stale\", \"stale_after\" IS NULL FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1",
                "ST|No privilege or table dropped|1 inaccessible table(s)|true|true"},
        });
    }
}
