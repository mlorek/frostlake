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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * How a structured parameter is spelled where a routine's signature is shown. DESCRIBE's signature row, SHOW's
 * arguments and INFORMATION_SCHEMA's ARGUMENT_SIGNATURE keep the shape and spell the types inside it bare, a
 * field's NOT NULL kept: {@code (X OBJECT(a NUMBER NOT NULL, b VARCHAR))}, {@code ARRAY(NUMBER)}, {@code
 * MAP(VARCHAR, ARRAY(FLOAT))}. A VECTOR keeps its element type and size in DESCRIBE and SHOW and loses them in
 * INFORMATION_SCHEMA. A written signature names a structured parameter by its shape - field names, their order,
 * each type's family and a field's NOT NULL - whatever widths it writes inside. Every answer is live-verified.
 */
public class StructuredSignatureSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void aShapeIsSpelledWithBareTypesInside() {
        answer(
            "CREATE OR REPLACE FUNCTION f1(x OBJECT) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f2(x ARRAY(NUMBER), y MAP(VARCHAR, NUMBER)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f3(x OBJECT(a INT, b VARCHAR(10))) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f4(x ARRAY(NUMBER(5,2))) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f5(x OBJECT(a ARRAY(INT), b OBJECT(c VARCHAR))) RETURNS VARCHAR AS $$ 's' "
                + "$$");
        answer(
            "CREATE OR REPLACE FUNCTION f6(x ARRAY(OBJECT(a INT))) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f7(x OBJECT(a INT NOT NULL, b VARCHAR)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f8(x ARRAY(INT NOT NULL)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION f9(x MAP(VARCHAR(5), ARRAY(FLOAT))) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g1(x ARRAY) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g2(x VARIANT, y OBJECT(a TIMESTAMP_NTZ(3), b BINARY(4), c BOOLEAN, d DATE,"
                + " e TIME(2))) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g3(x VECTOR(INT, 3), y VECTOR(FLOAT, 2)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g4(x NUMBER(5,2), y VARCHAR(10), z TIMESTAMP_LTZ(3)) RETURNS VARCHAR AS "
                + "$$ 's' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g5(x INT) RETURNS OBJECT(a INT) AS $$ NULL::OBJECT(a INT) $$");
        answer(
            "CREATE OR REPLACE FUNCTION g6(x INT) RETURNS ARRAY(NUMBER(5,2)) AS $$ NULL::ARRAY(NUMBER(5,2)) $$");
        answer(
            "CREATE OR REPLACE FUNCTION g7(x INT) RETURNS MAP(VARCHAR, NUMBER) AS $$ NULL::MAP(VARCHAR, NUMBER) $$");
        answer(
            "CREATE OR REPLACE FUNCTION g8(x OBJECT(\"lower\" INT, \"With Space\" VARCHAR)) RETURNS VARCHAR AS $$ "
                + "'s' $$");
        answer(
            "CREATE OR REPLACE FUNCTION g9(x OBJECT(a INT) DEFAULT NULL) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE OR REPLACE PROCEDURE p1(x OBJECT(a INT), y ARRAY(NUMBER(5,2))) RETURNS VARCHAR LANGUAGE SQL "
                + "AS $$ BEGIN RETURN 's'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE p2(x MAP(VARCHAR, NUMBER)) RETURNS OBJECT(a INT) LANGUAGE SQL AS $$ "
                + "BEGIN RETURN NULL; END; $$");
        assertAnswer(
            "signature|(X OBJECT) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f1(OBJECT)");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f1(OBJECT(a INT))");
        assertAnswer(
            "signature|(X ARRAY(NUMBER), Y MAP(VARCHAR, NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f2(ARRAY(NUMBER), MAP(VARCHAR, NUMBER))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER, b VARCHAR)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f3(OBJECT(a INT, b VARCHAR(10)))");
        assertAnswer(
            "signature|(X ARRAY(NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f4(ARRAY(NUMBER(5,2)))");
        assertAnswer(
            "signature|(X OBJECT(a ARRAY(NUMBER), b OBJECT(c VARCHAR))) ;; returns|VARCHAR ;; language|SQL ;; "
                + "body| 's' ",
            "DESCRIBE FUNCTION f5(OBJECT(a ARRAY(INT), b OBJECT(c VARCHAR)))");
        assertAnswer(
            "signature|(X ARRAY(OBJECT(a NUMBER))) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f6(ARRAY(OBJECT(a INT)))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER NOT NULL, b VARCHAR)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f7(OBJECT(a INT NOT NULL, b VARCHAR))");
        answer(
            "DESCRIBE FUNCTION f8(ARRAY(INT NOT NULL))");
        assertAnswer(
            "signature|(X MAP(VARCHAR, ARRAY(FLOAT))) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f9(MAP(VARCHAR(5), ARRAY(FLOAT)))");
        assertAnswer(
            "signature|(X ARRAY) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION g1(ARRAY)");
        assertAnswer(
            "signature|(X VARIANT, Y OBJECT(a TIMESTAMP_NTZ, b BINARY, c BOOLEAN, d DATE, e TIME)) ;; "
                + "returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION g2(VARIANT, OBJECT(a TIMESTAMP_NTZ(3), b BINARY(4), c BOOLEAN, d DATE, e TIME(2)))");
        assertAnswer(
            "signature|(X VECTOR(INT, 3), Y VECTOR(FLOAT, 2)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION g3(VECTOR(INT, 3), VECTOR(FLOAT, 2))");
        assertAnswer(
            "signature|(X NUMBER, Y VARCHAR, Z TIMESTAMP_LTZ) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION g4(NUMBER(5,2), VARCHAR(10), TIMESTAMP_LTZ(3))");
        assertAnswer(
            "signature|(X NUMBER) ;; returns|OBJECT(a NUMBER(38,0)) ;; language|SQL ;; body| NULL::OBJECT(a INT) ",
            "DESCRIBE FUNCTION g5(INT)");
        assertAnswer(
            "signature|(X NUMBER) ;; returns|ARRAY(NUMBER(5,2)) ;; language|SQL ;; body| NULL::ARRAY(NUMBER(5,2)) ",
            "DESCRIBE FUNCTION g6(INT)");
        answer(
            "DESCRIBE FUNCTION g7(INT)");
        assertAnswer(
            "signature|(X OBJECT(lower NUMBER, With Space VARCHAR)) ;; returns|VARCHAR ;; language|SQL ;; body| "
                + "'s' ",
            "DESCRIBE FUNCTION g8(OBJECT(\"lower\" INT, \"With Space\" VARCHAR))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION g9(OBJECT(a INT))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER), Y ARRAY(NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; execute "
                + "as|OWNER ;; body| BEGIN RETURN 's'; END; ",
            "DESCRIBE PROCEDURE p1(OBJECT(a INT), ARRAY(NUMBER(5,2)))");
        assertAnswer(
            "signature|(X MAP(VARCHAR, NUMBER)) ;; returns|OBJECT(a NUMBER(38,0)) ;; language|SQL ;; execute "
                + "as|OWNER ;; body| BEGIN RETURN NULL; END; ",
            "DESCRIBE PROCEDURE p2(MAP(VARCHAR, NUMBER))");
        answer(
            "SELECT function_name, argument_signature, data_type FROM information_schema.functions WHERE "
                + "function_schema = 'TEST_SCHEMA' ORDER BY function_name, argument_signature");
        assertAnswer(
            "P1|(X OBJECT(a NUMBER), Y ARRAY(NUMBER))|VARCHAR(134217728) ;; P2|(X MAP(VARCHAR, NUMBER))|OBJECT(a "
                + "NUMBER(38,0))",
            "SELECT procedure_name, argument_signature, data_type FROM information_schema.procedures WHERE "
                + "procedure_schema = 'TEST_SCHEMA' ORDER BY procedure_name, argument_signature");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
                + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
                + "is_ai_function:VARCHAR(16777216)",
            "SHOW USER FUNCTIONS IN SCHEMA test_db.test_schema");
        answer(
            "SELECT \"name\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1, 2");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216)",
            "SHOW PROCEDURES LIKE 'P_' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "P1|P1(OBJECT(a NUMBER), ARRAY(NUMBER)) RETURN VARCHAR ;; P2|P2(MAP(VARCHAR, NUMBER)) RETURN OBJECT(a "
                + "NUMBER)",
            "SELECT \"name\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1, 2");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"F1\"(\"X\" OBJECT(a NUMBER(38,0))) | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS ' ''s'' ';",
            "SELECT GET_DDL('FUNCTION', 'f1(OBJECT(a INT))')");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"F3\"(\"X\" OBJECT(a NUMBER(38,0), b VARCHAR(10))) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS ' ''s'' ';",
            "SELECT GET_DDL('FUNCTION', 'f3(OBJECT(a INT, b VARCHAR(10)))')");
        answer(
            "SELECT GET_DDL('FUNCTION', 'f5(OBJECT(a ARRAY(INT), b OBJECT(c VARCHAR)))')");
        answer(
            "SELECT GET_DDL('FUNCTION', 'f7(OBJECT(a INT NOT NULL, b VARCHAR))')");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"G5\"(\"X\" NUMBER(38,0)) | RETURNS OBJECT(a NUMBER(38,0)) | LANGUAGE "
                + "SQL | AS ' NULL::OBJECT(a INT) ';",
            "SELECT GET_DDL('FUNCTION', 'g5(INT)')");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"P1\"(\"X\" OBJECT(a NUMBER(38,0)), \"Y\" ARRAY(NUMBER(5,2))) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | EXECUTE AS OWNER | AS ' BEGIN RETURN ''s''; END; ';",
            "SELECT GET_DDL('PROCEDURE', 'p1(OBJECT(a INT), ARRAY(NUMBER(5,2)))')");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f1(OBJECT(a NUMBER))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F1.",
            "DESCRIBE FUNCTION f1(OBJECT(a VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F1.",
            "DESCRIBE FUNCTION f1(OBJECT(b INT))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER, b VARCHAR)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f3(OBJECT(a INT, b VARCHAR))");
        assertAnswer(
            "signature|(X OBJECT(a NUMBER, b VARCHAR)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f3(OBJECT(a INT, b VARCHAR(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F3' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F3.",
            "DESCRIBE FUNCTION f3(OBJECT(b VARCHAR(10), a INT))");
        assertAnswer(
            "signature|(X ARRAY(NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f4(ARRAY(NUMBER))");
        assertAnswer(
            "signature|(X ARRAY(NUMBER)) ;; returns|VARCHAR ;; language|SQL ;; body| 's' ",
            "DESCRIBE FUNCTION f4(ARRAY(NUMBER(10,0)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F4' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F4.",
            "DESCRIBE FUNCTION f4(ARRAY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F7' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F7.",
            "DESCRIBE FUNCTION f7(OBJECT(a INT, b VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F8' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F8.",
            "DESCRIBE FUNCTION f8(ARRAY(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F2.",
            "DESCRIBE FUNCTION f2(ARRAY, MAP(VARCHAR, NUMBER))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F2.",
            "DESCRIBE FUNCTION f2(ARRAY(NUMBER), MAP(VARCHAR, VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F2' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F2.",
            "DESCRIBE FUNCTION f2(ARRAY(NUMBER), OBJECT)");
        assertAnswer(
            "s|s",
            "SELECT f1(OBJECT_CONSTRUCT('a', 1)), f1(OBJECT_CONSTRUCT('a', 1)::OBJECT(a INT))");
        assertAnswer(
            "OBJECT(a NUMBER(38,0))[LOB]|ARRAY(NUMBER(5,2))[LOB]|MAP(VARCHAR, NUMBER(38,0))[LOB]",
            "SELECT SYSTEM$TYPEOF(g5(1)), SYSTEM$TYPEOF(g6(1)), SYSTEM$TYPEOF(g7(1))");
        shape(
            "SELECT g5(1) a, g6(1) b, g7(1) c");
    }

    @Test
    public void aShapeIsAnOverloadOfItsOwn() {
        answer(
            "CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        answer(
            "INSERT INTO t1 VALUES (1, 2)");
        answer(
            "CREATE OR REPLACE MATERIALIZED VIEW mv0 AS SELECT x, y FROM t1");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 's' $$");
        answer(
            "CREATE FUNCTION f1(x OBJECT(b INT)) RETURNS VARCHAR AS $$ 't' $$");
        answer(
            "CREATE FUNCTION f1(x OBJECT(a VARCHAR)) RETURNS VARCHAR AS $$ 'u' $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'F1' already exists.",
            "CREATE FUNCTION f1(x OBJECT(a NUMBER(10,2))) RETURNS VARCHAR AS $$ 'w' $$");
        answer(
            "CREATE FUNCTION f1(x OBJECT(a INT NOT NULL)) RETURNS VARCHAR AS $$ 'n' $$");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
                + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
                + "is_ai_function:VARCHAR(16777216)",
            "SHOW USER FUNCTIONS LIKE 'F1' IN SCHEMA test_db.test_schema");
        answer(
            "SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        answer(
            "DROP FUNCTION f1(OBJECT(b INT))");
        answer(
            "DROP FUNCTION f1(OBJECT(a VARCHAR(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F1' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F1.",
            "DROP FUNCTION f1(OBJECT)");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216), is_external_function:VARCHAR(16777216), "
                + "language:VARCHAR(16777216), is_memoizable:VARCHAR(16777216), is_data_metric:VARCHAR(16777216), "
                + "is_ai_function:VARCHAR(16777216)",
            "SHOW USER FUNCTIONS LIKE 'F1' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "F1(OBJECT(a NUMBER)) RETURN VARCHAR",
            "SELECT \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }
}
