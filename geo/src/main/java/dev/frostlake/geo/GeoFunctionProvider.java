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

import dev.frostlake.functions.FunctionProvider;
import dev.frostlake.functions.FunctionRegistry;

/**
 * Contributes the GEOGRAPHY / GEOMETRY function pack to the engine via the {@code FunctionProvider}
 * ServiceLoader SPI — add {@code frostlake-geo} to the classpath and the ST_ family is available;
 * without it the engine keeps only the type surface (columns parse, values carry) and geo function
 * calls are unknown.
 */
public class GeoFunctionProvider implements FunctionProvider {

    @Override
    public void contribute(final FunctionRegistry registry) {
        registry.register(new ToGeography());
        registry.register(new TryToGeography());
        registry.register(new ToGeometry());
        registry.register(new TryToGeometry());
        registry.register(new StMakePoint());
        registry.register(new StMakeGeomPoint());
        registry.register(new StAsText());
        registry.register(new StAsGeojson());
        registry.register(new StAsWkb());
        registry.register(new StX());
        registry.register(new StY());
        registry.register(new StSrid());
        registry.register(new StDimension());
        registry.register(new StNPoints());
        registry.register(new StXMin());
        registry.register(new StXMax());
        registry.register(new StYMin());
        registry.register(new StYMax());
        registry.register(new StDistance());
        registry.register(new StLength());
        registry.register(new StArea());
        registry.register(new StPerimeter());
        registry.register(new StContains());
        registry.register(new StWithin());
        registry.register(new StIntersects());
        registry.register(new StDisjoint());
        registry.register(new StDwithin());
        registry.register(new StCentroid());
        registry.registerAlias("ST_POINT", new StMakePoint());
        registry.registerAlias("ST_GEOM_POINT", new StMakeGeomPoint());
        registry.registerAlias("ST_ASWKT", new StAsText());
        registry.registerAlias("ST_ASBINARY", new StAsWkb());
        registry.registerAlias("ST_NUMPOINTS", new StNPoints());
        registry.registerAlias("ST_GEOGRAPHYFROMWKT", new ToGeography());
        registry.registerAlias("ST_GEOGFROMTEXT", new ToGeography());
        registry.registerAlias("ST_GEOMETRYFROMWKT", new ToGeometry());
        registry.registerAlias("ST_GEOMFROMTEXT", new ToGeometry());
    }
}
