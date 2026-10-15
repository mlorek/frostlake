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
 * How a routine parameter's DEFAULT is told back. GET_DDL prints the default from the plan it compiled to, not
 * as it was written: a cast written {@code ::} is {@code CAST('2020-01-01' AS DATE)}, and so are
 * {@code TO_DATE('2020-01-01')} and {@code DATE '2020-01-01'}; a NULL is {@code null}, a bare
 * {@code CURRENT_DATE} is {@code CURRENT_DATE()}, {@code [1, 2]} is {@code ARRAY_CONSTRUCT(1, 2)},
 * {@code NULL::INT} is {@code SYSTEM$NULL_TO_FIXED(null)} and a CASE is {@code CASE_FLATTENED(...)}; a text
 * given to a DATE parameter stays the text. A function, a procedure, a JavaScript and a table function tell
 * it alike. A default that reads a query is refused where the query starts, and the unit word of a date
 * function is no name the default reads. Every answer is live-verified.
 */
public class RoutineParameterDefaultTest extends MeasuredCellsTestSupport {

    @Test
    public void aDateDefaultIsToldFromItsPlan() {
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT '2020-01-01'::DATE) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT CAST('2020-01-01' AS DATE)) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT CURRENT_DATE) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT CURRENT_DATE()) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT CURRENT_DATE()) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT CURRENT_DATE()) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT TO_DATE('2020-01-01')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT CAST('2020-01-01' AS DATE)) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT DATE '2020-01-01') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT CAST('2020-01-01' AS DATE)) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_DQ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dq(p DATE DEFAULT '2020-01-01') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DQ\"(\"P\" DATE DEFAULT '2020-01-01') | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_dq(DATE)')");
        assertAnswer(
            "Function NF_D successfully created.",
            "CREATE OR REPLACE FUNCTION nf_d(d DATE DEFAULT '2020-01-01'::DATE, n NUMBER DEFAULT NULL) RETURNS "
                + "VARCHAR AS 'd::VARCHAR'");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_D\"(\"D\" DATE DEFAULT CAST('2020-01-01' AS DATE), \"N\" NUMBER(38,"
                + "0) DEFAULT null) | RETURNS VARCHAR | LANGUAGE SQL | AS 'd::VARCHAR';",
            "SELECT GET_DDL('FUNCTION', 'nf_d(DATE, NUMBER)')");
        assertAnswer(
            "signature|(D DATE, N NUMBER) ;; returns|VARCHAR ;; language|SQL ;; body|d::VARCHAR",
            "DESCRIBE FUNCTION nf_d(DATE, NUMBER)");
        assertAnswer(
            "NF_D(DEFAULT DATE, DEFAULT NUMBER) RETURN VARCHAR",
            "SHOW USER FUNCTIONS LIKE 'NF_D' ->> SELECT \"arguments\" FROM $1");
        assertAnswer(
            "(D DATE, N NUMBER)",
            "SELECT argument_signature FROM information_schema.functions WHERE function_name = 'NF_D'");
        assertAnswer(
            "Function NF_DP successfully created.",
            "CREATE OR REPLACE PROCEDURE nf_dp(d DATE DEFAULT '2020-01-01'::DATE, n NUMBER DEFAULT NULL) RETURNS "
                + "VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"NF_DP\"(\"D\" DATE DEFAULT CAST('2020-01-01' AS DATE), \"N\" NUMBER(38,"
                + "0) DEFAULT null) | RETURNS VARCHAR | LANGUAGE SQL | EXECUTE AS OWNER | AS 'BEGIN RETURN 1; END';",
            "SELECT GET_DDL('PROCEDURE', 'nf_dp(DATE, NUMBER)')");
        assertAnswer(
            "signature|(D DATE, N NUMBER) ;; returns|VARCHAR ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                + "RETURN 1; END",
            "DESCRIBE PROCEDURE nf_dp(DATE, NUMBER)");
        assertAnswer(
            "NF_DP(DEFAULT DATE, DEFAULT NUMBER) RETURN VARCHAR",
            "SHOW PROCEDURES LIKE 'NF_DP' ->> SELECT \"arguments\" FROM $1");
        assertAnswer(
            "2020-01-01",
            "SELECT nf_d()");
        assertAnswer(
            "Function NF_DJ successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dj(d FLOAT DEFAULT 1::FLOAT, s VARCHAR DEFAULT NULL) RETURNS FLOAT "
                + "LANGUAGE JAVASCRIPT AS 'return D;'");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DJ\"(\"D\" FLOAT DEFAULT CAST(1 AS FLOAT), \"S\" VARCHAR DEFAULT "
                + "null) | RETURNS FLOAT | LANGUAGE JAVASCRIPT | AS 'return D;';",
            "SELECT GET_DDL('FUNCTION', 'nf_dj(FLOAT, VARCHAR)')");
        assertAnswer(
            "Function NF_DT successfully created.",
            "CREATE OR REPLACE FUNCTION nf_dt(d DATE DEFAULT '2020-01-01'::DATE) RETURNS TABLE (x DATE) AS "
                + "'SELECT d'");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_DT\"(\"D\" DATE DEFAULT CAST('2020-01-01' AS DATE)) | RETURNS TABLE "
                + "(\"X\" DATE) | LANGUAGE SQL | AS 'SELECT d';",
            "SELECT GET_DDL('FUNCTION', 'nf_dt(DATE)')");
    }

    @Test
    public void everyShapeOfDefault() {
        assertAnswer(
            "Function NF_E1 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e1(p NUMBER DEFAULT NULL) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E1\"(\"P\" NUMBER(38,0) DEFAULT null) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e1(NUMBER)')");
        assertAnswer(
            "Function NF_E2 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e2(p NUMBER DEFAULT 1 + 1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E2\"(\"P\" NUMBER(38,0) DEFAULT 1 + 1) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e2(NUMBER)')");
        assertAnswer(
            "Function NF_E3 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e3(p VARCHAR DEFAULT UPPER('q')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E3\"(\"P\" VARCHAR DEFAULT UPPER('q')) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e3(VARCHAR)')");
        assertAnswer(
            "Function NF_E4 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e4(p VARCHAR DEFAULT 'x') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E4\"(\"P\" VARCHAR DEFAULT 'x') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e4(VARCHAR)')");
        assertAnswer(
            "Function NF_E5 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e5(p NUMBER DEFAULT 1.5) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E5\"(\"P\" NUMBER(38,0) DEFAULT 1.5) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e5(NUMBER)')");
        assertAnswer(
            "Function NF_E6 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e6(p NUMBER DEFAULT -1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E6\"(\"P\" NUMBER(38,0) DEFAULT -1) | RETURNS VARCHAR | LANGUAGE SQL "
                + "| AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e6(NUMBER)')");
        assertAnswer(
            "Function NF_E7 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e7(p BOOLEAN DEFAULT TRUE) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E7\"(\"P\" BOOLEAN DEFAULT TRUE) | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e7(BOOLEAN)')");
        assertAnswer(
            "Function NF_E8 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e8(p ARRAY DEFAULT [1, 2]) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E8\"(\"P\" ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e8(ARRAY)')");
        assertAnswer(
            "Function NF_E9 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e9(p OBJECT DEFAULT {'a': 1}) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E9\"(\"P\" OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1)) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e9(OBJECT)')");
        assertAnswer(
            "Function NF_E10 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e10(p NUMBER DEFAULT 1::INT) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E10\"(\"P\" NUMBER(38,0) DEFAULT CAST(1 AS NUMBER(38,0))) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e10(NUMBER)')");
        assertAnswer(
            "Function NF_E11 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e11(p VARCHAR DEFAULT CAST('a' AS VARCHAR)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E11\"(\"P\" VARCHAR DEFAULT 'a') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e11(VARCHAR)')");
        assertAnswer(
            "Function NF_E12 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e12(p VARCHAR DEFAULT 'a' || 'b') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E12\"(\"P\" VARCHAR DEFAULT 'a' || 'b') | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e12(VARCHAR)')");
        assertAnswer(
            "Function NF_E13 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e13(p NUMBER DEFAULT NULL::INT) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E13\"(\"P\" NUMBER(38,0) DEFAULT SYSTEM$NULL_TO_FIXED(null)) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e13(NUMBER)')");
        assertAnswer(
            "Function NF_E14 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e14(p NUMBER DEFAULT (1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E14\"(\"P\" NUMBER(38,0) DEFAULT 1) | RETURNS VARCHAR | LANGUAGE SQL "
                + "| AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e14(NUMBER)')");
        assertAnswer(
            "Function NF_E15 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e15(p BOOLEAN DEFAULT 1 = 1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E15\"(\"P\" BOOLEAN DEFAULT 1 = 1) | RETURNS VARCHAR | LANGUAGE SQL "
                + "| AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e15(BOOLEAN)')");
        assertAnswer(
            "Function NF_E16 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e16(p NUMBER DEFAULT CASE WHEN TRUE THEN 1 ELSE 2 END) RETURNS VARCHAR "
                + "AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E16\"(\"P\" NUMBER(38,0) DEFAULT CASE_FLATTENED(CAST(TRUE AS "
                + "BOOLEAN), 1, 2)) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e16(NUMBER)')");
        assertAnswer(
            "Function NF_E17 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e17(p NUMBER DEFAULT IFF(TRUE, 1, 2)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E17\"(\"P\" NUMBER(38,0) DEFAULT IFF(CAST(TRUE AS BOOLEAN), 1, 2)) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e17(NUMBER)')");
        assertAnswer(
            "Function NF_E18 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e18(p VARCHAR DEFAULT null) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E18\"(\"P\" VARCHAR DEFAULT null) | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e18(VARCHAR)')");
        assertAnswer(
            "Function NF_E19 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e19(p NUMBER DEFAULT 1+1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E19\"(\"P\" NUMBER(38,0) DEFAULT 1 + 1) | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e19(NUMBER)')");
        assertAnswer(
            "Function NF_E20 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e20(p FLOAT DEFAULT 1e2) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E20\"(\"P\" FLOAT DEFAULT 100) | RETURNS VARCHAR | LANGUAGE SQL | AS "
                + "'''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e20(FLOAT)')");
        assertAnswer(
            "Function NF_E21 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e21(p VARCHAR DEFAULT 'it''s') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E21\"(\"P\" VARCHAR DEFAULT 'it''s') | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e21(VARCHAR)')");
        assertAnswer(
            "Function NF_E22 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e22(p TIMESTAMP_NTZ DEFAULT '2020-01-01 00:00:00'::TIMESTAMP_NTZ) "
                + "RETURNS VARCHAR AS '''x'''");
        answer(
            "SELECT GET_DDL('FUNCTION', 'nf_e22(TIMESTAMP_NTZ)')");
        assertAnswer(
            "Function NF_E23 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e23(p VARCHAR DEFAULT 'a'::VARCHAR(5)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E23\"(\"P\" VARCHAR DEFAULT 'a') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e23(VARCHAR)')");
        assertAnswer(
            "Function NF_E24 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e24(p NUMBER DEFAULT 1::NUMBER(10,2)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E24\"(\"P\" NUMBER(38,0) DEFAULT CAST(1 AS NUMBER(10,2))) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e24(NUMBER)')");
        assertAnswer(
            "Function NF_E25 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e25(p NUMBER DEFAULT abs(-1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E25\"(\"P\" NUMBER(38,0) DEFAULT ABS(-1)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e25(NUMBER)')");
        assertAnswer(
            "Function NF_E26 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e26(p VARCHAR DEFAULT current_user) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E26\"(\"P\" VARCHAR DEFAULT CURRENT_USER()) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e26(VARCHAR)')");
        assertAnswer(
            "Function NF_E27 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e27(p NUMBER DEFAULT 1 * (2 + 3)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "Function NF_E29 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e29(p VARIANT DEFAULT PARSE_JSON('1')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E29\"(\"P\" VARIANT DEFAULT PARSE_JSON('1')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e29(VARIANT)')");
        assertAnswer(
            "Function NF_E31 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e31(p VARCHAR DEFAULT TRY_CAST('1' AS VARCHAR)) RETURNS VARCHAR AS "
                + "'''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E31\"(\"P\" VARCHAR DEFAULT '1') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e31(VARCHAR)')");
        assertAnswer(
            "Function NF_E32 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e32(p NUMBER DEFAULT '5') RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E32\"(\"P\" NUMBER(38,0) DEFAULT '5') | RETURNS VARCHAR | LANGUAGE "
                + "SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e32(NUMBER)')");
        assertAnswer(
            "Function NF_E33 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e33(p VARCHAR DEFAULT 1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E33\"(\"P\" VARCHAR DEFAULT 1) | RETURNS VARCHAR | LANGUAGE SQL | AS "
                + "'''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e33(VARCHAR)')");
        assertAnswer(
            "Function NF_E34 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e34(p VARCHAR DEFAULT CURRENT_USER()) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E34\"(\"P\" VARCHAR DEFAULT CURRENT_USER()) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e34(VARCHAR)')");
        assertAnswer(
            "Function NF_E35 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e35(p TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP) RETURNS VARCHAR AS "
                + "'''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E35\"(\"P\" TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP()) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e35(TIMESTAMP_LTZ)')");
        assertAnswer(
            "Function NF_E36 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e36(p TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP()) RETURNS VARCHAR AS "
                + "'''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E36\"(\"P\" TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP()) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e36(TIMESTAMP_LTZ)')");
        assertAnswer(
            "Function NF_E37 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e37(p TIME DEFAULT CURRENT_TIME) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E37\"(\"P\" TIME(9) DEFAULT CURRENT_TIME()) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e37(TIME)')");
        assertAnswer(
            "Function NF_E38 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e38(p NUMBER DEFAULT COALESCE(NULL, 1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E38\"(\"P\" NUMBER(38,0) DEFAULT IFNULL(SYSTEM$NULL_TO_FIXED(null), "
                + "1)) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e38(NUMBER)')");
        assertAnswer(
            "Function NF_E39 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e39(p NUMBER DEFAULT NVL(NULL, 1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E39\"(\"P\" NUMBER(38,0) DEFAULT NVL(SYSTEM$NULL_TO_FIXED(null), 1)) "
                + "| RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e39(NUMBER)')");
        assertAnswer(
            "Function NF_E40 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e40(p VARCHAR DEFAULT TO_VARCHAR(1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "Function NF_E41 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e41(p VARCHAR DEFAULT TO_CHAR(1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "Function NF_E42 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e42(p NUMBER DEFAULT TO_NUMBER('1')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E42\"(\"P\" NUMBER(38,0) DEFAULT TO_NUMBER('1')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e42(NUMBER)')");
        assertAnswer(
            "Function NF_E44 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e44(p NUMBER DEFAULT 1 - -1) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E44\"(\"P\" NUMBER(38,0) DEFAULT 1 - -1) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e44(NUMBER)')");
        assertAnswer(
            "Function NF_E45 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e45(p VARCHAR DEFAULT CONCAT('a', 'b')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E45\"(\"P\" VARCHAR DEFAULT CONCAT('a', 'b')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e45(VARCHAR)')");
        assertAnswer(
            "Function NF_E46 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e46(p ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E46\"(\"P\" ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e46(ARRAY)')");
        assertAnswer(
            "Function NF_E47 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e47(p FLOAT DEFAULT 1.5::FLOAT) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E47\"(\"P\" FLOAT DEFAULT CAST(1.5 AS FLOAT)) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e47(FLOAT)')");
        assertAnswer(
            "Function NF_E48 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e48(p NUMBER DEFAULT LENGTH('abc')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E48\"(\"P\" NUMBER(38,0) DEFAULT LENGTH('abc')) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e48(NUMBER)')");
        assertAnswer(
            "Function NF_E49 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e49(p DATE DEFAULT DATEADD(day, 1, '2020-01-01'::DATE)) RETURNS "
                + "VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E49\"(\"P\" DATE DEFAULT DATE_ADDDAYSTODATE(1, CAST('2020-01-01' AS "
                + "DATE))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e49(DATE)')");
        assertAnswer(
            "Function NF_E53 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e53(p VARCHAR DEFAULT CASE 1 WHEN 1 THEN 'a' END) RETURNS VARCHAR AS "
                + "'''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E53\"(\"P\" VARCHAR DEFAULT CASE 1 WHEN 1 THEN 'a' ELSE "
                + "SYSTEM$NULL_TO_TEXT(null) END) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e53(VARCHAR)')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 51 | invalid identifier 'X'",
            "CREATE OR REPLACE FUNCTION nf_e54(p NUMBER DEFAULT x) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'nf_e54(NUMBER)' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'nf_e54(NUMBER)')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "CREATE OR REPLACE FUNCTION nf_e55(p NUMBER DEFAULT nosuchfn(1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'nf_e55(NUMBER)' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'nf_e55(NUMBER)')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 52 | invalid default argument expression "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION nf_e56(p NUMBER DEFAULT (SELECT 1)) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'nf_e56(NUMBER)' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'nf_e56(NUMBER)')");
        assertAnswer(
            "Function NF_E57 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e57(p NUMBER DEFAULT RANDOM()) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E57\"(\"P\" NUMBER(38,0) DEFAULT RANDOM()) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e57(NUMBER)')");
        assertAnswer(
            "Function NF_E58 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e58(p VARCHAR DEFAULT 'a'::TEXT) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E58\"(\"P\" VARCHAR DEFAULT 'a') | RETURNS VARCHAR | LANGUAGE SQL | "
                + "AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e58(VARCHAR)')");
        assertAnswer(
            "Function NF_E59 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e59(p NUMBER DEFAULT 1::NUMBER) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E59\"(\"P\" NUMBER(38,0) DEFAULT CAST(1 AS NUMBER(38,0))) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e59(NUMBER)')");
        assertAnswer(
            "Function NF_E60 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e60(p BINARY DEFAULT TO_BINARY('ab', 'HEX')) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E60\"(\"P\" BINARY DEFAULT TO_BINARY('ab', 'HEX')) | RETURNS VARCHAR "
                + "| LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e60(BINARY)')");
        assertAnswer(
            "Function NF_E61 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e61(p TIMESTAMP_TZ DEFAULT '2020-01-01 00:00:00 +01:00'::TIMESTAMP_TZ) "
                + "RETURNS VARCHAR AS '''x'''");
        answer(
            "SELECT GET_DDL('FUNCTION', 'nf_e61(TIMESTAMP_TZ)')");
        assertAnswer(
            "Function NF_E62 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e62(p VARCHAR DEFAULT 1 || 2) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E62\"(\"P\" VARCHAR DEFAULT (CAST(1 AS VARCHAR(134217728))) || "
                + "(CAST(2 AS VARCHAR(134217728)))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e62(VARCHAR)')");
        assertAnswer(
            "Function NF_E63 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e63(p NUMBER DEFAULT 10 / 4) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E63\"(\"P\" NUMBER(38,0) DEFAULT (CAST(10 AS NUMBER(8,6))) / 4) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e63(NUMBER)')");
        assertAnswer(
            "Function NF_E64 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e64(p NUMBER DEFAULT 10 % 4) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E64\"(\"P\" NUMBER(38,0) DEFAULT 10 % 4) | RETURNS VARCHAR | "
                + "LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e64(NUMBER)')");
        assertAnswer(
            "Function NF_E65 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_e65(p VARCHAR DEFAULT NULL || NULL) RETURNS VARCHAR AS '''x'''");
        assertAnswer(
            "CREATE OR REPLACE FUNCTION \"NF_E65\"(\"P\" VARCHAR DEFAULT (SYSTEM$NULL_TO_TEXT(null)) || "
                + "(SYSTEM$NULL_TO_TEXT(null))) | RETURNS VARCHAR | LANGUAGE SQL | AS '''x''';",
            "SELECT GET_DDL('FUNCTION', 'nf_e65(VARCHAR)')");
        assertAnswer(
            "Function NF_EP1 successfully created.",
            "CREATE OR REPLACE PROCEDURE nf_ep1(a NUMBER DEFAULT 1 + 1, b VARCHAR DEFAULT UPPER('q'), c DATE "
                + "DEFAULT CURRENT_DATE) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "CREATE OR REPLACE PROCEDURE \"NF_EP1\"(\"A\" NUMBER(38,0) DEFAULT 1 + 1, \"B\" VARCHAR DEFAULT "
                + "UPPER('q'), \"C\" DATE DEFAULT CURRENT_DATE()) | RETURNS VARCHAR | LANGUAGE SQL | EXECUTE AS "
                + "OWNER | AS 'BEGIN RETURN 1; END';",
            "SELECT GET_DDL('PROCEDURE', 'nf_ep1(NUMBER, VARCHAR, DATE)')");
        assertAnswer(
            "signature|(A NUMBER, B VARCHAR, C DATE) ;; returns|VARCHAR ;; language|SQL ;; execute as|OWNER ;; "
                + "body|BEGIN RETURN 1; END",
            "DESCRIBE PROCEDURE nf_ep1(NUMBER, VARCHAR, DATE)");
        assertAnswer(
            "NF_EP1(DEFAULT NUMBER, DEFAULT VARCHAR, DEFAULT DATE) RETURN VARCHAR",
            "SHOW PROCEDURES LIKE 'NF_EP1' ->> SELECT \"arguments\" FROM $1");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|VARCHAR ;; language|SQL ;; body|'x'",
            "DESCRIBE FUNCTION nf_e2(NUMBER)");
        assertAnswer(
            "(P NUMBER)",
            "SELECT argument_signature FROM information_schema.functions WHERE function_name = 'NF_E2'");
    }
}
