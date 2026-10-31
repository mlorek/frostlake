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
 * A SEQ1, SEQ2, SEQ4 or SEQ8 call in a grouped select's item, outside an aggregate, numbers the rows the query
 * answers in the order it answers them - after the ORDER BY, a DISTINCT and a LIMIT with its OFFSET, a window
 * beside it or not - unless the ORDER BY reads the item, by its alias, its position or its text: then it numbers
 * the groups the HAVING kept in the order they were formed, before the sort. A SEQ inside an aggregate still
 * numbers the scan the grouping read (all live-verified).
 */
public class GroupedSeqNumberingTest extends MeasuredCellsTestSupport {

    @Test
    public void theAnsweredRows() {
        engine.execute("CREATE OR REPLACE TABLE gs678 (a INT)");
        engine.execute("INSERT INTO gs678 VALUES (3), (1), (1), (2), (2), (4)");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3", "SELECT a, SEQ4() AS s FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("1|2|0 ;; 2|2|1 ;; 3|1|2 ;; 4|1|3",
            "SELECT a, COUNT(*) AS n, SEQ4() AS s FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("1|0|4 ;; 2|1|4 ;; 3|2|4 ;; 4|3|4",
            "SELECT a, SEQ4() AS s, COUNT(*) OVER () AS c FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("3|0|4 ;; 1|1|4 ;; 2|2|4 ;; 4|3|4",
            "SELECT a, SEQ4() AS s, COUNT(*) OVER () AS c FROM gs678 GROUP BY a ORDER BY s");
        assertAnswer("1|10|4 ;; 2|11|4 ;; 3|12|4 ;; 4|13|4",
            "SELECT a, SEQ4() + 10 AS s, COUNT(*) OVER () AS c FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3",
            "SELECT a, SEQ4() AS s FROM gs678 GROUP BY a QUALIFY COUNT(*) OVER () > 0 ORDER BY a");
        assertAnswer("1|0 ;; 2|1", "SELECT a, SEQ4() AS s FROM gs678 GROUP BY a HAVING COUNT(*) > 1 ORDER BY a");
        assertAnswer("1|0|2 ;; 2|1|2",
            "SELECT a, SEQ4() AS s, COUNT(*) OVER () AS c FROM gs678 GROUP BY a HAVING COUNT(*) > 1 ORDER BY a");
        assertAnswer("1|0|4 ;; 2|1|3 ;; 3|2|2 ;; 4|3|1",
            "SELECT a, SEQ4() AS s, ROW_NUMBER() OVER (ORDER BY a DESC) AS r FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("1|0|1 ;; 2|1|2 ;; 3|2|3 ;; 4|3|4",
            "SELECT a, SEQ4() AS s, MAX(a) OVER (PARTITION BY a) AS m FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("2|0 ;; 3|1 ;; 4|2", "SELECT a, SEQ4() AS s FROM gs678 WHERE a > 1 GROUP BY a ORDER BY a");
        assertAnswer("1|0|3|4 ;; 2|1|7|4 ;; 3|2|0|4 ;; 4|3|5|4",
            "SELECT a, SEQ4() AS s, SUM(SEQ4()) AS t, COUNT(*) OVER () AS c FROM gs678 GROUP BY a ORDER BY a");
        assertAnswer("2|0|3 ;; 3|1|3 ;; 4|2|3",
            "SELECT a, SEQ8() AS s, COUNT(*) OVER () AS c FROM gs678 WHERE a > 1 GROUP BY a ORDER BY a");
    }

    @Test
    public void theOrderTheQueryAnswersIn() {
        engine.execute("CREATE OR REPLACE TABLE gs679 (a INT)");
        engine.execute("INSERT INTO gs679 VALUES (3), (1), (1), (2), (2), (4)");
        assertAnswer("1|1 ;; 1|2 ;; 2|3 ;; 2|4 ;; 3|0 ;; 4|5", "SELECT a, SEQ4() AS s FROM gs679 ORDER BY a, s");
        assertAnswer("3|0 ;; 1|1 ;; 1|2 ;; 2|3 ;; 2|4 ;; 4|5", "SELECT a, SEQ4() AS s FROM gs679 ORDER BY s");
        assertAnswer("4|5 ;; 3|0 ;; 2|3 ;; 2|4 ;; 1|1 ;; 1|2", "SELECT a, SEQ4() AS s FROM gs679 ORDER BY a DESC, s");
        assertAnswer("2|1 ;; 2|2 ;; 3|0 ;; 4|3", "SELECT a, SEQ4() AS s FROM gs679 WHERE a <> 1 ORDER BY a, s");
        assertAnswer("1|4|1 ;; 1|4|2 ;; 2|4|3 ;; 2|4|4 ;; 3|4|0 ;; 4|4|5",
            "SELECT a, MAX(a) OVER () AS m, SEQ4() AS s FROM gs679 ORDER BY a, s");
        assertAnswer("2|0 ;; 3|1", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY a LIMIT 2 OFFSET 1");
        assertAnswer("4|0 ;; 3|1 ;; 2|2 ;; 1|3", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY a DESC");
        assertAnswer("1|0 ;; 2|1", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a HAVING COUNT(*) > 1 ORDER BY s");
        assertAnswer("4|3 ;; 2|2 ;; 1|1 ;; 3|0", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY s DESC");
        assertAnswer("1|2 ;; 2|102 ;; 3|201 ;; 4|301",
            "SELECT a, SEQ4() * 100 + COUNT(*) AS s FROM gs679 GROUP BY a ORDER BY a");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|3",
            "SELECT a, s FROM (SELECT a, SEQ4() AS s FROM gs679 GROUP BY a) ORDER BY a");
        assertAnswer("1|1 ;; 2|2 ;; 3|0 ;; 4|3", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY a, s");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3", "SELECT DISTINCT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY a");
        assertAnswer("1|0 ;; 2|1 ;; 3|2 ;; 4|3", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY 1");
        assertAnswer("3|0 ;; 1|1 ;; 2|2 ;; 4|3", "SELECT a, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY 2");
        assertAnswer("4|0|3 ;; 2|1|2 ;; 1|2|1 ;; 3|3|0",
            "SELECT a, SEQ4() AS s, SEQ8() AS t FROM gs679 GROUP BY a ORDER BY t DESC");
        assertAnswer("1|0 ;; 1|3 ;; 2|1 ;; 2|2",
            "SELECT COUNT(*) AS n, SEQ4() AS s FROM gs679 GROUP BY a ORDER BY n, s");
    }
}
