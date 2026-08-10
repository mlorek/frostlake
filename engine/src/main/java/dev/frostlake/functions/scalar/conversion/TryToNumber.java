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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * TRY_TO_NUMBER / TRY_TO_DECIMAL / TRY_TO_NUMERIC — TO_NUMBER's full signature
 * (expr [, format] [, precision, scale]) returning NULL instead of erroring, INCLUDING the
 * scale handling: with no scale the result rounds to a whole number like TO_NUMBER's NUMBER(38,0)
 * default. (Previously the precision/scale arguments were silently ignored.)
 */
public class TryToNumber extends BuiltInFunction {

    private static final ToNumber BASE = new ToNumber();

    public TryToNumber() { super("TRY_TO_NUMBER", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        try {
            return BASE.evaluate(args);
        } catch (final Exception notNumeric) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 4; }
}
