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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * DROP EVENT TABLE drops an event table, answers a missing one as a drop answers it and refuses a plain
 * table as an object of another type. A column's projection policy is set and unset by ALTER or MODIFY, the
 * word COLUMN optional; setting the policy a column has and unsetting where none is set both succeed. A
 * projection policy is created with its name between quotes. Every answer is live-verified.
 */
public class EventTableDropAndColumnProjectionPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void theEventTableADropNamesAndTheColumnAPolicyIsSetOn() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "Table ET1 successfully created.",
            "CREATE OR REPLACE EVENT TABLE et1");
        assertAnswer(
            "ET1 successfully dropped.",
            "DROP EVENT TABLE et1");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.ET1' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.ET1.",
            "DROP EVENT TABLE et1");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_ET' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ET.",
            "DROP EVENT TABLE nosuch_et");
        assertAnswer(
            "Drop statement executed successfully (NOSUCH_ET already dropped).",
            "DROP EVENT TABLE IF EXISTS nosuch_et");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'TABLE', not specified type 'EVENT_TABLE'.",
            "DROP EVENT TABLE t");
        assertAnswer(
            "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'EVENT_TABLE'.",
            "DROP EVENT TABLE vw");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.NOSUCH_ET' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_ET.",
            "DROP EVENT TABLE test_schema.nosuch_et");
        assertAnswer(
            "Table ET2 successfully created.",
            "CREATE OR REPLACE EVENT TABLE et2");
        assertAnswer(
            "ET2 successfully dropped.",
            "DROP TABLE et2");
        assertAnswer(
            "Table ET3 successfully created.",
            "CREATE OR REPLACE EVENT TABLE et3");
        assertAnswer(
            "ET3 successfully dropped.",
            "DROP EVENT TABLE IF EXISTS et3");
        assertAnswer(
            "REFUSED SQL compilation error: | Event table 'TEST_DB.TEST_SCHEMA.ET3' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.ET3.",
            "DROP EVENT TABLE et3 CASCADE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '<EOF>'.",
            "DROP EVENT TABLE");
        assertAnswer(
            "Projection policy 'PJP1' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY a UNSET PROJECTION POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER a UNSET PROJECTION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY pjp1 FORCE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY pjp1 FORCE");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '<EOF>'.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'pjp1'.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY test_schema.pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "PJP1 successfully dropped.",
            "DROP PROJECTION POLICY pjp1");
    }

    @Test
    public void theSentenceAProjectionPolicyIsCreatedWith() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE VIEW vw AS SELECT a FROM t");
        engine.execute(
            "CREATE OR REPLACE TAG tg");
        assertAnswer(
            "Projection policy 'PJP1' is successfully created.",
            "CREATE PROJECTION POLICY IF NOT EXISTS pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "PJP1 already exists, statement succeeded.",
            "CREATE PROJECTION POLICY IF NOT EXISTS pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PJP1' already exists.",
            "CREATE PROJECTION POLICY pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => "
                + "true)");
        assertAnswer(
            "Projection policy 'PJP1' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "Projection policy 'PJP1' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY test_schema.pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "Projection policy 'pj p' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY \"pj p\" AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "pj p successfully dropped.",
            "DROP PROJECTION POLICY \"pj p\"");
        assertAnswer(
            "",
            "SELECT a FROM vw");
        assertAnswer(
            "PJP1 successfully dropped.",
            "DROP PROJECTION POLICY pjp1");
    }
}
