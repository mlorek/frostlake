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

/** TO_GEOGRAPHY(input [, allow_invalid]) — parses WKT or GeoJSON into a GEOGRAPHY (SRID 4326). */
public class ToGeography extends BuiltInFunction {
    public ToGeography() { super("TO_GEOGRAPHY", GeographyType.GEOGRAPHY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (args.get(0) instanceof GeoValue) return args.get(0);
        return GeoShapes.parse(args.get(0).toString(), true);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
