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
 * A statement that would end an open transaction and is refused for the names it writes is refused before the
 * transaction is touched: a DROP, an ALTER or a COMMENT of a table or a view that is not there, or is there as
 * the other kind, an UNDROP of a table never dropped or of a name a table holds, a CREATE TABLE or a CREATE VIEW
 * under a taken name, a table created like or as the clone of a source that is not there. A ROLLBACK afterwards
 * undoes the transaction's rows; in a block or a procedure the statement's own refusal stands, not the scope's.
 * A column to add that is there, a column to drop that is not and a row a query cannot compute are refused after
 * the commit. A rename - of a table, of a column, of a view - ends no transaction at all (live-verified).
 */
public class RefusedBeforeTheTransactionEndsTest extends MeasuredCellsTestSupport {

    @Test
    public void refusedForWhatItNames() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tx (a INT)");
            answer(
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            answer(
                "CREATE OR REPLACE PROCEDURE k_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN DROP TABLE "
                    + "k_nosuch_plain; RETURN 'ran'; END $$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX.A' already exists.",
                "ALTER TABLE other_tx RENAME COLUMN a TO a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Table K_NOSUCH_UNDROP did not exist or was purged.",
                "UNDROP TABLE k_nosuch_undrop");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN.",
                "DROP TABLE k_nosuch_plain");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Object 'OTHER_TX.A' already exists.",
                "BEGIN ALTER TABLE other_tx RENAME COLUMN a TO a; RETURN 'ran'; END;");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Table "
                    + "K_NOSUCH_UNDROP did not exist or was purged.",
                "BEGIN UNDROP TABLE k_nosuch_undrop; RETURN 'ran'; END;");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN.",
                "BEGIN DROP TABLE k_nosuch_plain; RETURN 'ran'; END;");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "CREATE TABLE other_tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_ALTER' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_ALTER.",
                "ALTER TABLE k_nosuch_alter ADD COLUMN b INT");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | column 'NOSUCH_COL' does not exist",
                "ALTER TABLE other_tx DROP COLUMN nosuch_col");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.K_NOSUCH_VIEW' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_VIEW.",
                "DROP VIEW k_nosuch_view");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_C' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_C.",
                "COMMENT ON TABLE k_nosuch_c IS 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "ALTER TABLE other_tx RENAME TO other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.K_T2 failed on column A with error: Division "
                    + "by zero",
                "CREATE TABLE k_t2 AS SELECT 1/0 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | column 'A' already exists",
                "ALTER TABLE other_tx ADD COLUMN a INT");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "DROP TABLE IF EXISTS k_new");
            answer(
                "DROP PROCEDURE IF EXISTS k_proc()");
            answer(
                "DROP VIEW IF EXISTS k_v");
            answer(
                "DROP VIEW IF EXISTS k_v2");
            answer(
                "DROP TABLE IF EXISTS k_t2");
            answer(
                "DROP TABLE IF EXISTS k_t3");
            answer(
                "DROP TABLE IF EXISTS k_t4");
            answer(
                "DROP TABLE IF EXISTS k_t5");
        }
    }

    @Test
    public void moreStatementsAndScopes() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tx (a INT)");
            answer(
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            answer(
                "CREATE OR REPLACE PROCEDURE k_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN DROP TABLE "
                    + "k_nosuch_plain; RETURN 'ran'; END $$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Drop statement executed successfully (K_NOSUCH_PLAIN already dropped).",
                "DROP TABLE IF EXISTS k_nosuch_plain");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx SET COMMENT = 'c'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Table K_NEW successfully created.",
                "CREATE OR REPLACE TABLE k_new (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH_COL'",
                "SELECT nosuch_col FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN DROP TABLE IF EXISTS k_nosuch_plain; RETURN 'ran'; END;");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation "
                    + "error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN' does not exist or not authorized. This "
                    + "executable runs with owner's rights. The owner role <ROLE> must have at least one privilege "
                    + "granted on TABLE TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN.",
                "CALL k_proc()");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_PLAIN.",
                "EXECUTE IMMEDIATE 'DROP TABLE k_nosuch_plain'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
                "CREATE TABLE k_t3 (a NOSUCHTYPE)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','.",
                "DROP TABLE k_nosuch_plain, other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.K_NOSUCH_VIEW' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_VIEW.",
                "ALTER VIEW k_nosuch_view RENAME TO k_v9");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'K_NOSUCH_LIKE' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE K_NOSUCH_LIKE.",
                "CREATE TABLE k_t4 LIKE k_nosuch_like");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'K_NOSUCH_CLONE' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TABLE K_NOSUCH_CLONE.",
                "CREATE TABLE k_t5 CLONE k_nosuch_clone");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.K_NOSUCH_FN' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH_FN.",
                "DROP FUNCTION k_nosuch_fn()");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "DROP TABLE IF EXISTS k_new");
            answer(
                "DROP PROCEDURE IF EXISTS k_proc()");
            answer(
                "DROP VIEW IF EXISTS k_v");
            answer(
                "DROP VIEW IF EXISTS k_v2");
            answer(
                "DROP TABLE IF EXISTS k_t2");
            answer(
                "DROP TABLE IF EXISTS k_t3");
            answer(
                "DROP TABLE IF EXISTS k_t4");
            answer(
                "DROP TABLE IF EXISTS k_t5");
        }
    }

    @Test
    public void otherKindsTakenNamesAndUndrop() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tx (a INT)");
            answer(
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            answer(
                "CREATE OR REPLACE VIEW k_vw AS SELECT 1 AS a");
            answer(
                "CREATE OR REPLACE PROCEDURE k_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'ran'; END "
                    + "$$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA_ZQ7' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA_ZQ7.",
                "DROP TABLE nosuch_schema_zq7.t");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "ALTER TABLE tx RENAME TO other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_C' does not exist or not authorized.",
                "ALTER TABLE other_tx RENAME COLUMN nosuch_c TO a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_C' does not exist or not authorized.",
                "ALTER TABLE other_tx RENAME COLUMN nosuch_c TO b");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "CREATE TABLE other_tx AS SELECT 1 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "CREATE TABLE other_tx LIKE tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists as TABLE",
                "CREATE VIEW other_tx AS SELECT 1 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'VIEW'.",
                "DROP VIEW other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'.",
                "DROP TABLE k_vw");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | View 'TEST_DB.TEST_SCHEMA.K_NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH.",
                "COMMENT ON VIEW k_nosuch IS 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH_C' does not exist or not authorized.",
                "COMMENT ON COLUMN other_tx.nosuch_c IS 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "COMMENT ON TABLE other_tx IS 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "ALTER VIEW k_vw RENAME TO other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'OTHER_TX' already exists.",
                "UNDROP TABLE other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.K_NOSUCH_P' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                    + "PROCEDURE TEST_DB.TEST_SCHEMA.K_NOSUCH_P.",
                "DROP PROCEDURE k_nosuch_p()");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "DROP PROCEDURE IF EXISTS k_proc()");
            answer(
                "DROP VIEW IF EXISTS k_vw");
            answer(
                "DROP TABLE IF EXISTS other_tx");
        }
    }

    @Test
    public void temporaryTablesListsAndIfExists() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tx (a INT)");
            answer(
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            answer(
                "CREATE OR REPLACE VIEW k_vw AS SELECT 1 AS a");
            answer(
                "CREATE OR REPLACE PROCEDURE k_proc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'ran'; END "
                    + "$$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.K_PROC' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                    + "TEST_DB.TEST_SCHEMA.K_PROC.",
                "DROP FUNCTION k_proc()");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE IF EXISTS k_nosuch ADD COLUMN b INT");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.K_NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.K_NOSUCH.",
                "ALTER TABLE k_nosuch SWAP WITH other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TEST_SCHEMA.OTHER_TX' already exists.",
                "CREATE TABLE test_schema.other_tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE TEMPORARY TABLE other_tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'K_VW' already exists.",
                "CREATE VIEW k_vw AS SELECT 2 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | column 'A' already exists",
                "ALTER TABLE other_tx ADD COLUMN c INT, a INT");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.\"k_nosuch_plain\"' does not exist "
                    + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.\"k_nosuch_plain\".",
                "DROP TABLE \"k_nosuch_plain\"");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TX' already exists.",
                "CREATE TABLE tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | column 'NOSUCH_C' does not exist",
                "ALTER TABLE other_tx DROP COLUMN a, nosuch_c");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx RENAME COLUMN a TO \"a\"");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Drop statement executed successfully (K_NOSUCH_VIEW already dropped).",
                "DROP VIEW IF EXISTS k_nosuch_view");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "OTHER_TX already exists, statement succeeded.",
                "CREATE TABLE IF NOT EXISTS other_tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
                "ALTER TABLE other_tx ALTER COLUMN a SET DATA TYPE NOSUCHTYPE");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "DROP PROCEDURE IF EXISTS k_proc()");
            answer(
                "DROP VIEW IF EXISTS k_vw");
            answer(
                "DROP TABLE IF EXISTS other_tx");
        }
    }

    @Test
    public void aRenameEndsNoTransaction() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tx (a INT)");
            answer(
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx RENAME COLUMN a TO z");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "ran",
                "BEGIN ALTER TABLE other_tx RENAME COLUMN z TO a; RETURN 'ran'; END;");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx RENAME TO other_ty");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_ty RENAME TO other_tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "View K_VW2 successfully created.",
                "CREATE OR REPLACE VIEW k_vw2 AS SELECT 1 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx RENAME COLUMN a TO b");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE other_tx RENAME COLUMN b TO a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER VIEW k_vw2 RENAME TO k_vw3");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'K_VW3' already exists.",
                "CREATE VIEW k_vw3 AS SELECT 2 AS a");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "K_VW3 successfully dropped.",
                "DROP VIEW k_vw3");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'TX' already exists.",
                "UNDROP TABLE tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "TRUNCATE TABLE tx");
        } finally {
            answer(
                "ROLLBACK;");
            answer(
                "DROP VIEW IF EXISTS k_vw2");
            answer(
                "DROP VIEW IF EXISTS k_vw3");
            answer(
                "DROP VIEW IF EXISTS k_vw4");
            answer(
                "DROP TABLE IF EXISTS other_ty");
            answer(
                "DROP TABLE IF EXISTS other_tx");
        }
    }
}
