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

package dev.frostlake.geo;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;
import dev.frostlake.values.GeoValue;

import java.util.List;

/** ST_DWITHIN(a, b, meters) — whether the minimum distance is within the given bound. */
public class StDwithin extends BuiltInFunction {
    public StDwithin() { super("ST_DWITHIN", BooleanType.BOOLEAN); }

    @Override
    public Object evaluate(final List<Object> args) {
        final GeoValue a = GeoShapes.asGeo(args.get(0));
        final GeoValue b = GeoShapes.asGeo(args.get(1));
        if (a == null || b == null || args.get(2) == null) return null;
        GeoShapes.requireSameKind("ST_DWITHIN", a, b);
        return Boolean.valueOf(GeoShapes.minDistance(a, b) <= ((Number) args.get(2)).doubleValue());
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
