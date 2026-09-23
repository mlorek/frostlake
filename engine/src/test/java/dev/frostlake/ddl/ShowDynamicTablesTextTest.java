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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW DYNAMIC TABLES re-prints a dynamic table's CREATE in its {@code text}: the words through the name as
 * written, a column list and the COMMENT it was created with, then its options from the current settings as
 * {@code lag}, {@code refresh_mode}, {@code initialize} and {@code warehouse}, then the AS and the query
 * exactly as written. A later ALTER of the lag shows there, a later comment does not. An unset comment and
 * cluster key are empty, and the clone, replica, Iceberg and insert-only flags read {@code false}. Every
 * cell is live-verified, over a warehouse created suspended.
 */
public class ShowDynamicTablesTextTest extends BaseDatabaseTest {

    /** The options after the lag, for a table created with the default refresh mode, on schedule. */
    private static final String OPTIONS = " refresh_mode = 'AUTO' initialize = 'ON_SCHEDULE' warehouse = DT_TEXT_WH";

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE WAREHOUSE DT_TEXT_WH WITH WAREHOUSE_SIZE = XSMALL AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE OR REPLACE DATABASE DT_TEXT_DB");
        engine.execute("CREATE OR REPLACE TABLE DT_TEXT_DB.PUBLIC.T (x INT, y INT)");
    }

    @AfterEach
    public void dropTable() {
        engine.execute("DROP DATABASE IF EXISTS DT_TEXT_DB");
        engine.execute("DROP WAREHOUSE IF EXISTS DT_TEXT_WH");
    }

    /** The named columns of the one dynamic table SHOW lists under a name, a comma between cells. */
    private String show(final String name, final String columns) {
        engine.execute("SHOW DYNAMIC TABLES LIKE '" + name + "' IN SCHEMA DT_TEXT_DB.PUBLIC");
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT " + columns + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))").getRows()) {
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void theCreateIsRePrintedAsWritten() {
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE DT TARGET_LAG = '1 day' WAREHOUSE = DT_TEXT_WH INITIALIZE = ON_SCHEDULE AS SELECT x FROM T");
        assertEquals(", , CREATE OR REPLACE DYNAMIC TABLE DT lag = '1 day'" + OPTIONS + " AS SELECT x FROM T, false, false, false, false, ",
            show("DT", "\"cluster_by\", \"comment\", \"text\", \"is_clone\", \"is_replica\", \"is_iceberg\", \"insert_only_inputs\","
                + " \"initialization_warehouse\""));
        engine.execute("CREATE DYNAMIC TABLE DT2 TARGET_LAG = DOWNSTREAM WAREHOUSE = DT_TEXT_WH REFRESH_MODE = FULL INITIALIZE = ON_SCHEDULE"
            + " COMMENT = 'hello' AS SELECT x, y FROM T WHERE x > 1");
        assertEquals("hello, CREATE DYNAMIC TABLE DT2 COMMENT = 'hello' lag = 'DOWNSTREAM' refresh_mode = 'FULL' initialize = 'ON_SCHEDULE'"
            + " warehouse = DT_TEXT_WH AS SELECT x, y FROM T WHERE x > 1", show("DT2", "\"comment\", \"text\""));
        engine.execute("create or replace dynamic table dt_text_db.public.dt3 target_lag='5 minutes' warehouse=dt_text_wh"
            + " initialize=on_schedule as select   x   from   t");
        assertEquals("create or replace dynamic table dt_text_db.public.dt3 lag = '5 minutes'" + OPTIONS + " as select   x   from   t",
            show("DT3", "\"text\""));
        engine.execute("CREATE DYNAMIC TABLE IF NOT EXISTS DT4 TARGET_LAG = '1 day' WAREHOUSE = \"DT_TEXT_WH\" INITIALIZE = ON_SCHEDULE"
            + " AS SELECT x FROM T");
        assertEquals("CREATE DYNAMIC TABLE IF NOT EXISTS DT4 lag = '1 day'" + OPTIONS + " AS SELECT x FROM T", show("DT4", "\"text\""));
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE DT_TEXT_DB.PUBLIC.DT5 TARGET_LAG = '1 day' WAREHOUSE = DT_TEXT_WH"
            + " INITIALIZE = ON_SCHEDULE AS\n  SELECT x\n  FROM T");
        assertEquals("CREATE OR REPLACE DYNAMIC TABLE DT_TEXT_DB.PUBLIC.DT5 lag = '1 day'" + OPTIONS + " AS\n  SELECT x\n  FROM T",
            show("DT5", "\"text\""));
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE DT6 (a) TARGET_LAG = '1 day' WAREHOUSE = DT_TEXT_WH INITIALIZE = ON_SCHEDULE"
            + " AS SELECT x FROM T");
        assertEquals("CREATE OR REPLACE DYNAMIC TABLE DT6 (a) lag = '1 day'" + OPTIONS + " AS SELECT x FROM T", show("DT6", "\"text\""));
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE DT7 TARGET_LAG = '1 day' WAREHOUSE = DT_TEXT_WH INITIALIZE = ON_SCHEDULE"
            + " COMMENT = 'it''s' AS SELECT x FROM T");
        assertEquals("it's, CREATE OR REPLACE DYNAMIC TABLE DT7 COMMENT = 'it''s' lag = '1 day'" + OPTIONS + " AS SELECT x FROM T",
            show("DT7", "\"comment\", \"text\""));
    }

    @Test
    public void theLagFollowsAnAlterAndTheCommentDoesNot() {
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE DT TARGET_LAG = '1 day' WAREHOUSE = DT_TEXT_WH INITIALIZE = ON_SCHEDULE"
            + " COMMENT = 'first' AS SELECT x FROM T");
        engine.execute("ALTER DYNAMIC TABLE DT SET COMMENT = 'second'");
        assertEquals("second, CREATE OR REPLACE DYNAMIC TABLE DT COMMENT = 'first' lag = '1 day'" + OPTIONS + " AS SELECT x FROM T",
            show("DT", "\"comment\", \"text\""));
        engine.execute("ALTER DYNAMIC TABLE DT UNSET COMMENT");
        engine.execute("ALTER DYNAMIC TABLE DT SET TARGET_LAG = DOWNSTREAM");
        assertEquals(", CREATE OR REPLACE DYNAMIC TABLE DT COMMENT = 'first' lag = 'DOWNSTREAM'" + OPTIONS + " AS SELECT x FROM T",
            show("DT", "\"comment\", \"text\""));
    }
}
