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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A masking, row access, projection or aggregation policy is refused DROP and CREATE OR REPLACE only while it is
 * itself attached: a namesake in another schema attached to a table, or a quoted name differing only in case,
 * leaves it free to drop and replace (all live-verified).
 */
public class PolicyInUseAcrossSchemasTest extends MeasuredCellsTestSupport {

    @Test
    public void sameNamedPoliciesInTwoSchemas() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.pq1");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.pt (a STRING, r STRING)");
        engine.execute("CREATE OR REPLACE MASKING POLICY test_db.pq1.mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY test_db.pq1.rp AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("ALTER TABLE test_db.test_schema.pt MODIFY COLUMN a SET MASKING POLICY test_db.pq1.mp");
        engine.execute("ALTER TABLE test_db.test_schema.pt ADD ROW ACCESS POLICY test_db.pq1.rp ON (r)");
        engine.execute("CREATE OR REPLACE MASKING POLICY test_db.test_schema.mp AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY test_db.test_schema.rp AS (r STRING) RETURNS BOOLEAN -> "
            + "TRUE");
        assertAnswer("Masking policy MP successfully created.",
            "CREATE OR REPLACE MASKING POLICY test_db.test_schema.mp AS (v STRING) RETURNS STRING -> 'x'");
        assertAnswer("MP successfully dropped.", "DROP MASKING POLICY test_db.test_schema.mp");
        assertAnswer("Row access policy 'RP' is successfully created.",
            "CREATE OR REPLACE ROW ACCESS POLICY test_db.test_schema.rp AS (r STRING) RETURNS BOOLEAN -> FALSE");
        assertAnswer("RP successfully dropped.", "DROP ROW ACCESS POLICY test_db.test_schema.rp");
        assertAnswer("REFUSED SQL compilation error: Policy MP cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP MASKING POLICY test_db.pq1.mp");
        assertAnswer("REFUSED SQL compilation error: Policy MP cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "CREATE OR REPLACE MASKING POLICY test_db.pq1.mp AS (v STRING) RETURNS STRING -> 'y'");
        assertAnswer("REFUSED SQL compilation error: Policy RP cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP ROW ACCESS POLICY test_db.pq1.rp");
        assertAnswer("REFUSED SQL compilation error: Policy RP cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "CREATE OR REPLACE ROW ACCESS POLICY test_db.pq1.rp AS (r STRING) RETURNS BOOLEAN -> FALSE");
        assertAnswer("Masking policy mp successfully created.",
            "CREATE MASKING POLICY test_db.pq1.\"mp\" AS (v STRING) RETURNS STRING -> v");
        assertAnswer("Masking policy mp successfully created.",
            "CREATE OR REPLACE MASKING POLICY test_db.pq1.\"mp\" AS (v STRING) RETURNS STRING -> 'z'");
        assertAnswer("mp successfully dropped.", "DROP MASKING POLICY test_db.pq1.\"mp\"");
        assertAnswer("Row access policy 'rp' is successfully created.",
            "CREATE ROW ACCESS POLICY test_db.pq1.\"rp\" AS (r STRING) RETURNS BOOLEAN -> TRUE");
        assertAnswer("rp successfully dropped.", "DROP ROW ACCESS POLICY test_db.pq1.\"rp\"");
        assertAnswer("Projection policy 'PP' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY test_db.pq1.pp AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer("REFUSED Cannot apply projection policy on 'R' because it is used as an argument by one or more "
                + "policies.",
            "ALTER TABLE test_db.test_schema.pt MODIFY COLUMN r SET PROJECTION POLICY test_db.pq1.pp");
        assertAnswer("Projection policy 'PP' is successfully created.",
            "CREATE OR REPLACE PROJECTION POLICY test_db.test_schema.pp AS () RETURNS PROJECTION_CONSTRAINT -> "
                + "PROJECTION_CONSTRAINT(ALLOW => true)");
        assertAnswer("PP successfully dropped.", "DROP PROJECTION POLICY test_db.test_schema.pp");
        assertAnswer("PP successfully dropped.", "DROP PROJECTION POLICY test_db.pq1.pp");
        assertAnswer("Aggregation policy 'AP' is successfully created.",
            "CREATE OR REPLACE AGGREGATION POLICY test_db.pq1.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.pt SET AGGREGATION POLICY test_db.pq1.ap");
        assertAnswer("Aggregation policy 'AP' is successfully created.",
            "CREATE OR REPLACE AGGREGATION POLICY test_db.test_schema.ap AS () RETURNS AGGREGATION_CONSTRAINT -> "
                + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        assertAnswer("AP successfully dropped.", "DROP AGGREGATION POLICY test_db.test_schema.ap");
        assertAnswer("REFUSED SQL compilation error: Policy AP cannot be dropped/replaced as it is associated with "
                + "one or more entities.",
            "DROP AGGREGATION POLICY test_db.pq1.ap");
    }
}
