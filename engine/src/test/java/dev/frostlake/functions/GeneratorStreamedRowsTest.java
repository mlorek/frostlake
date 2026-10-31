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
 * A GENERATOR read alone under a LIMIT or a FETCH generates no more rows than it is read for, its OFFSET's
 * included: a ROWCOUNT past what a list holds answers its first rows, SEQ4 numbers the rows kept, and a TIMELIMIT
 * ends with them. Past what a list holds, a WHERE reading each row is tested until the LIMIT is met, constant
 * aggregates count the rows, and a WHERE over a derived table's literal columns is decided before any row (all
 * live-verified).
 */
public class GeneratorStreamedRowsTest extends MeasuredCellsTestSupport {

    @Test
    public void streamedRows() {
        assertAnswer("0 ;; 1 ;; 2", "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 3");
        assertAnswer("10",
            "SELECT COUNT(*) FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 10)");
        assertAnswer("0 ;; 1", "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 2147483648)) LIMIT 2");
        assertAnswer("0", "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 100000000000000000000)) LIMIT 1");
        assertAnswer("5", "SELECT COUNT(*) FROM (SELECT SEQ4() AS s FROM TABLE(GENERATOR(TIMELIMIT => 1)) LIMIT 5)");
        assertAnswer("0 ;; 1", "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 2 OFFSET 5");
        assertAnswer("0 ;; 1",
            "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000, TIMELIMIT => 1)) LIMIT 2");
        assertAnswer("1 ;; 1",
            "SELECT UNIFORM(1, 1, RANDOM()) AS u FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 2");
        assertAnswer("0 ;; 1",
            "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) FETCH FIRST 2 ROWS ONLY");
    }

    @Test
    public void filteredCountedAndFoldedRows() {
        assertAnswer("4", "SELECT COUNT(*) FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) "
            + "WHERE UNIFORM(1, 2, RANDOM()) = 1 LIMIT 4)");
        assertAnswer("1 ;; 1",
            "SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))) WHERE x = 1 LIMIT 2");
        assertAnswer("3000000000", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))");
        assertAnswer("3000000000", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) WHERE TRUE");
        assertAnswer("3000000000|1", "SELECT COUNT(*), MAX(1) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))");
    }
}
