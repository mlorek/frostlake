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
 * Authentication policies (live-verified): CREATE [OR REPLACE] [IF NOT EXISTS] with the methods, the client
 * types, the security integrations, the MFA enrollment and the comment, each judged in the policy's own words;
 * ALTER … SET, UNSET and RENAME TO; DROP, SHOW AUTHENTICATION POLICIES, DESCRIBE and GET_DDL. The name is shared
 * with the schema's other policies, and a policy of another kind is refused for its kind by CREATE OR REPLACE,
 * ALTER, DROP and DESCRIBE.
 */
public class AuthenticationPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void aPolicyIsCreatedAndDropped() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap1", "Authentication policy 'AP1' created successfully"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 AUTHENTICATION_METHODS = ('PASSWORD') MFA_ENROLLMENT = "
                + "'OPTIONAL' COMMENT = 'c'",
                "Authentication policy 'AP2' created successfully"},
            {"SELECT GET_DDL('POLICY', 'ap2')",
                "create or replace authentication policy AP2 AUTHENTICATION_METHODS=('PASSWORD') "
                + "CLIENT_TYPES=('ALL') CLIENT_POLICY=() SECURITY_INTEGRATIONS=('ALL') MFA_ENROLLMENT=OPTIONAL "
                + "MFA_POLICY=(ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',ALLOWED_METHODS=('ALL')) "
                + "PAT_POLICY=(DEFAULT_EXPIRY_IN_DAYS=15,REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false,"
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED,REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true,"
                + "MAX_EXPIRY_IN_DAYS=365,BLOCKED_ROLES_LIST=()) "
                + "WORKLOAD_IDENTITY_POLICY=(ALLOWED_AWS_PARTITIONS=('ALL'),ALLOWED_OIDC_ISSUERS=('ALL'),"
                + "ALLOWED_PROVIDERS=('ALL'),ALLOWED_AZURE_ISSUERS=('ALL'),ALLOWED_AWS_ACCOUNTS=('ALL')) COMMENT='c' "
                + ";"},
            {"CREATE AUTHENTICATION POLICY ap1", "REFUSED SQL compilation error: | Object 'AP1' already exists."},
            {"CREATE AUTHENTICATION POLICY IF NOT EXISTS ap1", "AP1 already exists, statement succeeded."},
            {"ALTER AUTHENTICATION POLICY ap1 SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER AUTHENTICATION POLICY ap1 RENAME TO ap3", "Statement executed successfully."},
            {"DROP AUTHENTICATION POLICY ap3", "AP3 successfully dropped."},
            {"DROP AUTHENTICATION POLICY ap2", "AP2 successfully dropped."},
        });
    }

    @Test
    public void eachPropertyIsJudgedInItsOwnWords() {
        engine.execute("CREATE OR REPLACE AUTHENTICATION POLICY ap1");
        engine.execute("CREATE OR REPLACE SESSION POLICY sp1");
        assertCells(new String[][] {
            {"SELECT GET_DDL('POLICY', 'ap1')",
                "create or replace authentication policy AP1 AUTHENTICATION_METHODS=('ALL') CLIENT_TYPES=('ALL') "
                + "CLIENT_POLICY=() SECURITY_INTEGRATIONS=('ALL') MFA_ENROLLMENT=OPTIONAL "
                + "MFA_POLICY=(ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',ALLOWED_METHODS=('ALL')) "
                + "PAT_POLICY=(DEFAULT_EXPIRY_IN_DAYS=15,REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false,"
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED,REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true,"
                + "MAX_EXPIRY_IN_DAYS=365,BLOCKED_ROLES_LIST=()) "
                + "WORKLOAD_IDENTITY_POLICY=(ALLOWED_AWS_PARTITIONS=('ALL'),ALLOWED_OIDC_ISSUERS=('ALL'),"
                + "ALLOWED_PROVIDERS=('ALL'),ALLOWED_AZURE_ISSUERS=('ALL'),ALLOWED_AWS_ACCOUNTS=('ALL')) ;"},
            {"SHOW AUTHENTICATION POLICIES LIKE 'AP1' ->> SELECT \"name\", \"kind\", \"comment\", \"options\" FROM $1",
                "AP1|AUTHENTICATION_POLICY||"},
            {"SHOW TERSE AUTHENTICATION POLICIES LIKE 'AP1' ->> SELECT COUNT(*) FROM $1", "1"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 AUTHENTICATION_METHODS = ('NOPE')",
                "REFUSED SQL Compilation Error: NOPE invalid for Authentication Methods. "},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 AUTHENTICATION_METHODS = ('password', 'SAML')",
                "Authentication policy 'AP2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY ap2 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[PASSWORD, SAML] ;; CLIENT_TYPES|[ALL] ;; COMMENT|null ;; MFA_ENROLLMENT|"
                + "OPTIONAL ;; SECURITY_INTEGRATIONS|[ALL]"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 AUTHENTICATION_METHODS = ()",
                "Authentication policy 'AP2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY ap2 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[] ;; CLIENT_TYPES|[ALL] ;; COMMENT|null ;; MFA_ENROLLMENT|OPTIONAL ;; "
                + "SECURITY_INTEGRATIONS|[ALL]"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 AUTHENTICATION_METHODS = 'PASSWORD'",
                "REFUSED SQL compilation error: | invalid type of property ''PASSWORD'' for 'AUTHENTICATION_METHODS'"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 CLIENT_TYPES = ('NOPE')",
                "REFUSED SQL Compilation Error: NOPE invalid for Client Types. "},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 CLIENT_TYPES = ('SNOWFLAKE_UI', 'drivers')",
                "Authentication policy 'AP2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY ap2 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[ALL] ;; CLIENT_TYPES|[SNOWFLAKE_UI, DRIVERS] ;; COMMENT|null ;; "
                + "MFA_ENROLLMENT|OPTIONAL ;; SECURITY_INTEGRATIONS|[ALL]"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 MFA_ENROLLMENT = 'NOPE'",
                "REFUSED SQL Compilation Error: NOPE invalid for MFA_ENROLLMENT. "},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 MFA_ENROLLMENT = REQUIRED",
                "Authentication policy 'AP2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY ap2 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[ALL] ;; CLIENT_TYPES|[ALL] ;; COMMENT|null ;; MFA_ENROLLMENT|REQUIRED ;; "
                + "SECURITY_INTEGRATIONS|[ALL]"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 MFA_ENROLLMENT = 'required_password_only'",
                "Authentication policy 'AP2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY ap2 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[ALL] ;; CLIENT_TYPES|[ALL] ;; COMMENT|null ;; MFA_ENROLLMENT|"
                + "REQUIRED_PASSWORD_ONLY ;; SECURITY_INTEGRATIONS|[ALL]"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 SECURITY_INTEGRATIONS = ('nosuch_si')",
                "REFUSED SQL Compilation Error: Security integrations [nosuch_si] do not exist or are inactive."},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 NOPE = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'AUTHENTICATION_POLICY'"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 COMMENT = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'COMMENT'"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 COMMENT = 'a' COMMENT = 'b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY ap2 MFA_ENROLLMENT = 'OPTIONAL', COMMENT = 'b'",
                "Authentication policy 'AP2' created successfully"},
            {"CREATE AUTHENTICATION POLICY sp1", "REFUSED SQL compilation error: | Object 'SP1' already exists."},
            {"CREATE OR REPLACE AUTHENTICATION POLICY sp1",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'AUTHENTICATION_POLICY'."},
            {"CREATE SESSION POLICY ap1", "REFUSED SQL compilation error: | Object 'AP1' already exists."},
            {"CREATE OR REPLACE SESSION POLICY ap1",
                "REFUSED SQL compilation error: Object found is of type 'AUTHENTICATION_POLICY', not specified type "
                + "'SESSION_POLICY'."},
            {"ALTER AUTHENTICATION POLICY ap1 SET MFA_ENROLLMENT = 'REQUIRED' COMMENT = 'k'",
                "Statement executed successfully."},
            {"ALTER AUTHENTICATION POLICY ap1 SET AUTHENTICATION_METHODS = ('PASSWORD')",
                "Statement executed successfully."},
            {"DESCRIBE AUTHENTICATION POLICY ap1 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[PASSWORD] ;; CLIENT_TYPES|[ALL] ;; COMMENT|k ;; MFA_ENROLLMENT|REQUIRED ;; "
                + "SECURITY_INTEGRATIONS|[ALL]"},
            {"SELECT GET_DDL('POLICY', 'ap1')",
                "create or replace authentication policy AP1 AUTHENTICATION_METHODS=('PASSWORD') "
                + "CLIENT_TYPES=('ALL') CLIENT_POLICY=() SECURITY_INTEGRATIONS=('ALL') MFA_ENROLLMENT=REQUIRED "
                + "MFA_POLICY=(ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',ALLOWED_METHODS=('ALL')) "
                + "PAT_POLICY=(DEFAULT_EXPIRY_IN_DAYS=15,REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false,"
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED,REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true,"
                + "MAX_EXPIRY_IN_DAYS=365,BLOCKED_ROLES_LIST=()) "
                + "WORKLOAD_IDENTITY_POLICY=(ALLOWED_AWS_PARTITIONS=('ALL'),ALLOWED_OIDC_ISSUERS=('ALL'),"
                + "ALLOWED_PROVIDERS=('ALL'),ALLOWED_AZURE_ISSUERS=('ALL'),ALLOWED_AWS_ACCOUNTS=('ALL')) COMMENT='k' "
                + ";"},
            {"ALTER AUTHENTICATION POLICY ap1 UNSET MFA_ENROLLMENT", "Statement executed successfully."},
            {"ALTER AUTHENTICATION POLICY ap1 UNSET AUTHENTICATION_METHODS, COMMENT",
                "Statement executed successfully."},
            {"DESCRIBE AUTHENTICATION POLICY ap1 ->> SELECT \"property\", \"value\" FROM $1 WHERE \"property\" IN "
                + "('AUTHENTICATION_METHODS', 'CLIENT_TYPES', 'MFA_ENROLLMENT', 'SECURITY_INTEGRATIONS', 'COMMENT') "
                + "ORDER BY 1",
                "AUTHENTICATION_METHODS|[ALL] ;; CLIENT_TYPES|[ALL] ;; COMMENT|null ;; MFA_ENROLLMENT|OPTIONAL ;; "
                + "SECURITY_INTEGRATIONS|[ALL]"},
            {"ALTER AUTHENTICATION POLICY ap1 SET NOPE = 1",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'AUTHENTICATION_POLICY'"},
            {"ALTER AUTHENTICATION POLICY ap1 UNSET NOPE",
                "REFUSED SQL compilation error: | invalid property 'NOPE' for 'AUTHENTICATION_POLICY'"},
            {"ALTER AUTHENTICATION POLICY ap1 SET MFA_ENROLLMENT = 'NOPE'",
                "REFUSED SQL Compilation Error: NOPE invalid for MFA_ENROLLMENT. "},
            {"ALTER AUTHENTICATION POLICY nosuch_ap SET COMMENT = 'x'",
                "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_AP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_AP."},
            {"ALTER AUTHENTICATION POLICY IF EXISTS nosuch_ap SET COMMENT = 'x'", "Statement executed successfully."},
            {"ALTER AUTHENTICATION POLICY ap1 RENAME TO sp1",
                "REFUSED SQL compilation error: | Object 'SP1' already exists."},
            {"ALTER AUTHENTICATION POLICY ap1 RENAME TO ap9", "Statement executed successfully."},
            {"SHOW AUTHENTICATION POLICIES LIKE 'AP%' ->> SELECT \"name\" FROM $1 ORDER BY 1", "AP2 ;; AP9"},
            {"DESCRIBE AUTHENTICATION POLICY nosuch_ap",
                "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_AP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_AP."},
            {"DROP AUTHENTICATION POLICY nosuch_ap",
                "REFUSED SQL compilation error: | Authentication policy 'TEST_DB.TEST_SCHEMA.NOSUCH_AP' does not "
                + "exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH_AP."},
            {"DROP AUTHENTICATION POLICY IF EXISTS nosuch_ap",
                "Drop statement executed successfully (NOSUCH_AP already dropped)."},
            {"UNDROP AUTHENTICATION POLICY ap9", "REFUSED SQL compilation error: | Object 'AP9' already exists."},
            {"SHOW AUTHENTICATION POLICIES IN SCHEMA test_db.test_schema ->> SELECT COUNT(*) FROM $1", "2"},
            {"SHOW AUTHENTICATION POLICIES IN DATABASE test_db ->> SELECT COUNT(*) FROM $1", "2"},
            {"DROP AUTHENTICATION POLICY ap9", "AP9 successfully dropped."},
            {"DROP AUTHENTICATION POLICY sp1",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'AUTHENTICATION_POLICY'."},
        });
    }

    @Test
    public void aPolicyIsDescribedWhole() {
        engine.execute("CREATE OR REPLACE AUTHENTICATION POLICY aq1");
        engine.execute("CREATE OR REPLACE SESSION POLICY sq1");
        engine.execute("CREATE OR REPLACE PACKAGES POLICY pq1 LANGUAGE PYTHON");
        assertCells(new String[][] {
            {"DESCRIBE AUTHENTICATION POLICY aq1 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "NAME|AQ1|null|Name of authentication policy. ;; COMMENT|null|null|Comment associated with "
                + "authentication policy. ;; AUTHENTICATION_METHODS|[ALL]|[ALL]|Authentication methods that are "
                + "allowed for usage. ;; CLIENT_TYPES|[ALL]|[ALL]|Client types that are allowed for access. ;; "
                + "CLIENT_POLICY|{}|null|Client policy for the authentication policy. ;; SECURITY_INTEGRATIONS|[ALL]|"
                + "[ALL]|Security integrations that are allowed for authentication. ;; MFA_ENROLLMENT|OPTIONAL|"
                + "OPTIONAL|When set to REQUIRED, force a user to enroll in MFA according to the "
                + "MFA_AUTHENTICATION_METHODS. Allowed values are REQUIRED | OPTIONAL ;; MFA_POLICY|"
                + "{ALLOWED_METHODS=[ALL], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|{ALLOWED_METHODS=[ALL], "
                + "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|MFA-specific configuration for the authentication "
                + "policy. ;; PAT_POLICY|{DEFAULT_EXPIRY_IN_DAYS=15, MAX_EXPIRY_IN_DAYS=365, "
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED, REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true, "
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false, BLOCKED_ROLES_LIST=[]}|null|PAT-specific "
                + "configuration for the authentication policy. ;; WORKLOAD_IDENTITY_POLICY|{ALLOWED_PROVIDERS=[ALL],"
                + " ALLOWED_AWS_ACCOUNTS=[ALL], ALLOWED_AWS_PARTITIONS=[ALL], ALLOWED_AZURE_ISSUERS=[ALL], "
                + "ALLOWED_OIDC_ISSUERS=[ALL]}|null|Workload identity-specific configuration for the authentication "
                + "policy."},
            {"SHOW AUTHENTICATION POLICIES LIKE 'AQ%' ->> SELECT \"name\", \"database_name\", \"schema_name\", "
                + "\"kind\", \"comment\", \"owner_role_type\", \"options\" FROM $1 ORDER BY 1",
                "AQ1|TEST_DB|TEST_SCHEMA|AUTHENTICATION_POLICY||ROLE|"},
            {"SHOW TERSE AUTHENTICATION POLICIES LIKE 'AQ1' ->> SELECT \"name\", \"kind\" FROM $1",
                "AQ1|AUTHENTICATION_POLICY"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq2 AUTHENTICATION_METHODS = ('PASSWORD', 'SAML') CLIENT_TYPES "
                + "= ('SNOWFLAKE_UI', 'DRIVERS') MFA_ENROLLMENT = REQUIRED COMMENT = 'it''s'",
                "Authentication policy 'AQ2' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY aq2 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "NAME|AQ2|null|Name of authentication policy. ;; COMMENT|it's|null|Comment associated with "
                + "authentication policy. ;; AUTHENTICATION_METHODS|[PASSWORD, SAML]|[ALL]|Authentication methods "
                + "that are allowed for usage. ;; CLIENT_TYPES|[SNOWFLAKE_UI, DRIVERS]|[ALL]|Client types that are "
                + "allowed for access. ;; CLIENT_POLICY|{}|null|Client policy for the authentication policy. ;; "
                + "SECURITY_INTEGRATIONS|[ALL]|[ALL]|Security integrations that are allowed for authentication. ;; "
                + "MFA_ENROLLMENT|REQUIRED|OPTIONAL|When set to REQUIRED, force a user to enroll in MFA according to "
                + "the MFA_AUTHENTICATION_METHODS. Allowed values are REQUIRED | OPTIONAL ;; MFA_POLICY|"
                + "{ALLOWED_METHODS=[ALL], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|{ALLOWED_METHODS=[ALL], "
                + "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|MFA-specific configuration for the authentication "
                + "policy. ;; PAT_POLICY|{DEFAULT_EXPIRY_IN_DAYS=15, MAX_EXPIRY_IN_DAYS=365, "
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED, REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true, "
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false, BLOCKED_ROLES_LIST=[]}|null|PAT-specific "
                + "configuration for the authentication policy. ;; WORKLOAD_IDENTITY_POLICY|{ALLOWED_PROVIDERS=[ALL],"
                + " ALLOWED_AWS_ACCOUNTS=[ALL], ALLOWED_AWS_PARTITIONS=[ALL], ALLOWED_AZURE_ISSUERS=[ALL], "
                + "ALLOWED_OIDC_ISSUERS=[ALL]}|null|Workload identity-specific configuration for the authentication "
                + "policy."},
            {"SELECT GET_DDL('POLICY', 'aq2')",
                "create or replace authentication policy AQ2 AUTHENTICATION_METHODS=('PASSWORD','SAML') "
                + "CLIENT_TYPES=('SNOWFLAKE_UI','DRIVERS') CLIENT_POLICY=() SECURITY_INTEGRATIONS=('ALL') "
                + "MFA_ENROLLMENT=REQUIRED MFA_POLICY=(ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',"
                + "ALLOWED_METHODS=('ALL')) PAT_POLICY=(DEFAULT_EXPIRY_IN_DAYS=15,"
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false,NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED,"
                + "REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true,MAX_EXPIRY_IN_DAYS=365,BLOCKED_ROLES_LIST=()) "
                + "WORKLOAD_IDENTITY_POLICY=(ALLOWED_AWS_PARTITIONS=('ALL'),ALLOWED_OIDC_ISSUERS=('ALL'),"
                + "ALLOWED_PROVIDERS=('ALL'),ALLOWED_AZURE_ISSUERS=('ALL'),ALLOWED_AWS_ACCOUNTS=('ALL')) "
                + "COMMENT='it''s' ;"},
            {"SHOW AUTHENTICATION POLICIES LIKE 'AQ%' ->> SELECT \"name\", \"database_name\", \"schema_name\", "
                + "\"kind\", \"comment\", \"owner_role_type\", \"options\" FROM $1 ORDER BY 1",
                "AQ1|TEST_DB|TEST_SCHEMA|AUTHENTICATION_POLICY||ROLE| ;; AQ2|TEST_DB|TEST_SCHEMA|"
                + "AUTHENTICATION_POLICY|it's|ROLE|"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 AUTHENTICATION_METHODS = ('nope')",
                "REFUSED SQL Compilation Error: NOPE invalid for Authentication Methods. "},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 MFA_ENROLLMENT = 1",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'MFA_ENROLLMENT'"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 MFA_ENROLLMENT = 'nope'",
                "REFUSED SQL Compilation Error: nope invalid for MFA_ENROLLMENT. "},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 SECURITY_INTEGRATIONS = ('all')",
                "REFUSED SQL Compilation Error: Security integrations [all] do not exist or are inactive."},
            {"DESCRIBE AUTHENTICATION POLICY aq3 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Authentication policy 'TEST_DB.TEST_SCHEMA.AQ3' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on POLICY TEST_DB.TEST_SCHEMA.AQ3."},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 SECURITY_INTEGRATIONS = ('ALL', 'nosuch_a', 'nosuch_b')",
                "Authentication policy 'AQ3' created successfully"},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 CLIENT_TYPES = ()",
                "Authentication policy 'AQ3' created successfully"},
            {"DESCRIBE AUTHENTICATION POLICY aq3 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "NAME|AQ3|null|Name of authentication policy. ;; COMMENT|null|null|Comment associated with "
                + "authentication policy. ;; AUTHENTICATION_METHODS|[ALL]|[ALL]|Authentication methods that are "
                + "allowed for usage. ;; CLIENT_TYPES|[]|[ALL]|Client types that are allowed for access. ;; "
                + "CLIENT_POLICY|{}|null|Client policy for the authentication policy. ;; SECURITY_INTEGRATIONS|[ALL]|"
                + "[ALL]|Security integrations that are allowed for authentication. ;; MFA_ENROLLMENT|OPTIONAL|"
                + "OPTIONAL|When set to REQUIRED, force a user to enroll in MFA according to the "
                + "MFA_AUTHENTICATION_METHODS. Allowed values are REQUIRED | OPTIONAL ;; MFA_POLICY|"
                + "{ALLOWED_METHODS=[ALL], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|{ALLOWED_METHODS=[ALL], "
                + "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|MFA-specific configuration for the authentication "
                + "policy. ;; PAT_POLICY|{DEFAULT_EXPIRY_IN_DAYS=15, MAX_EXPIRY_IN_DAYS=365, "
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED, REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true, "
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false, BLOCKED_ROLES_LIST=[]}|null|PAT-specific "
                + "configuration for the authentication policy. ;; WORKLOAD_IDENTITY_POLICY|{ALLOWED_PROVIDERS=[ALL],"
                + " ALLOWED_AWS_ACCOUNTS=[ALL], ALLOWED_AWS_PARTITIONS=[ALL], ALLOWED_AZURE_ISSUERS=[ALL], "
                + "ALLOWED_OIDC_ISSUERS=[ALL]}|null|Workload identity-specific configuration for the authentication "
                + "policy."},
            {"CREATE OR REPLACE AUTHENTICATION POLICY aq3 COMMENT = 'a', MFA_ENROLLMENT = 'OPTIONAL'",
                "Authentication policy 'AQ3' created successfully"},
            {"ALTER AUTHENTICATION POLICY aq3 SET MFA_ENROLLMENT = 'REQUIRED', COMMENT = 'b'",
                "Statement executed successfully."},
            {"DESCRIBE AUTHENTICATION POLICY aq3 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "NAME|AQ3|null|Name of authentication policy. ;; COMMENT|b|null|Comment associated with "
                + "authentication policy. ;; AUTHENTICATION_METHODS|[ALL]|[ALL]|Authentication methods that are "
                + "allowed for usage. ;; CLIENT_TYPES|[ALL]|[ALL]|Client types that are allowed for access. ;; "
                + "CLIENT_POLICY|{}|null|Client policy for the authentication policy. ;; SECURITY_INTEGRATIONS|[ALL]|"
                + "[ALL]|Security integrations that are allowed for authentication. ;; MFA_ENROLLMENT|REQUIRED|"
                + "OPTIONAL|When set to REQUIRED, force a user to enroll in MFA according to the "
                + "MFA_AUTHENTICATION_METHODS. Allowed values are REQUIRED | OPTIONAL ;; MFA_POLICY|"
                + "{ALLOWED_METHODS=[ALL], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|{ALLOWED_METHODS=[ALL], "
                + "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|MFA-specific configuration for the authentication "
                + "policy. ;; PAT_POLICY|{DEFAULT_EXPIRY_IN_DAYS=15, MAX_EXPIRY_IN_DAYS=365, "
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED, REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true, "
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false, BLOCKED_ROLES_LIST=[]}|null|PAT-specific "
                + "configuration for the authentication policy. ;; WORKLOAD_IDENTITY_POLICY|{ALLOWED_PROVIDERS=[ALL],"
                + " ALLOWED_AWS_ACCOUNTS=[ALL], ALLOWED_AWS_PARTITIONS=[ALL], ALLOWED_AZURE_ISSUERS=[ALL], "
                + "ALLOWED_OIDC_ISSUERS=[ALL]}|null|Workload identity-specific configuration for the authentication "
                + "policy."},
            {"ALTER AUTHENTICATION POLICY aq3 UNSET MFA_ENROLLMENT", "Statement executed successfully."},
            {"DESCRIBE AUTHENTICATION POLICY aq3 ->> SELECT \"property\", \"value\", \"default\", \"description\" "
                + "FROM $1 WHERE \"property\" <> 'OWNER'",
                "NAME|AQ3|null|Name of authentication policy. ;; COMMENT|b|null|Comment associated with "
                + "authentication policy. ;; AUTHENTICATION_METHODS|[ALL]|[ALL]|Authentication methods that are "
                + "allowed for usage. ;; CLIENT_TYPES|[ALL]|[ALL]|Client types that are allowed for access. ;; "
                + "CLIENT_POLICY|{}|null|Client policy for the authentication policy. ;; SECURITY_INTEGRATIONS|[ALL]|"
                + "[ALL]|Security integrations that are allowed for authentication. ;; MFA_ENROLLMENT|OPTIONAL|"
                + "OPTIONAL|When set to REQUIRED, force a user to enroll in MFA according to the "
                + "MFA_AUTHENTICATION_METHODS. Allowed values are REQUIRED | OPTIONAL ;; MFA_POLICY|"
                + "{ALLOWED_METHODS=[ALL], ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|{ALLOWED_METHODS=[ALL], "
                + "ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION=NONE}|MFA-specific configuration for the authentication "
                + "policy. ;; PAT_POLICY|{DEFAULT_EXPIRY_IN_DAYS=15, MAX_EXPIRY_IN_DAYS=365, "
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED, REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true, "
                + "REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false, BLOCKED_ROLES_LIST=[]}|null|PAT-specific "
                + "configuration for the authentication policy. ;; WORKLOAD_IDENTITY_POLICY|{ALLOWED_PROVIDERS=[ALL],"
                + " ALLOWED_AWS_ACCOUNTS=[ALL], ALLOWED_AWS_PARTITIONS=[ALL], ALLOWED_AZURE_ISSUERS=[ALL], "
                + "ALLOWED_OIDC_ISSUERS=[ALL]}|null|Workload identity-specific configuration for the authentication "
                + "policy."},
            {"SELECT GET_DDL('POLICY', 'aq3')",
                "create or replace authentication policy AQ3 AUTHENTICATION_METHODS=('ALL') CLIENT_TYPES=('ALL') "
                + "CLIENT_POLICY=() SECURITY_INTEGRATIONS=('ALL') MFA_ENROLLMENT=OPTIONAL "
                + "MFA_POLICY=(ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION='NONE',ALLOWED_METHODS=('ALL')) "
                + "PAT_POLICY=(DEFAULT_EXPIRY_IN_DAYS=15,REQUIRE_ROLE_RESTRICTION_FOR_PERSON_USERS=false,"
                + "NETWORK_POLICY_EVALUATION=ENFORCED_REQUIRED,REQUIRE_ROLE_RESTRICTION_FOR_SERVICE_USERS=true,"
                + "MAX_EXPIRY_IN_DAYS=365,BLOCKED_ROLES_LIST=()) "
                + "WORKLOAD_IDENTITY_POLICY=(ALLOWED_AWS_PARTITIONS=('ALL'),ALLOWED_OIDC_ISSUERS=('ALL'),"
                + "ALLOWED_PROVIDERS=('ALL'),ALLOWED_AZURE_ISSUERS=('ALL'),ALLOWED_AWS_ACCOUNTS=('ALL')) COMMENT='b' "
                + ";"},
            {"DROP SESSION POLICY aq1",
                "REFUSED SQL compilation error: Object found is of type 'AUTHENTICATION_POLICY', not specified type "
                + "'SESSION_POLICY'."},
            {"DROP PACKAGES POLICY sq1",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'PACKAGES_POLICY'."},
            {"DROP AUTHENTICATION POLICY pq1",
                "REFUSED SQL compilation error: Object found is of type 'PACKAGES_POLICY', not specified type "
                + "'AUTHENTICATION_POLICY'."},
            {"DROP AUTHENTICATION POLICY IF EXISTS sq1",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'AUTHENTICATION_POLICY'."},
            {"DESCRIBE AUTHENTICATION POLICY sq1",
                "REFUSED SQL compilation error: | Invalid object type: 'AUTHENTICATION_POLICY'"},
            {"DESCRIBE PACKAGES POLICY aq1", "REFUSED SQL compilation error: | Invalid object type: 'PACKAGES_POLICY'"},
            {"DESCRIBE SESSION POLICY aq1", "REFUSED SQL compilation error: | Invalid object type: 'SESSION_POLICY'"},
            {"ALTER AUTHENTICATION POLICY sq1 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'AUTHENTICATION_POLICY'."},
            {"ALTER PACKAGES POLICY aq1 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: Object found is of type 'AUTHENTICATION_POLICY', not specified type "
                + "'PACKAGES_POLICY'."},
            {"ALTER SESSION POLICY aq1 SET COMMENT = 'x'",
                "REFUSED SQL compilation error: Object found is of type 'AUTHENTICATION_POLICY', not specified type "
                + "'SESSION_POLICY'."},
            {"CREATE OR REPLACE PACKAGES POLICY sq1 LANGUAGE PYTHON",
                "REFUSED SQL compilation error: Object found is of type 'SESSION_POLICY', not specified type "
                + "'PACKAGES_POLICY'."},
            {"CREATE OR REPLACE SESSION POLICY pq1",
                "REFUSED SQL compilation error: Object found is of type 'PACKAGES_POLICY', not specified type "
                + "'SESSION_POLICY'."},
            {"ALTER AUTHENTICATION POLICY aq2 RENAME TO pq1",
                "REFUSED SQL compilation error: | Object 'PQ1' already exists."},
            {"ALTER PACKAGES POLICY pq1 RENAME TO aq2",
                "REFUSED SQL compilation error: | Object 'AQ2' already exists."},
        });
    }
}
