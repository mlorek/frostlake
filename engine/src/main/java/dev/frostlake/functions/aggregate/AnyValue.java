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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * ANY_VALUE(expr) — an arbitrary non-NULL value from the group (this implementation keeps the first
 * one it sees), or NULL when the group holds none. Registered as VARIANT so it can carry a value of
 * any type.
 */
public class AnyValue extends AggregateFunction {
    /** Registers the aggregate as {@code ANY_VALUE} returning VARIANT. */
    public AnyValue() { super("ANY_VALUE", VariantType.VARIANT); }

    @Override
    public Accumulator createAccumulator() { return new AnyValueAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

}
