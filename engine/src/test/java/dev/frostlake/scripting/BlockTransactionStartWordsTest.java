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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * In a block only BEGIN TRANSACTION and START TRANSACTION open a transaction: any other BEGIN opens a nested block
 * there, so BEGIN WORK, BEGIN NAME and a bare BEGIN are refused at the token after the BEGIN, where a request outside
 * every block starts a transaction with each (live-verified).
 */
public class BlockTransactionStartWordsTest extends MeasuredCellsTestSupport {

    /** The BEGIN forms as requests and as statements of blocks, nested and in an IF. */
    @Test
    public void onlyTransactionWordsOpenATransactionInABlock() {
        assertCells(new String[][] {
            {"BEGIN WORK",
                "Statement executed successfully."},
            {"SELECT CURRENT_TRANSACTION() IS NULL",
                "false"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"BEGIN NAME t1",
                "Statement executed successfully."},
            {"SELECT CURRENT_TRANSACTION() IS NULL",
                "false"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"BEGIN WORK NAME t1",
                "Statement executed successfully."},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"BEGIN BEGIN WORK; ROLLBACK; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'."},
            {"BEGIN BEGIN TRANSACTION NAME t1; ROLLBACK; END",
                "null"},
            {"BEGIN START TRANSACTION NAME t1; ROLLBACK; END",
                "null"},
            {"BEGIN BEGIN NAME t1; ROLLBACK; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'NAME'."},
            {"BEGIN BEGIN TRANSACTION; COMMIT WORK; END",
                "null"},
            {"BEGIN BEGIN TRANSACTION; ROLLBACK WORK; END",
                "null"},
            {"BEGIN BEGIN WORK; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'."},
            {"BEGIN BEGIN; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected ';'."},
            {"BEGIN BEGIN TRANSACTION; ROLLBACK; BEGIN WORK; ROLLBACK; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'WORK'."},
            {"BEGIN LET x INT := 1; BEGIN WORK; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'WORK'."},
            {"SELECT CURRENT_TRANSACTION() IS NULL",
                "true"},
        });
        engine.execute("ROLLBACK");
    }
}
