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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.DateTimeType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ToTime extends BuiltInFunction {
    public ToTime() { super("TO_TIME", DateTimeType.TIME); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        Object v = args.get(0);
        if (v instanceof LocalTime) return v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalTime();
        String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (final Exception ignored) {}
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); } catch (final Exception ignored) {}
        throw new RuntimeException("Cannot parse time: " + s);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
