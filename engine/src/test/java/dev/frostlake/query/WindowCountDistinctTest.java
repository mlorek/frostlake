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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * COUNT(DISTINCT x) as a window function counts each value of its frame once, as the grouped COUNT does.
 * The answer is live-verified.
 */
public class WindowCountDistinctTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE s (id INT, v INT)");
        engine.execute("INSERT INTO s VALUES (1, 10), (2, 20), (3, 15), (4, 12), (5, 30), (6, 31), (7, 5), (8, 6)");
    }

    @Test
    public void aWindowedCountDistinctCountsEachValueOnce() {
        assertAnswer(
            "1|2 ;; 2|2 ;; 3|2 ;; 4|2 ;; 5|2 ;; 6|2 ;; 7|2 ;; 8|2",
            "SELECT id, COUNT(DISTINCT v % 2) OVER (PARTITION BY id > 4) AS c FROM s ORDER BY id");
    }
}
