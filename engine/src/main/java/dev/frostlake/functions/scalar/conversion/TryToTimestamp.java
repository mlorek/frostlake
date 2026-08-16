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
import dev.frostlake.types.DataType;
import dev.frostlake.types.TimestampFlavours;

import java.util.List;

/**
 * TRY_TO_TIMESTAMP / _NTZ / _LTZ / _TZ — the non-throwing form of TO_TIMESTAMP: the same conversion into
 * the same flavour's value (see {@link TimestampFlavourConversion}), NULL instead of a refusal for input it
 * cannot read. The source must be text — a NUMBER is refused while the statement compiles, as TRY_CAST
 * refuses it — so a string of digits is the only epoch it meets.
 */
public class TryToTimestamp extends BuiltInFunction {
    public TryToTimestamp() { this("TRY_TO_TIMESTAMP_NTZ"); }
    public TryToTimestamp(final String name) { super(name, TimestampFlavours.forFunctionName(name)); }

    /**
     * The BARE spelling follows the session's TIMESTAMP_TYPE_MAPPING, re-resolved on every call as
     * TO_TIMESTAMP's is: the registry builds each function once, before any session exists.
     */
    @Override
    public DataType getReturnType() {
        return "TRY_TO_TIMESTAMP".equals(getName())
            ? TimestampFlavours.forFunctionName(getName()) : super.getReturnType();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // The second argument is a format for a string input, or a scale (0-9) for a numeric epoch.
        final Object formatOrScale = args.size() >= 2 ? args.get(1) : null;
        if (args.size() >= 2 && formatOrScale == null) {
            return null;
        }
        try {
            return TimestampFlavourConversion.convert(getReturnType().getName(), args.get(0), formatOrScale);
        } catch (final Exception e) {
            return null;
        }
    }
    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
