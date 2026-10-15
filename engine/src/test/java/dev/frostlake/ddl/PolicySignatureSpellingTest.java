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
 * How DESCRIBE spells a policy's signature. A masking or a row access policy's arguments are spelled as a
 * routine's are: a structured type by its shape with bare types inside, NOT NULL kept, a vector with its element
 * type and length - {@code (VAL ARRAY(NUMBER))}, {@code (VAL OBJECT(a NUMBER, b VARCHAR))}, {@code (VAL VECTOR(FLOAT,
 * 3))} - where a plain argument keeps its length and digits. Every answer is live-verified.
 */
public class PolicySignatureSpellingTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicysSignature() {
        assertAnswer(
            "Masking policy MP_V successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_v AS (val VECTOR(FLOAT, 3)) RETURNS VECTOR(FLOAT, 3) -> val");
        assertAnswer(
            "MP_V|(VAL VECTOR(FLOAT, 3))|VECTOR(FLOAT, 3)|val",
            "DESCRIBE MASKING POLICY mp_v");
        assertAnswer(
            "Masking policy MP_A successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_a AS (val ARRAY(NUMBER)) RETURNS ARRAY(NUMBER) -> val");
        assertAnswer(
            "MP_A|(VAL ARRAY(NUMBER))|ARRAY(NUMBER(38,0))|val",
            "DESCRIBE MASKING POLICY mp_a");
        assertAnswer(
            "Masking policy MP_O successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_o AS (val OBJECT(a INT, b VARCHAR)) RETURNS OBJECT(a INT, b "
                + "VARCHAR) -> val");
        assertAnswer(
            "Masking policy MP_M successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_m AS (val MAP(VARCHAR, INT)) RETURNS MAP(VARCHAR, INT) -> val");
        assertAnswer(
            "Masking policy MP_N successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_n AS (val NUMBER(10,2), other VARCHAR(5)) RETURNS NUMBER(10,2) "
                + "-> val");
        assertAnswer(
            "MP_N|(VAL NUMBER, OTHER VARCHAR)|NUMBER(10,2)|val",
            "DESCRIBE MASKING POLICY mp_n");
        assertAnswer(
            "Masking policy MP_I successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_i AS (val VECTOR(INT, 2), o OBJECT(a INT NOT NULL)) RETURNS "
                + "VECTOR(INT, 2) -> val");
        assertAnswer(
            "MP_I|(VAL VECTOR(INT, 2), O OBJECT(a NUMBER NOT NULL))|VECTOR(INT, 2)|val",
            "DESCRIBE MASKING POLICY mp_i");
        assertAnswer(
            "Masking policy MP_P successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_p AS (val ARRAY, o OBJECT, v VARIANT) RETURNS ARRAY -> val");
        assertAnswer(
            "MP_P|(VAL ARRAY, O OBJECT, V VARIANT)|ARRAY|val",
            "DESCRIBE MASKING POLICY mp_p");
        assertAnswer(
            "Masking policy MP_Q successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_q AS (val ARRAY(OBJECT(a ARRAY(VARCHAR(3))))) RETURNS "
                + "ARRAY(OBJECT(a ARRAY(VARCHAR(3)))) -> val");
        assertAnswer(
            "MP_Q|(VAL ARRAY(OBJECT(a ARRAY(VARCHAR))))|ARRAY(OBJECT(a ARRAY(VARCHAR(3))))|val",
            "DESCRIBE MASKING POLICY mp_q");
        assertAnswer(
            "Row access policy 'RP_A' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY rp_a AS (val ARRAY(NUMBER), v VECTOR(FLOAT, 2)) RETURNS BOOLEAN "
                + "-> TRUE");
        assertAnswer(
            "RP_A|(VAL ARRAY(NUMBER), V VECTOR(FLOAT, 2))|BOOLEAN|TRUE",
            "DESCRIBE ROW ACCESS POLICY rp_a");
        assertAnswer(
            "Row access policy 'RP_O' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY rp_o AS (val OBJECT(a INT), n NUMBER(5,1)) RETURNS BOOLEAN -> "
                + "TRUE");
        assertAnswer(
            "RP_O|(VAL OBJECT(a NUMBER), N NUMBER)|BOOLEAN|TRUE",
            "DESCRIBE ROW ACCESS POLICY rp_o");
        assertShape(
            "created_on:TIMESTAMP_LTZ, name:VARCHAR(16777216), database_name:VARCHAR(16777216), "
                + "schema_name:VARCHAR(16777216), kind:VARCHAR(16777216), owner:VARCHAR(16777216), "
                + "comment:VARCHAR(16777216), owner_role_type:VARCHAR(16777216), options:VARCHAR(16777216)",
            "SHOW MASKING POLICIES IN SCHEMA test_db.test_schema");
        assertAnswer(
            "MP_A|MASKING_POLICY ;; MP_I|MASKING_POLICY ;; MP_M|MASKING_POLICY ;; MP_N|MASKING_POLICY ;; "
                + "MP_O|MASKING_POLICY ;; MP_P|MASKING_POLICY ;; MP_Q|MASKING_POLICY ;; MP_V|MASKING_POLICY",
            "SELECT \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_DB.INFORMATION_SCHEMA.POLICY_REFERENCES' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE TEST_DB.INFORMATION_SCHEMA.POLICY_REFERENCES.",
            "SELECT policy_name, policy_signature, policy_return_type FROM information_schema.policy_references "
                + "WHERE FALSE");
        assertAnswer(
            "create or replace masking policy MP_V as (VAL VECTOR(FLOAT, 3))  | returns VECTOR(FLOAT, 3) -> | val "
                + "| ;",
            "SELECT GET_DDL('POLICY', 'mp_v')");
        assertAnswer(
            "create or replace masking policy MP_A as (VAL ARRAY(NUMBER(38,0)))  | returns ARRAY(NUMBER(38,0)) -> "
                + "| val | ;",
            "SELECT GET_DDL('POLICY', 'mp_a')");
    }
}
