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
 * The roles IS_ROLE_IN_SESSION counts. A role is in the session when the primary role is it or holds it, and
 * when a secondary role does: with a role active that holds nothing, a role taken with USE SECONDARY ROLES is
 * in the session and the administrator's role is not. USE SECONDARY ROLES ALL takes every role granted to the
 * session's user, so the role the user came with is in the session whatever the primary role holds. A database
 * role's own grant on its database is listed with no grantor. Every answer is live-verified.
 */
public class SecondaryRoleMembershipTest extends MeasuredCellsTestSupport {

    @Test
    public void aSecondaryRoleIsInTheSession() {
        try {
            assertAnswer(
                "Role TEST_DB.FLQ_DR1 successfully created.",
                "CREATE DATABASE ROLE test_db.flq_dr1");
            assertShape(
                "created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                    + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
                    + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216), "
                    + "granted_by_role_type:VARCHAR(16777216)",
                "SHOW GRANTS ON DATABASE test_db");
            assertAnswer(
                "USAGE|DATABASE|TEST_DB|DATABASE_ROLE|FLQ_DR1|false||",
                "SELECT \"privilege\", \"granted_on\", \"name\", \"granted_to\", \"grantee_name\", "
                    + "\"grant_option\", \"granted_by\", \"granted_by_role_type\" FROM "
                    + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"granted_to\" = 'DATABASE_ROLE' ORDER BY 1");
            assertShape(
                "created_on:TIMESTAMP_LTZ, privilege:VARCHAR(16777216), granted_on:VARCHAR(16777216), "
                    + "name:VARCHAR(16777216), granted_to:VARCHAR(16777216), grantee_name:VARCHAR(16777216), "
                    + "grant_option:VARCHAR(16777216), granted_by:VARCHAR(16777216)",
                "SHOW GRANTS TO DATABASE ROLE test_db.flq_dr1");
            assertAnswer(
                "USAGE|DATABASE|TEST_DB|DATABASE_ROLE|FLQ_DR1|false|",
                "SELECT \"privilege\", \"granted_on\", \"name\", \"granted_to\", \"grantee_name\", "
                    + "\"grant_option\", \"granted_by\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
            assertAnswer(
                "Role FLQ_P1 successfully created.",
                "CREATE ROLE IF NOT EXISTS flq_p1");
            assertAnswer(
                "Role FLQ_S1 successfully created.",
                "CREATE ROLE IF NOT EXISTS flq_s1");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_p1 TO ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_s1 TO ROLE ACCOUNTADMIN");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "true|true",
                "SELECT IS_ROLE_IN_SESSION('FLQ_S1'), IS_ROLE_IN_SESSION('FLQ_P1')");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_p1");
            assertAnswer(
                "false|true|false",
                "SELECT IS_ROLE_IN_SESSION('FLQ_S1'), IS_ROLE_IN_SESSION('FLQ_P1'), "
                    + "IS_ROLE_IN_SESSION('ACCOUNTADMIN')");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES flq_s1");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_p1");
            assertAnswer(
                "true|true|false",
                "SELECT IS_ROLE_IN_SESSION('FLQ_S1'), IS_ROLE_IN_SESSION('FLQ_P1'), "
                    + "IS_ROLE_IN_SESSION('ACCOUNTADMIN')");
            assertAnswer(
                "{\"roles\":\"FLQ_S1\",\"value\":\"FLQ_S1\"}",
                "SELECT CURRENT_SECONDARY_ROLES()");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES ALL");
            answer(
                "DROP ROLE IF EXISTS flq_p1");
            answer(
                "DROP ROLE IF EXISTS flq_s1");
        }
    }

    @Test
    public void allTakesEveryRoleOfTheUser() {
        try {
            assertAnswer(
                "Role FLQ_P1 successfully created.",
                "CREATE ROLE IF NOT EXISTS flq_p1");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ROLE flq_p1 TO ROLE ACCOUNTADMIN");
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES ALL");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_p1");
            assertAnswer(
                "true|true|true",
                "SELECT IS_ROLE_IN_SESSION('ACCOUNTADMIN'), IS_ROLE_IN_SESSION('FLQ_P1'), "
                    + "IS_ROLE_IN_SESSION('PUBLIC')");
            answer(
                "USE ROLE ACCOUNTADMIN");
            assertAnswer(
                "Statement executed successfully.",
                "USE SECONDARY ROLES NONE");
            assertAnswer(
                "Statement executed successfully.",
                "USE ROLE flq_p1");
            assertAnswer(
                "false|true|true",
                "SELECT IS_ROLE_IN_SESSION('ACCOUNTADMIN'), IS_ROLE_IN_SESSION('FLQ_P1'), "
                    + "IS_ROLE_IN_SESSION('PUBLIC')");
        } finally {
            answer(
                "USE ROLE ACCOUNTADMIN");
            answer(
                "USE SECONDARY ROLES ALL");
            answer(
                "DROP ROLE IF EXISTS flq_p1");
        }
    }
}
