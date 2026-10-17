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
 * A correlated subquery that reads the outer row only through equalities of its WHERE answers as the subquery run for
 * each row does, whether one equality correlates it or several, and over DATE and BOOLEAN keys as over numbers and
 * texts (live-verified): EXISTS and NOT EXISTS as the matching join keeps its rows, a correlated aggregate as the join
 * counts them, a NULL key equal to nothing, and a number beside a text compared as the comparison converts it.
 */
public class CorrelatedKeyProbeTest extends MeasuredCellsTestSupport {

    @Test
    public void aSubqueryCorrelatedThroughEqualitiesAnswersAsItsJoinDoes() {
        engine.execute("CREATE OR REPLACE TABLE ca AS SELECT SEQ4() AS id, SEQ4() % 5 AS v, '2020-01-01'::DATE + "
            + "SEQ4() % 50 AS d, SEQ4() % 2 = 0 AS f, TO_VARCHAR(SEQ4() % 5) AS t FROM "
            + "TABLE(GENERATOR(ROWCOUNT => 200))");
        engine.execute("CREATE OR REPLACE TABLE cb AS SELECT SEQ4() AS id, SEQ4() % 7 AS k, '2020-01-01'::DATE + "
            + "SEQ4() % 30 AS d, SEQ4() % 3 = 0 AS f FROM TABLE(GENERATOR(ROWCOUNT => 150))");
        engine.execute("INSERT INTO ca VALUES (NULL, NULL, NULL, NULL, NULL)");
        engine.execute("INSERT INTO cb VALUES (NULL, NULL, NULL, NULL)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id AND cb.k = ca.v)", "25"},
            {"SELECT COUNT(DISTINCT ca.id) FROM ca JOIN cb ON cb.id = ca.id AND cb.k = ca.v", "25"},
            {"SELECT COUNT(*) FROM ca WHERE NOT EXISTS (SELECT 1 FROM cb WHERE cb.id = ca.id AND cb.k = ca.v)", "176"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.d = ca.d AND cb.f = ca.f)", "60"},
            {"SELECT COUNT(DISTINCT ca.id) FROM ca JOIN cb ON cb.d = ca.d AND cb.f = ca.f", "60"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.d = ca.d)", "120"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.f = ca.f AND cb.id > 140)", "200"},
            {"SELECT SUM((SELECT COUNT(*) FROM cb WHERE cb.k = ca.v AND cb.f = ca.f)) FROM ca", "2160"},
            {"SELECT COUNT(*) FROM ca JOIN cb ON cb.k = ca.v AND cb.f = ca.f", "2160"},
            {"SELECT ca.id, (SELECT MAX(cb.id) FROM cb WHERE cb.k = ca.v AND cb.d = ca.d) m FROM ca WHERE ca.id "
                + "< 6 OR ca.id IS NULL ORDER BY 1",
                "0|0 ;; 1|1 ;; 2|2 ;; 3|3 ;; 4|4 ;; 5|35 ;; null|null"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.k = ca.t)", "200"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.k = ca.t AND cb.f = ca.f)", "200"},
            {"SELECT COUNT(*) FROM ca WHERE EXISTS (SELECT 1 FROM cb WHERE cb.d = ca.d + 1 AND cb.k = ca.v + 2)", "76"},
            {"SELECT COUNT(*) FROM ca WHERE (SELECT COUNT(*) FROM cb WHERE cb.k = ca.v AND cb.d = ca.d) = 0", "101"},
        });
    }
}
