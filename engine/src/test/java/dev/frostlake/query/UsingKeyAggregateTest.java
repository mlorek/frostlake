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
 * An aggregate whose argument is a bare USING join key reads the merged key the join prints (live-verified): over
 * a FULL or a RIGHT join COUNT, SUM, MIN, MAX, AVG, COUNT(DISTINCT), LISTAGG and ARRAY_AGG count the rows only the
 * right side holds, grouped, in a HAVING and as the whole input alike.
 */
public class UsingKeyAggregateTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregateReadsTheMergedKey() {
        engine.execute("CREATE OR REPLACE TABLE ap (k INT, v INT)");
        engine.execute("INSERT INTO ap VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE aq (k INT, v INT)");
        engine.execute("INSERT INTO aq VALUES (1, 30), (3, 40)");
        assertCells(new String[][] {
            {"SELECT COUNT(k), SUM(k), MAX(k), COUNT(DISTINCT k), LISTAGG(k, ',') WITHIN GROUP (ORDER BY k) FROM ap "
                + "FULL JOIN aq USING (k)",
                "3|6|3|3|1,2,3"},
            {"SELECT SUM(k + 0), MAX(COALESCE(k, -1)) FROM ap FULL JOIN aq USING (k)", "6|3"},
            {"SELECT COUNT(k) FROM (SELECT * FROM ap FULL JOIN aq USING (k))", "3"},
            {"SELECT COUNT(k), SUM(k), MAX(k) FROM ap RIGHT JOIN aq USING (k)", "2|4|3"},
            {"SELECT aq.v, MAX(k) FROM ap FULL JOIN aq USING (k) GROUP BY aq.v ORDER BY 1", "30|1 ;; 40|3 ;; null|2"},
            {"SELECT COUNT(*) FROM ap FULL JOIN aq USING (k) HAVING MAX(k) = 3", "3"},
            {"SELECT ARRAY_AGG(k) WITHIN GROUP (ORDER BY k) FROM ap FULL JOIN aq USING (k)", "[1,2,3]"},
            {"SELECT MIN(k), AVG(k) FROM ap FULL JOIN aq USING (k)", "1|2.000000"},
        });
    }
}
