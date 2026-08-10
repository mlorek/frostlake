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
import dev.frostlake.types.BooleanType;

import java.util.List;

public class ToBoolean extends BuiltInFunction {
    public ToBoolean() { super("TO_BOOLEAN", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final Object arg = args.get(0);
        // Numeric input: 0 is false, any non-zero value is true (Snowflake).
        if (arg instanceof Number) {
            return ((Number) arg).doubleValue() != 0.0;
        }
        // Text input: only the recognized truthy/falsy tokens (a bare numeric string like '2' errors).
        final String v = arg.toString().trim().toUpperCase();
        switch (v) {
            case "TRUE": case "T": case "YES": case "Y": case "ON": case "1":
                return true;
            case "FALSE": case "F": case "NO": case "N": case "OFF": case "0":
                return false;
            default:
                throw new RuntimeException("Cannot convert to BOOLEAN: " + arg);
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
