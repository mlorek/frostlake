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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * An anonymous block that begins a transaction and ends without committing or rolling it back is refused as a stored
 * procedure is, and the transaction is rolled back; one that commits, one that fails and one that begins none run as
 * written (live-verified).
 */
public class AnonymousBlockScopedTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockMayNotLeaveItsTransactionOpen() {
        engine.execute("CREATE OR REPLACE TABLE ab (a INT)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN TRANSACTION; INSERT INTO ab VALUES (12); RETURN 'left open'; END $$",
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                + "incomplete and it was rolled back."},
            {"SELECT CURRENT_TRANSACTION() IS NULL", "true"},
            {"SELECT COUNT(*) FROM ab", "0"},
        });
        engine.execute("ROLLBACK");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN TRANSACTION; INSERT INTO ab VALUES (13); COMMIT; RETURN "
                + "'closed'; END $$",
                "closed"},
            {"SELECT COUNT(*) FROM ab", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN TRANSACTION; INSERT INTO ab VALUES (14); SELECT 1/0; RETURN 'x'; END $$",
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                + "incomplete and it was rolled back."},
            {"SELECT CURRENT_TRANSACTION() IS NULL", "true"},
            {"SELECT COUNT(*) FROM ab", "1"},
        });
        engine.execute("ROLLBACK");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO ab VALUES (15); RETURN 'no txn'; END $$", "no txn"},
            {"SELECT COUNT(*) FROM ab", "2"},
        });
        engine.execute("BEGIN TRANSACTION");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO ab VALUES (16); RETURN 'outer txn'; END $$", "outer txn"},
            {"SELECT CURRENT_TRANSACTION() IS NULL", "false"},
        });
        engine.execute("ROLLBACK");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM ab", "2"},
        });
    }
}
