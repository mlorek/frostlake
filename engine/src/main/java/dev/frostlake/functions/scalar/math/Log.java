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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

public class Log extends BuiltInFunction {
    public Log() { super("LOG", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;

        if (args.size() == 1) {
            double num = ((Number) args.get(0)).doubleValue();
            if (num <= 0) throw new RuntimeException("LOG requires a positive number");
            return Math.log10(num);
        } else {
            double base = ((Number) args.get(0)).doubleValue();
            double num = ((Number) args.get(1)).doubleValue();
            if (base <= 0 || base == 1 || num <= 0) {
                throw new RuntimeException("LOG requires positive numbers and base != 1");
            }
            return Math.log(num) / Math.log(base);
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
