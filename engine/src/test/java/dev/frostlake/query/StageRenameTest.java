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
 * ALTER STAGE … RENAME TO takes a bare or a qualified target: a name in the stage's own schema renames it,
 * and a name in another schema moves it there. A target that is taken — the stage's own name included — a
 * missing stage, schema or database are each refused in the account's words, and IF EXISTS forgives a missing
 * stage alone. Every answer is live-verified.
 */
public class StageRenameTest extends MeasuredCellsTestSupport {

    @Test
    public void aStageRenamedAndMoved() {
        engine.execute("CREATE OR REPLACE SCHEMA rn_s");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE OR REPLACE STAGE rn_a");
        engine.execute("CREATE OR REPLACE STAGE rn_b");
        engine.execute("CREATE OR REPLACE STAGE rn_c");
        engine.execute("CREATE OR REPLACE STAGE rn_d");
        engine.execute("CREATE OR REPLACE STAGE rn_e");
        engine.execute("CREATE OR REPLACE STAGE rn_taken");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_a RENAME TO rn_a2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_b RENAME TO test_db.test_schema.rn_b2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_c RENAME TO test_schema.rn_c2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_d RENAME TO rn_s.rn_d2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE test_db.test_schema.rn_e RENAME TO rn_e2");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'RN_TAKEN' already exists.",
            "ALTER STAGE rn_a2 RENAME TO rn_taken");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TEST_SCHEMA.RN_TAKEN' already exists.",
            "ALTER STAGE rn_a2 RENAME TO test_schema.rn_taken");
        assertAnswer(
            "REFUSED SQL compilation error: | Stage 'TEST_DB.TEST_SCHEMA.RN_NOPE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                + "TEST_DB.TEST_SCHEMA.RN_NOPE.",
            "ALTER STAGE rn_nope RENAME TO rn_x");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE IF EXISTS rn_nope RENAME TO rn_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOS' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOS.",
            "ALTER STAGE rn_a2 RENAME TO nos.rn_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Database 'NODB' does not exist or not authorized. Your primary "
                + "role <ROLE> must have USAGE or any other privilege granted on DATABASE NODB.",
            "ALTER STAGE rn_a2 RENAME TO nodb.test_schema.rn_x");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'RN_A2' already exists.",
            "ALTER STAGE rn_a2 RENAME TO rn_a2");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_a2 RENAME TO \"rn_q\"");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE \"rn_q\" RENAME TO rn_a2");
        engine.execute("SHOW STAGES IN SCHEMA test_schema");
        assertAnswer(
            "RN_A2 ;; RN_B2 ;; RN_C2 ;; RN_E2 ;; RN_TAKEN",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"name\" LIKE 'RN%' ORDER BY 1");
        engine.execute("SHOW STAGES IN SCHEMA rn_s");
        assertAnswer(
            "RN_D2",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER STAGE rn_s.rn_d2 RENAME TO test_schema.rn_d");
        assertAnswer(
            "",
            "LIST @rn_d");
    }
}
