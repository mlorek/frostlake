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

import java.time.ZoneId;
import java.util.List;

public class ConvertTimezone extends BuiltInFunction {
    public ConvertTimezone() { super("CONVERT_TIMEZONE", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.size() == 2) {
            if (args.get(1) == null) return null;
            final ZoneId tz = ZoneId.of(args.get(0).toString());
            return SharedFunctionHelpers.toLocalDateTime(args.get(1)).atZone(ZoneId.of("UTC")).withZoneSameInstant(tz).toLocalDateTime();
        }
        if (args.get(2) == null) return null;
        final ZoneId src = ZoneId.of(args.get(0).toString());
        final ZoneId tgt = ZoneId.of(args.get(1).toString());
        return SharedFunctionHelpers.toLocalDateTime(args.get(2)).atZone(src).withZoneSameInstant(tgt).toLocalDateTime();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
