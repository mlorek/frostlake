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

public class WidthBucket extends NumericArgumentFunction {
    public WidthBucket() { super("WIDTH_BUCKET", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final double val   = ((Number) args.get(0)).doubleValue();
        final double lo    = ((Number) args.get(1)).doubleValue();
        final double hi    = ((Number) args.get(2)).doubleValue();
        final long   count = ((Number) args.get(3)).longValue();
        if (count <= 0) throw new RuntimeException("WIDTH_BUCKET: bucket count must be positive");
        if (val < lo) return 0L;
        if (val >= hi) return count + 1;
        return (long) Math.floor(count * (val - lo) / (hi - lo)) + 1;
    }

    @Override
    public int getMinArgCount() { return 4; }
    @Override
    public int getMaxArgCount() { return 4; }
}
