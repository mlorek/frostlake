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
 * TOP n limits a select without a FROM as it limits one over a FROM (live-verified): TOP 0 answers no row, also
 * as a derived table.
 */
public class FromlessTopTest extends MeasuredCellsTestSupport {

    @Test
    public void topLimitsAFromlessSelect() {
        assertCells(new String[][] {
            {"SELECT TOP 0 1", ""},
            {"SELECT TOP 0 1 x, 2 y", ""},
            {"SELECT COUNT(*) FROM (SELECT TOP 0 1)", "0"},
            {"SELECT TOP 0 * FROM (SELECT 1)", ""},
            {"SELECT TOP 1 1 UNION ALL SELECT 2", "1 ;; 2"},
        });
    }
}
