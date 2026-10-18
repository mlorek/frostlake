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
 * An ALTER | MODIFY COLUMN that names a column its table lacks — its NOT NULL, its type, its comment, one of several
 * columns — is refused as the statement compiles, with the open transaction left open, so a ROLLBACK after it
 * undoes the transaction's rows (live-verified), as a RENAME COLUMN of a missing column or to a taken name and an
 * ALTER TABLE over a view are; a column to drop that is not there and a column to add that is are refused after the
 * commit.
 */
public class AlterColumnJudgedFirstTest extends MeasuredCellsTestSupport {

    @Test
    public void anUnknownColumnIsRefusedBeforeTheCommit() {
        engine.execute("CREATE OR REPLACE TABLE tx (a INT)");
        engine.execute("CREATE OR REPLACE TABLE other_tx (a INT, b INT)");
        engine.execute("CREATE OR REPLACE VIEW k_vw AS SELECT a FROM tx");
        assertCells(new String[][] {
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ALTER COLUMN nosuch_col SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx MODIFY COLUMN nosuch_col DROP NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ALTER COLUMN nosuch_col SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ALTER COLUMN nosuch_col COMMENT 'c'",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx RENAME COLUMN nosuch_col TO c",
                "REFUSED SQL compilation error: | Object 'NOSUCH_COL' does not exist or not authorized."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx DROP COLUMN nosuch_col",
                "REFUSED SQL compilation error: | column 'NOSUCH_COL' does not exist"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "1"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ALTER COLUMN a SET NOT NULL", "Statement executed successfully."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "1"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE k_vw ADD COLUMN c INT",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ALTER COLUMN b SET NOT NULL, COLUMN nosuch_col SET NOT NULL",
                "REFUSED SQL compilation error: error line 1 at position 57 | invalid identifier 'NOSUCH_COL'"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx ADD COLUMN a INT", "REFUSED SQL compilation error: | column 'A' already exists"},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "1"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
            {"BEGIN TRANSACTION", "Statement executed successfully."},
            {"INSERT INTO tx VALUES (1)", "1"},
            {"ALTER TABLE other_tx RENAME COLUMN a TO b",
                "REFUSED SQL compilation error: | Object 'OTHER_TX.B' already exists."},
            {"ROLLBACK", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM tx", "0"},
            {"TRUNCATE TABLE tx", "Statement executed successfully."},
        });
    }
}
