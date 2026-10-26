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
 * Every statement of an explicit transaction reads a stream as of the transaction's start, the transaction's own
 * changes not among them, and a commit leaves each stream the transaction consumed at that start, so the changes
 * made since stay for the next read; an autocommit statement is a transaction of its own (all live-verified).
 */
public class StreamRepeatableReadTest extends MeasuredCellsTestSupport {

    @Test
    public void readsInsideATransactionListTheSameChanges() {
        assertAnswer("Table T2 successfully created.", "CREATE OR REPLACE TABLE t2 (id INT, v STRING)");
        assertAnswer("1", "INSERT INTO t2 VALUES (1, 'a')");
        assertAnswer("Stream S2 successfully created.", "CREATE OR REPLACE STREAM s2 ON TABLE t2");
        assertAnswer("1", "INSERT INTO t2 VALUES (2, 'b')");
        assertAnswer("Table SINK successfully created.", "CREATE OR REPLACE TABLE sink (id INT)");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO sink SELECT id FROM s2");
        assertAnswer("1", "INSERT INTO t2 VALUES (9, 'z')");
        assertAnswer("1", "SELECT COUNT(*) FROM s2");
        assertAnswer("1", "INSERT INTO sink SELECT id FROM s2");
        assertAnswer("2|INSERT", "SELECT id, METADATA$ACTION FROM s2 ORDER BY 1");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('s2')");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("9|INSERT", "SELECT id, METADATA$ACTION FROM s2 ORDER BY 1");
        assertAnswer("2 ;; 2", "SELECT id FROM sink ORDER BY 1");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "SELECT COUNT(*) FROM s2");
        assertAnswer("1", "INSERT INTO t2 VALUES (10, 'y')");
        assertAnswer("1", "SELECT COUNT(*) FROM s2");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("9", "SELECT id FROM s2 ORDER BY 1");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1|0", "UPDATE t2 SET v = 'q' WHERE id = 1");
        assertAnswer("1", "DELETE FROM t2 WHERE id = 2");
        assertAnswer("9|z|INSERT|false", "SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM s2 ORDER BY 1, 3");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("1|a|DELETE|true ;; 1|q|INSERT|true ;; 2|b|DELETE|false ;; 9|z|INSERT|false",
            "SELECT id, v, METADATA$ACTION, METADATA$ISUPDATE FROM s2 ORDER BY 1, 3");
        assertAnswer("2", "INSERT INTO t2 SELECT id + 100, v FROM s2 WHERE METADATA$ACTION = 'INSERT'");
        assertAnswer("101|INSERT ;; 109|INSERT", "SELECT id, METADATA$ACTION FROM s2 ORDER BY 1");
        assertAnswer("2", "INSERT INTO sink SELECT id FROM s2");
        assertAnswer("", "SELECT id FROM s2 ORDER BY 1");
    }

    @Test
    public void aProcedureReadsAStreamAsItsTransactionBegan() {
        assertAnswer("Table T5 successfully created.", "CREATE OR REPLACE TABLE t5 (id INT)");
        assertAnswer("Stream S5 successfully created.", "CREATE OR REPLACE STREAM s5 ON TABLE t5");
        assertAnswer("Function P716A successfully created.",
            "CREATE OR REPLACE PROCEDURE p716a() RETURNS STRING LANGUAGE SQL AS $$\nDECLARE\n  c INT;\n  d "
                + "INT;\nBEGIN\n  BEGIN TRANSACTION;\n  INSERT INTO t5 VALUES (1);\n  SELECT COUNT(*) INTO :c FROM "
                + "s5;\n  COMMIT;\n  SELECT COUNT(*) INTO :d FROM s5;\n  RETURN TO_VARCHAR(c) || ',' || "
                + "TO_VARCHAR(d);\nEND;\n$$");
        assertAnswer("0,1", "CALL p716a()");
        assertAnswer("Function P716B successfully created.",
            "CREATE OR REPLACE PROCEDURE p716b() RETURNS STRING LANGUAGE SQL AS $$\nDECLARE\n  c INT;\nBEGIN\n  "
                + "INSERT INTO t5 VALUES (2);\n  SELECT COUNT(*) INTO :c FROM s5;\n  RETURN TO_VARCHAR(c);\nEND;\n$$");
        assertAnswer("2", "CALL p716b()");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("2", "CALL p716b()");
        assertAnswer("2", "SELECT COUNT(*) FROM s5");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("3", "SELECT COUNT(*) FROM s5");
    }

    @Test
    public void appendOnlyViewAndJoinStreamsReadAsTheTransactionBegan() {
        assertAnswer("Table T7 successfully created.", "CREATE OR REPLACE TABLE t7 (id INT)");
        assertAnswer("Stream S7 successfully created.", "CREATE OR REPLACE STREAM s7 ON TABLE t7 APPEND_ONLY = TRUE");
        assertAnswer("1", "INSERT INTO t7 VALUES (1)");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO t7 VALUES (2)");
        assertAnswer("1", "SELECT id FROM s7 ORDER BY 1");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("1 ;; 2", "SELECT id FROM s7 ORDER BY 1");
        assertAnswer("Table T8 successfully created.", "CREATE OR REPLACE TABLE t8 (id INT)");
        assertAnswer("View V8 successfully created.", "CREATE OR REPLACE VIEW v8 AS SELECT id FROM t8");
        assertAnswer("Stream S8 successfully created.", "CREATE OR REPLACE STREAM s8 ON VIEW v8");
        assertAnswer("1", "INSERT INTO t8 VALUES (1)");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO t8 VALUES (2)");
        assertAnswer("1|INSERT", "SELECT id, METADATA$ACTION FROM s8 ORDER BY 1");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("1|INSERT ;; 2|INSERT", "SELECT id, METADATA$ACTION FROM s8 ORDER BY 1");
        assertAnswer("Table T9 successfully created.", "CREATE OR REPLACE TABLE t9 (id INT)");
        assertAnswer("Stream S9 successfully created.", "CREATE OR REPLACE STREAM s9 ON TABLE t9");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO t9 VALUES (1)");
        assertAnswer("false", "SELECT SYSTEM$STREAM_HAS_DATA('s9')");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("true", "SELECT SYSTEM$STREAM_HAS_DATA('s9')");
        assertAnswer("Table J1 successfully created.", "CREATE OR REPLACE TABLE j1 (id INT, v INT)");
        assertAnswer("Table J2 successfully created.", "CREATE OR REPLACE TABLE j2 (id INT, w INT)");
        assertAnswer("1", "INSERT INTO j1 VALUES (1, 10)");
        assertAnswer("1", "INSERT INTO j2 VALUES (1, 100)");
        assertAnswer("View VJ successfully created.",
            "CREATE OR REPLACE VIEW vj AS SELECT j1.id, j1.v, j2.w FROM j1 JOIN j2 ON j1.id = j2.id");
        assertAnswer("Stream SJ successfully created.", "CREATE OR REPLACE STREAM sj ON VIEW vj");
        assertAnswer("Statement executed successfully.", "BEGIN");
        assertAnswer("1", "INSERT INTO j1 VALUES (2, 20)");
        assertAnswer("1", "INSERT INTO j2 VALUES (2, 200)");
        assertAnswer("", "SELECT id, v, w, METADATA$ACTION FROM sj ORDER BY 1");
        assertAnswer("Statement executed successfully.", "COMMIT");
        assertAnswer("2|20|200|INSERT", "SELECT id, v, w, METADATA$ACTION FROM sj ORDER BY 1");
    }
}
