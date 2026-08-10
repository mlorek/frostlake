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

public class Soundex extends TextArgumentFunction {
    public Soundex() { super("SOUNDEX", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String s = args.get(0).toString().toUpperCase().replaceAll("[^A-Z]", "");
        if (s.isEmpty()) return "";
        final String codes = "01230120022455012623010202";
        final StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(0));
        char prev = codes.charAt(s.charAt(0) - 'A');
        for (int i = 1; i < s.length() && sb.length() < 4; i++) {
            final char code = codes.charAt(s.charAt(i) - 'A');
            if (code != '0' && code != prev) sb.append(code);
            prev = code;
        }
        while (sb.length() < 4) sb.append('0');
        return sb.toString();
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
