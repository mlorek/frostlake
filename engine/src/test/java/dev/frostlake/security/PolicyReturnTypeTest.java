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

package dev.frostlake.security;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A policy's body is judged against the RETURNS it declares before anything else about the policy: a body of
 * another type is refused in a SQL function's words, then a masking policy whose argument and return types
 * disagree, and a row access policy declaring another type than BOOLEAN, by name. IF NOT EXISTS finds the name
 * taken only once the statement compiled, and OR REPLACE drops the policy it replaces only then, so a statement
 * refused leaves it as it was (all live-verified).
 */
public class PolicyReturnTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aMaskingPolicysBodyIsJudgedAgainstItsReturnType() {
        assertAnswer("REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type "
                + "'VARCHAR(134217728)'",
            "CREATE OR REPLACE MASKING POLICY pm_fresh AS (v VARCHAR) RETURNS NUMBER -> v");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'NUMBER(38,0)'",
            "CREATE OR REPLACE MASKING POLICY pm_fresh2 AS (v NUMBER) RETURNS VARCHAR -> v");
        assertAnswer("Masking policy PM_FRESH3 successfully created.",
            "CREATE OR REPLACE MASKING POLICY pm_fresh3 AS (v NUMBER(10,2)) RETURNS NUMBER -> v");
        assertAnswer("Masking policy PM_FRESH4 successfully created.",
            "CREATE OR REPLACE MASKING POLICY pm_fresh4 AS (v VARCHAR(10)) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED Declared return type 'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'DATE'",
            "CREATE OR REPLACE MASKING POLICY pm_fresh5 AS (v DATE) RETURNS TIMESTAMP -> v");
        assertAnswer("REFUSED SQL compilation error: Masking policy function argument and return type mismatch.",
            "CREATE OR REPLACE MASKING POLICY pm_fresh6 AS (v VARCHAR) RETURNS NUMBER -> 1");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_fresh AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'NUMBER(38,0)' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_fresh2 AS (v VARCHAR) RETURNS NUMBER -> 1");
        assertAnswer("REFUSED Declared return type 'BOOLEAN' is incompatible with actual return type 'NUMBER(1,0)'",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_fresh3 AS (v VARCHAR) RETURNS BOOLEAN -> 1");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type 'BOOLEAN'",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_fresh4 AS (v VARCHAR) RETURNS VARIANT -> TRUE");
        assertAnswer("Drop statement executed successfully (PM_FRESH6 already dropped).",
            "DROP MASKING POLICY IF EXISTS pm_fresh6");
    }

    @Test
    public void aRowAccessPolicyMayDeclareAnyTypeAndIsRefusedForItByName() {
        assertAnswer("Row access policy 'PR_A' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_a AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR ALTER ROW ACCESS POLICY pr_a AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR ALTER ROW ACCESS POLICY pr_new AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS STRING -> 'x'");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(10)' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS VARCHAR(10) -> v");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'NUMBER(10,2)' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS NUMBER(10,2) -> 1");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'DATE' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS DATE -> CURRENT_DATE()");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'FLOAT' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS FLOAT -> 1.5::FLOAT");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARIANT' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS VARIANT -> v::VARIANT");
        assertAnswer("REFUSED Declared return type 'TIMESTAMP_NTZ(9)' is incompatible with actual return type "
                + "'TIMESTAMP_LTZ(9)'",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS TIMESTAMP -> CURRENT_TIMESTAMP()");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS NOSUCHTYPE -> TRUE");
        assertAnswer("Row access policy 'PR_B' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS BOOLEAN -> v = 'x'");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE ROW ACCESS POLICY IF NOT EXISTS pr_a AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS VARCHAR -> nosuchcol");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'ARRAY' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS ARRAY -> ARRAY_CONSTRUCT(v)");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'BINARY(67108864)' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS BINARY -> TO_BINARY('AB', 'HEX')");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'NUMBER(38,0)' is not BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_b AS (v VARCHAR) RETURNS NUMBER -> 1.5");
        assertAnswer("REFUSED Declared return type 'BOOLEAN' is incompatible with actual return type 'NUMBER(1,0)'",
            "ALTER ROW ACCESS POLICY pr_a SET BODY -> 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'PR_A' already exists.",
            "CREATE ROW ACCESS POLICY pr_a AS (v VARCHAR) RETURNS VARCHAR -> v");
    }

    @Test
    public void aMaskingPolicyIsJudgedSoInEveryCreateForm() {
        assertAnswer("Masking policy MP_A successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_a AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type "
                + "'VARCHAR(134217728)'",
            "CREATE OR ALTER MASKING POLICY mp_new AS (v VARCHAR) RETURNS NUMBER -> v");
        assertAnswer("REFUSED SQL compilation error: Masking policy function argument and return type mismatch.",
            "CREATE OR ALTER MASKING POLICY mp_new2 AS (v VARCHAR) RETURNS NUMBER -> 1");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'NUMBER(1,0)'",
            "CREATE OR ALTER MASKING POLICY mp_a AS (v VARCHAR) RETURNS VARCHAR -> 1");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'NUMBER(1,0)'",
            "ALTER MASKING POLICY mp_a SET BODY -> 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v VARCHAR) RETURNS NUMBER -> nosuchcol");
        assertAnswer("REFUSED Declared return type 'FLOAT' is incompatible with actual return type 'NUMBER(38,0)'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v NUMBER) RETURNS FLOAT -> v");
        assertAnswer("REFUSED Declared return type 'TIMESTAMP_NTZ(9)' is incompatible with actual return type "
                + "'TIMESTAMP_LTZ(9)'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v TIMESTAMP_LTZ) RETURNS TIMESTAMP_NTZ -> v");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'VARIANT'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v VARIANT) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type "
                + "'VARCHAR(134217728)'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v VARCHAR) RETURNS VARIANT -> v");
        assertAnswer("REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'VARCHAR(1)'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v NUMBER) RETURNS NUMBER -> 'x'");
        assertAnswer("REFUSED Declared return type 'DATE' is incompatible with actual return type 'TIMESTAMP_LTZ(9)'",
            "CREATE OR REPLACE MASKING POLICY mp_b AS (v DATE) RETURNS DATE -> CURRENT_TIMESTAMP()");
    }

    @Test
    public void theStatementCompilesBeforeTheNameIsJudged() {
        assertAnswer("Row access policy 'PR_I' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_i AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE ROW ACCESS POLICY IF NOT EXISTS pr_i AS (v VARCHAR) RETURNS BOOLEAN -> nosuchcol");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type 'BOOLEAN'",
            "CREATE ROW ACCESS POLICY IF NOT EXISTS pr_i AS (v VARCHAR) RETURNS VARIANT -> TRUE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE ROW ACCESS POLICY IF NOT EXISTS pr_i AS (v NOSUCHTYPE) RETURNS BOOLEAN -> TRUE");
        assertAnswer("REFUSED SQL compilation error: | Object 'PR_I' already exists.",
            "CREATE ROW ACCESS POLICY pr_i AS (v VARCHAR) RETURNS VARIANT -> TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_i AS (v VARCHAR) RETURNS BOOLEAN -> nosuchcol");
        assertAnswer("PR_I|(V VARCHAR)|BOOLEAN|TRUE", "DESCRIBE ROW ACCESS POLICY pr_i");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY pr_i AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("PR_I|(V VARCHAR)|BOOLEAN|TRUE", "DESCRIBE ROW ACCESS POLICY pr_i");
        assertAnswer("Masking policy MP_I successfully created.",
            "CREATE OR REPLACE MASKING POLICY mp_i AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type "
                + "'VARCHAR(134217728)'",
            "CREATE MASKING POLICY IF NOT EXISTS mp_i AS (v VARCHAR) RETURNS NUMBER -> v");
        assertAnswer("REFUSED SQL compilation error: Masking policy function argument and return type mismatch.",
            "CREATE MASKING POLICY IF NOT EXISTS mp_i AS (v VARCHAR) RETURNS NUMBER -> 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE MASKING POLICY IF NOT EXISTS mp_i AS (v VARCHAR) RETURNS VARCHAR -> nosuchcol");
        assertAnswer("REFUSED SQL compilation error: | Object 'MP_I' already exists.",
            "CREATE MASKING POLICY mp_i AS (v VARCHAR) RETURNS NUMBER -> v");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier 'NOSUCHCOL'",
            "CREATE OR REPLACE MASKING POLICY mp_i AS (v VARCHAR) RETURNS VARCHAR -> nosuchcol");
        assertAnswer("MP_I|(V VARCHAR)|VARCHAR|v", "DESCRIBE MASKING POLICY mp_i");
        assertAnswer("REFUSED SQL compilation error: Masking policy function argument and return type mismatch.",
            "CREATE OR REPLACE MASKING POLICY mp_i AS (v VARCHAR) RETURNS NUMBER -> 1");
        assertAnswer("MP_I|(V VARCHAR)|VARCHAR|v", "DESCRIBE MASKING POLICY mp_i");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE ROW ACCESS POLICY IF NOT EXISTS mp_i AS (v VARCHAR) RETURNS VARCHAR -> v");
        assertAnswer("REFUSED SQL compilation error: Row access policy return type 'VARCHAR(134217728)' is not "
                + "BOOLEAN.",
            "CREATE OR REPLACE ROW ACCESS POLICY mp_i AS (v VARCHAR) RETURNS VARCHAR -> v");
    }
}
