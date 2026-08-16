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
import dev.frostlake.types.StringType;
import dev.frostlake.values.GeoValue;

import java.util.List;

/** ST_ASTEXT / ST_ASWKT — the value's normalized WKT text ({@code LINESTRING(0 0,1 0)}). */
public class StAsText extends BuiltInFunction {
    public StAsText() { super("ST_ASTEXT", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        final GeoValue geo = GeoShapes.asGeo(args.get(0));
        return geo == null ? null : GeoShapes.toWkt(geo.node());
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
