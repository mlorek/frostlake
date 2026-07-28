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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.StringType;

import java.util.List;

public class ConcatWs extends BuiltInFunction {
    public ConcatWs() { super("CONCAT_WS", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.isEmpty()) return "";
        // Snowflake: CONCAT_WS returns NULL if the separator or ANY value is NULL (it does not skip
        // NULLs the way MySQL does).
        if (args.get(0) == null) {
            return null;
        }
        final String sep = args.get(0).toString();
        final StringBuilder result = new StringBuilder();
        boolean first = true;
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i) == null) {
                return null;
            }
            if (!first) {
                result.append(sep);
            }
            result.append(SharedFunctionHelpers.textOf(args.get(i)));
            first = false;
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
