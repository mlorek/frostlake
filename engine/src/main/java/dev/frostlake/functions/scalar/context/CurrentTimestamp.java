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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.StatementClock;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.DateTimeType;

import java.util.List;

public class CurrentTimestamp extends BuiltInFunction {
    // Live types this TIMESTAMP_LTZ(9) — the session-timezone flavor, not NTZ.
    public CurrentTimestamp() { super("CURRENT_TIMESTAMP", DateTimeType.TIMESTAMP_LTZ); }

    @Override
    public Object evaluate(final List<Object> args) { return StatementClock.now(); }

    @Override
    public int getMinArgCount() { return 0; }
    // Snowflake accepts an optional fractional-seconds precision argument (CURRENT_TIMESTAMP(3));
    // the engine renders full precision regardless, so the argument is accepted and ignored.
    @Override
    public int getMaxArgCount() { return 1; }
}
