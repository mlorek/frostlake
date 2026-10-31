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
 * CREATE OR ALTER TABLE and VIEW set no policy: a table's row access, aggregation or join policy, a column's
 * masking or projection policy or tag and a view's row access policy each refuse the statement once the policy
 * is found, CREATE OR ALTER does not support setting policies or tags., where a policy that is not there is
 * refused as missing first (all live-verified).
 */
public class CreateOrAlterPolicyRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void policiesAreRefused() {
        engine.execute("CREATE OR REPLACE TAG t652");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap652 AS (a INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp652 AS (v INT) RETURNS INT -> v");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap652 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY pp652 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("CREATE OR REPLACE JOIN POLICY jp652 AS () RETURNS JOIN_CONSTRAINT -> "
            + "JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT) WITH ROW ACCESS POLICY rap652 ON (a)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT) WITH AGGREGATION POLICY ap652");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT) WITH JOIN POLICY jp652");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT WITH PROJECTION POLICY pp652)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT WITH MASKING POLICY mp652)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT WITH TAG (t652 = 'v'))");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT MASKING POLICY mp652)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT) WITH TAG (t652 = 'v')");
        assertAnswer("REFUSED SQL compilation error: | Masking policy 'TEST_DB.TEST_SCHEMA.NOSUCH652' does not exist "
                + "or not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                + "TEST_DB.TEST_SCHEMA.NOSUCH652.",
            "CREATE OR ALTER TABLE t652 (a INT WITH MASKING POLICY nosuch652)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER VIEW v652 WITH ROW ACCESS POLICY rap652 ON (a) AS SELECT 1 AS a");
        assertAnswer("Table T652 successfully created.", "CREATE OR ALTER TABLE t652 (a INT)");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER TABLE t652 (a INT WITH MASKING POLICY mp652)");
        assertAnswer("", "SHOW VIEWS LIKE 'V652'");
    }
}
