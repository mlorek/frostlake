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
 * TO_TIMESTAMP / TO_TIMESTAMP_LTZ / TO_TIMESTAMP_TZ. They share one conversion but NOT one declared
 * type: live reports each under the flavour its own name asks for, so the name decides — and the value
 * is held the way that flavour holds it (see {@link TimestampFlavourConversion}).
 */
public class ToTimestamp extends BuiltInFunction {
    public ToTimestamp(final String name) { super(name, TimestampFlavours.forFunctionName(name)); }

    /**
     * The BARE spelling is re-resolved on every call, because the session's TIMESTAMP_TYPE_MAPPING can
     * change between statements and the registry builds each function ONCE at startup — a type decided
     * in the constructor would be frozen at whatever the mapping was before any session existed. The
     * flavoured spellings carry their answer in their own name and never move.
     */
    @Override
    public DataType getReturnType() {
        return isBareSpelling() ? TimestampFlavours.forFunctionName(getName()) : super.getReturnType();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        // The second argument is a format for a string input, or a scale (0-9) for a numeric epoch.
        final Object formatOrScale = args.size() >= 2 ? args.get(1) : null;
        // A NULL format is a NULL answer (live-verified: TO_TIMESTAMP('2020-01-15', NULL)).
        if (args.size() >= 2 && formatOrScale == null) {
            return null;
        }
        return TimestampFlavourConversion.convert(getReturnType().getName(), args.get(0), formatOrScale);
    }

    /** Whether this is the unflavoured TO_TIMESTAMP, the only spelling the mapping reaches. */
    private boolean isBareSpelling() {
        return "TO_TIMESTAMP".equals(getName());
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
