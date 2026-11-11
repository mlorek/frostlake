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
 * EXPLAIN refuses a correlated subquery the account cannot evaluate beside a sequence's NEXTVAL, as a run refuses it
 * (live-verified): the plan reads the query's rows to judge the subquery and only looks at the value NEXTVAL would
 * draw, so the sequence does not move.
 */
public class ExplainSequenceCorrelationTest extends MeasuredCellsTestSupport {

    @Test
    public void aNextvalDoesNotSpareTheSubquery() {
        engine.execute("CREATE OR REPLACE TABLE u53 (id INT, v INT)");
        engine.execute("INSERT INTO u53 VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE SEQUENCE s53");
        assertCells(new String[][] {
            {"EXPLAIN SELECT s53.NEXTVAL n, d.id, (SELECT v FROM u53 WHERE u53.id = d.id) x FROM (SELECT id + 0 "
                + "AS id FROM u53) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 37"},
            {"EXPLAIN SELECT u53.id, (SELECT v FROM u53 x WHERE x.id = u53.id) y, s53.NEXTVAL n FROM u53",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT s53.NEXTVAL", "1"},
            {"EXPLAIN SELECT u53.id, (SELECT v FROM u53 x WHERE x.id = u53.id) y FROM u53",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT u53.id, (SELECT v FROM u53 x WHERE x.id = u53.id) y, s53.NEXTVAL n FROM u53",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
        });
    }
}
