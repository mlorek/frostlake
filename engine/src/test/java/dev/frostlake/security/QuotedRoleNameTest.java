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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A role keeps the name its statement resolved: a quoted name as written, an unquoted one in upper case, so
 * {@code "low"} and {@code LOW} are two roles, and a quoted name reaches only the role of exactly that name — USE
 * ROLE, GRANT, SHOW GRANTS and DROP alike, a database role too (live-verified). The account's roles are not made from
 * a test there, so this runs on the embedded engine only.
 */
public class QuotedRoleNameTest extends MeasuredCellsTestSupport {

    private static final String UNREACHABLE = "REFUSED SQL compilation error: | Object does not exist, or operation"
        + " cannot be performed.";

    private static final String ROLES = "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1";

    private String primary;

    @BeforeEach
    public void theSessionsRole() {
        Assumptions.assumeFalse(isLiveSnowflake(), "roles are the account's own; none is made from a test");
        primary = answer("SELECT CURRENT_ROLE()");
    }

    @AfterEach
    public void dropRoles() {
        answer("USE ROLE " + primary);
        for (final String role : new String[] {"\"low\"", "LOW", "FLQ_UR_R", "\"low2\""}) {
            answer("DROP ROLE IF EXISTS " + role);
        }
        answer("DROP DATABASE ROLE IF EXISTS test_db.\"low_dr\"");
    }

    @Test
    public void aQuotedNameIsThatNameAlone() {
        assertCells(new String[][] {
            {"CREATE ROLE \"low\"", "Role low successfully created."},
            {"CREATE ROLE \"LOW\"", "Role LOW successfully created."},
            {"CREATE ROLE low", "REFUSED SQL compilation error: | Object 'LOW' already exists."},
            {"CREATE ROLE flq_ur_r", "Role FLQ_UR_R successfully created."},
            {"USE ROLE \"flq_ur_r\"", UNREACHABLE},
        });
        answer("SHOW ROLES LIKE 'low'");
        assertAnswer("LOW ;; low", ROLES);
        assertCells(new String[][] {
            {"GRANT ROLE \"low\" TO ROLE " + primary, "Statement executed successfully."},
            {"USE ROLE \"low\"", "Statement executed successfully."},
            {"SELECT CURRENT_ROLE()", "low"},
            {"SELECT IS_ROLE_IN_SESSION('\"low\"'), IS_ROLE_IN_SESSION('low'), IS_ROLE_IN_SESSION('LOW')",
                "true|false|false"},
            {"USE ROLE " + primary, "Statement executed successfully."},
            {"GRANT ROLE \"low2\" TO ROLE " + primary, "REFUSED SQL compilation error: | Role '\"low2\"' does not"
                + " exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on"
                + " ROLE \"low2\"."},
            {"DROP ROLE \"low\"", "low successfully dropped."},
            {"DROP ROLE \"low\"", "REFUSED SQL compilation error: | Role '\"low\"' does not exist or not authorized."
                + " Your primary role <ROLE> must have at least one privilege granted on ROLE \"low\"."},
        });
        answer("SHOW ROLES LIKE 'low'");
        assertAnswer("LOW", ROLES);
    }

    @Test
    public void aDatabaseRoleAnswersToItsOwnSpelling() {
        assertCells(new String[][] {
            {"CREATE DATABASE ROLE test_db.\"low_dr\"", "Role TEST_DB.\"low_dr\" successfully created."},
            {"GRANT DATABASE ROLE test_db.\"low_dr\" TO ROLE " + primary, "Statement executed successfully."},
            {"SELECT IS_DATABASE_ROLE_IN_SESSION('\"low_dr\"'), IS_DATABASE_ROLE_IN_SESSION('low_dr'),"
                + " IS_DATABASE_ROLE_IN_SESSION('LOW_DR')", "true|false|false"},
            {"SELECT IS_DATABASE_ROLE_IN_SESSION(v) FROM (SELECT '\"low_dr\"' AS v UNION ALL SELECT 'low_dr')"
                + " ORDER BY 1", "false ;; true"},
        });
    }

    @Test
    public void aQuotedRoleOutlivesARestart() throws Exception {
        answer("CREATE ROLE \"low\"");
        answer("CREATE ROLE \"LOW\"");
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            assertEquals(2L, copy.executeQuery("SHOW ROLES LIKE 'low'").getRowCount());
            assertEquals("low", String.valueOf(copy.getCatalog().getRoleExact("low").getName()));
        } finally {
            copy.shutdown();
        }
    }
}
