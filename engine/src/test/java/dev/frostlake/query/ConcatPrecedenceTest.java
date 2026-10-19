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
 * The concatenation operator shares the level of + and -, left-associative with them and below *, / and %
 * (live-verified): 1 + 2 || 'a' is 3a, 'a' || 1 + 2 is ('a' || 1) + 2, 'x' || 2 * 3 is x6.
 */
public class ConcatPrecedenceTest extends MeasuredCellsTestSupport {

    @Test
    public void concatenationBindsAsAdditionDoes() {
        assertCells(new String[][] {
            {"SELECT 1 + 2 || 'a'", "3a"},
            {"SELECT 2 * 3 || 'a'", "6a"},
            {"SELECT 'x' || 2 * 3", "x6"},
            {"SELECT SYSTEM$TYPEOF(1 + 2 || 'a')", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1 || 'a')", "VARCHAR(134217728)[LOB]"},
            {"SELECT 'a' || 1 + 2", "REFUSED Numeric value 'a1' is not recognized"},
            {"SELECT 1 || 2 + 3", "15.00000"},
            {"SELECT 1 - 2 || 'a'", "-1a"},
            {"SELECT -1 || 'a'", "-1a"},
            {"SELECT 'a' || 1 = 'a1'", "true"},
            {"SELECT 1 + 2 || 3 + 4", "37.00000"},
            {"SELECT 10 / 4 || 'x'", "2.500000x"},
            {"SELECT 2 * 3 || 4 * 5", "620"},
            {"SELECT 'x' || 2 - 1", "REFUSED Numeric value 'x2' is not recognized"},
            {"SELECT 1 || 2 || 3 + 4", "127.00000"},
            {"SELECT 7 % 4 || 'r'", "3r"},
            {"SELECT 'a' || 'b' || 'c'", "abc"},
            {"SELECT 1 + 1 || 1 + 1", "22.00000"},
        });
    }
}
