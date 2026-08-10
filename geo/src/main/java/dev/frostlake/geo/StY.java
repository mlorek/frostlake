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
import dev.frostlake.types.NumericType;
import dev.frostlake.values.GeoValue;

import java.util.List;

/** ST_Y — a Point's latitude/y; any other shape raises Snowflake's type error. */
public class StY extends BuiltInFunction {
    public StY() { super("ST_Y", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        final GeoValue geo = GeoShapes.asGeo(args.get(0));
        if (geo == null) return null;
        if (!"Point".equals(geo.geoType())) {
            throw new RuntimeException("Type " + geo.geoType() + " is not supported as argument to ST_Y.");
        }
        return geo.node().get("coordinates").get(1).asDouble();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
