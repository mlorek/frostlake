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
import dev.frostlake.types.DateTimeType;

import java.time.LocalDate;
import java.util.List;

public class DateFromParts extends BuiltInFunction {
    public DateFromParts() { super("DATE_FROM_PARTS", DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        int year  = ((Number) args.get(0)).intValue();
        int month = ((Number) args.get(1)).intValue();
        int day   = ((Number) args.get(2)).intValue();
        return LocalDate.of(year, month, day);
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 3; }
}
