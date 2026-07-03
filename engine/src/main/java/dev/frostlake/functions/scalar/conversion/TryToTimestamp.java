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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;

import java.util.List;

/**
 * TRY_TO_TIMESTAMP / _NTZ / _LTZ / _TZ — the non-throwing form of TO_TIMESTAMP: parses the input as a
 * timestamp (honoring an optional format), returning NULL instead of raising on unparseable input or NULL
 * input. The _LTZ / _TZ variants behave like _NTZ in this engine (no timezone materialization), matching how
 * {@code ToTimestamp} treats the TO_TIMESTAMP family.
 */
public class TryToTimestamp extends BuiltInFunction {
    public TryToTimestamp() { this("TRY_TO_TIMESTAMP_NTZ"); }

    public TryToTimestamp(final String name) { super(name, DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String format = args.size() >= 2 && args.get(1) != null ? args.get(1).toString() : null;
        try { return SharedFunctionHelpers.parseTimestampWithFormat(args.get(0), format); } catch (final Exception e) { return null; }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
