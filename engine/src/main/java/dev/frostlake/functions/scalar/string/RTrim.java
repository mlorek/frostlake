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

public class RTrim extends TextArgumentFunction {
    public RTrim() {
        super("RTRIM", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        // Snowflake RTRIM(expr [, chars]): trims every character in the set (default whitespace).
        final String chars = args.size() > 1 && args.get(1) != null ? args.get(1).toString() : null;
        final int start = 0;
        int end = str.length();
        
        while (end > start && trimmed(str.charAt(end - 1), chars)) { end--; }
        return str.substring(start, end);
    }

    private static boolean trimmed(final char c, final String chars) {
        return chars == null ? Character.isWhitespace(c) : chars.indexOf(c) >= 0;
    }

    @Override
    public int getMinArgCount() { return 1; }

    @Override
    public int getMaxArgCount() { return 2; }
}
