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

/**
 * ST_CENTROID — the value's center point (planar area/length weighting; an approximation of
 * Snowflake's spherical centroid, within ~1e-4 degrees at ordinary extents).
 */
public class StCentroid extends BuiltInFunction {
    public StCentroid() { super("ST_CENTROID", GeographyType.GEOGRAPHY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final GeoValue geo = GeoShapes.asGeo(args.get(0));
        if (geo == null) return null;
        final double[] c = GeoShapes.centroid(geo.node());
        if (c == null) return null;
        return GeoValue.ofNode(GeoShapes.pointModel(c[0], c[1]), geo.isGeography(), geo.getSrid());
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
