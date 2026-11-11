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
 * A privacy policy: no arguments, a body answering a PRIVACY_BUDGET, listed, described, altered and dropped as the
 * schema's other policies are, and attached to a table with the one column its rows are counted by. Every answer is
 * live-verified.
 */
public class PrivacyPolicyTest extends MeasuredCellsTestSupport {

    private static final String REFERENCES = "SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, "
        + "REF_COLUMN_NAME, REF_ARG_COLUMN_NAMES, POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
        + "REF_ENTITY_NAME => 'TEST_DB.TEST_SCHEMA.KT957', REF_ENTITY_DOMAIN => 'TABLE'))";

    private static final String LISTED = "SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", "
        + "\"owner_role_type\", \"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    private static final String DESCRIBED = "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    @Test
    public void aPolicyIsCreatedListedAndDescribed() {
        assertAnswer("Privacy policy 'PV957' successfully created.",
            "CREATE OR REPLACE PRIVACY POLICY pv957 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY() "
                + "COMMENT = 'pc'");
        engine.execute("SHOW PRIVACY POLICIES LIKE 'PV957'");
        assertAnswer("PV957|TEST_DB|TEST_SCHEMA|PRIVACY_POLICY|pc|ROLE|", LISTED);
        engine.execute("SHOW TERSE PRIVACY POLICIES LIKE 'PV957'");
        assertAnswer("PV957|TEST_DB|TEST_SCHEMA|PRIVACY_POLICY|pc|ROLE|", LISTED);
        engine.execute("DESCRIBE PRIVACY POLICY pv957");
        assertAnswer("PV957|()|PRIVACY_BUDGET|NO_PRIVACY_POLICY()", DESCRIBED);
        assertAnswer("create or replace privacy policy PV957 as ()  | returns PRIVACY_BUDGET -> | "
            + "NO_PRIVACY_POLICY() | COMMENT='pc' | ;", "SELECT GET_DDL('POLICY', 'pv957')");
        assertAnswer("PV957 already exists, statement succeeded.",
            "CREATE PRIVACY POLICY IF NOT EXISTS pv957 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED SQL compilation error: | Object 'PV957' already exists.",
            "CREATE PRIVACY POLICY pv957 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("Privacy policy 'PV959' successfully created.",
            "CREATE OR REPLACE PRIVACY POLICY pv959 AS () RETURNS PRIVACY_BUDGET -> CASE WHEN CURRENT_ROLE() = 'X' "
                + "THEN NO_PRIVACY_POLICY() ELSE PRIVACY_BUDGET(BUDGET_NAME => 'b') END");
        engine.execute("DESCRIBE PRIVACY POLICY pv959");
        assertAnswer("PV959|()|PRIVACY_BUDGET|CASE WHEN CURRENT_ROLE() = 'X' THEN NO_PRIVACY_POLICY() ELSE "
            + "PRIVACY_BUDGET(BUDGET_NAME => 'b') END", DESCRIBED);
        assertAnswer("create or replace privacy policy PV959 as ()  | returns PRIVACY_BUDGET -> | CASE WHEN "
            + "CURRENT_ROLE() = 'X' THEN NO_PRIVACY_POLICY() ELSE PRIVACY_BUDGET(BUDGET_NAME => 'b') END | ;",
            "SELECT GET_DDL('POLICY', 'pv959')");
        engine.execute("SHOW PRIVACY POLICIES IN SCHEMA");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer("PV957 successfully dropped.", "DROP PRIVACY POLICY pv957");
        assertAnswer("Drop statement executed successfully (PV957 already dropped).",
            "DROP PRIVACY POLICY IF EXISTS pv957");
    }

    @Test
    public void theSignatureTheBodyAndTheReturnTypeAreJudgedInThatOrder() {
        assertAnswer("REFUSED Privacy policy must have exactly zero arguments, got 1 arguments.",
            "CREATE OR REPLACE PRIVACY POLICY pv957x AS (a INT) RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED Declared return type 'SYSTEM$PRIVACY_BUDGET' is incompatible with actual return type "
            + "'NUMBER(1,0)'", "CREATE OR REPLACE PRIVACY POLICY pv958x AS () RETURNS PRIVACY_BUDGET -> 1");
        assertAnswer("REFUSED Privacy policy return type 'VARCHAR(134217728)' is not PRIVACY_BUDGET.",
            "CREATE OR REPLACE PRIVACY POLICY pv958x AS () RETURNS VARCHAR -> 'a'");
        assertAnswer("REFUSED Privacy policy return type 'BOOLEAN' is not PRIVACY_BUDGET.",
            "CREATE OR REPLACE PRIVACY POLICY pv956 AS () RETURNS BOOLEAN -> TRUE");
        assertAnswer("Drop statement executed successfully (PV958X already dropped).",
            "DROP PRIVACY POLICY IF EXISTS pv958x");
    }

    @Test
    public void aPolicyIsAlteredInPlace() {
        engine.execute("CREATE OR REPLACE PRIVACY POLICY pv956 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("Statement executed successfully.", "ALTER PRIVACY POLICY pv956 SET COMMENT = 'c956'");
        engine.execute("SHOW PRIVACY POLICIES LIKE 'PV956'");
        assertAnswer("PV956|TEST_DB|TEST_SCHEMA|PRIVACY_POLICY|c956|ROLE|", LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER PRIVACY POLICY pv956 SET BODY -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED Declared return type 'SYSTEM$PRIVACY_BUDGET' is incompatible with actual return type "
            + "'NUMBER(1,0)'", "ALTER PRIVACY POLICY pv956 SET BODY -> 1");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'ARCHIVE_FOR_DAYS' for 'PRIVACY_POLICY'",
            "ALTER PRIVACY POLICY pv956 SET ARCHIVE_FOR_DAYS = 90");
        assertAnswer("Statement executed successfully.", "ALTER PRIVACY POLICY pv956 UNSET COMMENT");
        assertAnswer("Statement executed successfully.",
            "ALTER PRIVACY POLICY IF EXISTS nosuch959 SET COMMENT = 'x'");
        assertAnswer("REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.NOSUCH958' does not exist "
            + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
            + "TEST_DB.TEST_SCHEMA.NOSUCH958.", "ALTER PRIVACY POLICY nosuch958 SET COMMENT = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER PRIVACY POLICY pv956 RENAME TO pv956b");
        assertAnswer("PV956B successfully dropped.", "DROP PRIVACY POLICY pv956b");
    }

    @Test
    public void aTableHoldsOnePolicyWithItsEntityKey() {
        engine.execute("CREATE OR REPLACE TABLE kt957 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE PRIVACY POLICY pv957 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY() "
            + "COMMENT = 'pc'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE kt957 ADD PRIVACY POLICY pv957 ENTITY KEY (a)");
        assertAnswer("PV957|PRIVACY_POLICY|KT957|TABLE|null|[ \"A\" ]|ACTIVE", REFERENCES);
        assertAnswer("create or replace TABLE KT957 ( \tA NUMBER(38,0), \tB VARCHAR(16777216) ) WITH PRIVACY POLICY "
            + "TEST_DB.TEST_SCHEMA.PV957 ENTITY KEY (A) ;", "SELECT REPLACE(GET_DDL('TABLE', 'kt957'), CHR(10), ' ')");
        assertAnswer("REFUSED Object KT957 already has a PRIVACY_POLICY. Only one PRIVACY_POLICY is allowed at a "
            + "time.", "ALTER TABLE kt957 ADD PRIVACY POLICY pv957 ENTITY KEY (b)");
        assertAnswer("REFUSED SQL compilation error: Policy PV957 cannot be dropped/replaced as it is associated with "
            + "one or more entities.", "DROP PRIVACY POLICY pv957");
        assertAnswer("REFUSED SQL compilation error: Policy PV957 cannot be dropped/replaced as it is associated with "
            + "one or more entities.",
            "CREATE OR REPLACE PRIVACY POLICY pv957 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("Statement executed successfully.", "ALTER TABLE kt957 DROP PRIVACY POLICY pv957");
        assertAnswer("REFUSED Policy PV957 is not attached to TABLE KT957.",
            "ALTER TABLE kt957 DROP PRIVACY POLICY pv957");
        assertAnswer("REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.PV957X' does not exist or "
            + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
            + "TEST_DB.TEST_SCHEMA.PV957X.", "ALTER TABLE kt957 DROP PRIVACY POLICY pv957x");
        assertAnswer("PV957 successfully dropped.", "DROP PRIVACY POLICY pv957");
    }

    @Test
    public void theEntityKeyIsOneColumnOfTheTable() {
        engine.execute("CREATE OR REPLACE TABLE kt958 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE kt959 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE PRIVACY POLICY pv958 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        engine.execute("CREATE OR REPLACE PRIVACY POLICY pv959 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '<EOF>'.",
            "ALTER TABLE kt958 ADD PRIVACY POLICY pv958");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','.",
            "ALTER TABLE kt959 ADD PRIVACY POLICY pv959 ENTITY KEY (a, b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'NOSUCHCOL'",
            "ALTER TABLE kt958 ADD PRIVACY POLICY pv958 ENTITY KEY (nosuchcol)");
        assertAnswer("REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.NOSUCH958' does not exist "
            + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
            + "TEST_DB.TEST_SCHEMA.NOSUCH958.", "ALTER TABLE kt958 ADD PRIVACY POLICY nosuch958 ENTITY KEY (a)");
        assertAnswer("Table KT958C successfully created.",
            "CREATE OR REPLACE TABLE kt958c (a INT) WITH PRIVACY POLICY pv958 ENTITY KEY (a)");
        assertAnswer("create or replace TABLE KT958C ( \tA NUMBER(38,0) ) WITH PRIVACY POLICY "
            + "TEST_DB.TEST_SCHEMA.PV958 ENTITY KEY (A) ;", "SELECT REPLACE(GET_DDL('TABLE', 'kt958c'), CHR(10), ' ')");
        assertAnswer("REFUSED SQL compilation error: | Privacy policy 'TEST_DB.TEST_SCHEMA.NOSUCH959' does not exist "
            + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
            + "TEST_DB.TEST_SCHEMA.NOSUCH959.",
            "CREATE OR REPLACE TABLE kt959x (a INT) WITH PRIVACY POLICY nosuch959 ENTITY KEY (a)");
        assertAnswer("KT958C successfully dropped.", "DROP TABLE kt958c");
        assertAnswer("PV958 successfully dropped.", "DROP PRIVACY POLICY pv958");
        assertAnswer("PV959 successfully dropped.", "DROP PRIVACY POLICY pv959");
    }

    @Test
    public void aNameAnotherPolicyKindHoldsIsRefusedByItsKind() {
        engine.execute("CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959 AS (a INT) RETURNS BOOLEAN -> a > 0");
        engine.execute("CREATE OR REPLACE PRIVACY POLICY pv959 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED SQL compilation error: | Object 'SL959' already exists.",
            "CREATE PRIVACY POLICY sl959 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'STORAGE_LIFECYCLE_POLICY', not "
            + "specified type 'PRIVACY_POLICY'.",
            "CREATE OR REPLACE PRIVACY POLICY sl959 AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'PRIVACY_POLICY', not specified type "
            + "'STORAGE_LIFECYCLE_POLICY'.",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY pv959 AS (a INT) RETURNS BOOLEAN -> a > 0");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'PRIVACY_POLICY', not specified type "
            + "'STORAGE_LIFECYCLE_POLICY'.", "DROP STORAGE LIFECYCLE POLICY pv959");
        assertAnswer("REFUSED SQL compilation error: Object found is of type 'PRIVACY_POLICY', not specified type "
            + "'STORAGE_LIFECYCLE_POLICY'.", "DESCRIBE STORAGE LIFECYCLE POLICY pv959");
        assertAnswer("PV959 successfully dropped.", "DROP PRIVACY POLICY pv959");
        assertAnswer("SL959 successfully dropped.", "DROP STORAGE LIFECYCLE POLICY sl959");
    }
}
