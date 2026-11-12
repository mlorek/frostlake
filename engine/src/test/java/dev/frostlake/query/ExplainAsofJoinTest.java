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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXPLAIN plans an ASOF JOIN as an operator of its own, an ASOF Join with its keys and its matchCondition over both
 * sides, the left kept whole (documented: "->ASOF Join  joinKey: (S.LOCATION = R.LOCATION) AND (S.STATE = R.STATE),
 * matchCondition: (S.OBSERVED &gt;= R.OBSERVED)"). It was planned as an InnerJoin whose JoinFilter pruned the left
 * side, or without ON as a CartesianJoin. What the Result and the scans list follows the tables' statistics, which a
 * table's rows change, so only the join's own line is fixed.
 */
public class ExplainAsofJoinTest extends MeasuredCellsTestSupport {

    private void setUpReadings() {
        engine.execute("CREATE OR REPLACE TABLE eaj_r (loc VARCHAR, obs TIMESTAMP_NTZ, v INT)");
        engine.execute("CREATE OR REPLACE TABLE eaj_s (loc VARCHAR, obs TIMESTAMP_NTZ, w INT)");
    }

    @Test
    public void anAsofJoinIsItsOwnOperator() {
        setUpReadings();
        final String plan = answer("EXPLAIN USING TEXT SELECT * FROM eaj_s s ASOF JOIN eaj_r r"
            + " MATCH_CONDITION (s.obs >= r.obs) ON s.loc = r.loc");
        assertTrue(plan.contains("->ASOF Join  joinKey: (S.LOC = R.LOC), matchCondition: (S.OBS >= R.OBS)"), plan);
        assertFalse(plan.contains("InnerJoin"), plan);
        assertFalse(plan.contains("JoinFilter"), plan);
    }

    @Test
    public void anAsofJoinWithoutKeysIsNoCartesianJoin() {
        setUpReadings();
        final String plan = answer("EXPLAIN USING TEXT SELECT * FROM eaj_s s ASOF JOIN eaj_r r"
            + " MATCH_CONDITION (s.obs >= r.obs)");
        assertTrue(plan.contains("->ASOF Join  joinKey: (1 = EXPR1(1)), matchCondition: (S.OBS >= R.OBS), "
            + "joinFilter: 1 = 1"), plan);
        assertFalse(plan.contains("CartesianJoin"), plan);
    }

    @Test
    public void twoKeysAreAnded() {
        setUpReadings();
        final String plan = answer("EXPLAIN SELECT * FROM eaj_s s ASOF JOIN eaj_r r MATCH_CONDITION (s.obs >= r.obs)"
            + " ON s.loc = r.loc AND s.w = r.v");
        assertTrue(plan.contains(" ;; 1|1|[0]|ASOF Join|null|null|joinKey: (S.LOC = R.LOC) AND (S.W = R.V), "
            + "matchCondition: (S.OBS >= R.OBS)|null|null|null ;; "), plan);
    }
}
