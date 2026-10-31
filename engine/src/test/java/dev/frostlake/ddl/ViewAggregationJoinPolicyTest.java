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
 * A view takes an aggregation and a join policy as a table does: CREATE VIEW's [WITH] AGGREGATION POLICY and
 * [WITH] JOIN POLICY, before or after its COMMENT, and ALTER VIEW's SET and UNSET of either; a read of the view
 * is held to them, GET_DDL shows them, a missing policy is refused and CREATE OR ALTER VIEW refuses them (all
 * live-verified).
 */
public class ViewAggregationJoinPolicyTest extends MeasuredCellsTestSupport {

    @Test
    public void viewPolicies() {
        engine.execute("CREATE OR REPLACE TABLE t676 (a INT, b INT)");
        engine.execute("INSERT INTO t676 VALUES (1, 10), (1, 20), (2, 30), (3, 40)");
        engine.execute("CREATE OR REPLACE AGGREGATION POLICY ap676 AS () RETURNS AGGREGATION_CONSTRAINT -> "
            + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        assertAnswer("View V676 successfully created.",
            "CREATE OR REPLACE VIEW v676 WITH AGGREGATION POLICY ap676 AS SELECT a, b FROM t676");
        assertAnswer("REFUSED SQL compilation error: Aggregation policy violation: aggregation required.",
            "SELECT * FROM v676");
        assertAnswer("4", "SELECT COUNT(*) FROM v676");
        assertAnswer("1|2 ;; null|2", "SELECT a, COUNT(*) FROM v676 GROUP BY a ORDER BY a");
        assertAnswer("100", "SELECT SUM(b) FROM v676");
        assertAnswer("View V676B successfully created.",
            "CREATE OR REPLACE VIEW v676b AGGREGATION POLICY ap676 AS SELECT a, b FROM t676");
        assertAnswer("View V676C successfully created.",
            "CREATE OR REPLACE VIEW v676c WITH AGGREGATION POLICY ap676 ENTITY KEY (a) AS SELECT a, b FROM t676");
        assertAnswer("4", "SELECT COUNT(*) FROM v676c");
        assertAnswer("REFUSED SQL compilation error: Aggregation policy violation: aggregation required.",
            "SELECT a FROM v676c");
        assertAnswer("REFUSED SQL compilation error: | Aggregation policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_AP676' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NO_SUCH_AP676.",
            "CREATE OR REPLACE VIEW v676d WITH AGGREGATION POLICY no_such_ap676 AS SELECT a FROM t676");
        assertAnswer("REFUSED CREATE OR ALTER does not support setting policies or tags.",
            "CREATE OR ALTER VIEW v676e WITH AGGREGATION POLICY ap676 AS SELECT a FROM t676");
        assertAnswer("View V676F successfully created.", "CREATE OR REPLACE VIEW v676f AS SELECT a FROM t676");
        assertAnswer("Statement executed successfully.", "ALTER VIEW v676f SET AGGREGATION POLICY ap676");
        assertAnswer("REFUSED SQL compilation error: Aggregation policy violation: aggregation required.",
            "SELECT a FROM v676f");
        assertAnswer("Statement executed successfully.", "ALTER VIEW v676f UNSET AGGREGATION POLICY");
        assertAnswer("1 ;; 1 ;; 2 ;; 3", "SELECT a FROM v676f ORDER BY 1");
        assertAnswer("create or replace view V676( | 	A, | 	B | ) WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP676 | "
                + " as SELECT a, b FROM t676;",
            "SELECT GET_DDL('VIEW', 'v676')");
        assertAnswer("Join policy 'JP676P' is successfully created.",
            "CREATE OR REPLACE JOIN POLICY jp676p AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => "
                + "TRUE)");
        assertAnswer("View V676G successfully created.",
            "CREATE OR REPLACE VIEW v676g WITH JOIN POLICY jp676p AS SELECT a, b FROM t676");
        assertAnswer("REFUSED SQL compilation error: Join Policy violation, please contact the policy admin for "
                + "details",
            "SELECT * FROM v676g");
        assertAnswer("1 ;; 1 ;; 1 ;; 1 ;; 2 ;; 3", "SELECT v.a FROM v676g v JOIN t676 t ON v.a = t.a ORDER BY 1");
        assertAnswer("View V676H successfully created.",
            "CREATE OR REPLACE VIEW v676h JOIN POLICY jp676p AS SELECT a FROM t676");
        assertAnswer("View V676I successfully created.",
            "CREATE OR REPLACE VIEW v676i WITH AGGREGATION POLICY ap676 WITH JOIN POLICY jp676p AS SELECT a FROM t676");
        assertAnswer("REFUSED SQL compilation error: | Row access policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_RAP676' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "POLICY TEST_DB.TEST_SCHEMA.NO_SUCH_RAP676.",
            "CREATE OR REPLACE VIEW v676j WITH ROW ACCESS POLICY no_such_rap676 ON (a) AS SELECT a FROM t676");
        assertAnswer("View V676K successfully created.",
            "CREATE OR REPLACE VIEW v676k COMMENT = 'c' WITH AGGREGATION POLICY ap676 AS SELECT a FROM t676");
        assertAnswer("View V676L successfully created.",
            "CREATE OR REPLACE VIEW v676l WITH AGGREGATION POLICY ap676 COMMENT = 'c' AS SELECT a FROM t676");
    }
}
