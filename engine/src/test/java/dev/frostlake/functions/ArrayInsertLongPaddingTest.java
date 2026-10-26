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
 * ARRAY_INSERT at a position far past either end pads the gap with {@code undefined} holes, and the account builds the
 * array however long the gap: ARRAY_SIZE answers 1,000,000,001 for a position of a thousand million, from either end
 * (live-verified). Only past 2,147,483,647 elements is the array too large. The holes read as SQL NULL, and the
 * inserted element stands where it was put.
 */
public class ArrayInsertLongPaddingTest extends MeasuredCellsTestSupport {

    @Test
    public void aFarPositionBuildsTheWholeArray() {
        assertCells(new String[][] {
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 100000000, 9))", "100000001"},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 134217728, 9))", "134217729"},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 268435456, 9))", "268435457"},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 1000000000, 9))", "1000000001"},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], -1000000000, 9))", "1000000001"},
            {"SELECT ARRAY_SIZE(ARRAY_INSERT([1,2], 2147483647, 9))",
                "REFUSED Result array of (ARRAY_INSERT) is too large"},
        });
    }

    @Test
    public void theHolesAndTheElementReadWhereTheyStand() {
        assertCells(new String[][] {
            {"SELECT ARRAY_INSERT([1,2], 1000000000, 9)[0], ARRAY_INSERT([1,2], 1000000000, 9)[5], "
                + "ARRAY_INSERT([1,2], 1000000000, 9)[1000000000]", "1|null|9"},
            {"SELECT ARRAY_INSERT([1,2], -1000000000, 9)[0], ARRAY_INSERT([1,2], -1000000000, 9)[1000000000]",
                "9|2"},
        });
    }
}
