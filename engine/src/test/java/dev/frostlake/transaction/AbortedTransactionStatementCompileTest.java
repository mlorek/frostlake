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
package dev.frostlake.transaction;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session whose transaction SYSTEM$ABORT_TRANSACTION ended refuses each statement for what it fails to compile for
 * before it refuses it for the aborted transaction (live-verified): an INSERT's, UPDATE's, DELETE's or MERGE's table,
 * columns and values, a CREATE's taken name and the query it is made from - a view's named in full, its columns counted
 * and named - an ALTER's, DROP's or TRUNCATE's missing object, a DESCRIBE's or a SHOW's object and a CALL's procedure.
 * A statement that compiles, a DROP IF EXISTS and a CREATE IF NOT EXISTS meet the aborted transaction, and none of them
 * does anything.
 */
public class AbortedTransactionStatementCompileTest extends MeasuredCellsTestSupport {

    /** DML, DDL, DESCRIBE, SHOW and CALL in an aborted transaction. */
    @Test
    public void dmlAndDdlCompileFirst() {
        engine.execute("CREATE OR REPLACE TABLE ab (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ab2 (a INT, b INT)");
        assertCells(new String[][] {
            {"BEGIN",
                "Statement executed successfully."},
            {"SET txid = (SELECT CURRENT_TRANSACTION())",
                "Statement executed successfully."},
        });
        engine.execute("SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
        assertCells(new String[][] {
            {"INSERT INTO ab SELECT * FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"INSERT INTO ab SELECT nosuch_col FROM ab",
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_COL'"},
            {"MERGE INTO no_such_table_zq7 USING ab ON TRUE WHEN MATCHED THEN DELETE",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"MERGE INTO ab USING no_such_table_zq7 s ON ab.a = s.a WHEN MATCHED THEN DELETE",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"CREATE TABLE ab4 AS SELECT * FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"CREATE VIEW v_zq8 AS SELECT * FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7."},
            {"CREATE VIEW v_zq8 AS SELECT a FROM ab",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"ALTER TABLE no_such_table_zq7 ADD COLUMN c INT",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7."},
            {"DROP VIEW no_such_view_zq7",
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.NO_SUCH_VIEW_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_VIEW_ZQ7."},
            {"DROP TABLE IF EXISTS no_such_table_zq7",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"CREATE TABLE IF NOT EXISTS ab (x INT)",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"UPDATE ab SET a = nosuch_col",
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH_COL'"},
            {"DELETE FROM ab USING no_such_table_zq7 WHERE TRUE",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"INSERT INTO ab VALUES ('x')",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"INSERT INTO ab VALUES (1/0)",
                "REFUSED SQL compilation error: | Invalid expression [1 / 0] in VALUES clause"},
            {"CREATE SEQUENCE sq_zq7",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"DESCRIBE TABLE no_such_table_zq7",
                "REFUSED SQL compilation error: | Table 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"SHOW TABLES IN DATABASE no_such_db_zq7",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"CALL no_such_proc_zq7()",
                "REFUSED SQL compilation error: | Unknown function NO_SUCH_PROC_ZQ7."},
            {"CREATE OR REPLACE VIEW v_zq8 (q) AS SELECT 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"CREATE OR REPLACE VIEW v_zq8 (q, r) AS SELECT 1",
                "REFUSED SQL compilation error: | Invalid column definition list"},
            {"CREATE TABLE ab (x INT)",
                "REFUSED SQL compilation error: | Object 'AB' already exists."},
            {"INSERT INTO ab (a, a) VALUES (1, 2)",
                "REFUSED SQL compilation error: | duplicate column name 'A'"},
            {"UPDATE ab SET a = 1 WHERE nosuch_col = 1",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_COL'"},
            {"DROP TABLE ab2",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ab2",
                "0"},
            {"SHOW VIEWS LIKE 'V_ZQ8'",
                ""},
            {"SHOW SEQUENCES LIKE 'SQ_ZQ7'",
                ""},
        });
    }

    /** Queries, inserts, updates and drops in an aborted transaction. */
    @Test
    public void queriesInsertsAndDropsCompileFirst() {
        engine.execute("CREATE OR REPLACE TABLE ab (a INT)");
        engine.execute("CREATE OR REPLACE TABLE ab2 (a INT, b INT)");
        assertCells(new String[][] {
            {"BEGIN",
                "Statement executed successfully."},
            {"SET txid = (SELECT CURRENT_TRANSACTION())",
                "Statement executed successfully."},
        });
        engine.execute("SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
        assertCells(new String[][] {
            {"SELECT * FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"SELECT nosuch_col FROM ab",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL'"},
            {"SELECT NOSUCH_FN_ZQ7()",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZQ7."},
            {"SELECT UPPER()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[UPPER()], expected 1, got 0"},
            {"SELECT 1 FROM ab x, ab x",
                "REFUSED SQL compilation error: | duplicate alias 'X'"},
            {"INSERT INTO no_such_table_zq7 VALUES (1)",
                "REFUSED SQL compilation error: | Table 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"INSERT INTO ab VALUES (1, 2)",
                "REFUSED SQL compilation error: | Insert value list does not match column list expecting 1 but got 2"},
            {"CREATE TABLE ab (x INT)",
                "REFUSED SQL compilation error: | Object 'AB' already exists."},
            {"SELECT 1/0",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT 'a'::NUMBER",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"SELECT 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"INSERT INTO ab VALUES (1)",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"UPDATE ab SET a = 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"UPDATE no_such_table_zq7 SET a = 1",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"DELETE FROM no_such_table_zq7",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"SELECT a FROM ab WHERE nosuch_col = 1",
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_COL'"},
            {"CREATE TABLE ab3 (x INT)",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"DROP TABLE no_such_table_zq7",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7."},
            {"SELECT a FROM ab2",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"UPDATE ab SET nosuch_col = 1",
                "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_COL'"},
            {"INSERT INTO ab (nosuch_col) VALUES (1)",
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'NOSUCH_COL'"},
            {"SELECT ABS(1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2"},
            {"SELECT a FROM ab GROUP BY 2",
                "REFUSED SQL compilation error: | [2] is not a valid group by expression"},
            {"SELECT SUM(a) FROM ab GROUP BY a HAVING b > 1",
                "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'B'"},
            {"SELECT * FROM ab JOIN no_such_table_zq7 ON TRUE",
                "REFUSED SQL compilation error: | Object 'NO_SUCH_TABLE_ZQ7' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE NO_SUCH_TABLE_ZQ7."},
            {"DELETE FROM ab WHERE nosuch_col = 1",
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_COL'"},
            {"MERGE INTO ab USING ab2 ON ab.a = ab2.a WHEN MATCHED THEN UPDATE SET a = 1",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"CREATE VIEW v_zq7 AS SELECT 1",
                "REFUSED SQL compilation error: | Missing column specification"},
            {"ALTER TABLE ab ADD COLUMN c INT",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"TRUNCATE TABLE ab",
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                + "ROLLBACK."},
            {"TRUNCATE TABLE no_such_table_zq7",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NO_SUCH_TABLE_ZQ7."},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) \"VALUES\")'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(year, NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) DUAL)'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 FROM ab))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"12\" FROM AB AS AB)'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(month, NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(month from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(EXTRACT(year FROM NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(YEAR(NULL::DATE))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from SYSTEM$NULL_TO_DATE(null))'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION(DATE_PART(year, CURRENT_DATE()))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found 'EXTRACT(year from CURRENT_DATE())'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12 AS q FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 12 AS \"Q\" FROM (VALUES (null)) \"VALUES\")'"},
            {"SELECT SYSTEM$ABORT_TRANSACTION((SELECT 'a' FROM DUAL))",
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                + "constant, found '(SELECT 'a' AS \"'A'\" FROM (VALUES (null)) \"VALUES\")'"},
        });
    }
}
