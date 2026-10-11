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

import java.time.OffsetDateTime;
import java.util.List;

/** AS_TIMESTAMP_LTZ(v) — the TIMESTAMP_LTZ a VARIANT holds, else NULL; see {@link AsTimestampNtz}. */
public class AsTimestampLtz extends BuiltInFunction {
    public AsTimestampLtz() { super("AS_TIMESTAMP_LTZ", DateTimeType.TIMESTAMP_LTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        return args.get(0) instanceof OffsetDateTime ? args.get(0) : null;
    }

    @Override
    public boolean takesVariantOnly() {
        return true;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
