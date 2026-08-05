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
import dev.frostlake.types.GeographyType;
import dev.frostlake.values.GeoValue;

import java.util.List;

/** ST_MAKEPOINT(longitude, latitude) — a GEOGRAPHY point (ST_POINT is the same function). */
public class StMakePoint extends BuiltInFunction {
    public StMakePoint() { super("ST_MAKEPOINT", GeographyType.GEOGRAPHY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        return GeoValue.ofNode(GeoShapes.pointModel(
            ((Number) args.get(0)).doubleValue(), ((Number) args.get(1)).doubleValue()), true, 4326);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
