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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.DateTimeType;

import java.time.ZonedDateTime;
import java.util.List;

/** AS_TIMESTAMP_TZ(v [, scale]) — the TIMESTAMP_TZ a VARIANT holds, else NULL; see {@link AsTimestampNtz}. */
public class AsTimestampTz extends BuiltInFunction {
    public AsTimestampTz() { super("AS_TIMESTAMP_TZ", DateTimeType.TIMESTAMP_TZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        final int scale = VariantTimestampScale.of("AS_TIMESTAMP_TZ", args);
        return args.get(0) instanceof ZonedDateTime ? VariantTimestampScale.cut(args.get(0), scale) : null;
    }

    @Override
    public boolean takesVariantOnly() {
        return true;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
