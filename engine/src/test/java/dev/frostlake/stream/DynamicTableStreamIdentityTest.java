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
 * A stream on a dynamic table knows the table's rows (live-verified): a grouping table's by the values of its group
 * keys, shown or not, an aggregate with no GROUP BY as one row, and a projection's by the rows it projects, so a row
 * whose values a refresh changes is an update - a DELETE and an INSERT under one row id with METADATA$ISUPDATE TRUE -
 * whichever rows one refresh changes, and a row that leaves and comes back unchanged is no change. A table refreshed in
 * full takes no stream, and AUTO refreshes an aggregate with no GROUP BY in full.
 */
public class DynamicTableStreamIdentityTest extends MeasuredCellsTestSupport {

    /** A grouping table's rows across refreshes. */
    @Test
    public void aGroupKeepsItsRow() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE TABLE db805 (k VARCHAR, v INT)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE d805 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT k, SUM(v) AS s FROM db805 GROUP BY k");
        engine.execute("CREATE OR REPLACE STREAM sd805 ON DYNAMIC TABLE d805");
        assertCells(new String[][] {
            {"INSERT INTO db805 VALUES ('x', 10), ('y', 1)",
                "2"},
            {"ALTER DYNAMIC TABLE d805 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sd805 ORDER BY k, METADATA$ACTION",
                "x|10|INSERT|false ;; y|1|INSERT|false"},
            {"CREATE OR REPLACE TABLE dc805 AS SELECT * FROM sd805",
                "Table DC805 successfully created."},
            {"INSERT INTO db805 VALUES ('x', 5)",
                "1"},
            {"ALTER DYNAMIC TABLE d805 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sd805 ORDER BY k, METADATA$ACTION",
                "x|10|DELETE|true ;; x|15|INSERT|true"},
            {"DELETE FROM db805 WHERE k = 'y'",
                "1"},
            {"ALTER DYNAMIC TABLE d805 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sd805 ORDER BY k, METADATA$ACTION",
                "x|10|DELETE|true ;; x|15|INSERT|true ;; y|1|DELETE|false"},
            {"UPDATE db805 SET v = v + 1 WHERE k = 'x'",
                "2|0"},
            {"INSERT INTO db805 VALUES ('z', 3)",
                "1"},
            {"ALTER DYNAMIC TABLE d805 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sd805 ORDER BY k, METADATA$ACTION",
                "x|10|DELETE|true ;; x|17|INSERT|true ;; y|1|DELETE|false ;; z|3|INSERT|false"},
        });
    }

    /** Full, projecting, grouping, scalar and distinct tables under three changes. */
    @Test
    public void eachShapeOfTable() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE TABLE de807 (k VARCHAR, j INT, v INT)");
        engine.execute("INSERT INTO de807 VALUES ('x', 1, 10), ('y', 1, 1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE df807 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = FULL AS SELECT k, SUM(v) AS s FROM de807 GROUP BY k");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dp807 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = INCREMENTAL AS SELECT k, j, v FROM de807");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dg807 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = INCREMENTAL AS SELECT k, j, SUM(v) AS s FROM de807 GROUP BY k, j");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dn807 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT SUM(v) AS s, COUNT(*) AS c FROM de807");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dd807 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = INCREMENTAL AS SELECT DISTINCT k FROM de807");
        engine.execute("CREATE OR REPLACE STREAM sp807 ON DYNAMIC TABLE dp807");
        engine.execute("CREATE OR REPLACE STREAM sg807 ON DYNAMIC TABLE dg807");
        engine.execute("CREATE OR REPLACE STREAM sdd807 ON DYNAMIC TABLE dd807");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAM sf807 ON DYNAMIC TABLE df807",
                "REFUSED Change tracking is not supported on dynamic tables with 'FULL' REFRESH_MODE unless the "
                + "Dynamic Table has FROZEN WHERE constraint specified."},
            {"CREATE OR REPLACE STREAM sn807 ON DYNAMIC TABLE dn807",
                "REFUSED Change tracking is not supported on dynamic tables with 'FULL' REFRESH_MODE unless the "
                + "Dynamic Table has FROZEN WHERE constraint specified."},
            {"SHOW DYNAMIC TABLES LIKE 'D%807' ->> SELECT \"name\", \"refresh_mode\" FROM $1 ORDER BY 1",
                "DD807|INCREMENTAL ;; DF807|FULL ;; DG807|INCREMENTAL ;; DN807|FULL ;; DP807|INCREMENTAL"},
            {"UPDATE de807 SET v = v + 5 WHERE k = 'x'",
                "1|0"},
            {"ALTER DYNAMIC TABLE df807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dp807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dg807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dn807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dd807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sf807 ORDER BY k, METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sf807",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT k, j, v, METADATA$ACTION, METADATA$ISUPDATE FROM sp807 ORDER BY k, j, v, METADATA$ACTION",
                "x|1|10|DELETE|true ;; x|1|15|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sp807",
                "1|2"},
            {"SELECT k, j, s, METADATA$ACTION, METADATA$ISUPDATE FROM sg807 ORDER BY k, j, METADATA$ACTION",
                "x|1|10|DELETE|true ;; x|1|15|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sg807",
                "1|2"},
            {"SELECT s, c, METADATA$ACTION, METADATA$ISUPDATE FROM sn807 ORDER BY METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sn807",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT k, METADATA$ACTION, METADATA$ISUPDATE FROM sdd807 ORDER BY k, METADATA$ACTION",
                ""},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sdd807",
                "0|0"},
            {"INSERT INTO de807 VALUES ('y', 1, 5), ('y', 1, -5)",
                "2"},
            {"ALTER DYNAMIC TABLE df807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dp807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dg807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dn807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dd807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sf807 ORDER BY k, METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sf807",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT k, j, v, METADATA$ACTION, METADATA$ISUPDATE FROM sp807 ORDER BY k, j, v, METADATA$ACTION",
                "x|1|10|DELETE|true ;; x|1|15|INSERT|true ;; y|1|-5|INSERT|false ;; y|1|5|INSERT|false"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sp807",
                "3|4"},
            {"SELECT k, j, s, METADATA$ACTION, METADATA$ISUPDATE FROM sg807 ORDER BY k, j, METADATA$ACTION",
                "x|1|10|DELETE|true ;; x|1|15|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sg807",
                "1|2"},
            {"SELECT s, c, METADATA$ACTION, METADATA$ISUPDATE FROM sn807 ORDER BY METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sn807",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT k, METADATA$ACTION, METADATA$ISUPDATE FROM sdd807 ORDER BY k, METADATA$ACTION",
                ""},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sdd807",
                "0|0"},
            {"UPDATE de807 SET k = 'w' WHERE k = 'y'",
                "3|0"},
            {"ALTER DYNAMIC TABLE df807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dp807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dg807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dn807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE dd807 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sf807 ORDER BY k, METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sf807",
                "REFUSED SQL compilation error: | Object 'SF807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SF807."},
            {"SELECT k, j, v, METADATA$ACTION, METADATA$ISUPDATE FROM sp807 ORDER BY k, j, v, METADATA$ACTION",
                "w|1|-5|INSERT|false ;; w|1|1|INSERT|true ;; w|1|5|INSERT|false ;; x|1|10|DELETE|true ;; "
                + "x|1|15|INSERT|true ;; y|1|1|DELETE|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sp807",
                "4|6"},
            {"SELECT k, j, s, METADATA$ACTION, METADATA$ISUPDATE FROM sg807 ORDER BY k, j, METADATA$ACTION",
                "w|1|1|INSERT|false ;; x|1|10|DELETE|true ;; x|1|15|INSERT|true ;; y|1|1|DELETE|false"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sg807",
                "3|4"},
            {"SELECT s, c, METADATA$ACTION, METADATA$ISUPDATE FROM sn807 ORDER BY METADATA$ACTION",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sn807",
                "REFUSED SQL compilation error: | Object 'SN807' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SN807."},
            {"SELECT k, METADATA$ACTION, METADATA$ISUPDATE FROM sdd807 ORDER BY k, METADATA$ACTION",
                "w|INSERT|false ;; y|DELETE|false"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sdd807",
                "2|2"},
        });
    }

    /** A stream refused on a table refreshed in full, APPEND_ONLY too. */
    @Test
    public void fullRefreshTakesNoStream() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE TABLE fb808 (k VARCHAR, v INT)");
        engine.execute("INSERT INTO fb808 VALUES ('x', 1)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE ff808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = FULL AS SELECT k, SUM(v) AS s FROM fb808 GROUP BY k");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fa808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT SUM(v) AS s FROM fb808");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fc808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT COUNT(*) AS c FROM fb808");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fg808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT k, SUM(v) AS s FROM fb808 GROUP BY k");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fm808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT MAX(v) AS m FROM fb808");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE fw808 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT k, v, ROW_NUMBER() OVER (ORDER BY v) AS r FROM fb808");
        assertCells(new String[][] {
            {"CREATE OR REPLACE STREAM sff808 ON DYNAMIC TABLE ff808",
                "REFUSED Change tracking is not supported on dynamic tables with 'FULL' REFRESH_MODE unless the "
                + "Dynamic Table has FROZEN WHERE constraint specified."},
            {"CREATE OR REPLACE STREAM sfa808 ON DYNAMIC TABLE fa808",
                "REFUSED Change tracking is not supported on dynamic tables with 'FULL' REFRESH_MODE unless the "
                + "Dynamic Table has FROZEN WHERE constraint specified."},
            {"CREATE OR REPLACE STREAM sff808 ON DYNAMIC TABLE ff808 APPEND_ONLY = TRUE",
                "REFUSED Change tracking is not supported on dynamic tables with 'FULL' REFRESH_MODE unless the "
                + "Dynamic Table has FROZEN WHERE constraint specified."},
            {"CREATE OR REPLACE STREAM sfg808 ON DYNAMIC TABLE fg808",
                "Stream SFG808 successfully created."},
            {"SHOW DYNAMIC TABLES LIKE 'F%808' ->> SELECT \"name\", \"refresh_mode\" FROM $1 ORDER BY 1",
                "FA808|FULL ;; FC808|FULL ;; FF808|FULL ;; FG808|INCREMENTAL ;; FM808|FULL ;; FW808|FULL"},
            {"SHOW STREAMS LIKE 'SF%808' ->> SELECT \"name\", \"stale\" FROM $1 ORDER BY 1",
                "SFG808|false"},
        });
    }

    /** AUTO's choice for aggregates, and group keys by place, hidden or absent. */
    @Test
    public void groupKeysAndAutoModes() {
        final Object wh = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        final String warehouse = wh == null ? "COMPUTE_WH" : String.valueOf(wh);
        engine.execute("CREATE OR REPLACE TABLE gb811 (k VARCHAR, v INT)");
        engine.execute("INSERT INTO gb811 VALUES ('x', 1), ('y', 3)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE ga811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT SUM(v) AS s FROM gb811");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gc811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT COUNT(*) AS c FROM gb811");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gm811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT MAX(v) AS m FROM gb811");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gh811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT k, SUM(v) AS s FROM gb811 GROUP BY k HAVING SUM(v) > 0");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gx811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT SUM(v) + 1 AS s FROM gb811");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gw811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT COUNT(*) AS c FROM gb811 WHERE v > 0");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gi811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = INCREMENTAL AS SELECT SUM(v) AS s, COUNT(*) AS c FROM gb811");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE gv811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT v, COUNT(*) AS c FROM gb811 GROUP BY v");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE go811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " AS SELECT k, SUM(v) AS s FROM gb811 GROUP BY 1");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE ge811 TARGET_LAG = '1 hour' WAREHOUSE = " + warehouse
            + " REFRESH_MODE = INCREMENTAL AS SELECT SUM(v) AS s FROM gb811 GROUP BY k");
        assertCells(new String[][] {
            {"SHOW DYNAMIC TABLES LIKE 'G%811' ->> SELECT \"name\", \"refresh_mode\", \"refresh_mode_reason\" FROM $1 "
                + "ORDER BY 1",
                "GA811|FULL|This dynamic table contains a complex query. Refresh mode has been automatically set to "
                + "FULL for more predictable performance. To use INCREMENTAL, re-create the dynamic table with "
                + "REFRESH_MODE=INCREMENTAL. See https://docs.snowflake.com/user-guide/dynamic-table-performance-guide "
                + "for details. ;; GC811|FULL|This dynamic table contains a complex query. Refresh mode has been "
                + "automatically set to FULL for more predictable performance. To use INCREMENTAL, re-create the "
                + "dynamic table with REFRESH_MODE=INCREMENTAL. See "
                + "https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details. ;; "
                + "GE811|INCREMENTAL|null ;; GH811|INCREMENTAL|null ;; GI811|INCREMENTAL|null ;; GM811|FULL|This "
                + "dynamic table contains a complex query. Refresh mode has been automatically set to FULL for more "
                + "predictable performance. To use INCREMENTAL, re-create the dynamic table with "
                + "REFRESH_MODE=INCREMENTAL. See https://docs.snowflake.com/user-guide/dynamic-table-performance-guide "
                + "for details. ;; GO811|INCREMENTAL|null ;; GV811|INCREMENTAL|null ;; GW811|FULL|This dynamic table "
                + "contains a complex query. Refresh mode has been automatically set to FULL for more predictable "
                + "performance. To use INCREMENTAL, re-create the dynamic table with REFRESH_MODE=INCREMENTAL. See "
                + "https://docs.snowflake.com/user-guide/dynamic-table-performance-guide for details. ;; "
                + "GX811|FULL|This dynamic table contains a complex query. Refresh mode has been automatically set to "
                + "FULL for more predictable performance. To use INCREMENTAL, re-create the dynamic table with "
                + "REFRESH_MODE=INCREMENTAL. See https://docs.snowflake.com/user-guide/dynamic-table-performance-guide "
                + "for details."},
            {"CREATE OR REPLACE STREAM sgi811 ON DYNAMIC TABLE gi811",
                "Stream SGI811 successfully created."},
            {"CREATE OR REPLACE STREAM sgv811 ON DYNAMIC TABLE gv811",
                "Stream SGV811 successfully created."},
            {"CREATE OR REPLACE STREAM sgo811 ON DYNAMIC TABLE go811",
                "Stream SGO811 successfully created."},
            {"CREATE OR REPLACE STREAM sge811 ON DYNAMIC TABLE ge811",
                "Stream SGE811 successfully created."},
            {"CREATE OR REPLACE STREAM sgh811 ON DYNAMIC TABLE gh811",
                "Stream SGH811 successfully created."},
            {"UPDATE gb811 SET v = v + 1 WHERE k = 'x'",
                "1|0"},
            {"ALTER DYNAMIC TABLE gi811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE gv811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE go811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE ge811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE gh811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT s, c, METADATA$ACTION, METADATA$ISUPDATE FROM sgi811 ORDER BY METADATA$ACTION",
                "4|2|DELETE|true ;; 5|2|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgi811",
                "1|2"},
            {"SELECT v, c, METADATA$ACTION, METADATA$ISUPDATE FROM sgv811 ORDER BY v, METADATA$ACTION",
                "1|1|DELETE|false ;; 2|1|INSERT|false"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgv811",
                "2|2"},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sgo811 ORDER BY k, METADATA$ACTION",
                "x|1|DELETE|true ;; x|2|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgo811",
                "1|2"},
            {"SELECT s, METADATA$ACTION, METADATA$ISUPDATE FROM sge811 ORDER BY s, METADATA$ACTION",
                "1|DELETE|true ;; 2|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sge811",
                "1|2"},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sgh811 ORDER BY k, METADATA$ACTION",
                "x|1|DELETE|true ;; x|2|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgh811",
                "1|2"},
            {"UPDATE gb811 SET v = v - 1 WHERE k = 'x'",
                "1|0"},
            {"INSERT INTO gb811 VALUES ('y', 2)",
                "1"},
            {"ALTER DYNAMIC TABLE gi811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE gv811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE go811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE ge811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"ALTER DYNAMIC TABLE gh811 REFRESH ->> SELECT 1 FROM $1 LIMIT 0",
                ""},
            {"SELECT s, c, METADATA$ACTION, METADATA$ISUPDATE FROM sgi811 ORDER BY METADATA$ACTION",
                "4|2|DELETE|true ;; 6|3|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgi811",
                "1|2"},
            {"SELECT v, c, METADATA$ACTION, METADATA$ISUPDATE FROM sgv811 ORDER BY v, METADATA$ACTION",
                "2|1|INSERT|false"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgv811",
                "1|1"},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sgo811 ORDER BY k, METADATA$ACTION",
                "y|3|DELETE|true ;; y|5|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgo811",
                "1|2"},
            {"SELECT s, METADATA$ACTION, METADATA$ISUPDATE FROM sge811 ORDER BY s, METADATA$ACTION",
                "3|DELETE|true ;; 5|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sge811",
                "1|2"},
            {"SELECT k, s, METADATA$ACTION, METADATA$ISUPDATE FROM sgh811 ORDER BY k, METADATA$ACTION",
                "y|3|DELETE|true ;; y|5|INSERT|true"},
            {"SELECT COUNT(DISTINCT METADATA$ROW_ID), COUNT(*) FROM sgh811",
                "1|2"},
        });
    }
}
