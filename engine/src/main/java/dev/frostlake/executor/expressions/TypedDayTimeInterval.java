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

import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.values.DayTimeInterval;

import java.math.BigDecimal;

/**
 * A day-time interval converted to a declared type — a cast target or a column — which keeps the fields and
 * fractional digits it was converted to: it prints in them, and a string compared with it is read in them
 * ({@code h = '24'} over an {@code INTERVAL HOUR} column reads 24 hours). Equality, order and hashing stay the
 * span's alone, as for every day-time interval.
 */
final class TypedDayTimeInterval extends DayTimeInterval implements IntervalLiteral {

    private static final long serialVersionUID = 1L;

    private final IntervalQualifier qualifier;
    private final int fraction;

    /**
     * @param seconds   the span in seconds, already cut to the type's fields
     * @param qualifier the type's fields
     * @param fraction  the type's fractional second digits
     */
    TypedDayTimeInterval(final BigDecimal seconds, final IntervalQualifier qualifier, final int fraction) {
        super(seconds);
        this.qualifier = qualifier;
        this.fraction = fraction;
    }

    @Override
    public IntervalQualifier qualifier() {
        return qualifier;
    }

    /** @return the text of its type's fields: {@code +25} for an {@code INTERVAL HOUR} */
    @Override
    public String toString() {
        return IntervalFields.render(this, qualifier, fraction);
    }
}
