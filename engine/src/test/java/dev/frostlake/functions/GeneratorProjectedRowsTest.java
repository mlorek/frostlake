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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A GENERATOR's rows past 2147483647 read through a derived table or a CTE that only projects them, under a
 * LIMIT of the select reading it, or under a WHERE of literals that is TRUE: the account streams the rows and
 * generates no more of them than are read (all live-verified).
 */
public class GeneratorProjectedRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void projectedRows() {
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT * FROM (SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))) "
                + "LIMIT 2)");
        assertAnswer("1 ;; 1", "SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))) LIMIT 2");
        assertAnswer("1 ;; 1 ;; 1",
            "SELECT x FROM (SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)))) LIMIT 3");
        assertAnswer("1 ;; 1",
            "WITH c AS (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))) SELECT x FROM c LIMIT 2");
        assertAnswer("1 ;; 1",
            "SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) WHERE TRUE LIMIT 2)");
        assertAnswer("1 ;; 1",
            "SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) WHERE 1 = 1) LIMIT 2");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 2) WHERE x = 1");
        assertAnswer("1 ;; 1",
            "SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 5) LIMIT 2");
    }
}
