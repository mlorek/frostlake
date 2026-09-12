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
import dev.frostlake.types.DateTimeType;

import java.util.List;

/**
 * TRY_TO_TIME(expr [, format]) — the non-throwing form of TO_TIME: delegates to {@link ToTime} and returns
 * NULL instead of raising when the input cannot be parsed as a time. NULL input yields NULL. The optional
 * format is applied as in {@link ToTime}: an input the model cannot read is NULL, and so is a NULL format.
 */
public class TryToTime extends BuiltInFunction {
    private final ToTime base = new ToTime();

    public TryToTime() { super("TRY_TO_TIME", DateTimeType.TIME); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        try {
            return base.evaluate(args);
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
