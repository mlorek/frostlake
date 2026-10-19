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
 * ALTER TABLE ... ALTER | MODIFY COLUMN ... SET MASKING | PROJECTION POLICY resolves the policy before the
 * column: a missing policy is refused whatever the column, the schema of its path before the policy itself,
 * and a missing column only once the policy is found. UNSET names no policy, so the column is what it refuses.
 * The table is found before either. Every answer is live-verified.
 */
public class ColumnPolicyRefusalOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void thePolicyIsResolvedBeforeTheColumn() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE MASKING POLICY mp1 AS (v INT) RETURNS INT -> v");
        engine.execute(
            "CREATE OR REPLACE PROJECTION POLICY pjp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET MASKING POLICY mp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "ALTER TABLE t ALTER COLUMN a SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ALTER COLUMN a SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ALTER COLUMN a SET MASKING POLICY mp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET MASKING POLICY nosuch_s.x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t ALTER COLUMN nosuch_c UNSET MASKING POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a SET MASKING POLICY mp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a UNSET MASKING POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET PROJECTION POLICY pjp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER TABLE t ALTER COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ALTER COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t ALTER COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER TABLE t ALTER COLUMN nosuch_c SET PROJECTION POLICY nosuch_s.x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t ALTER COLUMN nosuch_c UNSET PROJECTION POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t ALTER COLUMN a UNSET PROJECTION POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET MASKING POLICY mp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH_MP' does not exist or "
                + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_MP.",
            "ALTER TABLE t MODIFY COLUMN a SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET MASKING POLICY nosuch_mp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET MASKING POLICY mp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET MASKING POLICY nosuch_s.x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t MODIFY COLUMN nosuch_c UNSET MASKING POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET MASKING POLICY mp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET MASKING POLICY");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET PROJECTION POLICY pjp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Projection policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PJP' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_PJP.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET PROJECTION POLICY nosuch_pjp");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'TEST_DB.TEST_SCHEMA.NOSUCH_T' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.NOSUCH_T.",
            "ALTER TABLE nosuch_t MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH_S.",
            "ALTER TABLE t MODIFY COLUMN nosuch_c SET PROJECTION POLICY nosuch_s.x");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'NOSUCH_C'",
            "ALTER TABLE t MODIFY COLUMN nosuch_c UNSET PROJECTION POLICY");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a SET PROJECTION POLICY pjp1");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER TABLE t MODIFY COLUMN a UNSET PROJECTION POLICY");
    }
}
