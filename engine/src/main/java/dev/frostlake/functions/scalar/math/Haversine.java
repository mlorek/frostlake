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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

public class Haversine extends NumericArgumentFunction {
    public Haversine() { super("HAVERSINE", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null || args.get(3) == null) return null;
        final double lat1 = Math.toRadians(((Number) args.get(0)).doubleValue());
        final double lon1 = Math.toRadians(((Number) args.get(1)).doubleValue());
        final double lat2 = Math.toRadians(((Number) args.get(2)).doubleValue());
        final double lon2 = Math.toRadians(((Number) args.get(3)).doubleValue());
        final double dlat = lat2 - lat1;
        final double dlon = lon2 - lon1;
        final double a = Math.pow(Math.sin(dlat / 2), 2)
                 + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371.0 * 2 * Math.asin(Math.sqrt(a));
    }

    @Override
    public int getMinArgCount() { return 4; }
    @Override
    public int getMaxArgCount() { return 4; }
}
