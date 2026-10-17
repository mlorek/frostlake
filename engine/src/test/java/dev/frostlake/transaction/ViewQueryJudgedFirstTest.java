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
 * A CREATE VIEW whose query does not compile — a column or a relation it names that nothing answers to — is refused
 * with the transaction it would end still open, so a ROLLBACK after it undoes the transaction's rows; in a block the
 * view's own fault is named (live-verified).
 */
public class ViewQueryJudgedFirstTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewThatDoesNotCompileLeavesTheTransactionOpen() {
        engine.execute("CREATE OR REPLACE TABLE tx (a INT)");
        engine.execute("CREATE OR REPLACE TABLE other_tx (b INT)");
        assertCells(new String[][] {
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE VIEW k_v AS SELECT nosuch_col FROM tx",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE OR REPLACE VIEW k_v2 AS SELECT * FROM k_nosuch_table",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.K_NOSUCH_TABLE' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.K_NOSUCH_TABLE."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE VIEW k_v3 AS SELECT 1 x", "View K_V3 successfully created."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "1"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE SECURE VIEW k_v4 AS SELECT nosuch FROM tx",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE OR REPLACE VIEW k_v5 (c) AS SELECT a FROM tx WHERE nosuch_w = 1",
                "REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'NOSUCH_W'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"BEGIN CREATE VIEW k_vw4 AS SELECT nosuch_col FROM tx; RETURN 'ran'; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: error line 1 at position 28 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"CREATE VIEW k_v6 AS SELECT a FROM tx JOIN other_tx ON nosuch_j = b",
                "REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'NOSUCH_J'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
        });
    }
}
