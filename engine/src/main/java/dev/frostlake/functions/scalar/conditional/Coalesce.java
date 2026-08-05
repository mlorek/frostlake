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

package dev.frostlake.functions.scalar.conditional;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.VariantType;

import java.util.List;

public class Coalesce extends BuiltInFunction {
    public Coalesce() { super("COALESCE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        for (final Object arg : args) {
            if (arg != null) return arg;
        }
        return null;
    }

    // Live-verified: COALESCE needs at least TWO arguments — SELECT COALESCE(1) fails
    // "not enough arguments for function [COALESCE(1)], expected 2, got 1". GREATEST/LEAST, by
    // contrast, accept a single argument, so this is not a family-wide rule.
    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
