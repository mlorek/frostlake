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
import dev.frostlake.types.GeometryType;
import dev.frostlake.values.GeoValue;

import java.util.List;

/** TO_GEOMETRY(input [, srid] [, allow_invalid]) — parses WKT or GeoJSON into a planar GEOMETRY. */
public class ToGeometry extends BuiltInFunction {
    public ToGeometry() { super("TO_GEOMETRY", GeometryType.GEOMETRY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof GeoValue) return args.get(0);
        final GeoValue parsed = GeoShapes.parse(args.get(0).toString(), false);
        if (args.size() > 1 && args.get(1) instanceof Number) {
            return GeoValue.ofNode(parsed.node(), false, ((Number) args.get(1)).intValue());
        }
        return parsed;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 3; }
}
