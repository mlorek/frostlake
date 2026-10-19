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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A RECURSIVE view whose recursive term holds an INTERSECT - the term's operands joined by INTERSECT, or a
 * parenthesized INTERSECT - recurses through the intersection: each step's rows are the rows every operand of the
 * term answers over the previous step's. The account creates such a view and fails reading it with an internal
 * error of its own, which the engine does not mirror, so the answers are the engine's alone.
 */
public class RecursiveViewIntersectTermTest extends MeasuredCellsTestSupport {

    @Test
    public void aRecursiveTermIntersectsItsOperands() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account fails reading these views with an internal error");
        assertAnswer("View RV8 successfully created.", "CREATE OR REPLACE RECURSIVE VIEW rv8(n) AS SELECT 1 UNION ALL"
            + " SELECT n + 1 FROM rv8 WHERE n < 3 INTERSECT SELECT 2");
        assertAnswer("1 ;; 2", "SELECT * FROM rv8 ORDER BY 1");
        assertAnswer("View RVI successfully created.", "CREATE OR REPLACE RECURSIVE VIEW rvi(n) AS SELECT 1 UNION ALL"
            + " (SELECT n + 1 FROM rvi WHERE n < 3 INTERSECT SELECT 2)");
        assertAnswer("1 ;; 2", "SELECT * FROM rvi ORDER BY 1");
        assertAnswer("View RVM successfully created.", "CREATE OR REPLACE RECURSIVE VIEW rvm(n) AS SELECT 1 UNION ALL"
            + " SELECT n + 1 FROM rvm WHERE n < 3 INTERSECT SELECT n + 1 FROM rvm WHERE n < 2");
        assertAnswer("1 ;; 2", "SELECT * FROM rvm ORDER BY 1");
        assertAnswer("View RVN successfully created.", "CREATE OR REPLACE RECURSIVE VIEW rvn(n) AS SELECT 1 UNION ALL"
            + " SELECT n + 1 FROM rvn WHERE n < 5 INTERSECT SELECT * FROM (VALUES (2), (3), (5)) v(x)");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM rvn ORDER BY 1");
    }
}
