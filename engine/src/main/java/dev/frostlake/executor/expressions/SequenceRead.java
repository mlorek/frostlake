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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.NumericType;
import dev.frostlake.values.ValueRange;

import java.math.BigDecimal;

/**
 * What the plan knows of a sequence read, {@code <sequence>.NEXTVAL}, before any value is handed out. It
 * declares NUMBER(19,0), which a result column, a CTAS and a view over it carry, and it is tagged [SB8], the
 * signed 64-bit integer a sequence counts in, though nineteen digits alone would need sixteen bytes. That
 * interval survives being picked and negated but nothing computed from it: {@code -s.NEXTVAL} and
 * {@code IFF(TRUE, s.NEXTVAL, 1)} are [SB8], {@code s.NEXTVAL + 0} is NUMBER(20,0)[SB16] (live-verified).
 */
final class SequenceRead {

    /** The declared type of a sequence read. */
    static final NumericType TYPE = new NumericType("NUMBER", 19, 0);

    /** Every value a sequence may hand out, symmetric so a negation stays within the same width. */
    private static final ValueRange RANGE = ValueRange.between(BigDecimal.valueOf(-Long.MAX_VALUE),
        BigDecimal.valueOf(Long.MAX_VALUE)).opaqueToConditions().opaqueToComputation();

    private SequenceRead() {
    }

    /**
     * The interval a sequence read is tagged by.
     *
     * @return the 64-bit interval, opaque to conditions and to computation
     */
    static ValueRange range() {
        return RANGE;
    }
}
