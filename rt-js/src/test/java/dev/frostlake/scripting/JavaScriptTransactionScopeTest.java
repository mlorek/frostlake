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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transaction and the JavaScript procedure a statement of it calls: the handler is a scope of its own as a SQL
 * body is. What it writes joins the transaction its caller began; a COMMIT, a ROLLBACK and a statement that
 * commits are refused to it as another scope's, whatever rights it runs with, and what it wrote before the
 * refusal stays in the caller's transaction; a transaction it begins is its own, and one it leaves open is
 * rolled back and reported. The refusal's own sentence is live-verified; the words the runtime puts around a
 * failed statement are its own.
 */
public class JavaScriptTransactionScopeTest extends MeasuredCellsTestSupport {

    /** The CALL is refused, and its refusal carries the sentence inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String sql) {
        final String answer = answer(sql);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), sql + " ==> " + answer);
    }

    @Test
    public void aHandlerInsideItsCallersTransaction() {
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
                "Function JS_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (22)\"}); snowflake.execute({sqlText: "
                    + "\"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_rollback_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (23)\"}); snowflake.execute({sqlText: "
                    + "\"ROLLBACK\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_ddl_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (24)\"}); snowflake.execute({sqlText: "
                    + "\"CREATE OR REPLACE TABLE made_in_proc (b INT)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_dml_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (25)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_txn() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (26)\"}); snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_open() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (28)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in_clr() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS "
                    + "CALLER AS $$ snowflake.execute({sqlText: \"INSERT INTO tx VALUES (30)\"}); "
                    + "snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_commit_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,22",
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_rollback_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,23",
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_ddl_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,24",
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
                "done",
                "CALL js_dml_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,25",
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
                "done",
                "CALL js_own_txn()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,26",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "26",
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
                "CALL js_own_open()");
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_commit_in_clr()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "1,30",
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
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
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
                "Function JS_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (22)\"}); snowflake.execute({sqlText: "
                    + "\"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_rollback_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (23)\"}); snowflake.execute({sqlText: "
                    + "\"ROLLBACK\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_ddl_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (24)\"}); snowflake.execute({sqlText: "
                    + "\"CREATE OR REPLACE TABLE made_in_proc (b INT)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_dml_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (25)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_txn() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (26)\"}); snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_open() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (28)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in_clr() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS "
                    + "CALLER AS $$ snowflake.execute({sqlText: \"INSERT INTO tx VALUES (30)\"}); "
                    + "snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_commit_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1,22",
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
            assertRefusedWith(
                "Modifying a transaction that has started at a different scope is not allowed.",
                "CALL js_rollback_in()");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "1,23",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer("ROLLBACK");
        }
    }

    @Test
    public void aHandlerWithNoTransactionOpen() {
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
                "Function JS_COMMIT_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (22)\"}); snowflake.execute({sqlText: "
                    + "\"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_ROLLBACK_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_rollback_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (23)\"}); snowflake.execute({sqlText: "
                    + "\"ROLLBACK\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DDL_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_ddl_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (24)\"}); snowflake.execute({sqlText: "
                    + "\"CREATE OR REPLACE TABLE made_in_proc (b INT)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_DML_IN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_dml_in() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"INSERT INTO tx VALUES (25)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_TXN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_txn() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (26)\"}); snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_OWN_OPEN successfully created.",
                "CREATE OR REPLACE PROCEDURE js_own_open() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                    + "snowflake.execute({sqlText: \"BEGIN TRANSACTION\"}); snowflake.execute({sqlText: \"INSERT "
                    + "INTO tx VALUES (28)\"}); return \"done\"; $$");
            assertAnswer(
                "Function JS_COMMIT_IN_CLR successfully created.",
                "CREATE OR REPLACE PROCEDURE js_commit_in_clr() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS "
                    + "CALLER AS $$ snowflake.execute({sqlText: \"INSERT INTO tx VALUES (30)\"}); "
                    + "snowflake.execute({sqlText: \"COMMIT\"}); return \"done\"; $$");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            answer(
                "DELETE FROM tx");
            assertAnswer(
                "Drop statement executed successfully (MADE_IN_PROC already dropped).",
                "DROP TABLE IF EXISTS made_in_proc");
            assertAnswer(
                "done",
                "CALL js_commit_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "22",
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
                "done",
                "CALL js_rollback_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "23",
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
                "done",
                "CALL js_ddl_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "24",
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
                "done",
                "CALL js_dml_in()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "25",
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
                "done",
                "CALL js_own_txn()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "26",
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
                "CALL js_own_open()");
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
                "done",
                "CALL js_commit_in_clr()");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "30",
                "SELECT NVL(LISTAGG(a, ',') WITHIN GROUP (ORDER BY a), 'none') FROM tx");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "false",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
        } finally {
            answer("ROLLBACK");
        }
    }
}
