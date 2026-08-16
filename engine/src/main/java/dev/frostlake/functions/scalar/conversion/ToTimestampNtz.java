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

import java.util.List;

public class ToTimestampNtz extends BuiltInFunction {
    public ToTimestampNtz() { super("TO_TIMESTAMP_NTZ", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // The second argument is a format for a string input, or a scale (0-9) for a numeric epoch.
        final Object formatOrScale = args.size() >= 2 ? args.get(1) : null;
        if (args.size() >= 2 && formatOrScale == null) {
            return null;
        }
        return TimestampFlavourConversion.convert("TIMESTAMP_NTZ", args.get(0), formatOrScale);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
