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
 * A projection policy restricts a MAX of its column as it restricts the column, in a query and in an INSERT's
 * query, and lets a COUNT through; a VALUES item that is a query of the column over a table of more than one row
 * is refused as VALUES refuses a query the plan does not fold (all live-verified).
 */
public class ProjectionPolicyAggregateWritesTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregatesOfTheColumn() {
        engine.execute("CREATE OR REPLACE PROJECTION POLICY deny683 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE TABLE pt683 (id INT, p VARCHAR WITH PROJECTION POLICY deny683)");
        engine.execute("INSERT INTO pt683 VALUES (1, 'hidden')");
        engine.execute("CREATE OR REPLACE TABLE pt683b (id INT, p VARCHAR WITH PROJECTION POLICY deny683)");
        engine.execute("INSERT INTO pt683b VALUES (1, 'hidden'), (2, 'other')");
        engine.execute("CREATE OR REPLACE TABLE t683 (id INT, x VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  P",
            "SELECT p FROM pt683");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  MAX(P)",
            "SELECT MAX(p) FROM pt683");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  MAX(P)",
            "SELECT MAX(p) FROM pt683b");
        assertAnswer("2", "SELECT COUNT(p) FROM pt683b");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT MAX(PT683B.P) AS \"MAX(P)\" FROM "
                + "PT683B AS PT683B)] in VALUES clause",
            "INSERT INTO t683 (x) VALUES ((SELECT MAX(p) FROM pt683b))");
        assertAnswer("REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                + "Please remove them from the list of projected columns: |  |  MAX(P)",
            "INSERT INTO t683 (x) SELECT MAX(p) FROM pt683");
        assertAnswer("", "SELECT x FROM t683 ORDER BY 1");
    }
}
