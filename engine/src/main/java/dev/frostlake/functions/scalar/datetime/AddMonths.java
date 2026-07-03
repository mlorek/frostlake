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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;

import java.time.LocalDate;
import java.util.List;

public class AddMonths extends BuiltInFunction {
    public AddMonths() { super("ADD_MONTHS", DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final LocalDate d = SharedFunctionHelpers.toLocalDate(args.get(0));
        final long months = ((Number) args.get(1)).longValue();
        LocalDate result = d.plusMonths(months);
        // Snowflake preserves end-of-month: if the input is the last day of its month, the result is
        // the last day of the target month (e.g. ADD_MONTHS('2016-02-29', 1) = 2016-03-31).
        if (d.getDayOfMonth() == d.lengthOfMonth()) {
            result = result.withDayOfMonth(result.lengthOfMonth());
        }
        return result;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
