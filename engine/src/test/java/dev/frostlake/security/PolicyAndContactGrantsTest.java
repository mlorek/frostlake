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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * GRANT and REVOKE reach a join, an aggregation, a projection, an authentication and a packages policy and a contact
 * by the exact name written, as they reach a masking policy (documented): a policy takes APPLY, a contact APPLY and
 * MODIFY, and another privilege is refused for the kind. A dropped policy's or contact's privileges are not listed, and
 * a new one of its name does not take them; one that replaces another keeps them.
 */
public class PolicyAndContactGrantsTest extends MeasuredCellsTestSupport {

    private static final String ACCOUNT_GRANTS =
        "creates a role and grants to it, which the account this suite is checked against is not altered by";

    private static final String LISTED =
        "SHOW GRANTS TO ROLE pc_r ->> SELECT \"privilege\", \"granted_on\", \"name\" FROM $1 ORDER BY 2, 1";

    @Override
    protected void setupTest() {
        Assumptions.assumeFalse(isLiveSnowflake(), ACCOUNT_GRANTS);
        engine.execute("CREATE ROLE IF NOT EXISTS pc_r");
    }

    @Test
    public void eachKindIsGrantedOnByItsExactName() {
        engine.execute("CREATE OR REPLACE JOIN POLICY \"Jp_M\" AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE CONTACT \"Ct_M\" EMAIL_DISTRIBUTION_LIST = 'a@b.com'");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pc_pp AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY pc_ag AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE OR REPLACE AUTHENTICATION POLICY pc_ap");
        engine.execute("CREATE OR REPLACE PACKAGES POLICY pc_pk LANGUAGE PYTHON");
        assertCells(new String[][] {
            {"GRANT APPLY ON JOIN POLICY \"Jp_M\" TO ROLE pc_r", "Statement executed successfully."},
            {"GRANT APPLY ON JOIN POLICY jp_m TO ROLE pc_r",
                "REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.JP_M' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.JP_M."},
            {"GRANT SELECT ON JOIN POLICY \"Jp_M\" TO ROLE pc_r",
                "REFUSED SQL compilation error: | Invalid object type 'POLICY' for privilege 'SELECT'."},
            {"GRANT APPLY, MODIFY ON CONTACT \"Ct_M\" TO ROLE pc_r", "Statement executed successfully."},
            {"GRANT USAGE ON CONTACT \"Ct_M\" TO ROLE pc_r",
                "REFUSED SQL compilation error: | Invalid object type 'CONTACT' for privilege 'USAGE'."},
            {"GRANT APPLY ON CONTACT ct_m TO ROLE pc_r",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.CT_M' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.CT_M."},
            {"GRANT ALL ON PROJECTION POLICY pc_pp TO ROLE pc_r", "Statement executed successfully."},
            {"GRANT APPLY ON AGGREGATION POLICY pc_ag TO ROLE pc_r", "Statement executed successfully."},
            {"GRANT APPLY ON AUTHENTICATION POLICY pc_ap TO ROLE pc_r", "Statement executed successfully."},
            {"GRANT APPLY ON PACKAGES POLICY pc_pk TO ROLE pc_r", "Statement executed successfully."},
            {LISTED, "APPLY|AGGREGATION_POLICY|TEST_DB.TEST_SCHEMA.PC_AG ;; "
                + "APPLY|AUTHENTICATION_POLICY|TEST_DB.TEST_SCHEMA.PC_AP ;; "
                + "APPLY|CONTACT|TEST_DB.TEST_SCHEMA.\"Ct_M\" ;; MODIFY|CONTACT|TEST_DB.TEST_SCHEMA.\"Ct_M\" ;; "
                + "APPLY|JOIN_POLICY|TEST_DB.TEST_SCHEMA.\"Jp_M\" ;; "
                + "APPLY|PACKAGES_POLICY|TEST_DB.TEST_SCHEMA.PC_PK ;; "
                + "APPLY|PROJECTION_POLICY|TEST_DB.TEST_SCHEMA.PC_PP"},
            {"SHOW GRANTS ON JOIN POLICY \"Jp_M\" ->> SELECT \"privilege\", \"grantee_name\" FROM $1 ORDER BY 1",
                "APPLY|PC_R ;; OWNERSHIP|SYSADMIN"},
            {"REVOKE MODIFY ON CONTACT \"Ct_M\" FROM ROLE pc_r",
                "Statement executed successfully. 1 objects affected."},
            {"SHOW GRANTS ON CONTACT \"Ct_M\" ->> SELECT \"privilege\", \"grantee_name\" FROM $1 ORDER BY 1",
                "APPLY|PC_R ;; OWNERSHIP|SYSADMIN"},
        });
    }

    @Test
    public void aDroppedPolicysPrivilegesGoWithIt() {
        engine.execute("CREATE OR REPLACE JOIN POLICY pc_jp AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE MASKING POLICY pc_mp AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE OR REPLACE PASSWORD POLICY pc_pw");
        engine.execute("GRANT APPLY ON JOIN POLICY pc_jp TO ROLE pc_r");
        engine.execute("GRANT APPLY ON MASKING POLICY pc_mp TO ROLE pc_r");
        engine.execute("GRANT APPLY ON PASSWORD POLICY pc_pw TO ROLE pc_r");
        assertCells(new String[][] {
            {"CREATE OR REPLACE MASKING POLICY pc_mp AS (v VARCHAR) RETURNS VARCHAR -> v",
                "Masking policy PC_MP successfully created."},
            {LISTED, "APPLY|JOIN_POLICY|TEST_DB.TEST_SCHEMA.PC_JP ;; APPLY|MASKING_POLICY|TEST_DB.TEST_SCHEMA.PC_MP ;; "
                + "APPLY|PASSWORD_POLICY|TEST_DB.TEST_SCHEMA.PC_PW"},
            {"DROP JOIN POLICY pc_jp", "PC_JP successfully dropped."},
            {"DROP MASKING POLICY pc_mp", "PC_MP successfully dropped."},
            {"DROP PASSWORD POLICY pc_pw", "PC_PW successfully dropped."},
            {LISTED, ""},
            {"CREATE JOIN POLICY pc_jp AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)",
                "Join policy 'PC_JP' is successfully created."},
            {"CREATE MASKING POLICY pc_mp AS (v VARCHAR) RETURNS VARCHAR -> v",
                "Masking policy PC_MP successfully created."},
            {"CREATE PASSWORD POLICY pc_pw", "Password policy 'PC_PW' created successfully"},
            {LISTED, ""},
        });
    }
}
