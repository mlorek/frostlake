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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * POLICY_REFERENCES refuses a missing database, schema, policy or entity as a statement over its kind refuses it,
 * the privilege sentence included; TAG_REFERENCES and SYSTEM$GET_TAG refuse an object of a kind this engine models
 * none of by that kind's noun (live-verified).
 */
public class ReferenceFunctionMissingObjectTest extends MeasuredCellsTestSupport {

    private static final String PR = "SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(";

    private static final String TR = "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('anything', '";

    private static String missing(final String kind, final String name, final String on) {
        return "REFUSED SQL compilation error: | " + kind + " '" + name + "' does not exist or not authorized. Your "
            + "primary role <ROLE> must have " + on + ".";
    }

    private static String any(final String kind, final String name, final String on) {
        return missing(kind, name, "at least one privilege granted on " + on + " " + name);
    }

    @Test
    public void policyReferencesNamesTheMissingContainerOrObjectWithTheHint() {
        assertCells(new String[][] {
            {PR + "POLICY_NAME => 'nosuch_pol'))", any("Policy", "TEST_DB.TEST_SCHEMA.NOSUCH_POL", "POLICY")},
            {PR + "POLICY_NAME => 'nosuch_db1449.s.p'))", missing("Database", "NOSUCH_DB1449",
                "USAGE or any other privilege granted on DATABASE NOSUCH_DB1449")},
            {PR + "POLICY_NAME => 'test_db.nosuch_s1449.p'))", missing("Schema", "TEST_DB.NOSUCH_S1449",
                "USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S1449")},
            {PR + "REF_ENTITY_NAME => 'nosuch_t', REF_ENTITY_DOMAIN => 'table'))", any("Table", "NOSUCH_T", "TABLE")},
            {PR + "REF_ENTITY_NAME => 'nosuch_t', REF_ENTITY_DOMAIN => 'view'))", missing("View", "NOSUCH_T",
                "at least one privilege granted on TABLE NOSUCH_T")},
            {PR + "REF_ENTITY_NAME => 'test_db.test_schema.nosuch_t', REF_ENTITY_DOMAIN => 'table'))",
                any("Table", "TEST_DB.TEST_SCHEMA.NOSUCH_T", "TABLE")},
            {PR + "REF_ENTITY_NAME => '\"nosuch_t\"', REF_ENTITY_DOMAIN => 'table'))", any("Table", "\"nosuch_t\"",
                "TABLE")},
        });
    }

    @Test
    public void policyReferencesReadsADomainAsTheReferenceFunctionsDo() {
        answer("CREATE OR REPLACE TABLE pr1449 (a INT)");
        final String invalid = "REFUSED SQL compilation error: Invalid value '%s' for argument 'ref_entity_domain' for "
            + "function 'information_schema.policy_references'";
        final String entity = PR + "REF_ENTITY_NAME => 'pr1449', REF_ENTITY_DOMAIN => '%s'))";
        assertCells(new String[][] {
            {String.format(entity, "NONSENSE"), "REFUSED Unknown domain: NONSENSE."},
            {String.format(entity, "nonsense"), "REFUSED Unknown domain: nonsense."},
            {String.format(entity, " table"), "REFUSED Unknown domain:  table."},
            {String.format(entity, "stage"), String.format(invalid, "STAGE")},
            {String.format(entity, "COLUMN"), String.format(invalid, "COLUMN")},
            {String.format(entity, "MATERIALIZED VIEW"), String.format(invalid, "MATERIALIZED_VIEW")},
            {String.format(entity, "DYNAMIC TABLE"), String.format(invalid, "DYNAMIC_TABLE")},
            {String.format(entity, "EXTERNAL TABLE"), ""},
            {String.format(entity, "TAG"), any("Tag", "PR1449", "TAG")},
            {String.format(entity, "USER"), missing("User", "PR1449", "USAGE granted on ACCOUNT <ACCOUNT>")},
            {String.format(entity, "ACCOUNT"), missing("Account", "PR1449",
                "at least one privilege granted on ACCOUNT <ACCOUNT>")},
        });
    }

    @Test
    public void aDomainThisEngineModelsNoObjectOfIsRefusedByItsNoun() {
        assertCells(new String[][] {
            {TR + "SHARE'))", any("Share", "\"<ACCOUNT>.ANYTHING\"", "SHARE")},
            {TR + "ACCOUNT'))", missing("Account", "ANYTHING", "at least one privilege granted on ACCOUNT <ACCOUNT>")},
            {TR + "FAILOVER GROUP'))", any("Replication group", "ANYTHING", "REPLICATION GROUP")},
            {TR + "REPLICATION GROUP'))", any("Replication group", "ANYTHING", "REPLICATION GROUP")},
            {TR + "INSTANCE'))", any("Instance", "TEST_DB.TEST_SCHEMA.ANYTHING", "INSTANCE")},
            {TR + "SNAPSHOT POLICY'))", any("Backup Policy", "TEST_DB.TEST_SCHEMA.ANYTHING", "BACKUP POLICY")},
            {TR + "BACKUP SET'))", any("Backup Set", "TEST_DB.TEST_SCHEMA.ANYTHING", "BACKUP SET")},
            {TR + "CORTEX AGENT'))", any("Agent", "TEST_DB.TEST_SCHEMA.ANYTHING", "AGENT")},
            {TR + "CORTEX_SEARCH_SERVICE'))", any("Cortex Search Service", "ANYTHING", "CORTEX SEARCH SERVICE")},
            {TR + "SNOWFLAKE INTELLIGENCE'))", any("Snowflake Intelligence", "ANYTHING", "SNOWFLAKE INTELLIGENCE")},
            {"SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.anything', 'INSTANCE'))",
                missing("Schema", "TEST_DB.TEST_DB", "USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB")},
            {"SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"anything\"', 'CORTEX AGENT'))",
                any("Agent", "TEST_DB.TEST_SCHEMA.\"anything\"", "AGENT")},
        });
        answer("CREATE OR REPLACE TAG tg1449");
        assertCells(new String[][] {
            {"SELECT SYSTEM$GET_TAG('tg1449', 'nosuch', 'CORTEX SEARCH SERVICE')", any("Cortex Search Service",
                "NOSUCH", "CORTEX SEARCH SERVICE")},
            {"SELECT SYSTEM$GET_TAG('tg1449', 'nosuch', 'ACCOUNT')", missing("Account", "NOSUCH",
                "at least one privilege granted on ACCOUNT <ACCOUNT>")},
            {"SELECT SYSTEM$GET_TAG('tg1449', 'nosuch', 'REPLICATION GROUP')", any("Replication group", "NOSUCH",
                "REPLICATION GROUP")},
        });
    }
}
