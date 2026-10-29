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
 * With AUTOCOMMIT off, the transaction a statement opens belongs to the scope it runs in, as one a BEGIN TRANSACTION
 * opens does: a block or a procedure that writes and leaves its transaction open is refused and the write rolled back,
 * while one run after a statement of the session opened a transaction writes in that transaction. A SELECT opens none,
 * a COMMIT inside a block ends the block's own, and turning AUTOCOMMIT back on or running a DDL commits what the
 * session holds open (live-verified).
 */
public class AutocommitOffScopedTransactionTest extends MeasuredCellsTestSupport {

    /**
     * A block that writes and does not commit is refused and its row rolled back; turning AUTOCOMMIT on or a DDL
     * commits the transaction the session holds open.
     */
    @Test
    public void aBlockMayNotLeaveItsTransactionOpen() {
        engine.execute("CREATE OR REPLACE TABLE ta783 (i INT)");
        assertCells(new String[][] {
            {"ALTER SESSION SET AUTOCOMMIT = FALSE",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta783",
                "0"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"BEGIN INSERT INTO ta783 VALUES (4); END;",
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                + "incomplete and it was rolled back."},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta783",
                "0"},
            {"INSERT INTO ta783 VALUES (6)",
                "1"},
            {"ALTER SESSION SET AUTOCOMMIT = TRUE",
                "Statement executed successfully."},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta783",
                "1"},
            {"ALTER SESSION SET AUTOCOMMIT = FALSE",
                "Statement executed successfully."},
            {"SELECT 1",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"CREATE OR REPLACE TABLE tb783 (i INT)",
                "Table TB783 successfully created."},
            {"INSERT INTO ta783 VALUES (7)",
                "1"},
            {"CREATE OR REPLACE TABLE tc783 (i INT)",
                "Table TC783 successfully created."},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta783",
                "2"},
            {"ALTER SESSION SET AUTOCOMMIT = TRUE",
                "Statement executed successfully."},
        });
    }

    /**
     * A block or a procedure that commits its own write keeps it, and one run while the session holds a transaction
     * open writes in it.
     */
    @Test
    public void aBlockJoinsTheTransactionTheSessionHoldsOpen() {
        engine.execute("CREATE OR REPLACE TABLE ta784 (i INT)");
        engine.execute("CREATE OR REPLACE PROCEDURE p784() RETURNS INT LANGUAGE SQL AS 'BEGIN INSERT INTO ta784 VALUES "
            + "(9); RETURN 1; END'");
        assertCells(new String[][] {
            {"ALTER SESSION SET AUTOCOMMIT = FALSE",
                "Statement executed successfully."},
            {"EXECUTE IMMEDIATE $$INSERT INTO ta784 VALUES (1)$$",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta784",
                "0"},
            {"BEGIN SELECT 1; END;",
                "null"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"BEGIN INSERT INTO ta784 VALUES (3); COMMIT; END;",
                "null"},
            {"SELECT COUNT(*) FROM ta784",
                "1"},
            {"BEGIN BEGIN TRANSACTION; INSERT INTO ta784 VALUES (4); COMMIT; END;",
                "null"},
            {"SELECT COUNT(*) FROM ta784",
                "2"},
            {"CALL p784()",
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                + "incomplete and it was rolled back."},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "false"},
            {"SELECT COUNT(*) FROM ta784",
                "2"},
            {"INSERT INTO ta784 VALUES (5)",
                "1"},
            {"BEGIN INSERT INTO ta784 VALUES (6); END;",
                "null"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ta784",
                "2"},
            {"BEGIN LET x INT := (SELECT COUNT(*) FROM ta784); RETURN x; END;",
                "2"},
            {"ALTER SESSION SET AUTOCOMMIT = TRUE",
                "Statement executed successfully."},
        });
    }

    /**
     * A request of several statements runs in the session's transaction: its writes wait for a COMMIT or go with a
     * ROLLBACK, a COMMIT or ROLLBACK inside it ends the transaction there, a statement that fails leaves the earlier
     * writes to the session's ROLLBACK, and a DDL inside it commits what is open before it.
     */
    @Test
    public void aRequestOfSeveralStatementsWritesInTheSessionsTransaction() {
        engine.execute("CREATE OR REPLACE TABLE ms854 (a INT)");
        final String rows = "SELECT COUNT(*), LISTAGG(a, ',') WITHIN GROUP (ORDER BY a) FROM ms854";
        assertCells(new String[][] {
            {"ALTER SESSION SET AUTOCOMMIT = FALSE",
                "Statement executed successfully."},
            {"INSERT INTO ms854 VALUES (1); INSERT INTO ms854 VALUES (2)",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "0|"},
            {"INSERT INTO ms854 VALUES (3); COMMIT",
                "1"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "1|3"},
            {"INSERT INTO ms854 VALUES (4); SELECT 1/0",
                "REFUSED JavaScript execution error: Uncaught Execution of multiple statements failed on statement "
                + "\"SELECT 1/0\" (at line 1, position 30). | Division by zero in SYSTEM$MULTISTMT at '    throw "
                + "`Execution of multiple statements failed on statement {0} (at line {1}, position {2}).`"
                + ".replace('{1}', LINES[i])' position 4 | stackstrace:  | SYSTEM$MULTISTMT line: 10"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "1|3"},
        });
        // A request that starts with a BEGIN or a DDL first answers a driver with an update count and the engine's
        // own API with a status text, so only what it leaves behind is compared.
        engine.execute("BEGIN; INSERT INTO ms854 VALUES (5)");
        assertCells(new String[][] {
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "1|3"},
            {"INSERT INTO ms854 VALUES (6); ROLLBACK; INSERT INTO ms854 VALUES (7)",
                "1"},
            {"COMMIT",
                "Statement executed successfully."},
            {rows,
                "2|3,7"},
            {"SELECT 1; INSERT INTO ms854 VALUES (8)",
                "1"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "2|3,7"},
        });
        engine.execute("CREATE OR REPLACE TABLE ms854b (a INT); INSERT INTO ms854 VALUES (9)");
        assertCells(new String[][] {
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "2|3,7"},
            {"INSERT INTO ms854 VALUES (10); CREATE OR REPLACE TABLE ms854c (a INT)",
                "1"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "3|3,7,10"},
            {"INSERT INTO ms854 VALUES (11); BEGIN; INSERT INTO ms854 VALUES (12)",
                "1"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {rows,
                "3|3,7,10"},
            {"COMMIT",
                "Statement executed successfully."},
            {"ALTER SESSION SET AUTOCOMMIT = TRUE",
                "Statement executed successfully."},
        });
    }
}
