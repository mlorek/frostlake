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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$STREAM_BACKLOG lists the versions commits wrote of a stream's table past its offset, the table rewritten
 * whole, with the DML mode and the change records the account estimates, and refuses a name that is no stream
 * (all live-verified).
 */
public class StreamBacklogTest extends MeasuredCellsTestSupport {

    @Test
    public void backlogOfAStream() {
        engine.execute("CREATE OR REPLACE TABLE tsr (a INT)");
        engine.execute("INSERT INTO tsr VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM ssr ON TABLE tsr");
        engine.execute("CREATE OR REPLACE STREAM sap ON TABLE tsr APPEND_ONLY = TRUE");
        engine.execute("CREATE OR REPLACE VIEW vsr AS SELECT a FROM tsr");
        engine.execute("CREATE OR REPLACE STREAM svs ON VIEW vsr");
        assertAnswer("", "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('ssr'))");
        assertShape("BASE_TABLE_NAME:VARCHAR(134217728), STREAM_OFFSET_TIME:TIMESTAMP_LTZ, "
                + "DML_MODE:VARCHAR(134217728), ROWS_ADDED:NUMBER(38,0), ROWS_REMOVED:NUMBER(38,0), "
                + "ESTIMATED_ROWS_TO_PROCESS:NUMBER(38,0)",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('ssr'))");
        assertAnswer("", "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG(STREAM_NAME => 'ssr')) WHERE 1 = 0");
        assertAnswer("REFUSED Invalid STREAM 'TSR'. STREAM_BACKLOG expectes a fully or partially qualified Stream "
                + "name.",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('tsr'))");
        assertAnswer("REFUSED Invalid STREAM 'NOSUCH_S'. STREAM_BACKLOG expectes a fully or partially qualified "
                + "Stream name.",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('nosuch_s'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | missing required argument "
                + "[STREAM_NAME] for function [SYSTEM$STREAM_BACKLOG]",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG(NULL))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | too many arguments for function "
                + "[SYSTEM$STREAM_BACKLOG] expected 1, got 2",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('ssr', 1))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter 'STREAM_NAME'",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG(1))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | not enough arguments for function "
                + "[SYSTEM$STREAM_BACKLOG], expected 1, got 0",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG())");
        assertAnswer("1", "INSERT INTO tsr VALUES (3)");
        assertAnswer("TEST_DB.TEST_SCHEMA.TSR|INSERT|3|2|1|true",
            "SELECT BASE_TABLE_NAME, DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS, "
                + "STREAM_OFFSET_TIME IS NOT NULL FROM TABLE(SYSTEM$STREAM_BACKLOG('ssr'))");
        assertAnswer("2", "INSERT INTO tsr VALUES (4), (5)");
        assertAnswer("TEST_DB.TEST_SCHEMA.TSR|INSERT|3|2|1 ;; TEST_DB.TEST_SCHEMA.TSR|INSERT|5|3|2",
            "SELECT BASE_TABLE_NAME, DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ssr')) ORDER BY ROWS_ADDED");
        assertAnswer("1", "DELETE FROM tsr WHERE a = 1");
        assertAnswer("TEST_DB.TEST_SCHEMA.TSR|INSERT|3|2|1 ;; TEST_DB.TEST_SCHEMA.TSR|UPDATE|4|5|3 ;; "
                + "TEST_DB.TEST_SCHEMA.TSR|INSERT|5|3|2",
            "SELECT BASE_TABLE_NAME, DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ssr')) ORDER BY ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("TEST_DB.TEST_SCHEMA.TSR|INSERT|3|2|1 ;; TEST_DB.TEST_SCHEMA.TSR|UPDATE|4|5|4 ;; "
                + "TEST_DB.TEST_SCHEMA.TSR|INSERT|5|3|2",
            "SELECT BASE_TABLE_NAME, DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('sap')) ORDER BY ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("TEST_DB.TEST_SCHEMA.TSR|INSERT ;; TEST_DB.TEST_SCHEMA.TSR|INSERT ;; TEST_DB.TEST_SCHEMA.TSR|"
                + "UPDATE",
            "SELECT BASE_TABLE_NAME, DML_MODE FROM TABLE(SYSTEM$STREAM_BACKLOG('TEST_DB.TEST_SCHEMA.SSR'))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(SYSTEM$STREAM_BACKLOG('ssr'))");
    }

    @Test
    public void backlogByDmlKind() {
        engine.execute("CREATE OR REPLACE TABLE b1 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM s1 ON TABLE b1");
        engine.execute("CREATE OR REPLACE STREAM s1a ON TABLE b1 APPEND_ONLY = TRUE");
        assertAnswer("3", "INSERT INTO b1 VALUES (1), (2), (3)");
        assertAnswer("INSERT|3|0|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|0", "UPDATE b1 SET a = a + 10 WHERE a = 1");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1", "DELETE FROM b1 WHERE a = 2");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("2", "DELETE FROM b1");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2 ;; UPDATE|0|2|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2 ;; UPDATE|0|2|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1000", "INSERT INTO b1 SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2 ;; UPDATE|0|2|1 ;; INSERT|1000|0|1000",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2 ;; UPDATE|0|2|0 ;; INSERT|1000|0|1000",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1", "INSERT INTO b1 VALUES (5000)");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2 ;; UPDATE|0|2|1 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2 ;; UPDATE|0|2|0 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("10|0", "UPDATE b1 SET a = a + 1 WHERE a < 10");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2 ;; UPDATE|0|2|1 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1 ;; UPDATE|1001|1001|601",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2 ;; UPDATE|0|2|0 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1 ;; UPDATE|1001|1001|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "TRUNCATE TABLE b1");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|2 ;; UPDATE|2|3|2 ;; UPDATE|0|2|1 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1 ;; UPDATE|1001|1001|601 ;; TRUNCATE|0|1001|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|3|0|3 ;; UPDATE|3|3|0 ;; UPDATE|2|3|2 ;; UPDATE|0|2|0 ;; INSERT|1000|0|1000 ;; INSERT|"
                + "1001|1000|1 ;; UPDATE|1001|1001|0 ;; TRUNCATE|0|1001|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s1a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("0", "SELECT COUNT(*) FROM s1");
        assertAnswer("1004", "SELECT COUNT(*) FROM s1a");
        assertAnswer("8|TEST_DB.TEST_SCHEMA.B1",
            "SELECT COUNT(DISTINCT STREAM_OFFSET_TIME), MIN(BASE_TABLE_NAME) FROM TABLE(SYSTEM$STREAM_BACKLOG('s1'))");
    }

    @Test
    public void backlogOfInitialRowsAndNames() {
        engine.execute("CREATE OR REPLACE TABLE b2 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO b2 VALUES (1, 'w'), (2, 'x'), (3, 'y'), (4, 'z')");
        engine.execute("CREATE OR REPLACE TABLE b2sink (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE STREAM s2 ON TABLE b2 SHOW_INITIAL_ROWS = TRUE");
        engine.execute("CREATE OR REPLACE TABLE b3 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM \"s q\" ON TABLE b3");
        assertAnswer("",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s2')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|1",
            "MERGE INTO b2 USING (SELECT 1 AS a, 'z' AS b UNION ALL SELECT 9, 'n') src ON b2.a = src.a WHEN MATCHED "
                + "THEN UPDATE SET b = src.b WHEN NOT MATCHED THEN INSERT VALUES (src.a, src.b)");
        assertAnswer("UPDATE|5|4|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('s2')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO b2 VALUES (10, 'p')");
        assertAnswer("1", "INSERT INTO b2 VALUES (11, 'q')");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("4", "INSERT INTO b2sink SELECT a, b FROM s2");
        assertAnswer("1", "INSERT INTO b2 VALUES (12, 'r')");
        assertAnswer("", "SELECT DML_MODE FROM TABLE(SYSTEM$STREAM_BACKLOG('\"s q\"'))");
        assertAnswer("1", "INSERT INTO b3 VALUES (1)");
        assertAnswer("INSERT|1|0|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('\"s q\"'))");
        assertAnswer("REFUSED Invalid STREAM 'S Q'. STREAM_BACKLOG expectes a fully or partially qualified Stream "
                + "name.",
            "SELECT DML_MODE FROM TABLE(SYSTEM$STREAM_BACKLOG('s q'))");
    }
}
