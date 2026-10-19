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
 * A correlated EXISTS or scalar aggregate over two equalities compares a collated inner key under its collation
 * whichever place the equality takes (live-verified): 'abc' matches 'ABC' under 'en-ci' as the join's ON does.
 */
public class CorrelatedCollatedKeysTest extends MeasuredCellsTestSupport {

    @Test
    public void aCollatedSecondKeyStillMatches() {
        engine.execute("CREATE OR REPLACE TABLE co_o (id INT, s VARCHAR)");
        engine.execute("INSERT INTO co_o VALUES (1, 'abc'), (2, 'XYZ')");
        engine.execute("CREATE OR REPLACE TABLE co_i (id INT, s VARCHAR COLLATE 'en-ci', v INT)");
        engine.execute("INSERT INTO co_i VALUES (1, 'ABC', 5), (2, 'xyz', 7)");
        assertCells(new String[][] {
            {"SELECT co_o.id, EXISTS (SELECT 1 FROM co_i WHERE co_i.id = co_o.id AND co_i.s = co_o.s) FROM co_o ORDER "
                + "BY 1",
                "1|true ;; 2|true"},
            {"SELECT co_o.id, EXISTS (SELECT 1 FROM co_i WHERE co_i.s = co_o.s AND co_i.id = co_o.id) FROM co_o ORDER "
                + "BY 1",
                "1|true ;; 2|true"},
            {"SELECT co_o.id, (SELECT MAX(v) FROM co_i WHERE co_i.id = co_o.id AND co_i.s = co_o.s) FROM co_o ORDER "
                + "BY 1",
                "1|5 ;; 2|7"},
            {"SELECT co_o.id FROM co_o WHERE EXISTS (SELECT 1 FROM co_i WHERE co_i.id = co_o.id AND co_i.s = co_o.s) "
                + "ORDER BY 1",
                "1 ;; 2"},
            {"SELECT co_o.id FROM co_o JOIN co_i ON co_i.id = co_o.id AND co_i.s = co_o.s ORDER BY 1", "1 ;; 2"},
        });
    }
}
