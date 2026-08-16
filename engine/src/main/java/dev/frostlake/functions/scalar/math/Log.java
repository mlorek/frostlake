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

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

public class Log extends NumericArgumentFunction {
    public Log() { super("LOG", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake's LOG is strictly two-argument: LOG(base, x) (live-verified — LOG(10) is
        // "not enough arguments ... expected 2, got 1").
        if (args.get(0) == null || args.get(1) == null) return null;
        final double base = ((Number) args.get(0)).doubleValue();
        final double num = ((Number) args.get(1)).doubleValue();
        if (base <= 0 || base == 1 || num <= 0) {
            // Live-verified wording: "Invalid floating point operation: log(10,-1)".
            throw new RuntimeException("Invalid floating point operation: log("
                + trimNumber(args.get(0)) + "," + trimNumber(args.get(1)) + ")");
        }
        return Math.log(num) / Math.log(base);
    }

    private static String trimNumber(final Object value) {
        final String text = value.toString();
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
