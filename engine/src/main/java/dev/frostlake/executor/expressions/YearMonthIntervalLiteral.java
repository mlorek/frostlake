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
 * A year-month interval value that knows the fields its expression declares: a unit-suffixed literal
 * ({@code INTERVAL '1' YEAR}, {@code INTERVAL '14' MONTH}, {@code INTERVAL '1-2' YEAR TO MONTH}), or the result
 * of arithmetic on one. It is its span in months, so {@code INTERVAL '1' YEAR} and {@code INTERVAL '12' MONTH}
 * are equal, ordered and hashed as one, while its text follows its fields.
 */
final class YearMonthIntervalLiteral extends YearMonthInterval implements IntervalLiteral {

    private static final long serialVersionUID = 1L;

    private final IntervalQualifier qualifier;

    YearMonthIntervalLiteral(final long months, final IntervalQualifier qualifier) {
        super(months);
        this.qualifier = qualifier;
    }

    @Override
    public IntervalQualifier qualifier() {
        return qualifier;
    }

    /** @return the text of its fields: {@code +1} for {@code INTERVAL '1' YEAR} */
    @Override
    public String toString() {
        return IntervalText.render(this, qualifier);
    }
}
