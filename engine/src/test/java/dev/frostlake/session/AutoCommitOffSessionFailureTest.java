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

package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A session with AUTOCOMMIT off keeps its implicit transaction open when a statement fails in it (live-verified):
 * a query of a table that is not there, an INSERT of a value its column refuses, and a DDL refused as it
 * compiles - CREATE VIEW over a bad query, DROP or ALTER of a table that is not there - undo only themselves, so
 * the rows written before them commit with the transaction, or roll back with it.
 */
public class AutoCommitOffSessionFailureTest extends MeasuredCellsTestSupport {

    @Test
    public void aFailedStatementKeepsTheTransactionOpen() {
        engine.execute("CREATE OR REPLACE TABLE tx (id INT)");
        engine.execute("ALTER SESSION SET AUTOCOMMIT = FALSE");
        assertCells(new String[][] {
            {"INSERT INTO tx VALUES (3)", "1"},
            {"SELECT * FROM nosuch_tx",
                "REFUSED SQL compilation error: | Object 'NOSUCH_TX' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TX."},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL", "true"},
            {"INSERT INTO tx VALUES (4)", "1"},
            {"INSERT INTO tx VALUES ('x')",
                "REFUSED DML operation to table TX failed on column ID with error: Numeric value 'x' is not "
                + "recognized"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL", "true"},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT id FROM tx ORDER BY 1", "3 ;; 4"},
            {"INSERT INTO tx VALUES (5)", "1"},
            {"CREATE OR REPLACE VIEW vbad AS SELECT nosuch FROM tx",
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'NOSUCH'"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL", "true"},
            {"INSERT INTO tx VALUES (6)", "1"},
            {"DROP TABLE nosuch_tx2",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_TX2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TX2."},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL", "true"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT id FROM tx ORDER BY 1", "3 ;; 4"},
            {"INSERT INTO tx VALUES (7)", "1"},
            {"ALTER TABLE nosuch_tx3 ADD COLUMN c INT",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_TX3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_TX3."},
            {"COMMIT", "Statement executed successfully."},
            {"SELECT id FROM tx ORDER BY 1", "3 ;; 4 ;; 7"},
            {"ALTER SESSION SET AUTOCOMMIT = TRUE", "Statement executed successfully."},
        });
    }
}
