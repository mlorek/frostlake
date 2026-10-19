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
 * Which statements leave a transaction behind for LAST_TRANSACTION to answer. DML does, whether or not it
 * changes a row, and so do a TRUNCATE, a COPY into a table, a CALL that writes, a CREATE TABLE … AS SELECT, a
 * table CLONE and a CREATE STREAM, the end of an explicit transaction, and a statement that fails over a
 * value. Plain DDL, a query, SHOW, SET, ALTER SESSION, an unload and a statement refused while it compiles
 * leave the last transaction as it was. Every answer is live-verified.
 */
public class LastTransactionByStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void whatEachStatementLeavesBehind() {
        try {
            engine.execute("CREATE OR REPLACE TABLE lt_a (a INT, b INT)");
            engine.execute("CREATE OR REPLACE STAGE lt_st");
            engine.execute("CREATE OR REPLACE PROCEDURE lt_p() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN INSERT INTO lt_a VALUES "
            + "(9, 9); RETURN 1; END'");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE TABLE lt_n (a INT)");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("ALTER TABLE lt_n ADD COLUMN c INT");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE VIEW lt_v AS SELECT a FROM lt_a");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("COMMENT ON TABLE lt_n IS 'c'");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE SEQUENCE lt_q");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("SELECT COUNT(*) FROM lt_a");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("SHOW TABLES LIKE 'LT_A'");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("SET lt_var = 1");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("ALTER SESSION SET QUERY_TAG = 'lt'");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("COPY INTO @lt_st/x FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("DROP TABLE lt_n");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("UNDROP TABLE lt_n");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("ALTER TABLE lt_n RENAME TO lt_n2");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("GRANT SELECT ON TABLE lt_n2 TO ROLE PUBLIC");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE TABLE lt_b AS SELECT 1 AS a");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE TABLE lt_f (a INT) AS SELECT 1");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE TABLE lt_c CLONE lt_a");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE STREAM lt_s ON TABLE lt_a");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("INSERT INTO lt_a (a, b) VALUES (1, 2)");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("INSERT INTO lt_a (a, b) SELECT 1, 2 WHERE 1 = 0");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("UPDATE lt_a SET a = 1 WHERE 1 = 0");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("DELETE FROM lt_a WHERE 1 = 0");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("MERGE INTO lt_a USING (SELECT 1 AS a) s ON lt_a.a = s.a WHEN MATCHED THEN UPDATE SET b = 3");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("COPY INTO lt_f FROM @lt_st/x FILE_FORMAT = (TYPE = CSV)");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CALL lt_p()");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("INSERT OVERWRITE INTO lt_f SELECT 3");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("TRUNCATE TABLE lt_f");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            assertAnswer(
                "REFUSED DML operation to table LT_A failed on column A with error: Numeric value 'x' is not "
                + "recognized",
                "INSERT INTO lt_a (a, b) VALUES ('x', 2)");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'LT_MISSING' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE LT_MISSING.",
                "INSERT INTO lt_missing VALUES (1)");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("BEGIN");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("SELECT COUNT(*) FROM lt_a");
            assertAnswer(
                "true",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("COMMIT");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("BEGIN");
            engine.execute("INSERT INTO lt_a (a, b) VALUES (5, 6)");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("ROLLBACK");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            engine.execute("BEGIN");
            engine.execute("SET prev = (SELECT IFNULL(LAST_TRANSACTION(), 'none'))");
            engine.execute("CREATE OR REPLACE TABLE lt_d (a INT)");
            assertAnswer(
                "false",
                "SELECT IFNULL(LAST_TRANSACTION(), 'none') = $prev");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
        } finally {
            engine.execute("ROLLBACK");
            engine.execute("ALTER SESSION UNSET QUERY_TAG");
        }
    }
}
