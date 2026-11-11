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
 * A volatile select item - a sequence's NEXTVAL - is drawn once a row whatever stage reads it, and the rows shown carry
 * the values the stage read (live-verified): a sort that reads the item by its position or its alias, over one
 * relation or a join, an item that reads another item's alias, a window beside a QUALIFY, a window over a join, and an
 * inline QUALIFY window that orders by the alias.
 */
public class VolatileItemDrawnOnceTest extends MeasuredCellsTestSupport {

    @Test
    public void aVolatileItemIsDrawnOnceARowWhateverReadsIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE t906 (a INT)", "Table T906 successfully created."},
            {"INSERT INTO t906 SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 6))", "6"},
            {"CREATE OR REPLACE SEQUENCE sq906_0", "Sequence SQ906_0 successfully created."},
            {"SELECT sq906_0.NEXTVAL FROM t906 ORDER BY 1 DESC, a + 0", "6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1"},
            {"CREATE OR REPLACE SEQUENCE sq906_1", "Sequence SQ906_1 successfully created."},
            {"SELECT sq906_1.NEXTVAL n FROM t906 x JOIN t906 y ON x.a = y.a ORDER BY n DESC, x.a + 0",
                "6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1"},
            {"CREATE OR REPLACE SEQUENCE sq906_2", "Sequence SQ906_2 successfully created."},
            {"SELECT sq906_2.NEXTVAL n, RANK() OVER (ORDER BY n) rk FROM t906 QUALIFY rk <= 3 ORDER BY n",
                "1|1 ;; 2|2 ;; 3|3"},
            {"CREATE OR REPLACE SEQUENCE sq906_3", "Sequence SQ906_3 successfully created."},
            {"SELECT sq906_3.NEXTVAL n, RANK() OVER (ORDER BY n) rk FROM t906 x JOIN t906 y ON x.a = y.a ORDER BY n",
                "1|1 ;; 2|2 ;; 3|3 ;; 4|4 ;; 5|5 ;; 6|6"},
            {"CREATE OR REPLACE SEQUENCE sq906_4", "Sequence SQ906_4 successfully created."},
            {"SELECT sq906_4.NEXTVAL n, n + 100 m FROM t906 ORDER BY m DESC, a + 0",
                "6|106 ;; 5|105 ;; 4|104 ;; 3|103 ;; 2|102 ;; 1|101"},
            {"CREATE OR REPLACE SEQUENCE sq906_5", "Sequence SQ906_5 successfully created."},
            {"SELECT sq906_5.NEXTVAL n FROM t906 WHERE a < 5 ORDER BY n DESC, a + 0", "5 ;; 4 ;; 3 ;; 2 ;; 1"},
            {"CREATE OR REPLACE SEQUENCE sq906_6", "Sequence SQ906_6 successfully created."},
            {"SELECT sq906_6.NEXTVAL n FROM t906 ORDER BY n DESC, a + 0 LIMIT 2", "6 ;; 5"},
            {"CREATE OR REPLACE SEQUENCE sq906_7", "Sequence SQ906_7 successfully created."},
            {"SELECT sq906_7.NEXTVAL n, ROW_NUMBER() OVER (ORDER BY n DESC) r FROM t906 ORDER BY n",
                "1|6 ;; 2|5 ;; 3|4 ;; 4|3 ;; 5|2 ;; 6|1"},
            {"CREATE OR REPLACE SEQUENCE sq906_8", "Sequence SQ906_8 successfully created."},
            {"SELECT sq906_8.NEXTVAL + 0 FROM t906 ORDER BY 1 DESC", "6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1"},
            {"CREATE OR REPLACE SEQUENCE sq906_9", "Sequence SQ906_9 successfully created."},
            {"SELECT a, sq906_9.NEXTVAL FROM t906 ORDER BY 2 DESC", "5|6 ;; 4|5 ;; 3|4 ;; 2|3 ;; 1|2 ;; 0|1"},
            {"CREATE OR REPLACE SEQUENCE sq906_10", "Sequence SQ906_10 successfully created."},
            {"SELECT sq906_10.NEXTVAL n, n * 2 m, m + 1 k FROM t906 ORDER BY k DESC",
                "6|12|13 ;; 5|10|11 ;; 4|8|9 ;; 3|6|7 ;; 2|4|5 ;; 1|2|3"},
            {"CREATE OR REPLACE SEQUENCE sq906_11", "Sequence SQ906_11 successfully created."},
            {"SELECT sq906_11.NEXTVAL n FROM t906 x, t906 y WHERE x.a = y.a ORDER BY n DESC",
                "6 ;; 5 ;; 4 ;; 3 ;; 2 ;; 1"},
            {"CREATE OR REPLACE SEQUENCE sq906_12", "Sequence SQ906_12 successfully created."},
            {"SELECT sq906_12.NEXTVAL n FROM t906 QUALIFY ROW_NUMBER() OVER (ORDER BY n) <= 2 ORDER BY n", "1 ;; 2"},
        });
    }
}
