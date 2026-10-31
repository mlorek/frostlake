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
 * A window call that reads a select alias reads the value the projection shows: a sequence's NEXTVAL is drawn
 * once a row, in row order, through ORDER BY, PARTITION BY and the call's argument alike (all live-verified).
 */
public class VolatileAliasWindowTest extends MeasuredCellsTestSupport {

    @Test
    public void aWindowReadsTheAliasValueShown() {
        assertAnswer("Table T729C successfully created.", "CREATE OR REPLACE TABLE t729c (a INT)");
        assertAnswer("6", "INSERT INTO t729c SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 6))");
        assertAnswer("Sequence SQ729G successfully created.", "CREATE OR REPLACE SEQUENCE sq729g");
        assertAnswer("6|1 ;; 5|2 ;; 4|3 ;; 3|4 ;; 2|5 ;; 1|6",
            "SELECT sq729g.NEXTVAL n, RANK() OVER (ORDER BY n DESC) rk FROM t729c ORDER BY rk");
        assertAnswer("Sequence SQ729H successfully created.", "CREATE OR REPLACE SEQUENCE sq729h");
        assertAnswer("1|1 ;; 2|2 ;; 3|3 ;; 4|4 ;; 5|5 ;; 6|6",
            "SELECT sq729h.NEXTVAL n, ROW_NUMBER() OVER (ORDER BY n) rn FROM t729c ORDER BY rn");
        assertAnswer("Sequence SQ729I successfully created.", "CREATE OR REPLACE SEQUENCE sq729i");
        assertAnswer("1|21 ;; 2|21 ;; 3|21 ;; 4|21 ;; 5|21 ;; 6|21",
            "SELECT sq729i.NEXTVAL n, SUM(n) OVER () s FROM t729c ORDER BY n");
        assertAnswer("Sequence SQ729J successfully created.", "CREATE OR REPLACE SEQUENCE sq729j");
        assertAnswer("1|5 ;; 2|6 ;; 3|5 ;; 4|6 ;; 5|5 ;; 6|6",
            "SELECT sq729j.NEXTVAL n, MAX(n) OVER (PARTITION BY MOD(a, 2)) m FROM t729c ORDER BY n");
        assertAnswer("Sequence SQ729K successfully created.", "CREATE OR REPLACE SEQUENCE sq729k");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1 ;; 6|1",
            "SELECT sq729k.NEXTVAL n, COUNT(*) OVER (PARTITION BY n) c FROM t729c ORDER BY n");
        assertAnswer("Sequence SQ729L successfully created.", "CREATE OR REPLACE SEQUENCE sq729l");
        assertAnswer("1|null ;; 2|1 ;; 3|2 ;; 4|3 ;; 5|4 ;; 6|5",
            "SELECT sq729l.NEXTVAL n, LAG(n) OVER (ORDER BY n) l FROM t729c ORDER BY n");
        assertAnswer("Sequence SQ729M successfully created.", "CREATE OR REPLACE SEQUENCE sq729m");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6", "SELECT sq729m.NEXTVAL n FROM t729c ORDER BY n");
    }
}
