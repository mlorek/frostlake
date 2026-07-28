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

import java.time.LocalDateTime;
import java.util.List;

public class TimestampFromParts extends BuiltInFunction {
    public TimestampFromParts() { super("TIMESTAMP_FROM_PARTS", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — any NULL part yields NULL (an NPE escaped before)
        for (int i = 0; i < args.size(); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        int year   = ((Number) args.get(0)).intValue();
        int month  = ((Number) args.get(1)).intValue();
        int day    = ((Number) args.get(2)).intValue();
        int hour   = ((Number) args.get(3)).intValue();
        int minute = ((Number) args.get(4)).intValue();
        int second = ((Number) args.get(5)).intValue();
        int nanos  = args.size() > 6 && args.get(6) != null ? ((Number) args.get(6)).intValue() : 0;
        return LocalDateTime.of(year, month, day, hour, minute, second, nanos);
    }

    @Override public int getMinArgCount() { return 6; }
    @Override public int getMaxArgCount() { return 7; }
}
