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
 * A schema's clone holds its policies under their names as the source holds them, a quoted name in its own case: a
 * masking, row access, projection or aggregation policy created as {@code "mp"} is described, attached and refused
 * in the clone by that name exactly, and the clone's masking policy masks the column it is set on again
 * (live-verified).
 */
public class ClonedPolicyNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aClonesQuotedPoliciesAreReachedByTheirOwnNames() {
        engine.execute("CREATE OR REPLACE MASKING POLICY \"cpn_mp\" AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY \"cpn_rap\" AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY \"cpn_pp\" AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY \"cpn_ap\" AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE OR REPLACE TABLE cpn_t (c VARCHAR WITH MASKING POLICY \"cpn_mp\", d VARCHAR)");
        engine.execute("INSERT INTO cpn_t VALUES ('secret', 'plain')");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.cpn_s CLONE test_db.test_schema");
        assertCells(new String[][] {
            {"DESCRIBE MASKING POLICY test_db.cpn_s.\"cpn_mp\"", "cpn_mp|(V VARCHAR)|VARCHAR|'***'"},
            {"DESCRIBE ROW ACCESS POLICY test_db.cpn_s.\"cpn_rap\"", "cpn_rap|(V VARCHAR)|BOOLEAN|TRUE"},
            {"DESCRIBE PROJECTION POLICY test_db.cpn_s.\"cpn_pp\"",
                "cpn_pp|()|PROJECTION_CONSTRAINT|PROJECTION_CONSTRAINT(ALLOW => true)"},
            {"DESCRIBE AGGREGATION POLICY test_db.cpn_s.\"cpn_ap\"",
                "cpn_ap|()|AGGREGATION_CONSTRAINT|NO_AGGREGATION_CONSTRAINT()"},
            {"SHOW MASKING POLICIES IN SCHEMA test_db.cpn_s ->> SELECT \"name\", \"schema_name\" FROM $1",
                "cpn_mp|CPN_S"},
            {"SELECT c, d FROM test_db.cpn_s.cpn_t", "***|plain"},
            {"ALTER TABLE test_db.cpn_s.cpn_t MODIFY COLUMN c UNSET MASKING POLICY",
                "Statement executed successfully."},
            {"SELECT c FROM test_db.cpn_s.cpn_t", "secret"},
            {"ALTER TABLE test_db.cpn_s.cpn_t MODIFY COLUMN c SET MASKING POLICY test_db.cpn_s.\"cpn_mp\"",
                "Statement executed successfully."},
            {"SELECT c FROM test_db.cpn_s.cpn_t", "***"},
            {"DROP MASKING POLICY test_db.cpn_s.\"CPN_MP\"",
                "REFUSED SQL compilation error: | Masking policy 'TEST_DB.CPN_S.CPN_MP' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.CPN_S.CPN_MP."},
        });
    }
}
