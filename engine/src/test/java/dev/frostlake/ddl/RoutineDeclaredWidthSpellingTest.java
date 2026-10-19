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
 * A routine's RETURNS type and its arguments' types are spelled as they were declared: a text or a binary
 * written with its width keeps it in GET_DDL, DESCRIBE and INFORMATION_SCHEMA — VARCHAR(16777216),
 * BINARY(8388608) — where one written bare reads bare, and at the widest a value can be in INFORMATION_SCHEMA
 * (live-verified).
 */
public class RoutineDeclaredWidthSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void aWidthWrittenOutIsKept() {
        try {
            answer(
                "CREATE OR REPLACE FUNCTION rw_a() RETURNS VARCHAR(16777216) AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_b() RETURNS VARCHAR AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_c() RETURNS BINARY(8388608) AS $$TO_BINARY('41', 'HEX')$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_d() RETURNS BINARY AS $$TO_BINARY('41', 'HEX')$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_e(x VARCHAR(16777216)) RETURNS VARCHAR AS $$x$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_f(x VARCHAR) RETURNS VARCHAR(134217728) AS $$x$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_g(x BINARY(8388608)) RETURNS NUMBER AS $$1$$");
            answer(
                "CREATE OR REPLACE FUNCTION rw_h(x VARCHAR(10)) RETURNS VARCHAR(10) AS $$x$$");
            answer(
                "CREATE OR REPLACE PROCEDURE rw_p() RETURNS VARCHAR(16777216) LANGUAGE SQL AS $$BEGIN RETURN 'a'; "
                    + "END$$");
            answer(
                "CREATE OR REPLACE PROCEDURE rw_q(x VARCHAR(16777216)) RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN "
                    + "RETURN x; END$$");
            answer(
                "CREATE OR REPLACE PROCEDURE rw_r() RETURNS BINARY(8388608) LANGUAGE SQL AS $$BEGIN RETURN "
                    + "TO_BINARY('41', 'HEX'); END$$");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_A\"() | RETURNS VARCHAR(16777216) | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rw_a()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR(16777216) ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rw_a()");
            assertAnswer(
                "VARCHAR(16777216)|16777216|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_A'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_B\"() | RETURNS VARCHAR | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rw_b()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rw_b()");
            assertAnswer(
                "VARCHAR(134217728)|134217728|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_B'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_C\"() | RETURNS BINARY(8388608) | LANGUAGE SQL | AS "
                    + "'TO_BINARY(''41'', ''HEX'')';",
                "SELECT GET_DDL('FUNCTION', 'rw_c()')");
            assertAnswer(
                "signature|() ;; returns|BINARY(8388608) ;; language|SQL ;; body|TO_BINARY('41', 'HEX')",
                "DESCRIBE FUNCTION rw_c()");
            assertAnswer(
                "BINARY(8388608)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_C'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_D\"() | RETURNS BINARY | LANGUAGE SQL | AS 'TO_BINARY(''41'', "
                    + "''HEX'')';",
                "SELECT GET_DDL('FUNCTION', 'rw_d()')");
            assertAnswer(
                "signature|() ;; returns|BINARY ;; language|SQL ;; body|TO_BINARY('41', 'HEX')",
                "DESCRIBE FUNCTION rw_d()");
            assertAnswer(
                "BINARY(67108864)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_D'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_E\"(\"X\" VARCHAR(16777216)) | RETURNS VARCHAR | LANGUAGE SQL | "
                    + "AS 'x';",
                "SELECT GET_DDL('FUNCTION', 'rw_e(VARCHAR)')");
            assertAnswer(
                "signature|(X VARCHAR) ;; returns|VARCHAR ;; language|SQL ;; body|x",
                "DESCRIBE FUNCTION rw_e(VARCHAR)");
            assertAnswer(
                "VARCHAR(134217728)|134217728|(X VARCHAR)",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_E'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_F\"(\"X\" VARCHAR) | RETURNS VARCHAR(134217728) | LANGUAGE SQL | "
                    + "AS 'x';",
                "SELECT GET_DDL('FUNCTION', 'rw_f(VARCHAR)')");
            assertAnswer(
                "signature|(X VARCHAR) ;; returns|VARCHAR(134217728) ;; language|SQL ;; body|x",
                "DESCRIBE FUNCTION rw_f(VARCHAR)");
            assertAnswer(
                "VARCHAR(134217728)|134217728|(X VARCHAR)",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_F'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_G\"(\"X\" BINARY(8388608)) | RETURNS NUMBER(38,0) | LANGUAGE SQL "
                    + "| AS '1';",
                "SELECT GET_DDL('FUNCTION', 'rw_g(BINARY)')");
            assertAnswer(
                "signature|(X BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
                "DESCRIBE FUNCTION rw_g(BINARY)");
            assertAnswer(
                "NUMBER(38,0)|null|(X BINARY)",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_G'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_H\"(\"X\" VARCHAR(10)) | RETURNS VARCHAR(10) | LANGUAGE SQL | AS "
                    + "'x';",
                "SELECT GET_DDL('FUNCTION', 'rw_h(VARCHAR)')");
            assertAnswer(
                "signature|(X VARCHAR) ;; returns|VARCHAR(10) ;; language|SQL ;; body|x",
                "DESCRIBE FUNCTION rw_h(VARCHAR)");
            assertAnswer(
                "VARCHAR(10)|10|(X VARCHAR)",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RW_H'");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"RW_P\"() | RETURNS VARCHAR(16777216) | LANGUAGE SQL | EXECUTE AS "
                    + "OWNER | AS 'BEGIN RETURN ''a''; END';",
                "SELECT GET_DDL('PROCEDURE', 'rw_p()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR(16777216) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                    + "RETURN 'a'; END",
                "DESCRIBE PROCEDURE rw_p()");
            assertAnswer(
                "VARCHAR(16777216)|16777216|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM "
                    + "information_schema.procedures WHERE procedure_name = 'RW_P'");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"RW_Q\"(\"X\" VARCHAR(16777216)) | RETURNS VARCHAR | LANGUAGE SQL | "
                    + "EXECUTE AS OWNER | AS 'BEGIN RETURN x; END';",
                "SELECT GET_DDL('PROCEDURE', 'rw_q(VARCHAR)')");
            assertAnswer(
                "signature|(X VARCHAR) ;; returns|VARCHAR ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                    + "RETURN x; END",
                "DESCRIBE PROCEDURE rw_q(VARCHAR)");
            assertAnswer(
                "VARCHAR(134217728)|134217728|(X VARCHAR)",
                "SELECT data_type, character_maximum_length, argument_signature FROM "
                    + "information_schema.procedures WHERE procedure_name = 'RW_Q'");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"RW_R\"() | RETURNS BINARY(8388608) | LANGUAGE SQL | EXECUTE AS "
                    + "OWNER | AS 'BEGIN RETURN TO_BINARY(''41'', ''HEX''); END';",
                "SELECT GET_DDL('PROCEDURE', 'rw_r()')");
            assertAnswer(
                "signature|() ;; returns|BINARY(8388608) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN RETURN "
                    + "TO_BINARY('41', 'HEX'); END",
                "DESCRIBE PROCEDURE rw_r()");
            assertAnswer(
                "BINARY(8388608)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM "
                    + "information_schema.procedures WHERE procedure_name = 'RW_R'");
            assertAnswer(
                "VARCHAR(1)[LOB]|VARCHAR(1)[LOB]|BINARY[LOB]|BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(rw_a()), SYSTEM$TYPEOF(rw_b()), SYSTEM$TYPEOF(rw_c()), SYSTEM$TYPEOF(rw_d())");
            assertAnswer(
                "VARCHAR(1)[LOB]|VARCHAR(1)[LOB]|VARCHAR(1)[LOB]",
                "SELECT SYSTEM$TYPEOF(rw_e('a')), SYSTEM$TYPEOF(rw_f('a')), SYSTEM$TYPEOF(rw_h('a'))");
            assertAnswer(
                "a",
                "CALL rw_p()");
            assertAnswer(
                "a",
                "CALL rw_q('a')");
            assertAnswer(
                "41",
                "CALL rw_r()");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_E\"(\"X\" VARCHAR(16777216)) | RETURNS VARCHAR | LANGUAGE SQL | "
                    + "AS 'x';",
                "SELECT GET_DDL('FUNCTION', 'rw_e(VARCHAR(16777216))')");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RW_H\"(\"X\" VARCHAR(10)) | RETURNS VARCHAR(10) | LANGUAGE SQL | AS "
                    + "'x';",
                "SELECT GET_DDL('FUNCTION', 'rw_h(VARCHAR(10))')");
            assertAnswer(
                "signature|(X VARCHAR) ;; returns|VARCHAR ;; language|SQL ;; body|x",
                "DESCRIBE FUNCTION rw_e(VARCHAR(16777216))");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS rw_a()");
            answer(
                "DROP FUNCTION IF EXISTS rw_b()");
            answer(
                "DROP FUNCTION IF EXISTS rw_c()");
            answer(
                "DROP FUNCTION IF EXISTS rw_d()");
            answer(
                "DROP FUNCTION IF EXISTS rw_e(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS rw_f(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS rw_g(BINARY)");
            answer(
                "DROP FUNCTION IF EXISTS rw_h(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS rw_p()");
            answer(
                "DROP PROCEDURE IF EXISTS rw_q(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS rw_r()");
        }
    }

    @Test
    public void otherSpellingsOfTextAndBinary() {
        try {
            answer(
                "CREATE OR REPLACE FUNCTION rv_a() RETURNS STRING AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_b() RETURNS TEXT AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_c() RETURNS STRING(16777216) AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_d() RETURNS VARBINARY AS $$TO_BINARY('41', 'HEX')$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_e() RETURNS BINARY(100) AS $$TO_BINARY('41', 'HEX')$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_f() RETURNS CHAR AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_g() RETURNS VARCHAR(16777216) NOT NULL AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_h(x BINARY, y STRING, z TEXT(16777216), w BINARY(100)) RETURNS "
                    + "NUMBER AS $$1$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_i() RETURNS VARCHAR(134217728) AS $$'a'$$");
            answer(
                "CREATE OR REPLACE FUNCTION rv_j() RETURNS BINARY(67108864) AS $$TO_BINARY('41', 'HEX')$$");
            answer(
                "CREATE OR REPLACE PROCEDURE rv_p() RETURNS STRING LANGUAGE SQL AS $$BEGIN RETURN 'a'; END$$");
            answer(
                "CREATE OR REPLACE PROCEDURE rv_q(x BINARY(8388608), y VARCHAR) RETURNS BINARY LANGUAGE SQL AS "
                    + "$$BEGIN RETURN x; END$$");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_A\"() | RETURNS VARCHAR | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_a()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rv_a()");
            assertAnswer(
                "VARCHAR(134217728)|134217728|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_A'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_B\"() | RETURNS VARCHAR | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_b()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rv_b()");
            assertAnswer(
                "VARCHAR(134217728)|134217728|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_B'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_C\"() | RETURNS VARCHAR(16777216) | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_c()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR(16777216) ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rv_c()");
            assertAnswer(
                "VARCHAR(16777216)|16777216|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_C'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_D\"() | RETURNS BINARY | LANGUAGE SQL | AS 'TO_BINARY(''41'', "
                    + "''HEX'')';",
                "SELECT GET_DDL('FUNCTION', 'rv_d()')");
            assertAnswer(
                "signature|() ;; returns|BINARY ;; language|SQL ;; body|TO_BINARY('41', 'HEX')",
                "DESCRIBE FUNCTION rv_d()");
            assertAnswer(
                "BINARY(67108864)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_D'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_E\"() | RETURNS BINARY(100) | LANGUAGE SQL | AS "
                    + "'TO_BINARY(''41'', ''HEX'')';",
                "SELECT GET_DDL('FUNCTION', 'rv_e()')");
            assertAnswer(
                "signature|() ;; returns|BINARY(100) ;; language|SQL ;; body|TO_BINARY('41', 'HEX')",
                "DESCRIBE FUNCTION rv_e()");
            assertAnswer(
                "BINARY(100)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_E'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_F\"() | RETURNS VARCHAR(1) | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_f()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR(1) ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rv_f()");
            assertAnswer(
                "VARCHAR(1)|1|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_F'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_G\"() | RETURNS VARCHAR(16777216) | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_g()')");
            assertAnswer(
                "VARCHAR(16777216)|16777216|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_G'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_H\"(\"X\" BINARY, \"Y\" VARCHAR, \"Z\" VARCHAR(16777216), \"W\" "
                    + "BINARY(100)) | RETURNS NUMBER(38,0) | LANGUAGE SQL | AS '1';",
                "SELECT GET_DDL('FUNCTION', 'rv_h(BINARY, VARCHAR, VARCHAR, BINARY)')");
            assertAnswer(
                "signature|(X BINARY, Y VARCHAR, Z VARCHAR, W BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; "
                    + "body|1",
                "DESCRIBE FUNCTION rv_h(BINARY, VARCHAR, VARCHAR, BINARY)");
            assertAnswer(
                "NUMBER(38,0)|null|(X BINARY, Y VARCHAR, Z VARCHAR, W BINARY)",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_H'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_I\"() | RETURNS VARCHAR(134217728) | LANGUAGE SQL | AS '''a''';",
                "SELECT GET_DDL('FUNCTION', 'rv_i()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR(134217728) ;; language|SQL ;; body|'a'",
                "DESCRIBE FUNCTION rv_i()");
            assertAnswer(
                "VARCHAR(134217728)|134217728|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_I'");
            assertAnswer(
                "CREATE OR REPLACE FUNCTION \"RV_J\"() | RETURNS BINARY(67108864) | LANGUAGE SQL | AS "
                    + "'TO_BINARY(''41'', ''HEX'')';",
                "SELECT GET_DDL('FUNCTION', 'rv_j()')");
            assertAnswer(
                "signature|() ;; returns|BINARY(67108864) ;; language|SQL ;; body|TO_BINARY('41', 'HEX')",
                "DESCRIBE FUNCTION rv_j()");
            assertAnswer(
                "BINARY(67108864)|null|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM information_schema.functions "
                    + "WHERE function_name = 'RV_J'");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"RV_P\"() | RETURNS VARCHAR | LANGUAGE SQL | EXECUTE AS OWNER | AS "
                    + "'BEGIN RETURN ''a''; END';",
                "SELECT GET_DDL('PROCEDURE', 'rv_p()')");
            assertAnswer(
                "signature|() ;; returns|VARCHAR ;; language|SQL ;; execute as|OWNER ;; body|BEGIN RETURN 'a'; END",
                "DESCRIBE PROCEDURE rv_p()");
            assertAnswer(
                "VARCHAR(134217728)|134217728|()",
                "SELECT data_type, character_maximum_length, argument_signature FROM "
                    + "information_schema.procedures WHERE procedure_name = 'RV_P'");
            assertAnswer(
                "CREATE OR REPLACE PROCEDURE \"RV_Q\"(\"X\" BINARY(8388608), \"Y\" VARCHAR) | RETURNS BINARY | "
                    + "LANGUAGE SQL | EXECUTE AS OWNER | AS 'BEGIN RETURN x; END';",
                "SELECT GET_DDL('PROCEDURE', 'rv_q(BINARY, VARCHAR)')");
            assertAnswer(
                "signature|(X BINARY, Y VARCHAR) ;; returns|BINARY ;; language|SQL ;; execute as|OWNER ;; "
                    + "body|BEGIN RETURN x; END",
                "DESCRIBE PROCEDURE rv_q(BINARY, VARCHAR)");
            assertAnswer(
                "BINARY(67108864)|null|(X BINARY, Y VARCHAR)",
                "SELECT data_type, character_maximum_length, argument_signature FROM "
                    + "information_schema.procedures WHERE procedure_name = 'RV_Q'");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS rv_a()");
            answer(
                "DROP FUNCTION IF EXISTS rv_b()");
            answer(
                "DROP FUNCTION IF EXISTS rv_c()");
            answer(
                "DROP FUNCTION IF EXISTS rv_d()");
            answer(
                "DROP FUNCTION IF EXISTS rv_e()");
            answer(
                "DROP FUNCTION IF EXISTS rv_f()");
            answer(
                "DROP FUNCTION IF EXISTS rv_g()");
            answer(
                "DROP FUNCTION IF EXISTS rv_h(BINARY, VARCHAR, VARCHAR, BINARY)");
            answer(
                "DROP FUNCTION IF EXISTS rv_i()");
            answer(
                "DROP FUNCTION IF EXISTS rv_j()");
            answer(
                "DROP PROCEDURE IF EXISTS rv_p()");
            answer(
                "DROP PROCEDURE IF EXISTS rv_q(BINARY, VARCHAR)");
        }
    }
}
