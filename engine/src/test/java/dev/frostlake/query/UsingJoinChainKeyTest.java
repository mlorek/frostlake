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
 * A USING or NATURAL join after an earlier USING join of the same key keys on the merged column the earlier
 * join prints (live-verified): after a LEFT join the left value, after a RIGHT join the right one and after a
 * FULL join the first that is not NULL, as the same join over a derived table does.
 */
public class UsingJoinChainKeyTest extends MeasuredCellsTestSupport {

    @Test
    public void aChainKeysOnTheMergedColumn() {
        engine.execute("CREATE OR REPLACE TABLE x1 (a INT, p INT)");
        engine.execute("INSERT INTO x1 VALUES (1, 1), (2, 2)");
        engine.execute("CREATE OR REPLACE TABLE x2 (a INT, q INT)");
        engine.execute("INSERT INTO x2 VALUES (2, 20), (3, 30)");
        engine.execute("CREATE OR REPLACE TABLE x3 (a INT, r INT)");
        engine.execute("INSERT INTO x3 VALUES (3, 300), (4, 400), (1, 100), (2, 200)");
        assertCells(new String[][] {
            {"SELECT * FROM x1 LEFT JOIN x2 USING (a) JOIN x3 USING (a) ORDER BY 1", "1|1|null|100 ;; 2|2|20|200"},
            {"SELECT * FROM (SELECT * FROM x1 LEFT JOIN x2 USING (a)) d JOIN x3 USING (a) ORDER BY 1",
                "1|1|null|100 ;; 2|2|20|200"},
            {"SELECT * FROM x1 FULL JOIN x2 USING (a) FULL JOIN x3 USING (a) ORDER BY 1, 2",
                "1|1|null|100 ;; 2|2|20|200 ;; 3|null|30|300 ;; 4|null|null|400"},
            {"SELECT * FROM x3 LEFT JOIN (x1 FULL JOIN x2 USING (a)) USING (a) ORDER BY 1",
                "1|100|1|null ;; 2|200|2|20 ;; 3|300|null|30 ;; 4|400|null|null"},
            {"SELECT * FROM x3 LEFT JOIN (SELECT * FROM x1 FULL JOIN x2 USING (a)) g USING (a) ORDER BY 1",
                "1|100|1|null ;; 2|200|2|20 ;; 3|300|null|30 ;; 4|400|null|null"},
            {"SELECT * FROM x1 RIGHT JOIN x2 USING (a) JOIN x3 USING (a) ORDER BY 1", "2|2|20|200 ;; 3|null|30|300"},
            {"SELECT * FROM x1 LEFT JOIN x2 USING (a) NATURAL JOIN x3 ORDER BY 1", "1|1|null|100 ;; 2|2|20|200"},
            {"SELECT a, COUNT(*) FROM x1 FULL JOIN x2 USING (a) LEFT JOIN x3 USING (a) GROUP BY a ORDER BY 1",
                "1|1 ;; 2|1 ;; 3|1"},
        });
    }
}
