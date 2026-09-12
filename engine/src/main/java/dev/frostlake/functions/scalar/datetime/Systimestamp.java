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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.executor.StatementClock;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.DateTimeType;

import java.util.List;

/**
 * SYSTIMESTAMP() - the statement's instant in the session's zone, as TIMESTAMP_LTZ. Despite the name
 * it is not SYSDATE: that one answers UTC and is TIMESTAMP_NTZ, where this one carries the session's
 * offset the way CURRENT_TIMESTAMP does (live-verified).
 */
public class Systimestamp extends BuiltInFunction {
    public Systimestamp() { super("SYSTIMESTAMP", DateTimeType.TIMESTAMP_LTZ); }

    @Override
    public Object evaluate(final List<Object> args) { return StatementClock.now(); }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
