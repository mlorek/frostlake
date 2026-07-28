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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.SnowflakeDateFormat;
import dev.frostlake.functions.scalar.SnowflakeNumberFormat;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.List;

public class ToChar extends BuiltInFunction {
    public ToChar() { super("TO_CHAR", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final Object value = args.get(0);
        if (args.size() >= 2 && args.get(1) != null) {
            final String format = args.get(1).toString();
            if (!format.isEmpty()) {
                // The format model is chosen by the input type: numeric vs date/time.
                if (value instanceof Number) {
                    return SnowflakeNumberFormat.format(new BigDecimal(value.toString()), format);
                }
                if (value instanceof Temporal) {
                    return SnowflakeDateFormat.format(value, format);
                }
                // A DATE/TIMESTAMP value can reach a function as its ISO string; apply the date model
                // when the format uses date/time elements and the string parses as a temporal. A plain
                // (non-temporal) string falls through to the verbatim value below.
                if (value instanceof String && SnowflakeDateFormat.isDateFormat(format)) {
                    try {
                        return SnowflakeDateFormat.format(value, format);
                    } catch (final RuntimeException ignored) {
                        // not a temporal value
                    }
                }
            }
        }
        // No format: temporals render in Snowflake's default output forms (space + FF3), not java.time's.
        return SharedFunctionHelpers.textOf(value);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
