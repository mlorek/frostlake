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

import dev.frostlake.functions.aggregate.Sum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SUM's {@link AggregateFunction.Accumulator} contract. The grouped and windowed fast paths compute
 * SUM directly, so the accumulator is exercised only through this API — accumulate/reset/merge and
 * the Snowflake rule that SUM over no non-null inputs is NULL, not zero.
 */
public class SumAccumulatorContractTest {

    @Test
    public void accumulateSkipsNullsAndSums() {
        final AggregateFunction.Accumulator acc = new Sum().createAccumulator();
        assertNull(acc.getResult(), "no inputs yet: SUM is NULL");

        acc.accumulate(1);
        acc.accumulate(null);
        acc.accumulate(new BigDecimal("2.5"));
        assertEquals(0, new BigDecimal("3.5").compareTo((BigDecimal) acc.getResult()));

        acc.reset();
        assertNull(acc.getResult(), "reset returns to the NULL (no inputs) state");
    }

    @Test
    public void mergeCombinesPartialSums() {
        final Sum sum = new Sum();
        final AggregateFunction.Accumulator left = sum.createAccumulator();
        final AggregateFunction.Accumulator right = sum.createAccumulator();
        left.accumulate(10);
        right.accumulate(32);
        left.merge(right);
        assertEquals(0, new BigDecimal("42").compareTo((BigDecimal) left.getResult()));

        final AggregateFunction.Accumulator empty = sum.createAccumulator();
        final AggregateFunction.Accumulator alsoEmpty = sum.createAccumulator();
        empty.merge(alsoEmpty);
        assertNull(empty.getResult(), "merging two empty accumulators stays NULL");
    }
}
