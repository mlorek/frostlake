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
 * An ORDER BY that names an item by its alias or position beside a key that is no output reads the value the
 * projection shows: a sequence's NEXTVAL is drawn once a row and the rows come out in the order of the values
 * shown (all live-verified).
 */
public class VolatileAliasSortTest extends MeasuredCellsTestSupport {

    @Test
    public void anAliasKeyReadsTheValueShown() {
        assertAnswer("Table T729 successfully created.", "CREATE OR REPLACE TABLE t729 (a INT)");
        assertAnswer("8", "INSERT INTO t729 SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 8))");
        assertAnswer("Sequence SQ729A successfully created.", "CREATE OR REPLACE SEQUENCE sq729a");
        assertAnswer("8 ;; 7 ;; 6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1",
            "SELECT sq729a.NEXTVAL n FROM t729 ORDER BY n DESC, a + 0");
        assertAnswer("Sequence SQ729B successfully created.", "CREATE OR REPLACE SEQUENCE sq729b");
        assertAnswer("8 ;; 7 ;; 6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1", "SELECT sq729b.NEXTVAL n FROM t729 ORDER BY n DESC");
        assertAnswer("Sequence SQ729C successfully created.", "CREATE OR REPLACE SEQUENCE sq729c");
        assertAnswer("1 ;; 2 ;; 3", "SELECT sq729c.NEXTVAL n FROM t729 ORDER BY n, a + 0 LIMIT 3");
        assertAnswer("Sequence SQ729D successfully created.", "CREATE OR REPLACE SEQUENCE sq729d");
        assertAnswer("8 ;; 7 ;; 6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1",
            "SELECT sq729d.NEXTVAL n FROM t729 ORDER BY n DESC NULLS LAST, MOD(a, 2)");
        assertAnswer("Sequence SQ729E successfully created.", "CREATE OR REPLACE SEQUENCE sq729e");
        assertAnswer("8 ;; 7 ;; 6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1",
            "SELECT sq729e.NEXTVAL + 0 n FROM t729 ORDER BY n DESC, a + 0");
        assertAnswer("Sequence SQ729F successfully created.", "CREATE OR REPLACE SEQUENCE sq729f");
        assertAnswer("8 ;; 7 ;; 6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1",
            "SELECT sq729f.NEXTVAL n FROM t729 ORDER BY 1 DESC, a + 0");
    }
}
