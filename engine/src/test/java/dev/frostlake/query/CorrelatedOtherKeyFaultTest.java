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
 * A correlated subquery reading the outer row through an equality (live-verified): IN refuses a member's fault in an
 * inner row of a key no outer row asks for, while EXISTS and a scalar aggregate read the asked keys' rows alone and
 * answer; a residual predicate beside the equality is judged per outer row.
 */
public class CorrelatedOtherKeyFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void anInnerRowOfAnotherKey() {
        engine.execute("CREATE OR REPLACE TABLE ka914 (id INT, v INT)");
        engine.execute("INSERT INTO ka914 VALUES (1, 10), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE kb914 (id INT, k INT, z INT)");
        engine.execute("INSERT INTO kb914 VALUES (1, 10, 0), (2, 30, 1), (3, 30, 2)");
        assertCells(new String[][] {
            {"SELECT ka914.id, ka914.id IN (SELECT 10 / kb914.z FROM kb914 WHERE kb914.k = ka914.v) FROM ka914 "
                + "WHERE ka914.v = 30 ORDER BY 1",
                "REFUSED Division by zero"},
            {"SELECT ka914.id, EXISTS (SELECT 1 FROM kb914 WHERE kb914.k = ka914.v AND 10 / kb914.z > 0) FROM "
                + "ka914 WHERE ka914.v = 30 ORDER BY 1",
                "2|true"},
            {"SELECT ka914.id, (SELECT MAX(10 / kb914.z) FROM kb914 WHERE kb914.k = ka914.v) FROM ka914 WHERE "
                + "ka914.v = 30 ORDER BY 1",
                "2|10.000000"},
            {"SELECT ka914.id, EXISTS (SELECT 10 / kb914.z FROM kb914 WHERE kb914.k = ka914.v) FROM ka914 WHERE "
                + "ka914.v = 30 ORDER BY 1",
                "2|true"},
            {"SELECT ka914.id, (SELECT COUNT(*) FROM kb914 WHERE kb914.k = ka914.v AND kb914.id > ka914.id) "
                + "FROM ka914 ORDER BY 1",
                "1|0 ;; 2|1"},
            {"SELECT ka914.id FROM ka914 WHERE EXISTS (SELECT 1 FROM kb914 WHERE kb914.k = ka914.v AND kb914.z "
                + "> ka914.id) ORDER BY 1",
                ""},
        });
    }
}
