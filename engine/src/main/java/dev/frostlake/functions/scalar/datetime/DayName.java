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
import dev.frostlake.types.StringType;

import java.util.List;

public class DayName extends BuiltInFunction {
    public DayName() { super("DAYNAME", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // Three letters, not the full name — live answers Mon,Tue,Wed,Thu,Fri,Sat,Sun, measured
        // across a whole week, and NULL for NULL. A TIMESTAMP argument answers the same way.
        final String day = SharedFunctionHelpers.toLocalDate(args.get(0)).getDayOfWeek().name();
        return day.substring(0, 1) + day.substring(1, 3).toLowerCase();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
