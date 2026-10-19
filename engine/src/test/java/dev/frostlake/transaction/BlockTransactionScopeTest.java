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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transaction and the blocks that run inside it. A block the session sends, and a text that is a block, is a
 * scope of its own as a procedure's body is: it adds to the transaction that is open when it starts and may not
 * end it, a transaction it begins is its own - beside the caller's when one is open, seeing nothing the caller
 * has not committed - and one it leaves open is rolled back and reported. A block nested in another shares its
 * scope, and so does a text that is one statement: its COMMIT ends the transaction of the block that runs it. A
 * second BEGIN in the scope that began a transaction changes nothing. The refusal a handler reads for a
 * transaction left open is 90232 / 25000. Every answer is live-verified.
 */
public class BlockTransactionScopeTest extends MeasuredCellsTestSupport {

    /** The CALL is refused, and its refusal carries the sentence inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String sql) {
        final String answer = answer(sql);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), sql + " ==> " + answer);
    }

    @Test
    public void aBlockInsideTheSessionsTransaction() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN INSERT INTO tx VALUES (2); COMMIT; RETURN 'committed'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,2",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN INSERT INTO tx VALUES (3); ROLLBACK; RETURN 'rolled back'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,3",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN INSERT INTO tx VALUES (4); CREATE OR REPLACE TABLE made_in_proc (b INT); RETURN 'ddl'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,4",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "EXECUTE IMMEDIATE 'COMMIT'");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 33 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "EXECUTE IMMEDIATE 'BEGIN INSERT INTO tx VALUES (5); COMMIT; RETURN 1; END;'");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,5",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "own",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (6); COMMIT; RETURN 'own'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,6",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "6",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer("ROLLBACK");
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("UNSET k_sv");
            answer("UNSET tx_sv");
            answer("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void aBlockWithNoTransactionOpen() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Function OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_open() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (8); RETURN 'left open'; END; $$");
            assertAnswer(
                "Function COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (2); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function COUNTS successfully created.",
                "CREATE OR REPLACE PROCEDURE counts() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE n INT; BEGIN "
                    + "BEGIN TRANSACTION; n := (SELECT COUNT(*) FROM tx); INSERT INTO tx VALUES (40); COMMIT; RETURN "
                    + "'saw ' || n; END; $$");
            assertAnswer(
                "Function NESTED_COMMIT successfully created.",
                "CREATE OR REPLACE PROCEDURE nested_commit() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (50); BEGIN COMMIT; END; RETURN 'nested committed'; END; $$");
            assertAnswer(
                "Function TWO_TXNS successfully created.",
                "CREATE OR REPLACE PROCEDURE two_txns() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (60); COMMIT; BEGIN TRANSACTION; INSERT INTO tx VALUES "
                    + "(61); ROLLBACK; RETURN 'two'; END; $$");
            assertAnswer(
                "Function HANDLES_SCOPED successfully created.",
                "CREATE OR REPLACE PROCEDURE handles_scoped() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN CALL "
                    + "own_open(); RETURN 'unreached'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                    + "|| ' ' || SQLERRM; END; $$");
            assertAnswer(
                "Function BEGINS_TWICE successfully created.",
                "CREATE OR REPLACE PROCEDURE begins_twice() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (70); BEGIN TRANSACTION; INSERT INTO tx VALUES (71); "
                    + "COMMIT; RETURN 'twice'; END; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "nested",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); BEGIN COMMIT; END; RETURN 'nested'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); RETURN 'left open'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); RETURN 'left open'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "EXECUTE IMMEDIATE 'BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); RETURN 1; END;'");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); CALL commit_in(); COMMIT; RETURN 'done'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "dyn",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); EXECUTE IMMEDIATE 'COMMIT'; RETURN 'dyn'; "
                    + "END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); EXECUTE IMMEDIATE 'BEGIN COMMIT; END;'; "
                    + "RETURN 'dyn block'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "nested committed",
                "CALL nested_commit()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "50",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "50",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "two",
                "CALL two_txns()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "60",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "60",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "90232 25000 Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL handles_scoped()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "twice",
                "CALL begins_twice()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "70,71",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "70,71",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "false",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); CREATE OR REPLACE TABLE made_in_block (b "
                    + "INT); RETURN CURRENT_TRANSACTION() IS NOT NULL; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "after",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (1); ROLLBACK; INSERT INTO tx VALUES (2); RETURN "
                    + "'after'; END;");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "2",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "2",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer("ROLLBACK");
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("UNSET k_sv");
            answer("UNSET tx_sv");
            answer("USE SCHEMA test_db.test_schema");
        }
    }

    @Test
    public void scopesInsideAnOpenTransaction() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Function OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_open() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (8); RETURN 'left open'; END; $$");
            assertAnswer(
                "Function COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (2); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function COUNTS successfully created.",
                "CREATE OR REPLACE PROCEDURE counts() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE n INT; BEGIN "
                    + "BEGIN TRANSACTION; n := (SELECT COUNT(*) FROM tx); INSERT INTO tx VALUES (40); COMMIT; RETURN "
                    + "'saw ' || n; END; $$");
            assertAnswer(
                "Function NESTED_COMMIT successfully created.",
                "CREATE OR REPLACE PROCEDURE nested_commit() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (50); BEGIN COMMIT; END; RETURN 'nested committed'; END; $$");
            assertAnswer(
                "Function TWO_TXNS successfully created.",
                "CREATE OR REPLACE PROCEDURE two_txns() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (60); COMMIT; BEGIN TRANSACTION; INSERT INTO tx VALUES "
                    + "(61); ROLLBACK; RETURN 'two'; END; $$");
            assertAnswer(
                "Function HANDLES_SCOPED successfully created.",
                "CREATE OR REPLACE PROCEDURE handles_scoped() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN CALL "
                    + "own_open(); RETURN 'unreached'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE "
                    + "|| ' ' || SQLERRM; END; $$");
            assertAnswer(
                "Function BEGINS_TWICE successfully created.",
                "CREATE OR REPLACE PROCEDURE begins_twice() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (70); BEGIN TRANSACTION; INSERT INTO tx VALUES (71); "
                    + "COMMIT; RETURN 'twice'; END; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "BEGIN BEGIN TRANSACTION; INSERT INTO tx VALUES (6); RETURN 'left open'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "saw 0",
                "CALL counts()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,40",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "40",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'N' cannot have its type "
                    + "inferred from initializer",
                "BEGIN LET n := (SELECT COUNT(*) FROM tx); RETURN 'saw ' || n; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "nested committed",
                "CALL nested_commit()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,50",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "50",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "two",
                "CALL two_txns()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,60",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "60",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "90232 25000 Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL handles_scoped()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "twice",
                "CALL begins_twice()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,70,71",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "70,71",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 12 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN BEGIN COMMIT; END; RETURN 'x'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN ALTER TABLE other_tx ADD COLUMN c INT; RETURN 'altered'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN DROP TABLE IF EXISTS nosuch_tx_table; RETURN 'dropped'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN CREATE OR REPLACE VIEW v_tx AS SELECT 1 AS x; RETURN 'view'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN CREATE OR REPLACE TEMPORARY TABLE tmp_tx (a INT); RETURN 'temp'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN CREATE OR REPLACE TABLE ctas_tx AS SELECT 1 AS a; RETURN 'ctas'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "BEGIN COMMENT ON TABLE other_tx IS 'x'; RETURN 'comment'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "truncated",
                "BEGIN TRUNCATE TABLE other_tx; RETURN 'truncated'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "shown",
                "BEGIN SHOW TABLES LIKE 'OTHER_TX'; RETURN 'shown'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "used",
                "BEGIN USE SCHEMA test_db.test_schema; RETURN 'used'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "set",
                "BEGIN SET tx_sv = 1; RETURN 'set'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "altered session",
                "BEGIN ALTER SESSION SET QUERY_TAG = 'in-tx'; RETURN 'altered session'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "sequence",
                "BEGIN CREATE OR REPLACE SEQUENCE seq_tx; RETURN 'sequence'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "procedure",
                "BEGIN CREATE OR REPLACE PROCEDURE p_tx() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'; "
                    + "RETURN 'procedure'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "dml",
                "BEGIN INSERT INTO other_tx (a) VALUES (1); RETURN 'dml'; END;");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Table TOP_DDL_TX successfully created.",
                "CREATE OR REPLACE TABLE top_ddl_tx (a INT)");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Table DYN_DDL_TX successfully created.",
                "EXECUTE IMMEDIATE 'CREATE OR REPLACE TABLE dyn_ddl_tx (a INT)'");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "START TRANSACTION");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET QUERY_TAG");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET tx_sv");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA test_db.test_schema");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer("ROLLBACK");
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("UNSET k_sv");
            answer("UNSET tx_sv");
            answer("USE SCHEMA test_db.test_schema");
        }
    }
}
