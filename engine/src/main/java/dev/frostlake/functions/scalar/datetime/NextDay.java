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
import java.time.DayOfWeek;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class NextDay extends BuiltInFunction {
    public NextDay() { super("NEXT_DAY", DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        LocalDate d = SharedFunctionHelpers.toLocalDate(args.get(0));
        DayOfWeek dow = SharedFunctionHelpers.parseDayOfWeek(args.get(1).toString());
        return d.with(TemporalAdjusters.next(dow));
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
