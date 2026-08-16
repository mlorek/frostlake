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

import dev.frostlake.types.BooleanType;

import java.util.List;

public class BoolXorAgg extends BooleanAggregate {
    public BoolXorAgg() { super("BOOLXOR_AGG", new BooleanType()); }

    @Override
    public Accumulator createAccumulator() { return new BoolXorAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

}
