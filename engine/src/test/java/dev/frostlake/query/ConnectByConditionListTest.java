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
 * CONNECT BY takes one condition: a comma-separated list of PRIOR conditions is refused at its first comma and
 * nothing after it, the AND of them answers (all live-verified).
 */
public class ConnectByConditionListTest extends MeasuredCellsTestSupport {

    @Test
    public void conditionList() {
        engine.execute("CREATE OR REPLACE TABLE e2 (id INT, mgr INT, name VARCHAR, dept INT)");
        engine.execute("INSERT INTO e2 VALUES (1, NULL, 'a', 10), (2, 1, 'b', 10), (3, 1, 'c', 20), (4, 2, 'd', 20), "
            + "(5, 4, 'e', 10), (6, NULL, 'f', 30), (7, 6, 'g', 30)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ','.",
            "SELECT id, LEVEL FROM e2 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id, PRIOR dept = dept ORDER BY id");
        assertAnswer("1|1 ;; 2|2 ;; 6|1 ;; 7|2",
            "SELECT id, LEVEL FROM e2 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id AND PRIOR dept = dept ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ','.",
            "SELECT id, LEVEL FROM e2 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id, PRIOR dept = dept, PRIOR id "
                + "< id ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ','.",
            "SELECT id, LEVEL FROM e2 START WITH mgr IS NULL CONNECT BY PRIOR id = mgr, dept = PRIOR dept ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected ','.",
            "SELECT id, LEVEL FROM e2 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id, dept > 0 ORDER BY id");
    }
}
