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
 * A column a row access policy or a conditional masking policy reads as an argument takes no projection policy,
 * the column a masking policy masks does, and a column a projection policy guards is read as no policy's
 * argument, either way round (all live-verified).
 */
public class ProjectionPolicyArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void projectionPolicyAndPolicyArguments() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE TABLE test_db.test_schema.qt (a STRING, r STRING, c STRING, m STRING)");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY test_db.test_schema.qpp AS () RETURNS "
            + "PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => true)");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY test_db.test_schema.qrp AS (r STRING) RETURNS BOOLEAN -> "
            + "TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY test_db.test_schema.qmp AS (v STRING) RETURNS STRING -> "
            + "'***'");
        engine.execute("CREATE OR REPLACE MASKING POLICY test_db.test_schema.qcm AS (v STRING, w STRING) RETURNS "
            + "STRING -> CASE WHEN w = 'x' THEN v ELSE '*' END");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt ADD ROW ACCESS POLICY test_db.test_schema.qrp ON (r)");
        assertAnswer("REFUSED Cannot apply projection policy on 'R' because it is used as an argument by one or more "
                + "policies.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN r SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("506002 23001",
            "EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE test_db.test_schema.qt MODIFY COLUMN r SET PROJECTION POLICY "
                + "test_db.test_schema.qpp; RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE; END; $$");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN a SET MASKING POLICY test_db.test_schema.qcm USING (a, "
                + "c)");
        assertAnswer("REFUSED Cannot apply projection policy on 'C' because it is used as an argument by one or more "
                + "policies.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN c SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN a SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN m SET MASKING POLICY test_db.test_schema.qmp");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN m SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt DROP ROW ACCESS POLICY test_db.test_schema.qrp");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN a UNSET MASKING POLICY");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN a UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN c UNSET PROJECTION POLICY");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN r SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("REFUSED Column 'R' cannot be used as policy argument because it has a projection policy "
                + "attached.",
            "ALTER TABLE test_db.test_schema.qt ADD ROW ACCESS POLICY test_db.test_schema.qrp ON (r)");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN c SET PROJECTION POLICY test_db.test_schema.qpp");
        assertAnswer("REFUSED Column 'C' cannot be used as policy argument because it has a projection policy "
                + "attached.",
            "ALTER TABLE test_db.test_schema.qt MODIFY COLUMN a SET MASKING POLICY test_db.test_schema.qcm USING (a, "
                + "c)");
        assertAnswer("0", "SELECT COUNT(*) FROM test_db.test_schema.qt");
    }
}
