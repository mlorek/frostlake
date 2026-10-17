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
 * A VECTOR cast to ARRAY holds its elements as the account's own TO_ARRAY does (live-verified): a FLOAT vector's are
 * the DOUBLEs its 32-bit elements widen to, so 0.1234567 reads 0.1234567016 in ARRAY_TO_STRING, the cast text and
 * TO_JSON, and an INT vector's stay whole numbers.
 */
public class VectorToArrayElementsTest extends MeasuredCellsTestSupport {

    @Test
    public void aVectorCastToArrayHoldsItsWidenedElements() {
        assertCells(new String[][] {
            {"SELECT ARRAY_TO_STRING([1.0, 0.1234567]::VECTOR(FLOAT, 2)::ARRAY, ',')", "1,0.1234567016"},
            {"SELECT ([1.0, 0.1234567]::VECTOR(FLOAT, 2)::ARRAY)::VARCHAR",
                "[1.000000000000000e+00,1.234567016363144e-01]"},
            {"SELECT TO_JSON([1.0, 0.1234567]::VECTOR(FLOAT, 2)::ARRAY)",
                "[1.000000000000000e+00,1.234567016363144e-01]"},
            {"SELECT GET([1.0, 0.1234567]::VECTOR(FLOAT, 2)::ARRAY, 1)::VARCHAR, TYPEOF(GET([1.0, "
                + "0.1234567]::VECTOR(FLOAT, 2)::ARRAY, 1))",
                "0.1234567016|DOUBLE"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(1.5::FLOAT, 0.1234567::FLOAT, 1e20::FLOAT), ',')",
                "1.5,0.1234567,1e+20"},
            {"SELECT ARRAY_TO_STRING([1, 2]::VECTOR(INT, 2)::ARRAY, ','), TO_JSON([1, 2]::VECTOR(INT, 2)::ARRAY)",
                "1,2|[1,2]"},
            {"SELECT TYPEOF(GET([1, 2]::VECTOR(INT, 2)::ARRAY, 0)), TYPEOF(GET([1.0, 2.5]::VECTOR(FLOAT, "
                + "2)::ARRAY, 0))",
                "INTEGER|DOUBLE"},
            {"SELECT ARRAY_SIZE([1.0, 0.5]::VECTOR(FLOAT, 2)::ARRAY), [1.0, 0.5]::VECTOR(FLOAT, 2)::ARRAY[1]::VARCHAR",
                "2|0.5"},
            {"SELECT TO_VARIANT(0.1234567::FLOAT)::VARCHAR, TO_JSON(TO_VARIANT(0.1234567::FLOAT))",
                "0.1234567|1.234567000000000e-01"},
        });
    }
}
