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
import tools.jackson.databind.JsonNode;

import java.util.List;

/** ST_DIMENSION — 0 for points, 1 for lines, 2 for polygons (max across a collection). */
public class StDimension extends BuiltInFunction {
    public StDimension() { super("ST_DIMENSION", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        final GeoValue geo = GeoShapes.asGeo(args.get(0));
        return geo == null ? null : Long.valueOf(dimension(geo.geoType(), geo));
    }

    private int dimension(final String type, final GeoValue geo) {
        switch (type) {
            case "Point":
            case "MultiPoint":
                return 0;
            case "LineString":
            case "MultiLineString":
                return 1;
            case "Polygon":
            case "MultiPolygon":
                return 2;
            default: {
                int max = 0;
                for (final JsonNode part : geo.node().get("geometries")) {
                    final String partType = part.get("type").asText();
                    if (partType.contains("Polygon")) {
                        max = Math.max(max, 2);
                    } else if (partType.contains("LineString")) {
                        max = Math.max(max, 1);
                    }
                }
                return max;
            }
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
