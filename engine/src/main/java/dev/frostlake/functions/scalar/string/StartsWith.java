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

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.BooleanType;

import java.util.List;

public class StartsWith extends TextArgumentFunction {
    public StartsWith() { super("STARTSWITH", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — a NULL haystack or needle yields NULL, not 0/false
        for (int i = 0; i < Math.min(args.size(), 2); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        if (args.get(0) == null || args.get(1) == null) return false;
        return args.get(0).toString().startsWith(args.get(1).toString());
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
