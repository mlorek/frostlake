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
 * A transaction and the procedure a statement of it calls. The procedure's body is a scope of its own: what it
 * writes joins the transaction its caller began, and it may not end that transaction - a COMMIT, a ROLLBACK and a
 * statement that commits, written in the body, in a procedure the body calls or in a text it runs, are refused
 * with {@code Modifying a transaction that has started at a different scope is not allowed.}, which a handler
 * reads as 90239 / 25000. The refusal undoes nothing: what the body wrote before it is still in the caller's
 * transaction, as what a body wrote before any failure is. A BEGIN in the body starts a transaction of the
 * body's own beside the caller's, which commits and rolls back by itself; one the body leaves open is rolled
 * back and the CALL reports {@code Scoped transaction started in stored procedure is incomplete and it was
 * rolled back.}, whatever else the body let out. With no transaction open the body's statements each commit by
 * themselves, and its COMMIT and ROLLBACK end nothing. Every answer is live-verified.
 */
public class CallTransactionScopeTest extends MeasuredCellsTestSupport {

    /** The CALL is refused, and its refusal carries the sentence inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String sql) {
        final String answer = answer(sql);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), sql + " ==> " + answer);
    }

    @Test
    public void aBodyInsideItsCallersTransaction() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Function COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (2); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE rollback_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO "
                    + "tx VALUES (3); ROLLBACK; RETURN 'rolled back'; END; $$");
            assertAnswer(
                "Function DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE ddl_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (4); CREATE OR REPLACE TABLE made_in_proc (b INT); RETURN 'ddl'; END; $$");
            assertAnswer(
                "Function DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dml_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (5); RETURN 'dml'; END; $$");
            assertAnswer(
                "Function OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (6); COMMIT; RETURN 'own committed'; END; $$");
            assertAnswer(
                "Function OWN_TXN_RB successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn_rb() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (7); ROLLBACK; RETURN 'own rolled back'; END; $$");
            assertAnswer(
                "Function OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_open() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (8); RETURN 'left open'; END; $$");
            assertAnswer(
                "Function COMMIT_CAUGHT successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_caught() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "INSERT INTO tx VALUES (9); COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN RETURN "
                    + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; END; $$");
            assertAnswer(
                "Function COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in_clr() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "BEGIN INSERT INTO tx VALUES (10); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function TRUNCATE_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE truncate_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN TRUNCATE "
                    + "TABLE other_tx; RETURN 'truncated'; END; $$");
            assertAnswer(
                "Function FAILS_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE fails_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (11); SELECT 1/0; RETURN 'unreached'; END; $$");
            assertAnswer(
                "Function OUTER_BEGINS successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_begins() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (12); CALL commit_in(); COMMIT; RETURN 'outer done'; END; "
                    + "$$");
            assertAnswer(
                "Function OUTER_CALLS_COMMIT successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_calls_commit() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (13); CALL commit_in(); RETURN 'outer done'; END; $$");
            assertAnswer(
                "Function DYNAMIC_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dynamic_commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (14); EXECUTE IMMEDIATE 'COMMIT'; RETURN 'committed'; END; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL commit_in()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL rollback_in()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL ddl_in()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "dml",
                "CALL dml_in()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "own committed",
                "CALL own_txn()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "own rolled back",
                "CALL own_txn_rb()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL own_open()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "90239 25000 Modifying a transaction that has started at a different scope is not allowed.",
                "CALL commit_caught()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,9",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL commit_in_clr()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,10",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "truncated",
                "CALL truncate_in()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Division by zero",
                "CALL fails_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,11",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL outer_begins()");
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
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Uncaught "
                    + "exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a transaction that "
                    + "has started at a different scope is not allowed.",
                "CALL outer_calls_commit()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,2,13",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO tx VALUES (1)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL dynamic_commit_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,14",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.information_schema.tables WHERE table_name = 'MADE_IN_PROC'");
        } finally {
            answer("ROLLBACK");
        }
    }

    @Test
    public void theCallerCommitsAfterTheCall() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Function COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (2); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE rollback_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO "
                    + "tx VALUES (3); ROLLBACK; RETURN 'rolled back'; END; $$");
            assertAnswer(
                "Function DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE ddl_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (4); CREATE OR REPLACE TABLE made_in_proc (b INT); RETURN 'ddl'; END; $$");
            assertAnswer(
                "Function DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dml_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (5); RETURN 'dml'; END; $$");
            assertAnswer(
                "Function OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (6); COMMIT; RETURN 'own committed'; END; $$");
            assertAnswer(
                "Function OWN_TXN_RB successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn_rb() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (7); ROLLBACK; RETURN 'own rolled back'; END; $$");
            assertAnswer(
                "Function OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_open() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (8); RETURN 'left open'; END; $$");
            assertAnswer(
                "Function COMMIT_CAUGHT successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_caught() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "INSERT INTO tx VALUES (9); COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN RETURN "
                    + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; END; $$");
            assertAnswer(
                "Function COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in_clr() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "BEGIN INSERT INTO tx VALUES (10); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function TRUNCATE_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE truncate_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN TRUNCATE "
                    + "TABLE other_tx; RETURN 'truncated'; END; $$");
            assertAnswer(
                "Function FAILS_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE fails_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (11); SELECT 1/0; RETURN 'unreached'; END; $$");
            assertAnswer(
                "Function OUTER_BEGINS successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_begins() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (12); CALL commit_in(); COMMIT; RETURN 'outer done'; END; "
                    + "$$");
            assertAnswer(
                "Function OUTER_CALLS_COMMIT successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_calls_commit() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (13); CALL commit_in(); RETURN 'outer done'; END; $$");
            assertAnswer(
                "Function DYNAMIC_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dynamic_commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (14); EXECUTE IMMEDIATE 'COMMIT'; RETURN 'committed'; END; $$");
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
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL commit_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1,2",
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
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL rollback_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1,3",
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
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Modifying a "
                    + "transaction that has started at a different scope is not allowed.",
                "CALL ddl_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1,4",
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
                "own rolled back",
                "CALL own_txn_rb()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
        } finally {
            answer("ROLLBACK");
        }
    }

    @Test
    public void aBodyWithNoTransactionOpen() {
        try {
            assertAnswer(
                "Table TX successfully created.",
                "CREATE OR REPLACE TABLE tx (a INT)");
            assertAnswer(
                "Table OTHER_TX successfully created.",
                "CREATE OR REPLACE TABLE other_tx (a INT)");
            assertAnswer(
                "Function COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (2); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE rollback_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO "
                    + "tx VALUES (3); ROLLBACK; RETURN 'rolled back'; END; $$");
            assertAnswer(
                "Function DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE ddl_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (4); CREATE OR REPLACE TABLE made_in_proc (b INT); RETURN 'ddl'; END; $$");
            assertAnswer(
                "Function DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dml_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (5); RETURN 'dml'; END; $$");
            assertAnswer(
                "Function OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (6); COMMIT; RETURN 'own committed'; END; $$");
            assertAnswer(
                "Function OWN_TXN_RB successfully created.",
                "CREATE OR REPLACE PROCEDURE own_txn_rb() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (7); ROLLBACK; RETURN 'own rolled back'; END; $$");
            assertAnswer(
                "Function OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE own_open() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (8); RETURN 'left open'; END; $$");
            assertAnswer(
                "Function COMMIT_CAUGHT successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_caught() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "INSERT INTO tx VALUES (9); COMMIT; RETURN 'committed'; EXCEPTION WHEN OTHER THEN RETURN "
                    + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; END; $$");
            assertAnswer(
                "Function COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE commit_in_clr() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                    + "BEGIN INSERT INTO tx VALUES (10); COMMIT; RETURN 'committed'; END; $$");
            assertAnswer(
                "Function TRUNCATE_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE truncate_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN TRUNCATE "
                    + "TABLE other_tx; RETURN 'truncated'; END; $$");
            assertAnswer(
                "Function FAILS_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE fails_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO tx "
                    + "VALUES (11); SELECT 1/0; RETURN 'unreached'; END; $$");
            assertAnswer(
                "Function OUTER_BEGINS successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_begins() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN "
                    + "TRANSACTION; INSERT INTO tx VALUES (12); CALL commit_in(); COMMIT; RETURN 'outer done'; END; "
                    + "$$");
            assertAnswer(
                "Function OUTER_CALLS_COMMIT successfully created.",
                "CREATE OR REPLACE PROCEDURE outer_calls_commit() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (13); CALL commit_in(); RETURN 'outer done'; END; $$");
            assertAnswer(
                "Function DYNAMIC_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE dynamic_commit_in() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT "
                    + "INTO tx VALUES (14); EXECUTE IMMEDIATE 'COMMIT'; RETURN 'committed'; END; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "committed",
                "CALL commit_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "2",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "rolled back",
                "CALL rollback_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "3",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "ddl",
                "CALL ddl_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "4",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "MADE_IN_PROC successfully dropped.",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "dml",
                "CALL dml_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "5",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "own committed",
                "CALL own_txn()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "6",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "own rolled back",
                "CALL own_txn_rb()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL own_open()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "committed",
                "CALL commit_caught()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "9",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "committed",
                "CALL commit_in_clr()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "10",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "truncated",
                "CALL truncate_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Division by zero",
                "CALL fails_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "11",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "REFUSED Stored procedure execution error: Scoped transaction started in stored procedure is "
                    + "incomplete and it was rolled back.",
                "CALL outer_begins()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "outer done",
                "CALL outer_calls_commit()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "2,13",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "committed",
                "CALL dynamic_commit_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "14",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
        } finally {
            answer("ROLLBACK");
        }
    }
}
