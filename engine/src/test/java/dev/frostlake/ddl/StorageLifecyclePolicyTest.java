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
 * A storage lifecycle policy: a BOOLEAN body over one argument or more, an archive tier and the days an archived row
 * is kept, listed, described, altered and dropped as the schema's other policies are, and attached to a table ON the
 * columns its arguments read. Every answer is live-verified.
 */
public class StorageLifecyclePolicyTest extends MeasuredCellsTestSupport {

    private static final String SL = "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959 AS (a INT) RETURNS BOOLEAN "
        + "-> a > 0";

    private static final String LISTED = "SELECT \"name\", \"database_name\", \"schema_name\", \"kind\", \"comment\", "
        + "\"owner_role_type\", \"options\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    private static final String DESCRIBED = "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    /** Asserts what DESCRIBE answers for the policy, read back through RESULT_SCAN. */
    private void assertDescribed(final String expected, final String policy) {
        engine.execute("DESCRIBE STORAGE LIFECYCLE POLICY " + policy);
        assertAnswer(expected, DESCRIBED);
    }

    @Test
    public void aPolicyIsCreatedListedAndDescribed() {
        assertAnswer("Storage lifecycle policy 'SL958' created successfully.",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl958 AS (a INT) RETURNS BOOLEAN -> a > 0 ARCHIVE_TIER = COOL "
                + "ARCHIVE_FOR_DAYS = 90 COMMENT = 'sc'");
        engine.execute("SHOW STORAGE LIFECYCLE POLICIES LIKE 'SL958'");
        assertAnswer("SL958|TEST_DB|TEST_SCHEMA|STORAGE_LIFECYCLE_POLICY|sc|ROLE|{\"ARCHIVE_FOR_DAYS\":90,"
            + "\"ARCHIVE_TIER\":\"COOL\"}", LISTED);
        assertDescribed("SL958|(A NUMBER)|BOOLEAN|a > 0|90|COOL", "sl958");
        assertAnswer("create or replace storage lifecycle policy SL958 as (A NUMBER(38,0))  | returns BOOLEAN -> | "
            + "a > 0 | COMMENT='sc' | ARCHIVE_FOR_DAYS=90 | ARCHIVE_TIER='COOL' | ;",
            "SELECT GET_DDL('POLICY', 'sl958')");
        assertAnswer("Storage lifecycle policy 'SL959B' created successfully.",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959b AS (a INT, b VARCHAR(10)) RETURNS BOOLEAN -> a > 0 AND "
                + "b <> 'x'");
        assertDescribed("SL959B|(A NUMBER, B VARCHAR)|BOOLEAN|a > 0 AND b <> 'x'|null|NULL", "sl959b");
        engine.execute("SHOW TERSE STORAGE LIFECYCLE POLICIES LIKE 'SL959B'");
        assertAnswer("SL959B|TEST_DB|TEST_SCHEMA|STORAGE_LIFECYCLE_POLICY||ROLE|{\"ARCHIVE_FOR_DAYS\":null,"
            + "\"ARCHIVE_TIER\":\"NULL\"}", LISTED);
        assertAnswer("create or replace storage lifecycle policy SL959B as (A NUMBER(38,0), B VARCHAR(10))  | returns "
            + "BOOLEAN -> | a > 0 AND b <> 'x' | ;", "SELECT GET_DDL('POLICY', 'sl959b')");
        assertAnswer("SL958 successfully dropped.", "DROP STORAGE LIFECYCLE POLICY sl958");
        assertAnswer("Drop statement executed successfully (SL958 already dropped).",
            "DROP STORAGE LIFECYCLE POLICY IF EXISTS sl958");
    }

    @Test
    public void theSignatureTheBodyAndTheReturnTypeAreJudgedBeforeTheProperties() {
        assertAnswer("REFUSED SQL compilation error: Storage lifecycle policy must have at least one argument.",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959x AS () RETURNS BOOLEAN -> TRUE ARCHIVE_TIER = HOT");
        assertAnswer("REFUSED SQL compilation error: Storage lifecycle policy return type 'NUMBER(38,0)' is not "
            + "BOOLEAN.",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959x AS (a INT) RETURNS INT -> a ARCHIVE_TIER = HOT");
        assertAnswer("REFUSED Declared return type 'BOOLEAN' is incompatible with actual return type 'VARCHAR(1)'",
            "CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl959x AS (a INT) RETURNS BOOLEAN -> 'x' ARCHIVE_TIER = HOT");
        assertAnswer("Drop statement executed successfully (SL959X already dropped).",
            "DROP STORAGE LIFECYCLE POLICY IF EXISTS sl959x");
    }

    @Test
    public void theArchivePropertiesAreJudgedTogether() {
        assertAnswer("REFUSED SQL compilation error: | invalid value 'HOT' for property 'ARCHIVE_TIER'",
            SL + " ARCHIVE_TIER = HOT");
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='90' and "
            + "'ARCHIVE_TIER'='NULL'", SL + " ARCHIVE_FOR_DAYS = 90");
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='NULL' and "
            + "'ARCHIVE_TIER'='COOL'", SL + " ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED Invalid value '89' for property 'ARCHIVE_FOR_DAYS' when using 'COOL' archive tier . It "
            + "must be at least '90' days.", SL + " ARCHIVE_TIER = COOL ARCHIVE_FOR_DAYS = 89");
        assertAnswer("REFUSED Invalid value '179' for property 'ARCHIVE_FOR_DAYS' when using 'COLD' archive tier . "
            + "It must be at least '180' days.", SL + " ARCHIVE_TIER = COLD ARCHIVE_FOR_DAYS = 179");
        assertAnswer("REFUSED Invalid value '-1' for property 'ARCHIVE_FOR_DAYS' when using 'COOL' archive tier . It "
            + "must be at least '90' days.", SL + " ARCHIVE_FOR_DAYS = -1 ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'ARCHIVE_FOR_DAYS'",
            SL + " ARCHIVE_FOR_DAYS = 'x' ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid value [90.5] for parameter 'ARCHIVE_FOR_DAYS'",
            SL + " ARCHIVE_FOR_DAYS = 90.5 ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['120'] for parameter 'ARCHIVE_FOR_DAYS'",
            SL + " ARCHIVE_FOR_DAYS = '120' ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid value [2147483648] for parameter 'ARCHIVE_FOR_DAYS'",
            SL + " ARCHIVE_FOR_DAYS = 2147483648 ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'x' for 'STORAGE_LIFECYCLE_POLICY'",
            SL + " x = 1");
        assertAnswer("REFUSED SQL compilation error: | duplicate property 'COMMENT';",
            SL + " COMMENT = 'a' COMMENT = 'b'");
        assertAnswer("REFUSED SQL compilation error: | duplicate property 'ARCHIVE_TIER';",
            SL + " ARCHIVE_TIER = COOL ARCHIVE_TIER = COOL ARCHIVE_FOR_DAYS = 90");
        assertAnswer("Storage lifecycle policy 'SL959' created successfully.",
            SL + " ARCHIVE_TIER = COLD ARCHIVE_FOR_DAYS = 180");
        engine.execute("SHOW STORAGE LIFECYCLE POLICIES LIKE 'SL959'");
        assertAnswer("SL959|TEST_DB|TEST_SCHEMA|STORAGE_LIFECYCLE_POLICY||ROLE|{\"ARCHIVE_FOR_DAYS\":180,"
            + "\"ARCHIVE_TIER\":\"COLD\"}", LISTED);
        assertAnswer("Storage lifecycle policy 'SL959' created successfully.",
            SL + " ARCHIVE_TIER = 'COOL' ARCHIVE_FOR_DAYS = 90");
        assertAnswer("Storage lifecycle policy 'SL959' created successfully.",
            SL + " ARCHIVE_TIER = cool ARCHIVE_FOR_DAYS = 90");
        assertDescribed("SL959|(A NUMBER)|BOOLEAN|a > 0|90|COOL", "sl959");
    }

    @Test
    public void anAlterSetsTheTierWithItsDaysAndTheDaysAgainstTheTier() {
        engine.execute(SL + " ARCHIVE_TIER = COOL ARCHIVE_FOR_DAYS = 90 COMMENT = 'it''s'");
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='NULL' and "
            + "'ARCHIVE_TIER'='COLD'", "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_TIER = COLD");
        assertAnswer("REFUSED Invalid value '89' for property 'ARCHIVE_FOR_DAYS' when using 'COOL' archive tier . It "
            + "must be at least '90' days.", "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_FOR_DAYS = 89");
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='NULL' and "
            + "'ARCHIVE_TIER'='COOL'", "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_TIER = COOL");
        assertAnswer("Statement executed successfully.",
            "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_FOR_DAYS = 120");
        assertDescribed("SL959|(A NUMBER)|BOOLEAN|a > 0|120|COOL", "sl959");
        assertAnswer("Statement executed successfully.", "ALTER STORAGE LIFECYCLE POLICY sl959 UNSET ARCHIVE_FOR_DAYS");
        assertDescribed("SL959|(A NUMBER)|BOOLEAN|a > 0|null|COOL", "sl959");
        assertAnswer("REFUSED SQL compilation error: | invalid value 'NULL' for property 'ARCHIVE_TIER'",
            "ALTER STORAGE LIFECYCLE POLICY sl959 UNSET ARCHIVE_TIER");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'x' for 'STORAGE_LIFECYCLE_POLICY'",
            "ALTER STORAGE LIFECYCLE POLICY sl959 SET x = 1");
        assertAnswer("REFUSED Declared return type 'BOOLEAN' is incompatible with actual return type 'VARCHAR(1)'",
            "ALTER STORAGE LIFECYCLE POLICY sl959 SET BODY -> 'x'");
        assertAnswer("create or replace storage lifecycle policy SL959 as (A NUMBER(38,0))  | returns BOOLEAN -> | "
            + "a > 0 | COMMENT='it''s' | ARCHIVE_TIER='COOL' | ;", "SELECT GET_DDL('POLICY', 'sl959')");
        engine.execute(SL);
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='NULL' and "
            + "'ARCHIVE_TIER'='COOL'", "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_TIER = COOL");
        assertAnswer("REFUSED SQL compilation error: | invalid property combination 'ARCHIVE_FOR_DAYS'='90' and "
            + "'ARCHIVE_TIER'='NULL'", "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_FOR_DAYS = 90");
        assertAnswer("Statement executed successfully.",
            "ALTER STORAGE LIFECYCLE POLICY sl959 SET ARCHIVE_TIER = COOL, ARCHIVE_FOR_DAYS = 90");
        assertDescribed("SL959|(A NUMBER)|BOOLEAN|a > 0|90|COOL", "sl959");
        assertAnswer("Statement executed successfully.", "ALTER STORAGE LIFECYCLE POLICY sl959 SET COMMENT = 'c2'");
        engine.execute("SHOW TERSE STORAGE LIFECYCLE POLICIES LIKE 'SL959'");
        assertAnswer("SL959|TEST_DB|TEST_SCHEMA|STORAGE_LIFECYCLE_POLICY|c2|ROLE|{\"ARCHIVE_FOR_DAYS\":90,"
            + "\"ARCHIVE_TIER\":\"COOL\"}", LISTED);
        assertAnswer("Statement executed successfully.", "ALTER STORAGE LIFECYCLE POLICY sl959 RENAME TO sl959r");
        engine.execute("SHOW STORAGE LIFECYCLE POLICIES IN SCHEMA");
        assertAnswer("SL959R|STORAGE_LIFECYCLE_POLICY",
            "SELECT \"name\", \"kind\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void aTableHoldsOnePolicyOnTheColumnsItsArgumentsRead() {
        engine.execute("CREATE OR REPLACE TABLE kt957 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl957 AS (a INT) RETURNS BOOLEAN -> a > 0 "
            + "COMMENT = 'sc'");
        assertAnswer("Statement executed successfully.", "ALTER TABLE kt957 ADD STORAGE LIFECYCLE POLICY sl957 ON (a)");
        assertAnswer("SL957|STORAGE_LIFECYCLE_POLICY|KT957|TABLE|null|[ \"A\" ]|ACTIVE",
            "SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME, REF_ENTITY_DOMAIN, REF_COLUMN_NAME, "
                + "REF_ARG_COLUMN_NAMES, POLICY_STATUS FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                + "REF_ENTITY_NAME => 'TEST_DB.TEST_SCHEMA.KT957', REF_ENTITY_DOMAIN => 'TABLE'))");
        assertAnswer("create or replace TABLE KT957 ( \tA NUMBER(38,0), \tB VARCHAR(16777216) ) WITH STORAGE "
            + "LIFECYCLE POLICY TEST_DB.TEST_SCHEMA.SL957 ON (A) ;",
            "SELECT REPLACE(GET_DDL('TABLE', 'kt957'), CHR(10), ' ')");
        assertAnswer("REFUSED Object KT957 already has a STORAGE_LIFECYCLE_POLICY. Only one STORAGE_LIFECYCLE_POLICY "
            + "is allowed at a time.", "ALTER TABLE kt957 ADD STORAGE LIFECYCLE POLICY sl957 ON (a)");
        assertAnswer("REFUSED SQL compilation error: Policy SL957 cannot be dropped/replaced as it is associated with "
            + "one or more entities.", "DROP STORAGE LIFECYCLE POLICY sl957");
        assertAnswer("REFUSED SQL compilation error: Cannot drop column 'A' which is associated with a Storage "
            + "lifecycle policy.", "ALTER TABLE kt957 DROP COLUMN a");
        assertAnswer("Successfully detached storage lifecycle policy 'TEST_DB.TEST_SCHEMA.SL957' from the table.",
            "ALTER TABLE kt957 DROP STORAGE LIFECYCLE POLICY");
        assertAnswer("No storage lifecycle policy attached.", "ALTER TABLE kt957 DROP STORAGE LIFECYCLE POLICY");
        assertAnswer("SL957 successfully dropped.", "DROP STORAGE LIFECYCLE POLICY sl957");
        engine.execute("CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl957 AS (a INT, b VARCHAR) RETURNS BOOLEAN -> "
            + "a > 0 AND b IS NOT NULL");
        assertAnswer("REFUSED SQL compilation error: | Invalid number of arguments for attaching policy 'SL957' to "
            + "'KT957', expected 2 arguments, got 1 arguments.",
            "ALTER TABLE kt957 ADD STORAGE LIFECYCLE POLICY sl957 ON (a)");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE kt957 ADD STORAGE LIFECYCLE POLICY sl957 ON (a, b)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE kt957 RENAME COLUMN a TO a2");
        assertAnswer("create or replace TABLE KT957 ( \tA2 NUMBER(38,0), \tB VARCHAR(16777216) ) WITH STORAGE "
            + "LIFECYCLE POLICY TEST_DB.TEST_SCHEMA.SL957 ON (A2, B) ;",
            "SELECT REPLACE(GET_DDL('TABLE', 'kt957'), CHR(10), ' ')");
    }

    @Test
    public void theColumnsAreTheTablesOfTheArgumentsKinds() {
        engine.execute("CREATE OR REPLACE TABLE kt958b (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE STORAGE LIFECYCLE POLICY sl958 AS (a INT) RETURNS BOOLEAN -> a > 0");
        assertAnswer("REFUSED SQL compilation error: | Storage lifecycle policy 'TEST_DB.TEST_SCHEMA.NOSUCH958' does "
            + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
            + "POLICY TEST_DB.TEST_SCHEMA.NOSUCH958.",
            "ALTER TABLE kt958b ADD STORAGE LIFECYCLE POLICY nosuch958 ON (a)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'NOSUCHCOL'",
            "ALTER TABLE kt958b ADD STORAGE LIFECYCLE POLICY sl958 ON (nosuchcol)");
        assertAnswer("REFUSED SQL compilation error: Column 'B' data type 'VARCHAR(16777216)' does not match with "
            + "Storage lifecycle policy data type 'NUMBER(38,0)'.",
            "ALTER TABLE kt958b ADD STORAGE LIFECYCLE POLICY sl958 ON (b)");
        assertAnswer("REFUSED SQL compilation error: | column 'NOSUCHCOL' does not exist",
            "CREATE OR REPLACE TABLE kt959x (a INT) WITH STORAGE LIFECYCLE POLICY sl958 ON (nosuchcol)");
        assertAnswer("Table KT958D successfully created.",
            "CREATE OR REPLACE TABLE kt958d (a INT) WITH STORAGE LIFECYCLE POLICY sl958 ON (a)");
        assertAnswer("create or replace TABLE KT958D ( \tA NUMBER(38,0) ) WITH STORAGE LIFECYCLE POLICY "
            + "TEST_DB.TEST_SCHEMA.SL958 ON (A) ;", "SELECT REPLACE(GET_DDL('TABLE', 'kt958d'), CHR(10), ' ')");
        // A dropped table holds no policy.
        assertAnswer("KT958D successfully dropped.", "DROP TABLE kt958d");
        assertAnswer("SL958 successfully dropped.", "DROP STORAGE LIFECYCLE POLICY sl958");
    }
}
