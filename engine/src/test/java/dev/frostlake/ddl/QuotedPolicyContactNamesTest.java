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
 * A quoted name keeps its case for a join policy and a contact as it does for every policy: created beside its
 * upper-case namesake, reached, attached, renamed and dropped by exactly that name, a refusal spelling it quoted;
 * a policy attached to a table, a column or a view is refused a drop and a replace in its own case, and another
 * policy named the same in another case is not in use; DESCRIBE CONTACT is an unsupported feature (all
 * live-verified).
 */
public class QuotedPolicyContactNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void aJoinPolicyAndAContactByAQuotedName() {
        engine.execute("CREATE OR REPLACE JOIN POLICY CJP4 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE CONTACT CCT4 EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        engine.execute("CREATE OR REPLACE TABLE tq740 (a INT)");
        assertAnswer("Join policy 'cjp4' is successfully created.",
            "CREATE JOIN POLICY \"cjp4\" AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        assertAnswer("CJP4 ;; cjp4", "SHOW JOIN POLICIES LIKE 'cjp4' ->> SELECT \"name\" FROM $1 ORDER BY 1");
        assertAnswer("cjp4|()|JOIN_CONSTRAINT|JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)", "DESCRIBE JOIN POLICY \"cjp4\"");
        assertAnswer("CJP4|()|JOIN_CONSTRAINT|JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)", "DESCRIBE JOIN POLICY CJP4");
        assertAnswer("Statement executed successfully.", "ALTER JOIN POLICY \"cjp4\" SET COMMENT = 'x'");
        assertAnswer("CJP4| ;; cjp4|x",
            "SHOW JOIN POLICIES LIKE 'cjp4' ->> SELECT \"name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("Join policy 'cjp4' is successfully created.",
            "CREATE OR REPLACE JOIN POLICY \"cjp4\" AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED "
                + "=> FALSE)");
        assertAnswer("cjp4|()|JOIN_CONSTRAINT|JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)",
            "DESCRIBE JOIN POLICY \"cjp4\"");
        assertAnswer("cjp4 already exists, statement succeeded.",
            "CREATE JOIN POLICY IF NOT EXISTS \"cjp4\" AS () RETURNS JOIN_CONSTRAINT -> "
                + "JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        assertAnswer("Statement executed successfully.", "ALTER JOIN POLICY \"cjp4\" RENAME TO \"cjp5\"");
        assertAnswer("CJP4 ;; cjp5", "SHOW JOIN POLICIES LIKE 'cjp%' ->> SELECT \"name\" FROM $1 ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp4\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp4\".",
            "DESCRIBE JOIN POLICY \"cjp4\"");
        assertAnswer("cjp5 successfully dropped.", "DROP JOIN POLICY \"cjp5\"");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp5\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp5\".",
            "DROP JOIN POLICY \"cjp5\"");
        assertAnswer("Drop statement executed successfully (cjp5 already dropped).",
            "DROP JOIN POLICY IF EXISTS \"cjp5\"");
        assertAnswer("CJP4", "SHOW JOIN POLICIES LIKE 'cjp%' ->> SELECT \"name\" FROM $1 ORDER BY 1");
        assertAnswer("Contact cct4 successfully created.",
            "CREATE CONTACT \"cct4\" EMAIL_DISTRIBUTION_LIST = 'b@example.com'");
        assertAnswer("CCT4 ;; cct4", "SHOW CONTACTS LIKE 'cct4' ->> SELECT \"name\" FROM $1 ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER CONTACT \"cct4\" SET COMMENT = 'x'");
        assertAnswer("CCT4| ;; cct4|x",
            "SHOW CONTACTS LIKE 'cct4' ->> SELECT \"name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("REFUSED Unsupported feature 'CONTACT'.", "DESCRIBE CONTACT CCT4");
        assertAnswer("REFUSED Unsupported feature 'CONTACT'.", "DESCRIBE CONTACT \"cct4\"");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tq740 SET CONTACT STEWARD = \"cct4\"");
        assertAnswer("REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.\"nosuch\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.\"nosuch\".",
            "ALTER TABLE tq740 SET CONTACT SUPPORT = \"nosuch\"");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tq740 UNSET CONTACT STEWARD");
        assertAnswer("Contact cct4 successfully created.",
            "CREATE OR REPLACE CONTACT \"cct4\" URL = 'https://example.com'");
        assertAnswer("CCT4 ;; cct4", "SHOW CONTACTS LIKE 'cct4' ->> SELECT \"name\" FROM $1 ORDER BY 1");
        assertAnswer("cct4 successfully dropped.", "DROP CONTACT \"cct4\"");
        assertAnswer("REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.\"cct4\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.\"cct4\".",
            "DROP CONTACT \"cct4\"");
        assertAnswer("Drop statement executed successfully (cct4 already dropped).", "DROP CONTACT IF EXISTS \"cct4\"");
        assertAnswer("CCT4", "SHOW CONTACTS LIKE 'cct4' ->> SELECT \"name\" FROM $1 ORDER BY 1");
    }

    @Test
    public void inUseChecksCompareNamesExactly() {
        engine.execute("CREATE OR REPLACE TABLE tp740 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE MASKING POLICY QMP7 AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE MASKING POLICY \"qmp7\" AS (v INT) RETURNS INT -> 0");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY QPP7 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE PROJECTION POLICY \"qpp7\" AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY QAP7 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE AGGREGATION POLICY \"qap7\" AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("CREATE OR REPLACE JOIN POLICY QJP7 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tp740 MODIFY COLUMN a SET MASKING POLICY QMP7");
        assertAnswer("qmp7 successfully dropped.", "DROP MASKING POLICY \"qmp7\"");
        assertAnswer("Masking policy qmp7 successfully created.",
            "CREATE MASKING POLICY \"qmp7\" AS (v INT) RETURNS INT -> 1");
        assertAnswer("REFUSED Specified column already attached to another masking policy. A column cannot be "
                + "attached to multiple masking policies. Please drop the current association in order to attach a "
                + "new masking policy.",
            "ALTER TABLE tp740 MODIFY COLUMN a SET MASKING POLICY \"qmp7\"");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE tp740 MODIFY COLUMN a SET MASKING POLICY \"qmp7\" FORCE");
        assertAnswer("qmp7|MASKING_POLICY|A",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'tp740', REF_ENTITY_DOMAIN => "
                + "'table')) ORDER BY 1, 2, 3");
        assertAnswer("QMP7 successfully dropped.", "DROP MASKING POLICY QMP7");
        assertAnswer("REFUSED SQL compilation error: Policy qmp7 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP MASKING POLICY \"qmp7\"");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE tp740 MODIFY COLUMN b SET PROJECTION POLICY QPP7");
        assertAnswer("qpp7 successfully dropped.", "DROP PROJECTION POLICY \"qpp7\"");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tp740 SET AGGREGATION POLICY QAP7");
        assertAnswer("qap7 successfully dropped.", "DROP AGGREGATION POLICY \"qap7\"");
        assertAnswer("QAP7|AGGREGATION_POLICY|null ;; QPP7|PROJECTION_POLICY|B ;; qmp7|MASKING_POLICY|A",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'tp740', REF_ENTITY_DOMAIN => "
                + "'table')) ORDER BY 1, 2, 3");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tp740 SET JOIN POLICY QJP7");
        assertAnswer("QAP7|AGGREGATION_POLICY|null ;; QJP7|JOIN_POLICY|null ;; QPP7|PROJECTION_POLICY|B ;; qmp7|"
                + "MASKING_POLICY|A",
            "SELECT POLICY_NAME, POLICY_KIND, REF_COLUMN_NAME FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'tp740', REF_ENTITY_DOMAIN => "
                + "'table')) ORDER BY 1, 2, 3");
        assertAnswer("Masking policy qjp7 successfully created.",
            "CREATE MASKING POLICY \"qjp7\" AS (v INT) RETURNS INT -> v");
        assertAnswer("REFUSED SQL compilation error: | Object 'QJP7' already exists.",
            "CREATE MASKING POLICY qjp7 AS (v INT) RETURNS INT -> v");
        assertAnswer("Password policy 'QPW7' created successfully", "CREATE OR REPLACE PASSWORD POLICY QPW7");
        assertAnswer("Masking policy qpw7 successfully created.",
            "CREATE MASKING POLICY \"qpw7\" AS (v INT) RETURNS INT -> v");
        assertAnswer("REFUSED SQL compilation error: | Object 'QPW7' already exists.",
            "CREATE MASKING POLICY qpw7 AS (v INT) RETURNS INT -> v");
        assertAnswer("Session policy 'QSE7' created successfully.", "CREATE OR REPLACE SESSION POLICY QSE7");
        assertAnswer("Masking policy qse7 successfully created.",
            "CREATE MASKING POLICY \"qse7\" AS (v INT) RETURNS INT -> v");
        assertAnswer("REFUSED SQL compilation error: | Object 'QSE7' already exists.",
            "CREATE MASKING POLICY qse7 AS (v INT) RETURNS INT -> v");
        assertAnswer("qjp7 ;; qmp7 ;; qpw7 ;; qse7",
            "SHOW MASKING POLICIES LIKE 'q%7' ->> SELECT \"name\" FROM $1 ORDER BY 1");
    }

    @Test
    public void inUseRefusalsAndDescribeContact() {
        engine.execute("CREATE OR REPLACE TABLE tr741 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY RAP7 AS (v INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE ROW ACCESS POLICY \"rap7\" AS (v INT) RETURNS BOOLEAN -> FALSE");
        engine.execute("CREATE OR REPLACE MASKING POLICY QMP8 AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE MASKING POLICY \"qmp8\" AS (v INT) RETURNS INT -> 0");
        engine.execute("CREATE OR REPLACE CONTACT CCT8 EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        engine.execute("CREATE CONTACT \"cct8\" EMAIL_DISTRIBUTION_LIST = 'b@example.com'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tr741 ADD ROW ACCESS POLICY RAP7 ON (a)");
        assertAnswer("rap7 successfully dropped.", "DROP ROW ACCESS POLICY \"rap7\"");
        assertAnswer("REFUSED SQL compilation error: Policy RAP7 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP ROW ACCESS POLICY RAP7");
        assertAnswer("REFUSED SQL compilation error: Policy RAP7 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "CREATE OR REPLACE ROW ACCESS POLICY RAP7 AS (v INT) RETURNS BOOLEAN -> FALSE");
        assertAnswer("REFUSED SQL compilation error: Column 'A' cannot be masked because it is used as argument by "
                + "one or more masking/row-access policies.",
            "ALTER TABLE tr741 MODIFY COLUMN a SET MASKING POLICY QMP8");
        assertAnswer("Masking policy QMP8 successfully created.",
            "CREATE OR REPLACE MASKING POLICY QMP8 AS (v INT) RETURNS INT -> 0");
        assertAnswer("Masking policy qmp8 successfully created.",
            "CREATE OR REPLACE MASKING POLICY \"qmp8\" AS (v INT) RETURNS INT -> 1");
        assertAnswer("REFUSED SQL compilation error: Column 'A' cannot be masked because it is used as argument by "
                + "one or more masking/row-access policies.",
            "ALTER TABLE tr741 MODIFY COLUMN a SET MASKING POLICY QMP8");
        assertAnswer("REFUSED SQL compilation error: Column 'A' cannot be masked because it is used as argument by "
                + "one or more masking/row-access policies.",
            "ALTER TABLE tr741 MODIFY COLUMN a SET MASKING POLICY test_schema.QMP8");
        assertAnswer("REFUSED SQL compilation error: Column 'A' cannot be masked because it is used as argument by "
                + "one or more masking/row-access policies.",
            "ALTER TABLE tr741 MODIFY COLUMN a SET MASKING POLICY \"QMP8\"");
        assertAnswer("REFUSED SQL compilation error: Column 'A' cannot be masked because it is used as argument by "
                + "one or more masking/row-access policies.",
            "ALTER TABLE tr741 MODIFY COLUMN a SET MASKING POLICY test_db.test_schema.\"QMP8\"");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE tr741 MODIFY COLUMN b SET MASKING POLICY \"qmp8\"");
        assertAnswer("REFUSED SQL compilation error: Policy qmp8 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP MASKING POLICY \"qmp8\"");
        assertAnswer("", "SELECT * FROM tr741");
        assertAnswer("REFUSED Unsupported feature 'CONTACT'.", "DESCRIBE CONTACT nosuch");
        assertAnswer("REFUSED Unsupported feature 'CONTACT'.", "DESC CONTACT CCT8");
        assertAnswer("REFUSED Unsupported feature 'CONTACT'.", "DESCRIBE CONTACT test_schema.cct8");
        assertAnswer("CCT8|a@example.com ;; cct8|b@example.com",
            "SHOW CONTACTS LIKE 'cct8' ->> SELECT \"name\", \"email_distribution_list\" FROM $1 ORDER BY 1");
        assertAnswer("cct8 successfully dropped.", "DROP CONTACT \"cct8\"");
        assertAnswer("REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.\"cct8\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.\"cct8\".",
            "DROP CONTACT \"cct8\"");
        assertAnswer("CCT8", "SHOW CONTACTS LIKE 'cct8' ->> SELECT \"name\" FROM $1 ORDER BY 1");
    }

    @Test
    public void aJoinPolicyNamedInAnotherCaseAndATasksContact() {
        engine.execute("CREATE OR REPLACE JOIN POLICY CJP9 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE TABLE tj742 (a INT)");
        engine.execute("CREATE OR REPLACE VIEW vj742 AS SELECT a FROM tj742");
        engine.execute("CREATE OR REPLACE CONTACT CCT9 EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        engine.execute("CREATE CONTACT \"cct9\" EMAIL_DISTRIBUTION_LIST = 'b@example.com'");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp9\".",
            "DESCRIBE JOIN POLICY \"cjp9\"");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp9\".",
            "ALTER JOIN POLICY \"cjp9\" SET COMMENT = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER JOIN POLICY IF EXISTS \"cjp9\" SET COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp9\".",
            "ALTER TABLE tj742 SET JOIN POLICY \"cjp9\"");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp9\".",
            "CREATE OR REPLACE TABLE tj742b (a INT) JOIN POLICY \"cjp9\"");
        assertAnswer("REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.\"cjp9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.\"cjp9\".",
            "DROP JOIN POLICY \"cjp9\"");
        assertAnswer("CJP9|", "SHOW JOIN POLICIES LIKE 'cjp9' ->> SELECT \"name\", \"comment\" FROM $1 ORDER BY 1");
        assertAnswer("Statement executed successfully.", "ALTER VIEW vj742 SET JOIN POLICY CJP9");
        assertAnswer("REFUSED SQL compilation error: Policy CJP9 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP JOIN POLICY CJP9");
        assertAnswer("REFUSED SQL compilation error: Policy CJP9 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "CREATE OR REPLACE JOIN POLICY CJP9 AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => "
                + "TRUE)");
        assertAnswer("Statement executed successfully.", "ALTER VIEW vj742 UNSET JOIN POLICY");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tj742 SET JOIN POLICY CJP9");
        assertAnswer("REFUSED SQL compilation error: Policy CJP9 cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP JOIN POLICY CJP9");
        assertAnswer("Statement executed successfully.", "ALTER TABLE tj742 UNSET JOIN POLICY");
        assertAnswer("Statement executed successfully.",
            "SET q742 = 'CREATE OR REPLACE TASK tk742 WITH CONTACT (STEWARD = \"cct9\") WAREHOUSE = ' || "
                + "COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH') || ' AS SELECT 1'");
        assertAnswer("Task TK742 successfully created.", "EXECUTE IMMEDIATE $q742");
        assertAnswer("create or replace task TK742 |  WITH CONTACT (STEWARD=TEST_DB.TEST_SCHEMA.\"cct9\") | "
                + "	warehouse=WH | 	as SELECT 1;",
            "SELECT REPLACE(GET_DDL('TASK', 'tk742'), COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH'), 'WH')");
        assertAnswer("Statement executed successfully.", "ALTER TASK tk742 SET CONTACT SUPPORT = \"cct9\"");
        assertAnswer("create or replace task TK742 |  WITH CONTACT (STEWARD=TEST_DB.TEST_SCHEMA.\"cct9\", "
                + "SUPPORT=TEST_DB.TEST_SCHEMA.\"cct9\") | 	warehouse=WH | 	as SELECT 1;",
            "SELECT REPLACE(GET_DDL('TASK', 'tk742'), COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH'), 'WH')");
        assertAnswer("REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.\"nosuch9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.\"nosuch9\".",
            "ALTER TASK tk742 SET CONTACT SUPPORT = \"nosuch9\"");
        assertAnswer("Statement executed successfully.",
            "SET q742 = 'CREATE OR REPLACE TASK tk742 WITH CONTACT (STEWARD = \"nosuch9\") WAREHOUSE = ' || "
                + "COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH') || ' AS SELECT 1'");
        assertAnswer("REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.\"nosuch9\"' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.\"nosuch9\".",
            "EXECUTE IMMEDIATE $q742");
        assertAnswer("Statement executed successfully.",
            "SET q742 = 'CREATE OR REPLACE TASK tk742 WITH CONTACT (STEWARD = cct9) WAREHOUSE = ' || "
                + "COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH') || ' AS SELECT 1'");
        assertAnswer("Task TK742 successfully created.", "EXECUTE IMMEDIATE $q742");
        assertAnswer("create or replace task TK742 |  WITH CONTACT (STEWARD=TEST_DB.TEST_SCHEMA.CCT9) | 	warehouse=WH "
                + "| 	as SELECT 1;",
            "SELECT REPLACE(GET_DDL('TASK', 'tk742'), COALESCE((SELECT CURRENT_WAREHOUSE()), 'COMPUTE_WH'), 'WH')");
        assertAnswer("TK742 successfully dropped.", "DROP TASK tk742");
        assertAnswer("CJP9 successfully dropped.", "DROP JOIN POLICY CJP9");
        assertAnswer("0", "SHOW JOIN POLICIES LIKE 'cjp9' ->> SELECT COUNT(*) FROM $1");
    }
}
