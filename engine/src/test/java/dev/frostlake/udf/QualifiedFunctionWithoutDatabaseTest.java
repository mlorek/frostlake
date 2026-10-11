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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A function called by its full name in a session that has no current database: the name needs none, so the
 * call runs - with its defaults, with named arguments, from a view, over a table's rows and in a WHERE - and its
 * body reads the objects of its own schema. A name the session has to place - {@code schema.f}, a bare
 * {@code f} - names nothing there. A part the name writes is matched exactly, as any reference is: a quoted
 * schema or database of another case names no function, no table function and no procedure. The session is
 * left without a database by dropping one it had entered, and is put back in the test's schema whatever the
 * assertions make of the statements between. Every answer is live-verified.
 */
public class QualifiedFunctionWithoutDatabaseTest extends MeasuredCellsTestSupport {

    @Test
    public void aFullNameNeedsNoCurrentDatabase() {
        assertAnswer(
            "Table Q_T successfully created.",
            "CREATE OR REPLACE TABLE q_t (v NUMBER)");
        assertAnswer(
            "Function Q_F1 successfully created.",
            "CREATE OR REPLACE FUNCTION q_f1(x NUMBER) RETURNS NUMBER AS 'x * 2'");
        assertAnswer(
            "Function Q_FQ successfully created.",
            "CREATE OR REPLACE FUNCTION q_fq() RETURNS NUMBER AS 'SELECT COUNT(*) FROM q_t'");
        assertAnswer(
            "Function Q_TF successfully created.",
            "CREATE OR REPLACE FUNCTION q_tf() RETURNS TABLE (v NUMBER) AS 'SELECT v FROM q_t'");
        assertAnswer(
            "Function Q_P successfully created.",
            "CREATE OR REPLACE PROCEDURE q_p() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END; $$");
        try {
            assertAnswer(
                "Database FLQ_M6K_GONE successfully created.",
                "CREATE OR REPLACE DATABASE flq_m6k_gone");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE flq_m6k_gone");
            assertAnswer(
                "FLQ_M6K_GONE successfully dropped.",
                "DROP DATABASE flq_m6k_gone");
            assertAnswer(
                "null|null",
                "SELECT NVL(CURRENT_DATABASE(), 'null') || '|' || NVL(CURRENT_SCHEMA(), 'null')");
            assertAnswer(
                "42",
                "SELECT test_db.test_schema.q_f1(21)");
            assertAnswer(
                "0",
                "SELECT test_db.test_schema.q_fq()");
            assertAnswer(
                "",
                "SELECT * FROM TABLE(test_db.test_schema.q_tf())");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM test_db.test_schema.q_t");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function TEST_SCHEMA.Q_F1.",
                "SELECT test_schema.q_f1(21)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function Q_F1.",
                "SELECT q_f1(21)");
            assertAnswer(
                "7",
                "CALL test_db.test_schema.q_p()");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function Q_P.",
                "CALL q_p()");
            assertAnswer(
                "signature|(X NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|x * 2",
                "DESCRIBE FUNCTION test_db.test_schema.q_f1(NUMBER)");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"Q_F1\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE SQL | "
                    + "AS 'x * 2';",
                "SELECT GET_DDL('function', 'test_db.test_schema.q_f1(NUMBER)')");
            assertAnswer(
                "4",
                "SELECT test_db.test_schema.q_f1(test_db.test_schema.q_f1(1))");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.NOSUCH_FN.",
                "SELECT test_db.test_schema.nosuch_fn(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function NOSUCH_DB_ZZ.S.F.",
                "SELECT nosuch_db_zz.s.f(1)");
        } finally {
            answer(
                "USE SCHEMA test_db.test_schema");
            answer(
                "DROP DATABASE IF EXISTS flq_m6k_gone");
        }
        assertAnswer(
            "TEST_DB|TEST_SCHEMA",
            "SELECT CURRENT_DATABASE() || '|' || CURRENT_SCHEMA()");
    }

    @Test
    public void defaultsNamesViewsAndRefusals() {
        assertAnswer(
            "Table Q_T successfully created.",
            "CREATE OR REPLACE TABLE q_t (v NUMBER)");
        assertAnswer(
            "1",
            "INSERT INTO q_t VALUES (3)");
        assertAnswer(
            "Function Q_F1 successfully created.",
            "CREATE OR REPLACE FUNCTION q_f1(x NUMBER) RETURNS NUMBER AS 'x * 2'");
        assertAnswer(
            "Function Q_F2 successfully created.",
            "CREATE OR REPLACE FUNCTION q_f2() RETURNS NUMBER AS 'q_f1(3)'");
        assertAnswer(
            "Function Q_FD successfully created.",
            "CREATE OR REPLACE FUNCTION q_fd(x NUMBER DEFAULT 4) RETURNS NUMBER AS 'x + 1'");
        assertAnswer(
            "Function Q_FS successfully created.",
            "CREATE OR REPLACE FUNCTION q_fs() RETURNS NUMBER AS 'SELECT MAX(q_f1(v)) FROM q_t'");
        assertAnswer(
            "View Q_V successfully created.",
            "CREATE OR REPLACE VIEW q_v AS SELECT q_f1(5) AS c");
        try {
            assertAnswer(
                "Database FLQ_M6K_GONE successfully created.",
                "CREATE OR REPLACE DATABASE flq_m6k_gone");
            assertAnswer(
                "Statement executed successfully.",
                "USE DATABASE flq_m6k_gone");
            assertAnswer(
                "FLQ_M6K_GONE successfully dropped.",
                "DROP DATABASE flq_m6k_gone");
            assertAnswer(
                "null|null",
                "SELECT NVL(CURRENT_DATABASE(), 'null') || '|' || NVL(CURRENT_SCHEMA(), 'null')");
            assertAnswer(
                "6",
                "SELECT test_db.test_schema.q_f2()");
            assertAnswer(
                "5",
                "SELECT test_db.test_schema.q_fd()");
            assertAnswer(
                "10",
                "SELECT test_db.test_schema.q_fd(9)");
            assertAnswer(
                "6",
                "SELECT test_db.test_schema.q_fs()");
            assertAnswer(
                "42",
                "SELECT test_db.test_schema.q_f1(x => 21)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Z] do not match any "
                    + "signature for function TEST_DB.TEST_SCHEMA.Q_F1",
                "SELECT test_db.test_schema.q_f1(z => 21)");
            assertAnswer(
                "10",
                "SELECT c FROM test_db.test_schema.q_v");
            assertAnswer(
                "6",
                "SELECT test_db.test_schema.q_f1(v) FROM test_db.test_schema.q_t");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM test_db.test_schema.q_t WHERE test_db.test_schema.q_f1(v) = 6");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT test_db.test_schema.q_f1('abc')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'Q_F1': (DATE)",
                "SELECT test_db.test_schema.q_f1('2024-01-01'::DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'Q_F1': ()",
                "SELECT test_db.test_schema.q_f1()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[Q_F1(1, 2)] expected 1, got 2",
                "SELECT test_db.test_schema.q_f1(1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.NOSUCH_SCHEMA_ZZ.Q_F1.",
                "SELECT test_db.nosuch_schema_zz.q_f1(1)");
            assertAnswer(
                "6",
                "SELECT IDENTIFIER('test_db.test_schema.q_f1')(3)");
            assertAnswer(
                "12",
                "EXECUTE IMMEDIATE 'SELECT test_db.test_schema.q_f1(6)'");
            assertAnswer(
                "4",
                "SELECT TEST_DB.TEST_SCHEMA.Q_F1(1) + test_db.test_schema.q_fd(1)");
            assertAnswer(
                "16",
                "SELECT \"TEST_DB\".\"TEST_SCHEMA\".\"Q_F1\"(8)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined function \"test_db\".TEST_SCHEMA.Q_F1.",
                "SELECT \"test_db\".test_schema.q_f1(8)");
        } finally {
            answer(
                "USE SCHEMA test_db.test_schema");
            answer(
                "DROP DATABASE IF EXISTS flq_m6k_gone");
        }
        assertAnswer(
            "TEST_DB|TEST_SCHEMA",
            "SELECT CURRENT_DATABASE() || '|' || CURRENT_SCHEMA()");
    }

    @Test
    public void aWrittenPartIsMatchedExactly() {
        assertAnswer(
            "Table Q_T successfully created.",
            "CREATE OR REPLACE TABLE q_t (v NUMBER)");
        assertAnswer(
            "Function Q_F1 successfully created.",
            "CREATE OR REPLACE FUNCTION q_f1(x NUMBER) RETURNS NUMBER AS 'x * 2'");
        assertAnswer(
            "Function Q_TF successfully created.",
            "CREATE OR REPLACE FUNCTION q_tf() RETURNS TABLE (v NUMBER) AS 'SELECT 1'");
        assertAnswer(
            "Function Q_P successfully created.",
            "CREATE OR REPLACE PROCEDURE q_p() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function \"test_db\".TEST_SCHEMA.Q_F1.",
            "SELECT \"test_db\".test_schema.q_f1(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.\"test_schema\".Q_F1.",
            "SELECT test_db.\"test_schema\".q_f1(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.\"q_f1\".",
            "SELECT test_db.test_schema.\"q_f1\"(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function \"test_schema\".Q_F1.",
            "SELECT \"test_schema\".q_f1(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function \"q_f1\".",
            "SELECT \"q_f1\"(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Database '\"test_db\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE \"test_db\".",
            "SELECT COUNT(*) FROM \"test_db\".test_schema.q_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.\"test_schema\"' does not exist or not authorized. "
                + "Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                + "TEST_DB.\"test_schema\".",
            "SELECT COUNT(*) FROM test_db.\"test_schema\".q_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined table function \"test_db\".TEST_SCHEMA.Q_TF",
            "SELECT v FROM TABLE(\"test_db\".test_schema.q_tf())");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined table function TEST_DB.\"test_schema\".Q_TF",
            "SELECT v FROM TABLE(test_db.\"test_schema\".q_tf())");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function \"test_db\".TEST_SCHEMA.Q_P.",
            "CALL \"test_db\".test_schema.q_p()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.\"test_schema\".Q_P.",
            "CALL test_db.\"test_schema\".q_p()");
        assertAnswer(
            "16",
            "SELECT \"TEST_DB\".\"TEST_SCHEMA\".q_f1(8)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined table function TEST_SCHEMA.NOSUCH_TF_ZZ",
            "SELECT v FROM TABLE(test_schema.nosuch_tf_zz())");
    }
}
