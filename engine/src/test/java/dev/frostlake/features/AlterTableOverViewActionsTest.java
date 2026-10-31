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
 * ALTER TABLE over a view (live-verified). A constraint's ALTER, MODIFY or DROP, a key's and a dropped search
 * optimization are cannot use ALTER TABLE on a view, a renamed constraint is one the view does not have, an added
 * search optimization a materialized view's refused at the view's name, and DATA_METRIC_SCHEDULE refused by the view's
 * kind - IF EXISTS forgives none of these. The view's row access, aggregation and join policies, its tags and its
 * columns', its data metric functions and its contacts are set and unset as ALTER VIEW sets them.
 */
public class AlterTableOverViewActionsTest extends MeasuredCellsTestSupport {

    /** The actions refused over a view, and the ones applied to it. */
    @Test
    public void refusedActions() {
        engine.execute("CREATE OR REPLACE TABLE b805 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE VIEW v805 AS SELECT a, b FROM b805");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap805 AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap805 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 1)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp805 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE TAG tg805");
        engine.execute("CREATE OR REPLACE CONTACT ct805 EMAIL_DISTRIBUTION_LIST = 'p805@example.com'");
        assertCells(new String[][] {
            {"ALTER TABLE v805 RENAME CONSTRAINT c1 TO c2",
                "REFUSED SQL compilation error: | constraint 'C1' does not exist"},
            {"ALTER TABLE v805 ALTER CONSTRAINT c1 ENFORCED",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 MODIFY CONSTRAINT c1 RELY",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 DROP CONSTRAINT c1",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 DROP PRIMARY KEY",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 DROP UNIQUE (a)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 DROP FOREIGN KEY (a)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 ADD ROW ACCESS POLICY rap805 ON (a)",
                "Statement executed successfully."},
            {"ALTER TABLE v805 DROP ROW ACCESS POLICY rap805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 DROP ALL ROW ACCESS POLICIES",
                "Statement executed successfully."},
            {"ALTER TABLE v805 SET AGGREGATION POLICY ap805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 UNSET AGGREGATION POLICY",
                "Statement executed successfully."},
            {"ALTER TABLE v805 SET JOIN POLICY jp805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 UNSET JOIN POLICY",
                "Statement executed successfully."},
            {"ALTER TABLE v805 SET TAG tg805 = 'x'",
                "Statement executed successfully."},
            {"ALTER TABLE v805 UNSET TAG tg805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 MODIFY COLUMN a SET TAG tg805 = 'y'",
                "Statement executed successfully."},
            {"ALTER TABLE v805 MODIFY COLUMN a UNSET TAG tg805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 ADD SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: error line 1 at position 12 | Invalid materialized view definition. "
                + "'V805' should be a table."},
            {"ALTER TABLE v805 DROP SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v805 ADD SEARCH OPTIMIZATION ON EQUALITY(a)",
                "REFUSED SQL compilation error: error line 1 at position 12 | Invalid materialized view definition. "
                + "'V805' should be a table."},
            {"ALTER TABLE v805 SET DATA_METRIC_SCHEDULE = '60 MINUTE'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE v805 ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "Statement executed successfully."},
            {"ALTER TABLE v805 DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "Statement executed successfully."},
            {"ALTER TABLE v805 SET CONTACT STEWARD = ct805",
                "Statement executed successfully."},
            {"ALTER TABLE v805 UNSET CONTACT STEWARD",
                "Statement executed successfully."},
            {"ALTER TABLE nosuch805 SET CONTACT STEWARD = ct805",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH805' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH805."},
            {"ALTER TABLE nosuch805 DROP PRIMARY KEY",
                "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH805' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH805."},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('v805', 'table'))",
                "0"},
        });
    }

    /** Each applied action, what it leaves, and the forms of IF EXISTS. */
    @Test
    public void appliedActionsAndWhatTheyLeave() {
        engine.execute("CREATE OR REPLACE TABLE bv808 (a INT, b INT)");
        engine.execute("CREATE OR REPLACE VIEW v808 AS SELECT a, b FROM bv808");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap808 AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap808 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 1)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap808b AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 1)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp808 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE OR REPLACE TAG tg808");
        engine.execute("CREATE OR REPLACE CONTACT ct808 EMAIL_DISTRIBUTION_LIST = 'w808@example.com'");
        assertCells(new String[][] {
            {"ALTER TABLE v808 ALTER PRIMARY KEY ENFORCED",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v808 MODIFY UNIQUE (a) RELY",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE v808 ALTER FOREIGN KEY (a) NOT ENFORCED",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE IF EXISTS v808 RENAME CONSTRAINT c1 TO c2",
                "REFUSED SQL compilation error: | constraint 'C1' does not exist"},
            {"ALTER TABLE IF EXISTS v808 DROP CONSTRAINT c1",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE IF EXISTS v808 ADD SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid materialized view definition. "
                + "'V808' should be a table."},
            {"ALTER TABLE IF EXISTS v808 SET DATA_METRIC_SCHEDULE = '60 MINUTE'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE IF EXISTS v808 SET TAG tg808 = 'i'",
                "Statement executed successfully."},
            {"ALTER TABLE v808 UNSET TAG tg808",
                "Statement executed successfully."},
            {"ALTER TABLE v808 SET CONTACT STEWARD = nosuch808",
                "REFUSED SQL compilation error: | Contact 'TEST_DB.TEST_SCHEMA.NOSUCH808' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on CONTACT "
                + "TEST_DB.TEST_SCHEMA.NOSUCH808."},
            {"ALTER TABLE v808 SET CONTACT STEWARD = ct808",
                "Statement executed successfully."},
            {"SELECT purpose, level FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.v808', 'VIEW'))",
                "STEWARD|TABLE"},
            {"SELECT purpose, level FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.v808', 'TABLE'))",
                "STEWARD|TABLE"},
            {"ALTER TABLE v808 UNSET CONTACT STEWARD",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.v808', 'VIEW'))",
                "0"},
            {"ALTER VIEW v808 SET CONTACT STEWARD = ct808",
                "Statement executed successfully."},
            {"SELECT purpose, level FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('test_db.test_schema.v808', 'VIEW'))",
                "STEWARD|TABLE"},
            {"ALTER VIEW v808 UNSET CONTACT STEWARD",
                "Statement executed successfully."},
            {"ALTER TABLE v808 SET TAG tg808 = 'x'",
                "Statement executed successfully."},
            {"SELECT SYSTEM$GET_TAG('tg808', 'v808', 'TABLE')",
                "x"},
            {"SELECT tag_name, tag_value, domain FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('v808', 'table'))",
                "TG808|x|TABLE"},
            {"ALTER TABLE v808 MODIFY COLUMN a SET TAG tg808 = 'y'",
                "Statement executed successfully."},
            {"SELECT tag_value, column_name, domain FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('v808.a', 'column'))",
                "y|A|COLUMN"},
            {"ALTER TABLE v808 MODIFY COLUMN nosuch SET TAG tg808 = 'y'",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE v808 SET TAG nosuchtag808 = 'x'",
                "REFUSED SQL compilation error: | Tag 'NOSUCHTAG808' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCHTAG808."},
            {"ALTER TABLE v808 MODIFY COLUMN a UNSET TAG tg808",
                "Statement executed successfully."},
            {"ALTER TABLE v808 UNSET TAG tg808",
                "Statement executed successfully."},
            {"ALTER TABLE v808 ADD ROW ACCESS POLICY rap808 ON (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE v808 ADD ROW ACCESS POLICY rap808 ON (a)",
                "Statement executed successfully."},
            {"SELECT policy_name, policy_kind, ref_entity_domain, ref_arg_column_names FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.v808', "
                + "REF_ENTITY_DOMAIN => 'view')) ORDER BY 1",
                "RAP808|ROW_ACCESS_POLICY|VIEW|[ \"A\" ]"},
            {"ALTER TABLE v808 ADD ROW ACCESS POLICY rap808 ON (a)",
                "REFUSED Object V808 already has a ROW_ACCESS_POLICY. Only one ROW_ACCESS_POLICY is allowed at a "
                + "time."},
            {"ALTER TABLE v808 SET AGGREGATION POLICY ap808",
                "Statement executed successfully."},
            {"ALTER TABLE v808 SET AGGREGATION POLICY ap808b",
                "REFUSED Object V808 already has a AGGREGATION_POLICY. Only one AGGREGATION_POLICY is allowed at a "
                + "time."},
            {"ALTER TABLE v808 SET JOIN POLICY nosuch808",
                "REFUSED SQL compilation error: | Join policy 'TEST_DB.TEST_SCHEMA.NOSUCH808' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH808."},
            {"ALTER TABLE v808 SET JOIN POLICY jp808",
                "Statement executed successfully."},
            {"SELECT policy_name, policy_kind, ref_entity_domain, ref_arg_column_names FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.v808', "
                + "REF_ENTITY_DOMAIN => 'view')) ORDER BY 1",
                "AP808|AGGREGATION_POLICY|VIEW|null ;; JP808|JOIN_POLICY|VIEW|null ;; RAP808|ROW_ACCESS_POLICY|VIEW|[ "
                + "\"A\" ]"},
            {"ALTER TABLE v808 UNSET AGGREGATION POLICY",
                "Statement executed successfully."},
            {"ALTER TABLE v808 UNSET AGGREGATION POLICY",
                "REFUSED Any policy of kind AGGREGATION_POLICY is not attached to TABLE V808."},
            {"ALTER TABLE v808 UNSET JOIN POLICY",
                "Statement executed successfully."},
            {"ALTER TABLE v808 UNSET JOIN POLICY",
                "REFUSED Any policy of kind JOIN_POLICY is not attached to TABLE V808."},
            {"ALTER TABLE v808 DROP ROW ACCESS POLICY rap808",
                "Statement executed successfully."},
            {"ALTER TABLE v808 DROP ROW ACCESS POLICY rap808",
                "REFUSED Policy RAP808 is not attached to TABLE V808."},
            {"SELECT policy_name, policy_kind, ref_entity_domain, ref_arg_column_names FROM "
                + "TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'test_db.test_schema.v808', "
                + "REF_ENTITY_DOMAIN => 'view')) ORDER BY 1",
                ""},
            {"ALTER TABLE v808 ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "Statement executed successfully."},
            {"SELECT metric_name, ref_entity_domain FROM "
                + "TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'test_db.test_schema.v808', REF_ENTITY_DOMAIN => 'view'))",
                "NULL_COUNT|View"},
            {"ALTER TABLE v808 MODIFY DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a) SUSPEND",
                "Statement executed successfully."},
            {"ALTER TABLE v808 DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "Statement executed successfully."},
            {"ALTER TABLE v808 DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "REFUSED SQL compilation error: Data metric function NULL_COUNT is not attached to View V808."},
            {"ALTER TABLE v808 DROP SEARCH OPTIMIZATION ON EQUALITY(a)",
                "REFUSED SQL compilation error: | cannot use ALTER TABLE on a view; use ALTER VIEW instead"},
            {"ALTER TABLE test_db.test_schema.v808 ADD SEARCH OPTIMIZATION",
                "REFUSED SQL compilation error: error line 1 at position 12 | Invalid materialized view definition. "
                + "'TEST_DB.TEST_SCHEMA.V808' should be a table."},
            {"ALTER TABLE v808 UNSET DATA_METRIC_SCHEDULE",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"ALTER TABLE v808 SET DATA_METRIC_SCHEDULE = '60 MINUTE' COMMENT = 'c'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
        });
    }
}
