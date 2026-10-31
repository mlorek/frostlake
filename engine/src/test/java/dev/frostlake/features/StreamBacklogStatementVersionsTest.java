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
 * SYSTEM$STREAM_BACKLOG lists a version for each statement, inside an explicit transaction too: a statement that
 * changes no row lists one where it commits alone and none inside a transaction, a TRUNCATE lists one even of an
 * empty table, a MERGE is an UPDATE whatever its branches did, a stream on a view lists each table's versions, and
 * the estimate is three in ten of the rows added and removed rounded half up, an append-only stream reading the
 * rows a DELETE left and a MERGE's rows where the table grew (all live-verified).
 */
public class StreamBacklogStatementVersionsTest extends MeasuredCellsTestSupport {

    @Test
    public void eachStatementListsItsVersion() {
        engine.execute("CREATE OR REPLACE TABLE k1 (a INT)");
        engine.execute("INSERT INTO k1 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE STREAM ks1 ON TABLE k1");
        engine.execute("CREATE OR REPLACE TABLE k2 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM ks2 ON TABLE k2");
        engine.execute("CREATE OR REPLACE TABLE k3 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO k3 VALUES (1, 'x')");
        engine.execute("CREATE OR REPLACE STREAM ks3 ON TABLE k3 APPEND_ONLY = TRUE");
        engine.execute("CREATE OR REPLACE TABLE k4 (a INT)");
        engine.execute("CREATE OR REPLACE TABLE k5 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW kv AS SELECT a FROM k4 UNION ALL SELECT a FROM k5");
        engine.execute("CREATE OR REPLACE STREAM ksv ON VIEW kv");
        engine.execute("CREATE OR REPLACE TABLE k6 (a INT)");
        engine.execute("INSERT INTO k6 VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM ks6 ON TABLE k6");
        engine.execute("CREATE OR REPLACE TABLE k7 (a INT)");
        engine.execute("CREATE OR REPLACE STREAM ks7 ON TABLE k7");
        assertAnswer("0", "DELETE FROM k1 WHERE a = 999");
        assertAnswer("UPDATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|0", "UPDATE k1 SET a = a + 1 WHERE a = 1");
        assertAnswer("UPDATE|0|0|0 ;; UPDATE|2|2|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks1')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "TRUNCATE TABLE k2");
        assertAnswer("TRUNCATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks2')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("0", "DELETE FROM k2");
        assertAnswer("TRUNCATE|0|0|0 ;; UPDATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks2')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("0|0", "UPDATE k2 SET a = 1 WHERE a = 2");
        assertAnswer("TRUNCATE|0|0|0 ;; UPDATE|0|0|0 ;; UPDATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks2')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|1",
            "MERGE INTO k3 USING (SELECT 1 AS a, 'z' AS b UNION ALL SELECT 9, 'n') src ON k3.a = src.a WHEN MATCHED "
                + "THEN UPDATE SET b = src.b WHEN NOT MATCHED THEN INSERT VALUES (src.a, src.b)");
        assertAnswer("UPDATE|2|1|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks3')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1", "INSERT INTO k4 VALUES (1)");
        assertAnswer("2", "INSERT INTO k5 VALUES (2), (3)");
        assertAnswer("TEST_DB.TEST_SCHEMA.K4|INSERT|1|0|1 ;; TEST_DB.TEST_SCHEMA.K5|INSERT|2|0|2",
            "SELECT BASE_TABLE_NAME, DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ksv')) ORDER BY STREAM_OFFSET_TIME, BASE_TABLE_NAME, ROWS_ADDED, "
                + "ROWS_REMOVED");
        assertAnswer("1", "DELETE FROM k6 WHERE a = 1");
        assertAnswer("UPDATE|0|1|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks6')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO k6 VALUES (5)");
        assertAnswer("1|0", "UPDATE k6 SET a = 6 WHERE a = 5");
        assertAnswer("0", "DELETE FROM k6 WHERE a = 999");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("UPDATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks6')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO k6 VALUES (7)");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("UPDATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks6')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO k7 VALUES (1)");
        assertAnswer("1", "INSERT INTO k6 VALUES (8)");
        assertAnswer("1", "INSERT INTO k7 VALUES (2)");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("UPDATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1 ;; INSERT|2|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks6')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|1|0|1 ;; INSERT|2|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks7')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
    }

    @Test
    public void mergesAndStatementsOfOneTransaction() {
        engine.execute("CREATE OR REPLACE TABLE k8 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO k8 VALUES (1, 'x'), (2, 'y'), (3, 'z')");
        engine.execute("CREATE OR REPLACE STREAM ks8 ON TABLE k8 APPEND_ONLY = TRUE");
        engine.execute("CREATE OR REPLACE STREAM ks8s ON TABLE k8");
        engine.execute("CREATE OR REPLACE TABLE k9 (a INT)");
        engine.execute("INSERT INTO k9 VALUES (1)");
        engine.execute("CREATE OR REPLACE STREAM ks9 ON TABLE k9");
        engine.execute("CREATE OR REPLACE STREAM ks9a ON TABLE k9 APPEND_ONLY = TRUE");
        assertAnswer("1|1",
            "MERGE INTO k8 USING (SELECT 1 AS a, 'q' AS b UNION ALL SELECT 9, 'n') src ON k8.a = src.a WHEN MATCHED "
                + "THEN UPDATE SET b = src.b WHEN NOT MATCHED THEN INSERT VALUES (src.a, src.b)");
        assertAnswer("UPDATE|4|3|4",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("UPDATE|4|3|2",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8s')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1",
            "MERGE INTO k8 USING (SELECT 20 AS a, 'n' AS b) src ON k8.a = src.a WHEN NOT MATCHED THEN INSERT VALUES "
                + "(src.a, src.b)");
        assertAnswer("UPDATE|4|3|4 ;; UPDATE|5|4|5",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("UPDATE|4|3|2 ;; UPDATE|5|4|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8s')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|1",
            "MERGE INTO k8 USING (SELECT 2 AS a UNION ALL SELECT 30) src ON k8.a = src.a WHEN MATCHED THEN DELETE "
                + "WHEN NOT MATCHED THEN INSERT VALUES (src.a, 'd')");
        assertAnswer("UPDATE|4|3|4 ;; UPDATE|5|4|5 ;; UPDATE|5|5|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("UPDATE|4|3|2 ;; UPDATE|5|4|3 ;; UPDATE|5|5|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8s')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("0", "MERGE INTO k8 USING (SELECT 999 AS a) src ON k8.a = src.a WHEN MATCHED THEN DELETE");
        assertAnswer("UPDATE|4|3|4 ;; UPDATE|5|4|5 ;; UPDATE|5|5|0 ;; UPDATE|5|5|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("UPDATE|4|3|2 ;; UPDATE|5|4|3 ;; UPDATE|5|5|3 ;; UPDATE|5|5|3",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks8s')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("0", "INSERT INTO k9 SELECT a FROM k9 WHERE a < 0");
        assertAnswer("INSERT|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("1|0", "UPDATE k9 SET a = a WHERE a = 1");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("Statement executed successfully.", "TRUNCATE TABLE k9");
        assertAnswer("1", "INSERT INTO k9 VALUES (2)");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|1 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|0 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1|0", "UPDATE k9 SET a = 3 WHERE a = 2");
        assertAnswer("1|0", "UPDATE k9 SET a = 4 WHERE a = 3");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|1 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1 ;; UPDATE|1|1|1",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|0 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|0 ;; UPDATE|1|1|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "DELETE FROM k9 WHERE a = 4");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|1 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1 ;; UPDATE|1|1|1 "
                + ";; UPDATE|0|1|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|0 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|0 ;; UPDATE|1|1|0 "
                + ";; UPDATE|0|1|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("Statement executed successfully.", "TRUNCATE TABLE k9");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|1 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|1 ;; UPDATE|1|1|1 "
                + ";; UPDATE|0|1|0 ;; TRUNCATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
        assertAnswer("INSERT|0|0|0 ;; UPDATE|1|1|0 ;; TRUNCATE|0|1|0 ;; INSERT|1|0|1 ;; UPDATE|1|1|0 ;; UPDATE|1|1|0 "
                + ";; UPDATE|0|1|0 ;; TRUNCATE|0|0|0",
            "SELECT DML_MODE, ROWS_ADDED, ROWS_REMOVED, ESTIMATED_ROWS_TO_PROCESS FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG('ks9a')) ORDER BY STREAM_OFFSET_TIME, ROWS_ADDED, ROWS_REMOVED");
    }
}
