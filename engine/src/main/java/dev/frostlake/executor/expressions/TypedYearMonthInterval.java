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
import dev.frostlake.values.YearMonthInterval;

/**
 * A year-month interval converted to a declared type, which keeps the fields it was converted to: an
 * {@code INTERVAL YEAR TO MONTH} prints {@code +1-02}, an {@code INTERVAL MONTH} {@code +14}. Equality, order
 * and hashing stay the count of months alone.
 */
final class TypedYearMonthInterval extends YearMonthInterval implements IntervalLiteral {

    private static final long serialVersionUID = 1L;

    private final IntervalQualifier qualifier;

    /**
     * @param months    the span in months, already cut to the type's fields
     * @param qualifier the type's fields
     */
    TypedYearMonthInterval(final long months, final IntervalQualifier qualifier) {
        super(months);
        this.qualifier = qualifier;
    }

    @Override
    public IntervalQualifier qualifier() {
        return qualifier;
    }

    @Override
    public String toString() {
        return IntervalFields.render(this, qualifier, 0);
    }
}
