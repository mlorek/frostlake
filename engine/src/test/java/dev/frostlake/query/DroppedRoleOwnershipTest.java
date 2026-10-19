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
 * A dropped role hands what it owned to the role that dropped it: its tables, views, sequences, stages,
 * functions and schemas read the dropping role as their owner straight after the DROP ROLE, and that role may
 * replace and drop them. The privileges the dropped role held go with it, those another role holds stay, and a
 * role created anew under the name holds nothing. Every answer is live-verified.
 */
public class DroppedRoleOwnershipTest extends MeasuredCellsTestSupport {

    @Test
    public void whatADroppedRoleOwnedIsTheDroppingRoles() {
        try {
            engine.execute("DROP ROLE IF EXISTS flq_rp_owner");
            engine.execute("DROP ROLE IF EXISTS flq_rp_other");
            engine.execute("CREATE ROLE flq_rp_owner");
            engine.execute("CREATE ROLE flq_rp_other");
            engine.execute("CREATE OR REPLACE SCHEMA rp_s");
            engine.execute("USE SCHEMA test_schema");
            engine.execute("CREATE OR REPLACE TABLE rp_t (a INT)");
            engine.execute("CREATE OR REPLACE VIEW rp_v AS SELECT a FROM rp_t");
            engine.execute("CREATE OR REPLACE SEQUENCE rp_q");
            engine.execute("CREATE OR REPLACE STAGE rp_st");
            engine.execute("CREATE OR REPLACE FUNCTION rp_f() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE TABLE rp_kept (a INT)");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE rp_t TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON VIEW rp_v TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON SEQUENCE rp_q TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON STAGE rp_st TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON FUNCTION rp_f() TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON SCHEMA rp_s TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rp_kept TO ROLE flq_rp_owner");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rp_kept TO ROLE flq_rp_other");
            assertAnswer(
                "FLQ_RP_OWNER successfully dropped.",
                "DROP ROLE flq_rp_owner");
            assertAnswer(
                "RP_KEPT|true ;; RP_T|true ;; RP_V|true",
                "SELECT table_name, table_owner = CURRENT_ROLE() FROM information_schema.tables WHERE table_name "
                + "IN ('RP_T', 'RP_V', 'RP_KEPT') ORDER BY 1");
            assertAnswer(
                "true",
                "SELECT schema_owner = CURRENT_ROLE() FROM information_schema.schemata WHERE schema_name = 'RP_S'");
            assertAnswer(
                "true",
                "SELECT sequence_owner = CURRENT_ROLE() FROM information_schema.sequences WHERE sequence_name = 'RP_Q'");
            assertAnswer(
                "true",
                "SELECT stage_owner = CURRENT_ROLE() FROM information_schema.stages WHERE stage_name = 'RP_ST'");
            assertAnswer(
                "true",
                "SELECT function_owner = CURRENT_ROLE() FROM information_schema.functions WHERE function_name = 'RP_F'");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM rp_t");
            assertAnswer(
                "Table RP_T successfully created.",
                "CREATE OR REPLACE TABLE rp_t (a INT, b INT)");
            assertAnswer(
                "RP_V successfully dropped.",
                "DROP VIEW rp_v");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM information_schema.table_privileges WHERE table_name = 'RP_KEPT' AND "
                + "grantee = 'FLQ_RP_OWNER'");
            assertAnswer(
                "SELECT",
                "SELECT privilege_type FROM information_schema.table_privileges WHERE table_name = 'RP_KEPT' AND "
                + "grantee = 'FLQ_RP_OTHER'");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'FLQ_RP_OWNER' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE FLQ_RP_OWNER.",
                "DROP ROLE flq_rp_owner");
            assertAnswer(
                "Drop statement executed successfully (FLQ_RP_OWNER already dropped).",
                "DROP ROLE IF EXISTS flq_rp_owner");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'FLQ_RP_OWNER' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE FLQ_RP_OWNER.",
                "GRANT SELECT ON TABLE rp_kept TO ROLE flq_rp_owner");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'FLQ_RP_OWNER' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE FLQ_RP_OWNER.",
                "GRANT OWNERSHIP ON TABLE rp_kept TO ROLE flq_rp_owner");
            assertAnswer(
                "Role FLQ_RP_OWNER successfully created.",
                "CREATE ROLE flq_rp_owner");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee = 'FLQ_RP_OWNER'");
        } finally {
            engine.execute("DROP ROLE IF EXISTS flq_rp_owner");
            engine.execute("DROP ROLE IF EXISTS flq_rp_other");
        }
    }
}
