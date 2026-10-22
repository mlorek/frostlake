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
 * A text of several statements run by EXECUTE IMMEDIATE under a transaction: its statements join the caller's
 * transaction, a transaction it opens stays open, and a COMMIT or ROLLBACK it holds ends the caller's (all
 * live-verified).
 */
public class MultiStatementTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void severalStatementsInATransaction() {
        engine.execute("CREATE OR REPLACE TABLE tx (a INT)");
        answer("BEGIN TRANSACTION");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$INSERT INTO tx VALUES (1); INSERT INTO tx VALUES (2)$$");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$BEGIN TRANSACTION; INSERT INTO tx VALUES (3)$$");
        assertAnswer("false", "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        answer("BEGIN TRANSACTION");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$BEGIN TRANSACTION; INSERT INTO tx VALUES (4)$$");
        assertAnswer("true", "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        answer("BEGIN TRANSACTION");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$INSERT INTO tx VALUES (5); COMMIT$$");
        assertAnswer("true", "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$INSERT INTO tx VALUES (6); ROLLBACK$$");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
        answer("BEGIN TRANSACTION");
        assertAnswer("REFUSED Actual statement count 2 did not match the desired statement count 1.",
            "EXECUTE IMMEDIATE $$INSERT INTO tx VALUES (7); SELECT 1/0$$");
        assertAnswer("Statement executed successfully.", "ROLLBACK");
        assertAnswer("0", "SELECT COUNT(*) FROM tx");
    }
}
