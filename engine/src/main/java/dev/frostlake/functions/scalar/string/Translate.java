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
import dev.frostlake.types.StringType;

import java.util.List;

public class Translate extends TextArgumentFunction {
    public Translate() { super("TRANSLATE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        String str = args.get(0).toString();
        String from = args.get(1) == null ? "" : args.get(1).toString();
        String to   = args.get(2) == null ? "" : args.get(2).toString();
        StringBuilder result = new StringBuilder();
        for (final char c : str.toCharArray()) {
            int idx = from.indexOf(c);
            if (idx < 0) {
                result.append(c);
            } else if (idx < to.length()) {
                result.append(to.charAt(idx));
            }
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 3; }
}
