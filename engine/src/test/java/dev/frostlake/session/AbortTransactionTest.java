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
 * SYSTEM$ABORT_TRANSACTION ends the open transaction its argument names and answers {@code Aborted transaction
 * id: <id>}; an id nothing open has answers {@code Could not abort txn: <id>} and leaves the caller's own
 * transaction be. The session whose transaction was ended holds it until it acknowledges it: every statement -
 * a query of constants, a SHOW, a SET, a BEGIN - is refused with one sentence, a ROLLBACK succeeds and a COMMIT
 * fails naming the transaction, and LAST_TRANSACTION() is that transaction afterwards. A statement that does not
 * compile is refused for that first. A block that aborts the session's transaction goes on - its RETURN and
 * what it computes without a query - while each statement and each query of an expression is refused, with
 * SQLCODE 624 and SQLSTATE 57014 in a handler, and its COMMIT fails with 670; a transaction the block began
 * itself is simply over. The argument is a number or a text that spells one: a text is rounded half away from
 * zero, a written number with a fraction and most computed values answer {@code Bad transaction id}, NULL answers
 * NULL, a number wider than 64 bits wraps, and a BOOLEAN, a date, a time, a BINARY, an ARRAY and an OBJECT are
 * refused for their type; what is no constant - a column, CURRENT_TRANSACTION(), a typed NULL - is refused as
 * one. A view, a table created from a query and a SQL function may not hold the call, nor may a procedure that
 * runs with its owner's rights run it. Every answer is live-verified.
 */
public class AbortTransactionTest extends MeasuredCellsTestSupport {

    @Test
    public void abortsTheCallersTransactionAndAnswersForAnIdNothingHas() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(1234567890123456789)");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('1234567890123456789')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('abc')");
            assertAnswer(
                "null",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SYSTEM$ABORT_TRANSACTION()], expected 1, got 0",
                "SELECT SYSTEM$ABORT_TRANSACTION()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SYSTEM$ABORT_TRANSACTION(1, 2)] expected 1, got 2",
                "SELECT SYSTEM$ABORT_TRANSACTION(1, 2)");
            assertAnswer(
                "Could not abort txn: -1",
                "SELECT SYSTEM$ABORT_TRANSACTION(-1)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(1.5)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (BOOLEAN)",
                "SELECT SYSTEM$ABORT_TRANSACTION(TRUE)");
            assertAnswer(
                "Could not abort txn: 0",
                "SELECT SYSTEM$ABORT_TRANSACTION(0)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12)");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "CALL SYSTEM$ABORT_TRANSACTION(1234567890123456789)");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(1234567890123456789)");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertShapedAnswer(
                "REFUSED COMMIT failed. Your transaction '<QID>', id '<ID19>', was already aborted.  | ",
                "COMMIT");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (2)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "CALL SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "",
                "SHOW TRANSACTIONS");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void anAbortedSessionRunsNothingUntilItAcknowledges() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SHOW TRANSACTIONS");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "BEGIN");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SET x = 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "UNSET x");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "USE SCHEMA test_schema");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "ALTER SESSION SET QUERY_TAG = 'aborted'");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'SELEC'.",
                "SELEC 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "CREATE TABLE ab2 (a INT)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "DESCRIBE TABLE ab");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "EXPLAIN SELECT 1");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SHOW TABLES");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT CURRENT_SESSION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "true",
                "SELECT LAST_TRANSACTION() = $txid");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "QUERY_TAG||||String (up to 2000 characters) used to tag statements executed by the session|STRING",
                "SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET QUERY_TAG");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Session variable '$X' does not exist",
                "SELECT $x");
            assertAnswer(
                "",
                "SHOW TABLES LIKE 'AB2'");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET x");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void aCommitOfAnAbortedTransactionFailsNamingIt() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN NAME tx_named");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertShapedAnswer(
                "REFUSED COMMIT failed. Your transaction 'TX_NAMED', id '<ID19>', was already aborted.  | ",
                "COMMIT");
            assertAnswer(
                "true",
                "SELECT LAST_TRANSACTION() = $txid");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION NAME \"Tx Lower\"");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertShapedAnswer(
                "REFUSED COMMIT failed. Your transaction 'Tx Lower', id '<ID19>', was already aborted.  | ",
                "COMMIT");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "true",
                "SELECT LAST_TRANSACTION() = $txid");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (2)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'TO_NUMBER(LAST_TRANSACTION())'",
                "SELECT SYSTEM$ABORT_TRANSACTION(LAST_TRANSACTION()::NUMBER) LIKE 'Could not abort txn: %'");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (3)");
            assertAnswer(
                "Statement executed successfully.",
                "COMMIT");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'TO_NUMBER(LAST_TRANSACTION())'",
                "SELECT SYSTEM$ABORT_TRANSACTION(LAST_TRANSACTION()::NUMBER) LIKE 'Could not abort txn: %'");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void whatReadsAsAnId() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(' 12 ')");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('12.0')");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.5)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.4)");
            assertAnswer(
                "Could not abort txn: 100",
                "SELECT SYSTEM$ABORT_TRANSACTION('1e2')");
            assertAnswer(
                "Could not abort txn: 100",
                "SELECT SYSTEM$ABORT_TRANSACTION(1e2)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('+12')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(' ')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('0x1F')");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(12345678901234567890123)");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('12345678901234567890123')");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(9223372036854775807)");
            assertShapedAnswer(
                "Could not abort txn: -<ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(9223372036854775808)");
            assertShapedAnswer(
                "Could not abort txn: -<ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('9223372036854775808')");
            assertAnswer(
                "Could not abort txn: 0",
                "SELECT SYSTEM$ABORT_TRANSACTION('-0')");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('012')");
            assertAnswer(
                "Could not abort txn: -12",
                "SELECT SYSTEM$ABORT_TRANSACTION('-12')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('1,2')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('12abc')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_DATE())");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
                "SELECT SYSTEM$ABORT_TRANSACTION('2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (TIME(9))",
                "SELECT SYSTEM$ABORT_TRANSACTION('12:00:00'::TIME)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (TIMESTAMP_NTZ(9))",
                "SELECT SYSTEM$ABORT_TRANSACTION('2024-01-01 00:00:00'::TIMESTAMP_NTZ)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(TO_VARIANT(12))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('\"12\"'))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('\"abc\"'))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('null'))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('1.5'))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(PARSE_JSON('true'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (ARRAY)",
                "SELECT SYSTEM$ABORT_TRANSACTION(ARRAY_CONSTRUCT(12))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (OBJECT)",
                "SELECT SYSTEM$ABORT_TRANSACTION(OBJECT_CONSTRUCT('a', 12))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (BINARY(67108864))",
                "SELECT SYSTEM$ABORT_TRANSACTION(TO_BINARY('12', 'UTF-8'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::NUMBER)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'SYSTEM$NULL_TO_TEXT(null)'",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (BOOLEAN)",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::BOOLEAN)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'SYSTEM$NULL_TO_VARIANT(null)'",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::VARIANT)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::FLOAT)");
            assertAnswer(
                "Could not abort txn: 13",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.5::FLOAT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('NaN'::FLOAT)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found '\"values\".V'",
                "SELECT SYSTEM$ABORT_TRANSACTION(v) FROM (SELECT 12 AS v)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'T.V'",
                "SELECT SYSTEM$ABORT_TRANSACTION(v) FROM (VALUES (12), (13)) t(v) ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'T.V'",
                "SELECT SYSTEM$ABORT_TRANSACTION(v) FROM (VALUES ('12'), ('abc'), (NULL)) t(v) ORDER BY 1");
            assertAnswer(
                "Could not abort txn: 12 ;; Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12) FROM (VALUES (1), (2)) t(v)");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION(12) WHERE 1 = 0");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT system$abort_transaction(12)");
            assertAnswer(
                "23",
                "SELECT LENGTH(SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "Could not abort txn: 12x",
                "SELECT SYSTEM$ABORT_TRANSACTION(12) || 'x'");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(1 + 11)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12) AS c");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'.",
                "SELECT SYSTEM$ABORT_TRANSACTION(12,)");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$ABORT_TRANSACTION(NULL))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$ABORT_TRANSACTION('abc'))");
            assertAnswer(
                "Bad transaction id",
                "CALL SYSTEM$ABORT_TRANSACTION('abc')");
            assertAnswer(
                "null",
                "CALL SYSTEM$ABORT_TRANSACTION(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                    + "[SYSTEM$ABORT_TRANSACTION()], expected 1, got 0",
                "CALL SYSTEM$ABORT_TRANSACTION()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                    + "[SYSTEM$ABORT_TRANSACTION(1, 2)] expected 1, got 2",
                "CALL SYSTEM$ABORT_TRANSACTION(1, 2)");
            assertAnswer(
                "Could not abort txn: 12",
                "CALL SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "Bad transaction id",
                "CALL SYSTEM$ABORT_TRANSACTION(1.5)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT \"SYSTEM$ABORT_TRANSACTION\"(12)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT \"system$abort_transaction\"(12)");
            assertAnswer(
                "REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                    + "[SYSTEM$ABORT_TRANSACTION]",
                "CREATE OR REPLACE TABLE abt AS SELECT SYSTEM$ABORT_TRANSACTION(12) AS c");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'ABT' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TABLE ABT.",
                "DESCRIBE TABLE abt");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'ABT' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE ABT.",
                "SELECT * FROM abt");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION(12) FROM ab");
            assertAnswer(
                "1",
                "SELECT COUNT(SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void aTransactionAWriteOpenedWithAutocommitOff() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION SET AUTOCOMMIT = FALSE");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (5)");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NOT NULL");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertShapedAnswer(
                "Aborted transaction id: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SESSION UNSET AUTOCOMMIT");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void aBlockThatAbortsTheTransactionItRunsIn() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (7)");
            assertAnswer(
                "Aborted transaction id:",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  RETURN LEFT(r, 23);\nEND\n$$");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (7)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 5 at position 18 : Your "
                    + "transaction was aborted. All statements will be ignored until you issue a COMMIT or a "
                    + "ROLLBACK.",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  LET n NUMBER := (SELECT COUNT(*) FROM "
                    + "ab);\n  RETURN LEFT(r, 23) || ' ' || n;\nEND\n$$");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN TRANSACTION");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (7)");
            assertAnswer(
                "Aborted transaction id: 0",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  ROLLBACK;\n  LET n NUMBER := (SELECT "
                    + "COUNT(*) FROM ab);\n  RETURN LEFT(r, 23) || ' ' || n;\nEND\n$$");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "ran",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  BEGIN TRANSACTION;\n  INSERT INTO ab VALUES (9);\n  LET id NUMBER "
                    + ":= (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT "
                    + "SYSTEM$ABORT_TRANSACTION(:id));\n  BEGIN\n    INSERT INTO ab VALUES (10);\n  EXCEPTION WHEN "
                    + "OTHER THEN\n    ROLLBACK;\n    RETURN SQLERRM;\n  END;\n  RETURN 'ran';\nEND\n$$");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void whichConstantsNameAnId() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "Could not abort txn: 13",
                "SELECT SYSTEM$ABORT_TRANSACTION('12.5')");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('12.4')");
            assertAnswer(
                "Could not abort txn: 2",
                "SELECT SYSTEM$ABORT_TRANSACTION('1.5e0')");
            assertAnswer(
                "Could not abort txn: 1",
                "SELECT SYSTEM$ABORT_TRANSACTION('.5')");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('12.')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('1e400')");
            assertShapedAnswer(
                "Could not abort txn: -<ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('1e19')");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('1e18')");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION('-1e19')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::NUMBER)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('12'::NUMBER)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::VARCHAR)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(TO_NUMBER('12'))");
            assertAnswer(
                "Could not abort txn: -12",
                "SELECT SYSTEM$ABORT_TRANSACTION(-12)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(+12)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION((12))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12 * 1)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('1' || '2')");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(ABS(12))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(CAST(12 AS INT))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::NUMBER(10,2))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.5::NUMBER(10,0))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.5::NUMBER(10,1))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(TO_VARCHAR(12))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('12'::VARIANT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::VARIANT::NUMBER)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(IFF(TRUE, 12, 13))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(COALESCE(12, 13))");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(TO_DOUBLE('12'))");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::DOUBLE)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(12e0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(1.25e1)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(0.5e0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(1.5e0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(2.5e0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(-12.5e0)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12.4e0)");
            assertShapedAnswer(
                "Could not abort txn: -<ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(1e19)");
            assertShapedAnswer(
                "Could not abort txn: <ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(1e30)");
            assertShapedAnswer(
                "Could not abort txn: -<ID19>",
                "SELECT SYSTEM$ABORT_TRANSACTION(-1e30)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('inf'::FLOAT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('-inf'::FLOAT)");
            assertAnswer(
                "Statement executed successfully.",
                "SET v = 12");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION($v)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION($v::FLOAT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION($v + 0)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found ''V''",
                "SELECT SYSTEM$ABORT_TRANSACTION(GETVARIABLE('V'))");
            assertAnswer(
                "Statement executed successfully.",
                "SET v = 12.5");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION($v)");
            assertAnswer(
                "Statement executed successfully.",
                "SET v = 'abc'");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION($v)");
            assertAnswer(
                "Statement executed successfully.",
                "SET v = '12'");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION($v)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION($v::NUMBER)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 32 | Session variable '$NOSUCH' does not "
                    + "exist",
                "SELECT SYSTEM$ABORT_TRANSACTION($nosuch)");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET v");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'SYSTEM$NULL_TO_REAL(null)'",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::FLOAT)");
            assertAnswer(
                "null",
                "SELECT SYSTEM$ABORT_TRANSACTION(TRY_TO_NUMBER('abc'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'UNIFORM(1, 10, RANDOM())'",
                "SELECT SYSTEM$ABORT_TRANSACTION(UNIFORM(1, 10, RANDOM()))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'CURRENT_TRANSACTION()'",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_TRANSACTION())");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'LAST_TRANSACTION()'",
                "SELECT SYSTEM$ABORT_TRANSACTION(LAST_TRANSACTION())");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found '(SELECT 12 AS \"12\" FROM (VALUES (null)) DUAL)'",
                "SELECT SYSTEM$ABORT_TRANSACTION((SELECT 12))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'CURRENT_SESSION()'",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_SESSION())");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (TIMESTAMP_LTZ(9))",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_TIMESTAMP())");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION('12'::CHAR(2))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(CAST('12' AS VARCHAR))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(TRUE::NUMBER)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::NUMBER::VARCHAR)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION(UPPER('12'))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(SQRT(144))");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::TINYINT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::BIGINT)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT SYSTEM$ABORT_TRANSACTION('12'::INT)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(' 12 '::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (BOOLEAN)",
                "SELECT SYSTEM$ABORT_TRANSACTION(12::BOOLEAN)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'SYSTEM$ABORT_TRANSACTION': (DATE)",
                "SELECT SYSTEM$ABORT_TRANSACTION(NULL::DATE)");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'CURRENT_TRANSACTION()'",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_TRANSACTION())");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be "
                    + "constant, found 'TO_NUMBER(CURRENT_TRANSACTION())'",
                "SELECT SYSTEM$ABORT_TRANSACTION(CURRENT_TRANSACTION()::NUMBER)");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void whereTheCallMayStand() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                    + "[SYSTEM$ABORT_TRANSACTION]",
                "CREATE OR REPLACE VIEW abv AS SELECT SYSTEM$ABORT_TRANSACTION(12) AS c");
            assertAnswer(
                "Table ABS successfully created.",
                "CREATE OR REPLACE TABLE abs (c VARCHAR)");
            assertAnswer(
                "1",
                "INSERT INTO abs SELECT SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT * FROM abs");
            assertAnswer(
                "1",
                "INSERT INTO abs VALUES (SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM abs");
            assertAnswer(
                "REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                    + "[SYSTEM$ABORT_TRANSACTION]",
                "CREATE OR REPLACE FUNCTION abf() RETURNS VARCHAR AS 'SYSTEM$ABORT_TRANSACTION(12)'");
            assertAnswer(
                "Table ABW successfully created.",
                "CREATE OR REPLACE TABLE abw AS SELECT SYSTEM$WAIT(0) AS c");
            assertAnswer(
                "Table ABD successfully created.",
                "CREATE OR REPLACE TABLE abd (c VARCHAR DEFAULT SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "2|0",
                "UPDATE abs SET c = SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "2",
                "DELETE FROM abs WHERE c = SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "",
                "SELECT * FROM abs WHERE c = SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "",
                "SELECT c FROM abs GROUP BY c HAVING c = SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "1",
                "SELECT 1 ORDER BY SYSTEM$ABORT_TRANSACTION(12)");
            assertAnswer(
                "Could not abort txn: 12",
                "WITH w AS (SELECT SYSTEM$ABORT_TRANSACTION(12) AS c) SELECT * FROM w");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT * FROM (SELECT SYSTEM$ABORT_TRANSACTION(12) AS c)");
            assertAnswer(
                "Could not abort txn: 12",
                "SELECT (SELECT SYSTEM$ABORT_TRANSACTION(12))");
            answer(
                "SELECT SYSTEM$ABORT_TRANSACTION(12) UNION ALL SELECT SYSTEM$ABORT_TRANSACTION(13)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
                "VALUES (SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "Could not abort txn: 12|Could not abort txn: 13",
                "SELECT SYSTEM$ABORT_TRANSACTION(12), SYSTEM$ABORT_TRANSACTION(13)");
            assertAnswer(
                "Bad transaction id",
                "SELECT SYSTEM$ABORT_TRANSACTION(SYSTEM$ABORT_TRANSACTION(12))");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) WHERE 1 = 0");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "no",
                "SELECT IFF(FALSE, SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 'no')");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:|Could not abort txn: 17",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS a, "
                    + "LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS b");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:|0",
                "SELECT LEFT(SYSTEM$ABORT_TRANSACTION($txid::NUMBER), 23) AS a, (SELECT COUNT(*) FROM ab) AS n");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "false",
                "SELECT SYSTEM$ABORT_TRANSACTION($txid::NUMBER) = SYSTEM$ABORT_TRANSACTION($txid::NUMBER)");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }

    @Test
    public void blocksAndProcedures() {
        engine.execute(
            "CREATE OR REPLACE TABLE ab (a INT)");
        try {
            assertAnswer(
                "0",
                "DELETE FROM ab");
            assertAnswer(
                "Aborted transaction id: 0",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  BEGIN TRANSACTION;\n  INSERT INTO ab VALUES (9);\n  LET id NUMBER "
                    + ":= (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT "
                    + "SYSTEM$ABORT_TRANSACTION(:id));\n  LET n NUMBER := (SELECT COUNT(*) FROM ab);\n  RETURN "
                    + "LEFT(r, 23) || ' ' || n;\nEND\n$$");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (7)");
            assertAnswer(
                "624 57014 Your transaction was aborted. All statements will be ignored until you issue a COMMIT "
                    + "or a ROLLBACK.",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  BEGIN\n    INSERT INTO ab VALUES "
                    + "(10);\n  EXCEPTION WHEN OTHER THEN\n    RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                    + "SQLERRM;\n  END;\n  RETURN 'ran';\nEND\n$$");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "committed",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  BEGIN TRANSACTION;\n  INSERT INTO ab VALUES (11);\n  LET id "
                    + "NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT "
                    + "SYSTEM$ABORT_TRANSACTION(:id));\n  COMMIT;\n  RETURN 'committed';\nEND\n$$");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "true",
                "SELECT CURRENT_TRANSACTION() IS NULL");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (8)");
            assertShapedAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 5 at position 2 : COMMIT failed. "
                    + "Your transaction '<QID>', id '<ID19>', was already aborted.  | ",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  COMMIT;\n  RETURN 'committed';\nEND\n$$");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (8)");
            assertShapedAnswer(
                "670 57014 COMMIT failed. Your transaction '<QID>', id '<ID19>', was already aborted.  | ",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  LET id NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r "
                    + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  BEGIN\n    COMMIT;\n  EXCEPTION WHEN "
                    + "OTHER THEN\n    RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\n  END;\n  RETURN "
                    + "'ran';\nEND\n$$");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Aborted transaction id: 0",
                "EXECUTE IMMEDIATE $$\nBEGIN\n  BEGIN TRANSACTION;\n  INSERT INTO ab VALUES (13);\n  LET id "
                    + "NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT "
                    + "SYSTEM$ABORT_TRANSACTION(:id));\n  ROLLBACK;\n  LET n NUMBER := (SELECT COUNT(*) FROM ab);\n  "
                    + "RETURN LEFT(r, 23) || ' ' || n;\nEND\n$$");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Function P_ABORT successfully created.",
                "CREATE OR REPLACE PROCEDURE p_abort()\nRETURNS VARCHAR\nLANGUAGE SQL\nAS\n$$\nBEGIN\n  LET id "
                    + "NUMBER := (SELECT CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT "
                    + "SYSTEM$ABORT_TRANSACTION(:id));\n  RETURN LEFT(r, 23);\nEND\n$$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (20)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 4 at position 19 : SQL compilation "
                    + "error:  | Query called from a stored procedure contains a function with side effects "
                    + "[SYSTEM$ABORT_TRANSACTION].",
                "CALL p_abort()");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Function P_ABORT_OWN successfully created.",
                "CREATE OR REPLACE PROCEDURE p_abort_own()\nRETURNS VARCHAR\nLANGUAGE SQL\nAS\n$$\nBEGIN\n  BEGIN "
                    + "TRANSACTION;\n  INSERT INTO ab VALUES (21);\n  LET id NUMBER := (SELECT "
                    + "CURRENT_TRANSACTION()::NUMBER);\n  LET r VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n "
                    + " LET n NUMBER := (SELECT COUNT(*) FROM ab);\n  RETURN LEFT(r, 23) || ' ' || n;\nEND\n$$");
            assertAnswer(
                "1",
                "SELECT 1");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "Function P_ABORT_ID successfully created.",
                "CREATE OR REPLACE PROCEDURE p_abort_id(id NUMBER)\nRETURNS VARCHAR\nLANGUAGE SQL\nEXECUTE AS "
                    + "CALLER\nAS\n$$\nBEGIN\n  LET r VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(:id));\n  RETURN "
                    + "LEFT(r, 23);\nEND\n$$");
            assertAnswer(
                "Statement executed successfully.",
                "BEGIN");
            assertAnswer(
                "1",
                "INSERT INTO ab VALUES (30)");
            assertAnswer(
                "Statement executed successfully.",
                "SET txid = (SELECT CURRENT_TRANSACTION())");
            assertAnswer(
                "Aborted transaction id:",
                "CALL p_abort_id($txid::NUMBER)");
            assertAnswer(
                "REFUSED Your transaction was aborted. All statements will be ignored until you issue a COMMIT or "
                    + "a ROLLBACK.",
                "SELECT 1");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM ab");
            assertAnswer(
                "Could not abort txn: 12",
                "CALL p_abort_id(12)");
            assertAnswer(
                "Statement executed successfully.",
                "UNSET txid");
            assertAnswer(
                "Statement executed successfully.",
                "ROLLBACK");
        } finally {
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET AUTOCOMMIT");
            answer(
                "ROLLBACK");
            answer(
                "ALTER SESSION UNSET QUERY_TAG");
            answer(
                "UNSET txid");
            answer(
                "UNSET v");
            answer(
                "UNSET x");
        }
    }
}
