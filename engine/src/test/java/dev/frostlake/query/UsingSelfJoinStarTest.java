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
 * A star over a USING or NATURAL join of a table with itself reads each alias's columns from its own side
 * (live-verified): {@code SELECT * FROM p y JOIN p x USING (k)} prints y.v then x.v, an INSERT ... SELECT * stores
 * both, and a filter on x.v keeps the rows whose second V it names.
 */
public class UsingSelfJoinStarTest extends MeasuredCellsTestSupport {

    @Test
    public void eachAliasReadsItsOwnSide() {
        engine.execute("CREATE OR REPLACE TABLE p (k INT, v INT)");
        engine.execute("INSERT INTO p VALUES (1, 10), (1, 20)");
        engine.execute("CREATE OR REPLACE TABLE pairs (k INT, v1 INT, v2 INT)");
        assertCells(new String[][] {
            {"SELECT * FROM p y JOIN p x USING (k) ORDER BY 2, 3", "1|10|10 ;; 1|10|20 ;; 1|20|10 ;; 1|20|20"},
            {"SELECT * FROM p y JOIN p x USING (k) WHERE x.v = 20 ORDER BY 2", "1|10|20 ;; 1|20|20"},
            {"INSERT INTO pairs SELECT * FROM p y JOIN p x USING (k)", "4"},
            {"SELECT * FROM pairs ORDER BY 2, 3", "1|10|10 ;; 1|10|20 ;; 1|20|10 ;; 1|20|20"},
            {"SELECT * FROM p y LEFT JOIN p x USING (k) ORDER BY 2, 3", "1|10|10 ;; 1|10|20 ;; 1|20|10 ;; 1|20|20"},
            {"SELECT * FROM p y NATURAL JOIN p x ORDER BY 1, 2", "1|10 ;; 1|20"},
            {"SELECT y.*, x.* FROM p y JOIN p x USING (k) ORDER BY 2, 4",
                "1|10|1|10 ;; 1|10|1|20 ;; 1|20|1|10 ;; 1|20|1|20"},
        });
    }
}
