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
 * A routine is named by the signature a statement writes after its name - DESCRIBE, DROP, ALTER, COMMENT ON and
 * GET_DDL alike, a function's and a procedure's. A type written bare names a parameter of any width of its
 * family; a type written with a length or digits names only a parameter declared with the same, the family's
 * default included: {@code f(VARCHAR(16777216))} is no name for a parameter declared {@code VARCHAR}, which is
 * as wide as a text gets and is named by {@code VARCHAR(134217728)} alone, as a bare BINARY is by
 * {@code BINARY(67108864)}. A number is the one family whose default written out, NUMBER(38,0), reads as
 * written bare. Every answer is live-verified.
 */
public class WrittenSignatureWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void aWrittenWidthNamesAParameterDeclaredWithIt() {
        answer(
            "CREATE OR REPLACE FUNCTION sv0(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv1(p VARCHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv2(p VARCHAR(16777216)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv3(p STRING) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv4(p TEXT) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv5(p CHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv6(p VARCHAR(134217728)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv7(p CHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sv8(p STRING(16777216)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn0(p NUMBER) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn1(p NUMBER(38,0)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn2(p NUMBER(5,2)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn3(p INT) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn4(p NUMBER(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sn5(p DECIMAL(38,0)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sb0(p BINARY) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sb1(p BINARY(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sb2(p BINARY(8388608)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sb3(p VARBINARY) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION st0(p TIMESTAMP_NTZ) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION st1(p TIMESTAMP_NTZ(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION st2(p TIMESTAMP_NTZ(9)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION st3(p TIMESTAMP_LTZ(9)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION st4(p TIMESTAMP_TZ) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sm0(p TIME) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sm1(p TIME(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sm2(p TIME(9)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION sf0(p FLOAT) RETURNS INT AS '1'");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv0(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(VARCHAR(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv0(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv0(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv0(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV0.",
            "DESCRIBE FUNCTION sv0(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(VARCHAR)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(CHAR)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(CHAR(10))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv1(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV1.",
            "DESCRIBE FUNCTION sv1(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv2(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(VARCHAR(5))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv2(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv2(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv2(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV2.",
            "DESCRIBE FUNCTION sv2(VARCHAR(1))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv2(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv3(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(VARCHAR(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv3(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv3(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv3(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV3.",
            "DESCRIBE FUNCTION sv3(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv4(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(VARCHAR(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv4(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv4(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv4(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV4.",
            "DESCRIBE FUNCTION sv4(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv5(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv5(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv5(TEXT)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv5(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(STRING(10))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv5(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV5.",
            "DESCRIBE FUNCTION sv5(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv6(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(VARCHAR(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv6(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv6(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv6(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV6' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV6.",
            "DESCRIBE FUNCTION sv6(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(VARCHAR)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(CHAR)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(CHAR(10))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv7(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(VARCHAR(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV7' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV7.",
            "DESCRIBE FUNCTION sv7(STRING(16777216))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv8(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(VARCHAR(5))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv8(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(VARCHAR(134217728))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv8(STRING)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv8(TEXT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(CHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(CHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(STRING(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SV8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SV8.",
            "DESCRIBE FUNCTION sv8(VARCHAR(1))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sv8(STRING(16777216))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(NUMBER(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN0.",
            "DESCRIBE FUNCTION sn0(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn0(BIGINT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(NUMBER(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN1.",
            "DESCRIBE FUNCTION sn1(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn1(BIGINT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(NUMBER(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN2.",
            "DESCRIBE FUNCTION sn2(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN2.",
            "DESCRIBE FUNCTION sn2(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(INT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN2.",
            "DESCRIBE FUNCTION sn2(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN2.",
            "DESCRIBE FUNCTION sn2(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn2(BIGINT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(NUMBER(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN3.",
            "DESCRIBE FUNCTION sn3(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn3(BIGINT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMBER(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN4.",
            "DESCRIBE FUNCTION sn4(NUMBER(5,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN4.",
            "DESCRIBE FUNCTION sn4(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN4.",
            "DESCRIBE FUNCTION sn4(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN4.",
            "DESCRIBE FUNCTION sn4(FLOAT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn4(BIGINT)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(NUMBER)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(NUMBER(38,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(NUMBER(38,2))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(DECIMAL(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SN5' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SN5.",
            "DESCRIBE FUNCTION sn5(NUMBER(10))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(NUMBER(38))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(DECIMAL)");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(NUMERIC(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sn5(BIGINT)");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb0(BINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB0.",
            "DESCRIBE FUNCTION sb0(BINARY(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB0.",
            "DESCRIBE FUNCTION sb0(BINARY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB0.",
            "DESCRIBE FUNCTION sb0(BINARY(8388608))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb0(VARBINARY)");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb0(BINARY(67108864))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb1(BINARY)");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb1(BINARY(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB1.",
            "DESCRIBE FUNCTION sb1(BINARY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB1.",
            "DESCRIBE FUNCTION sb1(BINARY(8388608))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb1(VARBINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB1.",
            "DESCRIBE FUNCTION sb1(BINARY(67108864))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb2(BINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB2.",
            "DESCRIBE FUNCTION sb2(BINARY(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB2.",
            "DESCRIBE FUNCTION sb2(BINARY(5))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb2(BINARY(8388608))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb2(VARBINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB2.",
            "DESCRIBE FUNCTION sb2(BINARY(67108864))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb3(BINARY)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB3.",
            "DESCRIBE FUNCTION sb3(BINARY(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB3.",
            "DESCRIBE FUNCTION sb3(BINARY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SB3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SB3.",
            "DESCRIBE FUNCTION sb3(BINARY(8388608))");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb3(VARBINARY)");
        assertAnswer(
            "signature|(P BINARY) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sb3(BINARY(67108864))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_NTZ(3))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_NTZ(0))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(DATETIME)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(TIMESTAMP)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(TIMESTAMP(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_LTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST0.",
            "DESCRIBE FUNCTION st0(TIMESTAMP_TZ(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st0(DATETIME(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st1(TIMESTAMP_NTZ)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st1(TIMESTAMP_NTZ(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_NTZ(0))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st1(DATETIME)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st1(TIMESTAMP)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_LTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(TIMESTAMP_TZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST1.",
            "DESCRIBE FUNCTION st1(DATETIME(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_NTZ(3))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_NTZ(0))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(DATETIME)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(TIMESTAMP)");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(TIMESTAMP(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_LTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST2.",
            "DESCRIBE FUNCTION st2(TIMESTAMP_TZ(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_NTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st2(DATETIME(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_NTZ(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_NTZ(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(DATETIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_LTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st3(TIMESTAMP_LTZ)");
        assertAnswer(
            "signature|(P TIMESTAMP_LTZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st3(TIMESTAMP_LTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(TIMESTAMP_TZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST3.",
            "DESCRIBE FUNCTION st3(DATETIME(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_NTZ(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_NTZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_NTZ(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(DATETIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_LTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(TIMESTAMP_LTZ(9))");
        assertAnswer(
            "signature|(P TIMESTAMP_TZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st4(TIMESTAMP_TZ)");
        assertAnswer(
            "signature|(P TIMESTAMP_TZ) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION st4(TIMESTAMP_TZ(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.ST4' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.ST4.",
            "DESCRIBE FUNCTION st4(DATETIME(9))");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm0(TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM0.",
            "DESCRIBE FUNCTION sm0(TIME(3))");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm0(TIME(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM0.",
            "DESCRIBE FUNCTION sm0(TIME(0))");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm1(TIME)");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm1(TIME(3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM1.",
            "DESCRIBE FUNCTION sm1(TIME(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM1.",
            "DESCRIBE FUNCTION sm1(TIME(0))");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm2(TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM2.",
            "DESCRIBE FUNCTION sm2(TIME(3))");
        assertAnswer(
            "signature|(P TIME) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sm2(TIME(9))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SM2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SM2.",
            "DESCRIBE FUNCTION sm2(TIME(0))");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(FLOAT)");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(DOUBLE)");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(REAL)");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(FLOAT4)");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(FLOAT8)");
        assertAnswer(
            "signature|(P FLOAT) ;; returns|NUMBER(38,0) ;; language|SQL ;; body|1",
            "DESCRIBE FUNCTION sf0(DOUBLE PRECISION)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.SF0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.SF0.",
            "DESCRIBE FUNCTION sf0(NUMBER)");
    }

    @Test
    public void everyStatementThatWritesASignatureReadsItAlike() {
        answer(
            "CREATE OR REPLACE FUNCTION dv00(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv01(p VARCHAR(16777216)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv02(p VARCHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv03(p VARCHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv04(p VARCHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv05(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv06(p VARCHAR(16777216)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv07(p VARCHAR(16777216)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv08(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv09(p VARCHAR(134217728)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv10(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv11(p VARCHAR(134217728)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv12(p NUMBER(5,2)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv13(p NUMBER(5,2)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv14(p NUMBER(5,2)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv15(p NUMBER(5,2)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv16(p NUMBER) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv17(p NUMBER) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv18(p NUMBER(38,0)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv19(p INT) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv20(p INT) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv21(p BINARY) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv22(p BINARY(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv23(p BINARY(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv24(p BINARY) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv25(p TIMESTAMP_NTZ) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv26(p TIMESTAMP_NTZ(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv27(p TIMESTAMP_NTZ(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv28(p TIMESTAMP_NTZ) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv29(p TIME) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv30(p TIME(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION dv31(p TIME(3)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION av0(p VARCHAR) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION av1(p VARCHAR(10)) RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE PROCEDURE pv0(p VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        answer(
            "CREATE OR REPLACE PROCEDURE pv1(p VARCHAR(10)) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        answer(
            "CREATE OR REPLACE PROCEDURE pn2(p NUMBER(5,2)) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV00' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV00.",
            "DROP FUNCTION dv00(VARCHAR(16777216))");
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
            "SHOW USER FUNCTIONS LIKE 'DV00' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV01 successfully dropped.",
            "DROP FUNCTION dv01(VARCHAR(16777216))");
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
            "SHOW USER FUNCTIONS LIKE 'DV01' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV02 successfully dropped.",
            "DROP FUNCTION dv02(VARCHAR(10))");
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
            "SHOW USER FUNCTIONS LIKE 'DV02' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV03' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV03.",
            "DROP FUNCTION dv03(VARCHAR(5))");
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
            "SHOW USER FUNCTIONS LIKE 'DV03' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV04 successfully dropped.",
            "DROP FUNCTION dv04(VARCHAR)");
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
            "SHOW USER FUNCTIONS LIKE 'DV04' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV05' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV05.",
            "DROP FUNCTION dv05(VARCHAR(10))");
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
            "SHOW USER FUNCTIONS LIKE 'DV05' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV06 successfully dropped.",
            "DROP FUNCTION dv06(VARCHAR)");
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
            "SHOW USER FUNCTIONS LIKE 'DV06' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV07' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV07.",
            "DROP FUNCTION dv07(VARCHAR(10))");
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
            "SHOW USER FUNCTIONS LIKE 'DV07' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV08 successfully dropped.",
            "DROP FUNCTION dv08(VARCHAR)");
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
            "SHOW USER FUNCTIONS LIKE 'DV08' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV09 successfully dropped.",
            "DROP FUNCTION dv09(VARCHAR(134217728))");
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
            "SHOW USER FUNCTIONS LIKE 'DV09' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV10 successfully dropped.",
            "DROP FUNCTION dv10(VARCHAR(134217728))");
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
            "SHOW USER FUNCTIONS LIKE 'DV10' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV11' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV11.",
            "DROP FUNCTION dv11(VARCHAR(16777216))");
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
            "SHOW USER FUNCTIONS LIKE 'DV11' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV12 successfully dropped.",
            "DROP FUNCTION dv12(NUMBER(38,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV12' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV13 successfully dropped.",
            "DROP FUNCTION dv13(NUMBER(5,2))");
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
            "SHOW USER FUNCTIONS LIKE 'DV13' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV14' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV14.",
            "DROP FUNCTION dv14(NUMBER(10,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV14' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV15 successfully dropped.",
            "DROP FUNCTION dv15(NUMBER)");
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
            "SHOW USER FUNCTIONS LIKE 'DV15' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV16 successfully dropped.",
            "DROP FUNCTION dv16(NUMBER(38,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV16' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV17' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV17.",
            "DROP FUNCTION dv17(NUMBER(5,2))");
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
            "SHOW USER FUNCTIONS LIKE 'DV17' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV18 successfully dropped.",
            "DROP FUNCTION dv18(NUMBER(38,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV18' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV19 successfully dropped.",
            "DROP FUNCTION dv19(NUMBER(38,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV19' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV20' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV20.",
            "DROP FUNCTION dv20(NUMBER(10,0))");
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
            "SHOW USER FUNCTIONS LIKE 'DV20' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV21' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV21.",
            "DROP FUNCTION dv21(BINARY(8388608))");
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
            "SHOW USER FUNCTIONS LIKE 'DV21' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV22 successfully dropped.",
            "DROP FUNCTION dv22(BINARY(10))");
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
            "SHOW USER FUNCTIONS LIKE 'DV22' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV23' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV23.",
            "DROP FUNCTION dv23(BINARY(5))");
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
            "SHOW USER FUNCTIONS LIKE 'DV23' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV24' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV24.",
            "DROP FUNCTION dv24(BINARY(10))");
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
            "SHOW USER FUNCTIONS LIKE 'DV24' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV25 successfully dropped.",
            "DROP FUNCTION dv25(TIMESTAMP_NTZ(9))");
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
            "SHOW USER FUNCTIONS LIKE 'DV25' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV26' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV26.",
            "DROP FUNCTION dv26(TIMESTAMP_NTZ(9))");
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
            "SHOW USER FUNCTIONS LIKE 'DV26' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV27 successfully dropped.",
            "DROP FUNCTION dv27(TIMESTAMP_NTZ(3))");
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
            "SHOW USER FUNCTIONS LIKE 'DV27' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV28' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV28.",
            "DROP FUNCTION dv28(TIMESTAMP_NTZ(3))");
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
            "SHOW USER FUNCTIONS LIKE 'DV28' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV29 successfully dropped.",
            "DROP FUNCTION dv29(TIME(9))");
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
            "SHOW USER FUNCTIONS LIKE 'DV29' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.DV30' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.DV30.",
            "DROP FUNCTION dv30(TIME(9))");
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
            "SHOW USER FUNCTIONS LIKE 'DV30' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "DV31 successfully dropped.",
            "DROP FUNCTION dv31(TIME(3))");
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
            "SHOW USER FUNCTIONS LIKE 'DV31' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV0.",
            "ALTER FUNCTION av0(VARCHAR(16777216)) SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV0.",
            "ALTER FUNCTION av0(VARCHAR(10)) SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FUNCTION av0(VARCHAR) SET COMMENT = 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER FUNCTION av1(VARCHAR(10)) SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV1.",
            "ALTER FUNCTION av1(VARCHAR(5)) SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV1.",
            "ALTER FUNCTION av1(VARCHAR(16777216)) SET COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV0.",
            "COMMENT ON FUNCTION av0(VARCHAR(16777216)) IS 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.AV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.AV1.",
            "COMMENT ON FUNCTION av1(VARCHAR(5)) IS 'c'");
        assertAnswer(
            "Statement executed successfully.",
            "COMMENT ON FUNCTION av1(VARCHAR(10)) IS 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'av0(VARCHAR(16777216))' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'av0(VARCHAR(16777216))') IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'av0(VARCHAR(10))' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'av0(VARCHAR(10))') IS NOT NULL");
        assertAnswer(
            "true",
            "SELECT GET_DDL('FUNCTION', 'av0(VARCHAR)') IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'av1(VARCHAR(5))' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'av1(VARCHAR(5))') IS NOT NULL");
        assertAnswer(
            "true",
            "SELECT GET_DDL('FUNCTION', 'av1(VARCHAR(10))') IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'av1(VARCHAR(16777216))' does not exist or not authorized.",
            "SELECT GET_DDL('FUNCTION', 'av1(VARCHAR(16777216))') IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV0.",
            "DESCRIBE PROCEDURE pv0(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV0.",
            "DESCRIBE PROCEDURE pv0(VARCHAR(10))");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                + "RETURN 1; END;",
            "DESCRIBE PROCEDURE pv0(VARCHAR)");
        assertAnswer(
            "signature|(P VARCHAR) ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                + "RETURN 1; END;",
            "DESCRIBE PROCEDURE pv1(VARCHAR(10))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV1.",
            "DESCRIBE PROCEDURE pv1(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV1.",
            "DESCRIBE PROCEDURE pv1(VARCHAR(16777216))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                + "RETURN 1; END;",
            "DESCRIBE PROCEDURE pn2(NUMBER(38,0))");
        assertAnswer(
            "signature|(P NUMBER) ;; returns|NUMBER(38,0) ;; language|SQL ;; execute as|OWNER ;; body|BEGIN "
                + "RETURN 1; END;",
            "DESCRIBE PROCEDURE pn2(NUMBER(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PN2' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PN2.",
            "DESCRIBE PROCEDURE pn2(NUMBER(10,0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV0' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV0.",
            "DROP PROCEDURE pv0(VARCHAR(16777216))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV1.",
            "DROP PROCEDURE pv1(VARCHAR(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Procedure 'TEST_DB.TEST_SCHEMA.PV1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on PROCEDURE "
                + "TEST_DB.TEST_SCHEMA.PV1.",
            "DROP PROCEDURE pv1(VARCHAR(16777216))");
        assertAnswer(
            "PN2 successfully dropped.",
            "DROP PROCEDURE pn2(NUMBER(38,0))");
        assertAnswer(
            "Drop statement executed successfully (PV0 already dropped).",
            "DROP PROCEDURE IF EXISTS pv0(VARCHAR(16777216))");
        assertAnswer(
            "Drop statement executed successfully (AV0 already dropped).",
            "DROP FUNCTION IF EXISTS av0(VARCHAR(16777216))");
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
            "SHOW USER FUNCTIONS LIKE 'AV0' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), schema_name:VARCHAR(16777216), "
                + "is_builtin:VARCHAR(16777216), is_aggregate:VARCHAR(16777216), is_ansi:VARCHAR(16777216), "
                + "min_num_arguments:NUMBER(9,0), max_num_arguments:NUMBER(9,0), arguments:VARCHAR(16777216), "
                + "description:VARCHAR(16777216), catalog_name:VARCHAR(16777216), "
                + "is_table_function:VARCHAR(16777216), valid_for_clustering:VARCHAR(16777216), "
                + "is_secure:VARCHAR(16777216), secrets:VARCHAR(16777216), "
                + "external_access_integrations:VARCHAR(16777216)",
            "SHOW PROCEDURES LIKE 'P__' IN SCHEMA test_db.test_schema");
        assertAnswer(
            "2",
            "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
